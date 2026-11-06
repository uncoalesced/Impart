<!-- Engineered by uncoalesced -->
# Architecture & Technology Stack

---

## 1. Technology Stack

| Layer | Choice | Notes |
|---|---|---|
| Platform | Native Android | Min SDK 26 (Android 8.0), Target SDK 35 |
| Language | Kotlin | 100% Kotlin, no Java interop by design |
| Third-party code | Tink, SQLCipher, ZXing, AndroidX, Hilt | No Google Play services, Firebase or ML Kit (T21); every version pinned in `gradle/libs.versions.toml` |
| UI Toolkit | Jetpack Compose | Exclusively. No XML layouts, no Fragments |
| Dependency Injection | Dagger Hilt | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject` constructor injection |
| Transport / Signaling | **Encrypted SMS only** (`SmsManager` send; `SMS_RECEIVED`, or `SMS_DELIVER` in the optional default SMS app mode) | Wire format v3: 612 characters, per-pair tag. No Firebase, no Cloud Functions, no Google delivery path |
| Backend / Logic | **None** | Peer-to-peer over the carrier SMS channel; there is no server |
| Local Storage (structured) | Room Database (version 5), SQLCipher-encrypted | The Contact Matrix (with the re-pair flag) and the seen-alert IDs used against replay. The key lives in the encrypted preferences and dies with the duress wipe |
| Local Storage (key-value) | EncryptedSharedPreferences (one instance, `SecurePrefsManager`), plus one plain boolean for the decoy | Node UUID, own phone number, settings, dead man's switch schedule, salted PIN hashes and the lockout state, the SQLCipher key, accepted legal version. The PIN HMAC key and the master keys live in the Android Keystore |
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
    Rly->>Rly: pad, sign, encrypt payload (Tink), prefix the pair tag
    Rly->>Net: SmsManager multipart send
    Net->>OS: deliver SMS, wake modem/RIL out of Doze
    OS->>Rcv: broadcast SMS_RECEIVED
    Rcv->>Rcv: 612-char base64 body? acquire PARTIAL_WAKE_LOCK (30s), goAsync, match a contact pair tag
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

The doctrine is no third-party delivery infrastructure in the loop, at all. A push relay (FCM or otherwise) means Google's servers see who paged whom and when, decide whether and when to deliver, and can throttle or drop the message. For an emergency tool that is an unacceptable dependency and an unacceptable metadata leak.

SMS is a peer-to-peer carrier primitive. An incoming SMS is delivered by the modem/RIL and raises the `SMS_RECEIVED` broadcast on the receiver even when the app is killed or the device is in Doze. It is the same wake path an ordinary text uses, which no app-level battery optimizer can suppress. Impart carries only ciphertext in the message body (a 12-character pair tag + Tink output, padded to 612 characters), so the carrier relays an opaque text and never sees plaintext. `ImpartSmsReceiver` recognises the shape and the tag and hands control back to Impart's own code the instant the SMS arrives, which is what gets the wake lock, full-screen intent and alarm audio chain to fire.

Trade-offs, stated honestly: SMS requires a SIM with an SMS plan and cellular coverage; delivery latency is typically 1 to 5 s but is carrier-dependent and not guaranteed; and unless the user makes Impart the default SMS app (optional, T14), the ciphertext SMS also lands in the stock Messages inbox on the receiver, as 612 characters behind a per-pair tag that does not name the app. These are accepted costs of removing every server from the path.

## 4. The intrusion pipeline, mechanic by mechanic

### 4.1 Ingress: `RelayManagerImpl` → `SmsManager`

`RelayManagerImpl.dispatchPanic` runs entirely on the sender's device. It builds a `PanicPayload.Panic` (its own UUID, the recipient's UUID, a fresh random message ID, the time, and lat/lng only if location sharing is on). `AlertEnvelope` signs the serialized payload with the node's Ed25519 key and Tink-encrypts `<signature>.<payload>` to the contact's hybrid public key. The relay then prefixes the pair tag and hands the 612-character body to `SmsManager` as a multipart SMS. Each part carries a sent-intent aimed at `SmsSentReceiver`; a part the radio refuses becomes a notification rather than a log line. There is no ingress server. The "network" is the carrier's SMS channel.

### 4.2 The Brain: `ImpartSmsReceiver`

`ImpartSmsReceiver` is a `BroadcastReceiver` registered for `android.provider.Telephony.SMS_RECEIVED`. `onReceive` reassembles the multipart message body and, if it has the shape of a v3 alert (exactly 612 characters: a 12-character pair tag and base64), begins the intrusion sequence. In strict order:

1. Apply the syntactic filter and ignore everything else. A body with the old `IMPART_ALERT:` prefix is reported as outdated. If the node has been wiped by the duress PIN, stop here and stay dark. Under the wake lock, `AlertRecognizer` matches the tag against the pair tags of the active contacts; no match means the text is not an alert for this node.

   When Impart is the default SMS app (T14, optional), `ImpartSmsReceiver` steps aside and `SmsDeliverReceiver` receives `SMS_DELIVER` instead. `SmsDeliverDispatcher` routes an alert-shaped body into the same `AlertIntake` without touching the Telephony provider, and stores any other text in the provider like a normal SMS app. `AlertIntake` holds the pipeline from step 2 on, so both receivers run identical code.
2. Acquire the wake lock (below), call `goAsync()`, and move to the IO dispatcher. Nothing after this point runs on the main thread.
3. Hand the ciphertext to `AlertVerifier`, which decrypts, splits the Ed25519 signature from the payload, finds the sender by UUID among active contacts, verifies the signature with the key stored at pairing, checks the alert is addressed to this node, checks its age (at most 24 hours old, at most 10 minutes in the future) and records its ID so it can never be accepted twice. The exact order and reasoning are in `03_SECURITY_PROTOCOL.md` section 4.3.
4. Every verdict except acceptance posts a visible notification on the status channel: unreadable, malformed, outdated, unrecognized node, failed verification, misdirected, impossible time, too old, or repeated. None of them raises the alarm.
5. An accepted panic fires the full-screen-intent notification on the panic channel (`CATEGORY_ALARM`, `PRIORITY_MAX`, alarm-stream sound on the channel itself). An accepted acknowledgement posts "acknowledged by" with the contact's name.
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

`setShowWhenLocked(true)` and `setTurnScreenOn(true)` are the API 27+ replacements for the deprecated `FLAG_SHOW_WHEN_LOCKED` and `FLAG_TURN_SCREEN_ON` window flags; API 26 still gets the flags. `requestDismissKeyguard()` dismisses an insecure keyguard outright; on a secure keyguard it surfaces the activity above the lock screen and the user still authenticates to fully unlock. The intrusion screen renders regardless. The activity is `singleTop`, so a second panic while the first is showing updates the screen through `onNewIntent` instead of stacking a second alarm. It cancels the notification it was launched from, shows the sender's display name and when the panic was sent (with the delay if it arrived late), and blocks screenshots. On acknowledge it looks the contact up again and sends a signed, encrypted ACK to the phone number stored at pairing. It deliberately sits outside the app lock: the alarm and its acknowledgement must work over the lock screen, and this activity shows nothing beyond the one alert.

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
- The dashboard's Node Readiness card checks `isIgnoringBatteryOptimizations` and, on Android 14, `canUseFullScreenIntent`, and opens the right Settings page for each. Since T12 it also opens the per-OEM autostart or background screen (the "Don't kill my app" list, kept in code in `OemSettings`), which the user confirms.

Separately, SMS delivery depends on a SIM with SMS service and cellular coverage; there is no data/Wi-Fi fallback by design (that would put a server back in the loop). Impart does not claim reliable delivery on non-stock OEM skins or without cellular signal. The readiness card is the onboarding step: it asks for the battery exemption and the full-screen permission, and on Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus and Samsung it opens the maker's own background settings, which the user confirms because they cannot be read back.

## 6. Dependency Injection Topology

Two Hilt modules, both in `SingletonComponent`:

- `DatabaseModule` provides the Room database, `ContactDao` and `SeenAlertDao`.
- `RepositoryModule` binds `ContactRepository` to `ContactRepositoryImpl`, `RelayManager` to `RelayManagerImpl` (the SMS dispatcher), `KeysetSource` to `KeystoreHelper`, `SeenAlertStore` to `SeenAlertStoreImpl`, and `WallClock` to `SystemWallClock`.

Everything else (`CryptoManager`, `KeystoreHelper`, `AlertVerifier`, `AppLockManager`, the other managers, `SecurePrefsManager`, `LegalDocumentLoader`, `NodeReadinessChecker`) has an `@Inject` constructor, and the stateful ones are `@Singleton`. Framework services such as `PowerManager` are fetched from the context where they are used. The file map is in `04_FILE_STRUCTURE_AND_STATE.md`.
