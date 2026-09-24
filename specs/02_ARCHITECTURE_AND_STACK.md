<!-- Engineered by uncoalesced -->
# Architecture & Technology Stack

---

## 1. Technology Stack

| Layer | Choice | Notes |
|---|---|---|
| Platform | Native Android | Min SDK 26 (Android 8.0), Target SDK 35 |
| Language | Kotlin | 100% Kotlin, no Java interop by design |
| UI Toolkit | Jetpack Compose | Exclusively. No XML layouts, no Fragments |
| Dependency Injection | Dagger Hilt | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject` constructor injection |
| Transport / Signaling | **Encrypted SMS only** (`SmsManager` send, `SMS_RECEIVED` `BroadcastReceiver`) | No Firebase, no Cloud Functions, no Google delivery path |
| Backend / Logic | **None** | Peer-to-peer over the carrier SMS channel; there is no server |
| Local Storage (structured) | Room Database | Stores the Contact Matrix. Not encrypted at rest yet; SQLCipher is a roadmap item |
| Local Storage (key-value) | EncryptedSharedPreferences (one instance, `SecurePrefsManager`) | Node UUID, own phone number, location toggle, dead man's switch schedule, PINs, wiped flag |
| Auth | None | Identity is a locally generated UUID; trust is established only by the physical QR handshake |

## 2. Architectural Style: Event-Driven, OS-First

Impart is built around a single governing constraint: **the app cannot be trusted to be running.** Android will suspend it, Doze it, or kill it. Any architecture that assumes otherwise is already broken.

The solution is an Event-Driven Architecture (EDA) where the *event*, an incoming SMS, is capable of resurrecting the app from a fully stopped state. The flow is OS-first: the operating system, not the application, is the thing waking up. The application is just what runs once the OS has already done the hard part.

```mermaid
sequenceDiagram
    participant A as Node A (Sender)
    participant Rly as RelayManagerImpl
    participant Net as Carrier SMS network
    participant OS as Node B (OS)
    participant Rcv as ImpartSmsReceiver
    participant UI as IntrusionActivity

    A->>Rly: Initiate Panic (target phone number, public key)
    Rly->>Rly: encrypt payload (Tink), prefix IMPART_ALERT:
    Rly->>Net: SmsManager multipart send
    Net->>OS: deliver SMS, wake modem/RIL out of Doze
    OS->>Rcv: broadcast SMS_RECEIVED
    Rcv->>Rcv: match IMPART_ALERT:, acquire PARTIAL_WAKE_LOCK (30s), goAsync
    Rcv->>Rcv: decrypt ciphertext (Tink); failure posts a visible status notification
    Rcv->>Rcv: look up sender public key in the Contact Matrix; unknown sender gets no alarm
    Rcv->>OS: fire FullScreenIntent notification
    OS->>UI: launch over keyguard
    UI->>UI: setShowWhenLocked, setTurnScreenOn
    UI->>OS: requestDismissKeyguard()
    UI->>UI: play alarm audio via USAGE_ALARM, bypass DND
```

## 3. Why SMS, Not a Push Relay

This is the single most important transport decision in the system, so it's worth being explicit about why.

The doctrine is **no third-party delivery infrastructure in the loop, at all.** A push relay (FCM or otherwise) means Google's servers see who paged whom and when, decide whether and when to deliver, and can throttle or drop the message. For an emergency tool that is an unacceptable dependency and an unacceptable metadata leak.

**SMS is a peer-to-peer carrier primitive.** An incoming SMS is delivered by the modem/RIL and raises the `SMS_RECEIVED` broadcast on the receiver even when the app is killed or the device is in Doze. It is the same wake path an ordinary text uses, which no app-level battery optimizer can suppress. Impart carries only ciphertext in the message body (`IMPART_ALERT:` + Tink output), so the carrier relays an opaque text and never sees plaintext. `ImpartSmsReceiver` matches the prefix and hands control back to Impart's own code the instant the SMS arrives, which is what guarantees the WakeLock → FullScreenIntent → Alarm-Audio chain actually fires.

Trade-offs, stated honestly: SMS requires a SIM with an SMS plan and cellular coverage; delivery latency is typically 1–5 s but is carrier-dependent and not guaranteed; and because Impart is not the default SMS app, it cannot `abortBroadcast()`, so the ciphertext SMS also lands in the stock Messages inbox on the receiver. These are accepted costs of removing every server from the path.

## 4. The intrusion pipeline, mechanic by mechanic

### 4.1 Ingress: `RelayManagerImpl` → `SmsManager`

`RelayManagerImpl.dispatchPanic` runs entirely on the sender's device: it encodes a `PanicPayload.Panic` (type, urgency, the sender's own phone number and public key for the return ACK, optional lat/lng) with `PanicPayloadCodec`, Tink-encrypts it to the target contact's public key, prefixes `IMPART_ALERT:`, resolves the default SMS subscription (so dual-SIM phones send from the right SIM, and a phone with no SIM fails with a message instead of pretending), splits it with `divideMessage`, and sends it with `sendMultipartTextMessage`. Each part carries a sent-intent aimed at `SmsSentReceiver`; a part the radio refuses becomes a notification rather than a log line. There is no ingress server. The "network" is the carrier's SMS channel.

### 4.2 The Brain: `ImpartSmsReceiver`

`ImpartSmsReceiver` is a `BroadcastReceiver` registered for `android.provider.Telephony.SMS_RECEIVED`. `onReceive` reassembles the multipart message body and, if it starts with `IMPART_ALERT:`, begins the intrusion sequence. In strict order:

1. Match the `IMPART_ALERT:` prefix and ignore everything else. If the node has been wiped by the duress PIN, stop here and stay dark.
2. Acquire the wake lock (below), call `goAsync()`, and move to the IO dispatcher. Nothing after this point runs on the main thread.
3. Decrypt with Tink. A failure posts an "unreadable alert" notification on the status channel. A payload that decrypts but does not parse posts "malformed alert". Either way something is visible.
4. Look the sender's public key up in the Contact Matrix. A sender that is not there, or has been revoked, gets a "panic from unrecognized node" heads-up and no alarm. Trust is the handshake, not possession of the key.
5. Fire the full-screen-intent notification on the panic channel (`CATEGORY_ALARM`, `PRIORITY_MAX`, alarm-stream sound on the channel itself), or post the ACK notification.
6. Release the wake lock and finish the async result.

`abortBroadcast()` is not called. `SMS_RECEIVED` is not an ordered broadcast, so the call always threw and was always swallowed. The ciphertext text lands in the stock Messages inbox; that is accepted.

### 4.3 WakeLock Acquisition

```kotlin
val wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "Impart::IntrusionWakeLock"
).apply { acquire(30_000L) } // 30s timeout: enough to decrypt and fire the intent, not a second longer
```

A `PARTIAL_WAKE_LOCK` keeps the CPU running without necessarily lighting the screen. Screen-on is handled separately, deliberately, via the full-screen intent flags on the activity itself. The 30-second timeout is a safety valve: if something in the decrypt/build pipeline hangs, the wake lock releases itself rather than draining the battery indefinitely.

### 4.4 The Bypass: `FullScreenIntent`

```kotlin
val pendingIntent = PendingIntent.getActivity(
    context, 0, intrusionIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("IMPART: PANIC SIGNAL")
    .setPriority(NotificationCompat.PRIORITY_MAX)
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .setFullScreenIntent(pendingIntent, true)
    .build()
```

`setFullScreenIntent(pendingIntent, true)` is the Android-sanctioned mechanism for launching an activity directly over the lock screen, the same API used by incoming call screens and alarm clock apps. It requires the `USE_FULL_SCREEN_INTENT` manifest permission, already granted in the current build. As of Android 14 (API 34), this permission is auto-granted at install only to apps the Play Store categorizes as calling/alarm apps; other apps require the user to manually enable it via Settings. This is a real risk to track as Impart moves past sideloading on your own devices toward any wider distribution. The readiness card checks `canUseFullScreenIntent()` and opens the setting.

### 4.5 The Face: `IntrusionActivity`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setShowWhenLocked(true)
    setTurnScreenOn(true)
    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    keyguardManager.requestDismissKeyguard(this, null)
    setContent { IntrusionScreen(...) }
}
```

`setShowWhenLocked(true)` and `setTurnScreenOn(true)` are the API 27+ replacements for the deprecated `FLAG_SHOW_WHEN_LOCKED` and `FLAG_TURN_SCREEN_ON` window flags; API 26 still gets the flags. `requestDismissKeyguard()` dismisses an insecure keyguard outright; on a secure keyguard it surfaces the activity above the lock screen and the user still authenticates to fully unlock. The intrusion screen renders regardless. The activity is `singleTop`, so a second panic while the first is showing updates the screen through `onNewIntent` instead of stacking a second alarm. It cancels the notification it was launched from, shows the sender's display name, and on acknowledge sends the encrypted ACK back to the phone number carried in the payload.

### 4.6 Audio Override: `USAGE_ALARM`

```kotlin
val audioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
```

Routing playback through `USAGE_ALARM` places it in the same audio stream as the device's own alarm clock, the one stream Do Not Disturb deliberately does not silence. `IntrusionAlarm` loops the device's default alarm tone (falling back to the ringtone, then the notification sound) on a `MediaPlayer` with those attributes, raises the alarm stream to maximum for the duration and restores it on stop, and drives the vibrator with a repeating pattern. The panic notification channel carries the same alarm-stream sound, so even when Android 14 withholds the full-screen intent the heads-up is loud.

## 5. Doze mode and OEM battery management: honest caveats

Incoming SMS wakes the modem/RIL and raises `SMS_RECEIVED` even under **stock Android's** Doze Mode. That is the core reason SMS was chosen over a push relay. It does **not** automatically survive every OEM's custom battery management layer, which can still deny a backgrounded app the CPU/autostart it needs to run the receiver to completion. Be upfront about this, because it's a real gap:

- **Xiaomi (MIUI/HyperOS)** and other Chinese OEM skins aggressively kill background apps and require the user to manually disable battery restrictions and enable "Autostart" for Impart.
- **Samsung (One UI)** has historically applied similar restrictions under "Put unused apps to sleep" / "Deep sleeping apps" lists.
- The dashboard's Node Readiness card checks `isIgnoringBatteryOptimizations` and, on Android 14, `canUseFullScreenIntent`, and opens the right Settings page for each. Per-OEM deep links (the "Don't kill my app" intent list) are still a roadmap item.

Separately, SMS delivery depends on a SIM with SMS service and cellular coverage; there is no data/Wi-Fi fallback by design (that would put a server back in the loop). Impart should not overstate its reliability guarantee on non-stock OEM skins, or where there is no cellular signal, until this onboarding step exists.

## 6. Dependency Injection Topology

Two Hilt modules, both in `SingletonComponent`:

- `DatabaseModule` provides the Room database and `ContactDao`.
- `RepositoryModule` binds `ContactRepository` to `ContactRepositoryImpl`, `RelayManager` to `RelayManagerImpl` (the SMS dispatcher), and `KeysetSource` to `KeystoreHelper`.

Everything else (`CryptoManager`, `KeystoreHelper`, the managers, `SecurePrefsManager`, `NodeReadinessChecker`) is a `@Singleton` with an `@Inject` constructor. Framework services such as `PowerManager` are fetched from the context where they are used. The file map is in `04_FILE_STRUCTURE_AND_STATE.md`.
