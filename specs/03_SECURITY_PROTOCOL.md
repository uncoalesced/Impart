<!-- Engineered by uncoalesced -->
# Security protocol: trust no one

---

## 1. Threat model

The network carrying a message is not trusted with its contents. Impart's transport is the carrier SMS channel: the mobile operator and any SS7 intermediary. It is trusted to move ciphertext from A to B and nothing else. There is no Google infrastructure (no Firebase, no Cloud Functions, no FCM) and no Impart server anywhere in the path.

So anything that would mean something to an attacker, or to the carrier, never exists in plaintext outside the two phones. And because anyone who knows a phone number can text it, the receiving phone treats every incoming alert as hostile until it proves otherwise.

## 2. Identity

Every node has a version-4 UUID, generated on first launch. It is not derived from a phone number, email or device serial. A new UUID is a new identity, and every pairing has to be redone.

A node also holds two Tink keysets, both generated on first launch:

- A hybrid keyset (HPKE: X25519, HKDF-SHA256, AES-256-GCM). Alerts to this node are encrypted to its public half.
- A signing keyset (Ed25519). Every alert this node sends is signed with it.

Both keysets are stored in app-private preferences, encrypted by an AES key that lives in the Android Keystore (in secure hardware where the phone has it). The keysets are decrypted into app memory when used. The public halves travel in the QR code. The private halves never leave the phone.

### 2.1 The lock

The app lock asks for a PIN on every process start and after 30 seconds in the background. Two PINs exist: the standard PIN opens the app, the duress PIN wipes it (section 6, A6). Neither is stored.

- `KeystorePinHasher` holds a non-exportable HMAC-SHA256 key under the alias `impart_pin_mac`. StrongBox is requested first and the TEE is the fallback. The key can sign and nothing else; it cannot be read out, so an attacker with a copy of the preferences file and the master key still has to ask this phone's hardware for every guess.
- For each PIN `PinVerifier` stores a random 16-byte salt and `HMAC(key, salt || pin)` in the encrypted preferences (`SecurePrefsManager` implements `PinStore`). The same PIN gives different records on different phones and after every change.
- A candidate is checked against both records on every call with `MessageDigest.isEqual`, so the comparison takes the same time whether the first digit was right or not.
- The published defaults 1234 and 9999 are only a refusal list at setup. No read path falls back to them. Until the user has chosen PINs, nothing unlocks and nothing wipes.
- Builds before November 2026 stored the PINs as strings. On first run of the new code the plaintext values are hashed and removed in the same preferences commit.
- The duress wipe deletes the HMAC key, so even an intact record can never be verified again.

PIN digits travel through the app as character arrays that are zeroed after use (T06). The JVM may still copy them; that is a reduction of exposure, not a guarantee.

### 2.2 Lockouts

Four wrong PINs are free. From the fifth, the lock refuses PINs for 30 seconds, doubling each time up to 15 minutes. The counters are built to survive the obvious ways of resetting them:

- The failed count and the remaining wait are written with a synchronous commit before the lock screen hears the answer, so force-stopping the app right after a wrong guess still counts it.
- The wait is measured on `SystemClock.elapsedRealtime()`, which keeps counting through deep sleep and cannot be set by the user. The wall clock is recorded but never used to shorten a wait, so setting the date back or forward changes nothing.
- The boot count (`Settings.Global.BOOT_COUNT`) is stored with the wait. On a different boot the elapsed clock has restarted, so the lock resumes the full remaining wait and re-anchors it on the new boot. A reboot can only lengthen a lockout.
- Only a correct standard PIN or the first PIN setup clears the counter. Nothing else in the app touches it; wiping the app's data is the only other way.
- The duress PIN is checked before the lockout, so it works at any time.
- Optional hard cap (Joel, 2026-11-04): Settings has "Wipe after 20 wrong PINs", off by default and changed only behind the standard PIN. When on, the twentieth consecutive failure runs the duress wipe. With the lockouts in front of it, reaching twenty takes more than two hours of holding the phone.

## 3. The handshake

Trust is established one way only: scanning a QR code in person.

```mermaid
sequenceDiagram
    participant A as Device A
    participant B as Device B

    Note over A: QR v2: UUID_A, Phone_A,<br/>HybridKey_A, SigningKey_A
    A->>B: Display QR (physical presence)
    B->>B: Scan, validate keys and number
    B->>B: Store A in the Contact Matrix
    Note over B: QR v2: UUID_B, Phone_B,<br/>HybridKey_B, SigningKey_B
    B->>A: Display QR
    A->>A: Scan, validate, store B
    Note over A,B: Both read their key fingerprints aloud.<br/>No server brokered anything.
```

The QR payload is `{v: 2, uuid, phoneNumber, publicKey, signingKey}`. The phone number is typed in by the user on the My QR screen, because Android cannot read it reliably from the SIM. It has to be a full international number (`+`, country code, 8 to 15 digits). Short codes and local formats are refused on both sides, so a QR cannot point panics, or the automatic dead man's switch, at a premium-rate number.

When a QR is scanned:

- A v1 QR (no signing key) is refused with "update both phones".
- Keys that Tink cannot parse as the right type are refused.
- The node's own QR is refused.
- A new UUID is added.
- A known UUID with the same keys is a re-pair. The nickname is kept, the number is updated, and a revoked contact becomes active again.
- A known UUID with different keys is not written. The user sees a dialog with the old and new key fingerprints and decides. That is what a reinstall looks like, and it is also what a QR built to hijack an existing contact looks like.

Each node shows an 80-bit key fingerprint (SHA-256 over both public keys, five groups of four hex digits) under its QR and next to every contact. Reading it aloud while pairing confirms the QR really came from the phone in front of you.

Pairing is two-way: both phones scan each other.

## 4. Alerts

### 4.1 Payload

The plaintext of every alert, panic or acknowledgement, is a small JSON object with short keys to fit the SMS budget:

```json
{"v":3,"t":"P","from":"<sender uuid>","to":"<recipient uuid>","id":"<128-bit random, base64url>","ts":1790253160570,"lat":51.5,"lng":-0.12,"p":"<random padding>"}
```

- `t` is `P` for a panic or `A` for an acknowledgement. An acknowledgement carries `ack`, the ID of the panic it answers.
- `lat` and `lng` are present only when the sender turned on location sharing.
- `p` is random padding, sized so that `<signature>.<payload>` is always exactly 397 bytes. The receiver ignores it. It is inside the signature and the encryption, so nothing outside the ciphertext says how long the real payload was (section 6.1).
- There is no phone number or public key in the payload. The receiver uses what it stored at pairing.
- `v` is 3 since November 2026. A phone on v2 or earlier must update and re-pair: a v2 body is reported as outdated and never decrypted.

### 4.2 Sign, then encrypt

Send path:

1. Serialize the payload once, padded to the fixed length. That string is the payload from here on.
2. Sign its UTF-8 bytes with the sender's Ed25519 key.
3. Join as `<base64 signature>.<payload>`.
4. Encrypt that to the recipient's hybrid public key with Tink HPKE.
5. Base64, prefix the pair tag, and send as a multipart SMS from the default SMS SIM, with a sent-intent per part.

The pair tag (`PairTag`) is the first 12 base64url characters of `SHA-256("impart/v3/pair-tag" || 0 || min(pkA, pkB) || 0 || max(pkA, pkB))` over the two hybrid public keys the pair exchanged at the handshake. Both ends compute the same tag, no field is stored for it, and nothing in the body names the app. The tag is recognition only; the signature inside is what authenticates.

Every alert is exactly 612 characters, four SMS parts, whatever it carries. `AlertSizeTest` enforces this for a panic with and without coordinates, the worst case, and an acknowledgement.

The signature covers the exact bytes that travel, so the receiver verifies what was sent and nothing is re-serialized. Encryption comes last, so the carrier sees neither the payload nor who signed it.

### 4.3 Receive path

`ImpartSmsReceiver` first applies a syntactic filter with no I/O: a body is a candidate only if it is exactly 612 characters, starts with 12 tag characters and continues in base64. An ordinary text never costs a wake lock or a database read. A body starting with the old `IMPART_ALERT:` prefix is reported as outdated and never decrypted. The receiver does nothing at all if the node was wiped. Then, under `goAsync()` and a 30-second wake lock, `AlertRecognizer` computes the pair tag of every active contact and matches the body's tag; no match, no alert. For a match, `AlertVerifier` decides:

1. Decrypt with this node's hybrid key. Failure: unreadable.
2. Split the signature from the payload. A bare JSON plaintext is a v1 alert: outdated.
3. Parse the payload. A `v` below 3: outdated. Failure: malformed.
4. Find the sender by `from` among active contacts. Missing or revoked: unknown sender.
5. Verify the signature with the signing key stored for that contact. Failure: bad signature, which means someone is impersonating them.
6. Check the body's tag is the tag of this node and that sender. Otherwise: tag mismatch, which means a genuine alert was relayed under another pair's tag.
7. Check `to` is this node. Otherwise: not for this node. This stops a trusted contact forwarding a third node's signed alert.
8. Check the time. More than 10 minutes in the future: rejected. More than 24 hours old: stale, reported and not raised.
9. Record `id` in the seen-alerts table. Already there: replayed.
10. Accepted. A panic raises the full-screen alarm and shows the delay if it arrived late. An acknowledgement posts a notification.

The decrypted bytes are zeroed on every path out (section 4.4).

Every verdict other than acceptance posts a visible status notification. An alert whose tag matches no active contact is not an alert as far as this node is concerned and produces nothing, like any other text; that is the price of a body that does not name the app. Nothing about the payload is trusted until step 5 passes, and an ID is only recorded once the alert has passed every other check.

The acknowledgement goes back to the contact's stored phone number, encrypted to the contact's stored key. Nothing in the incoming alert decides where it goes.

### 4.4 Memory

Decrypted bytes live as briefly as the code can arrange. `AlertVerifier` decrypts into a `ByteArray`, splits the signature from the payload bytes, verifies the signature over those exact bytes, and zeroes both arrays in a `finally` block whatever the verdict. PIN digits travel as `CharArray`s (`PinEntry`, `PinVerifier`) that are zeroed after each use, and the bytes handed to the HMAC are zeroed too. `ZeroingTest` holds references to these buffers and checks them after the call.

The limit is the JVM: the JSON parser needs a `String`, strings are immutable objects the garbage collector copies and keeps until it collects them, and Compose state and intents hold the sender's name and coordinates for as long as the intrusion screen shows them. Zeroing shortens the exposure window; it does not close it. Code running as Impart's own UID can still read what is on screen (A2-5, A2-8).

### 4.5 Why these primitives

HPKE gives every message a fresh ephemeral key, so a QR only carries public keys and watching the scan does not let anyone decrypt anything. The pre-shared-secret alternative was rejected for exactly that reason. HPKE alone does not authenticate the sender, because anyone with the recipient's public key can encrypt to it. That is why every alert is also signed with Ed25519, and why the verifier checks the signature against the key stored at pairing, never a key named inside the message.

## 5. What the carrier can and cannot see

This section is a security requirement in itself. An honest threat model does not overclaim.

The carrier, and any SMS or SS7 intermediary, can see:

- That the sender's number texted the receiver's number, and when.
- That it took four SMS parts of base64 text, 612 characters, like every other Impart alert.
- The 12-character pair tag at the front, which is the same for every alert between those two phones and means nothing to anyone without both public keys.

The carrier cannot see the contents, the sender's identity inside the envelope, whether it is a panic or an acknowledgement, whether coordinates were attached, or the name of the app.

Impart gives confidentiality and authenticity of content, not metadata privacy. Over SMS the metadata (two real phone numbers and the timing) is arguably more exposing than a push token would be. That is the trade for removing every server from the loop and getting a wake path Doze cannot throttle.

A copy of each alert also lands in the phone's normal Messages app, because Android stores texts for apps that are not the default SMS app. Those copies cannot be decrypted, and the replay defence makes them useless to re-send, but they do show who exchanged alerts with whom until the user deletes them.

## 6. Attack surface and mitigations

This table is the authoritative result of the adversarial audit (task T01, 4 November 2026). It absorbs and renumbers the rows from the 24 September 2026 review of branch `claude/impart-kotlin-refactor-t8fwjh`. The adversaries are defined in `specs/fable/00_BRIEF.md` section 4.

Status values: **mitigated** (the attack fails, and a test or a cited line proves it), **partly** (the attack is made harder, residual risk stated), **open** (nothing stops it yet; the owner task closes it), **out of reach** (no app can stop it; written down so nobody believes otherwise).

### A1: phone thief

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A1-1 | Open the app on an unlocked phone and read the contacts | mitigated | `AppLockManager` starts every process locked, locks the moment the screen turns off (`MainActivity.onStop` with the screen not interactive) and relocks after 30 s in the background; `MainActivity` composes nothing behind the lock until the PIN passes | gate order in T17; `AppLockManagerTest` | T04 (done) |
| A1-2 | Guess the PIN with the phone in hand | mitigated | Four free attempts, then a 30 s lockout doubling to 15 min (`LockoutPolicy`), measured on the monotonic clock. Optional hard cap: 20 consecutive failures wipe the node (Settings, off by default; Joel, 2026-11-04) | `LockoutPolicyTest`, `AppLockManagerTest` | T03 (done) |
| A1-3 | Skip the lockout by changing the clock, rebooting or force-stopping the app | mitigated | The count and the remaining wait are committed before the answer is shown; the wait counts down on `elapsedRealtime` only; a changed boot count resumes the full remaining wait (section 2.1) | `AppLockManagerTest`: clock back, clock forward, reboot, force-stop, duress mid-lockout | T03 (done) |
| A1-4 | Read the PINs out of the app's storage with the phone's own privileges (ADB backup, `run-as` on a debug build, a file manager on a rooted phone) | mitigated | No PIN is stored. The preferences hold a salt and an HMAC under a non-exportable Keystore key (section 2.1) | `PinVerifierTest` | T02 (done) |
| A1-5 | Look at the recent-apps preview to see the last screen | mitigated | `WindowHardening.harden`: `FLAG_SECURE` on both activities and every dialog window, `setRecentsScreenshotEnabled(false)` on API 33+ | `WindowHardeningSourceTest` | T04 (done) |
| A1-6 | Send a fake panic from the stolen unlocked phone | partly | The thief holds the private keys, so the panic is genuine as far as the receivers know. The lock limits the window; the dead man's switch and the duress wipe do not help once the app is open. Contacts revoke a stolen node; the owner rotates on a new phone | n/a | T13 (done); documented |
| A1-7 | Install an "update" over a debug build to inherit its data | mitigated for release builds | The debug keystore is public, so a debug build stays a test build. The readiness card checks the installed certificate against the debug fingerprint, not only `BuildConfig.DEBUG`; release builds use the v2 and v3 schemes only and the deployment workflow refuses a debug-signed output | `SigningIdentityTest`; `verify-release-signature.sh` | T11 (done) |

### A2: forensic examiner

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A2-1 | Extract the contact database from a seized phone | mitigated | Room is SQLCipher-encrypted with a 256-bit random key held in EncryptedSharedPreferences under the Keystore master key (`DatabaseModule`, `SecurePrefsManager.databasePassphrase`); the wipe removes the file, the key and the master key (section 6.2) | manual (flight check); `DuressWiperTest` | done |
| A2-2 | Extract the Tink keysets | mitigated | Both keysets are wrapped by `android-keystore://impart_master_key`; the unwrapped keys exist only in app memory | `CryptoManagerTest` (JVM) | T06 |
| A2-3 | Brute-force the PINs offline from an extracted prefs file | mitigated | The HMAC key never leaves the Keystore, so every guess has to run on this phone, through the app, under the lockout (section 2.1). On the phone itself the lockout and the T03 hard cap apply | `PinVerifierTest`, `LockoutPolicyTest` | T02 (done) |
| A2-4 | Recover data the duress wipe missed | mitigated | Section 6.2: every preferences file, database and sidecar, directory and Keystore alias in `WipeInventory` is removed; only the plain decoy flag survives. Surviving flash blocks hold ciphertext whose keys are gone | `DuressWiperTest`, `DuressManagerTest` | T08 (done) |
| A2-5 | Read decrypted payloads or PINs from a memory dump | partly | Plaintext and payload bytes are zeroed after every verdict; PIN digits are CharArrays zeroed after use (section 4.4). The parser's `String` and the on-screen details cannot be zeroed | `ZeroingTest`, `PinEntryTest` | T06 (done); the rest is A2-8 |
| A2-6 | Read symbols and strings from the release APK to map the code | partly | R8 minifies and shrinks the release build; verbose, debug and info logs are compiled out; `mapping.txt` is kept as a CI artifact. Nothing secret is in the APK either way | `release-r8` job | T10 (done) |
| A2-7 | Restore a cloud or device-to-device backup onto an examiner's phone | mitigated | `allowBackup="false"`, `backup_rules.xml` and `data_extraction_rules.xml` exclude every domain; Keystore-wrapped data cannot be read elsewhere anyway | manifest check in T05 | T05 |
| A2-8 | Code running as the app's UID on a rooted or exploited phone while the app is unlocked | out of reach | It can read everything the app can. Documented in `PRIVACY.md` and the README | n/a | T19 |
| A2-9 | A compromised OS or bootloader | out of reach | Nothing in an app survives that | n/a | T19 |

### A3: SMS attacker

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A3-1 | Send a forged panic after seeing both QR codes (knows both public keys) | mitigated | Ed25519 signature checked against the signing key stored at pairing (section 4.3 step 5); the public keys cannot sign | `AlertVerifierTest.forged panic claiming to be a trusted contact is rejected` | done |
| A3-2 | Edit a captured alert in transit | mitigated | HPKE AEAD fails closed; the signature covers the exact payload bytes | `payload edited after signing is rejected` | done |
| A3-3 | Replay a copied alert (Messages inbox, backup, carrier) | mitigated | Unique 128-bit ID recorded in `seen_alerts` once accepted, 24-hour age limit, IDs kept for 48 h | `replayed alert is rejected the second time`, `alert older than a day is stale` | done |
| A3-4 | A trusted contact forwards a third node's signed alert | mitigated | Signed `to` must equal this node (step 6) | `signed alert addressed to another node is rejected` | done |
| A3-5 | Spoof the sender phone number | mitigated | The sender number is never consulted. Trust is by UUID and stored key inside the ciphertext; the ACK goes to the stored number | `ack round trip is accepted`; `IntrusionActivity` ACK path | done |
| A3-6 | Trigger a false alarm with a random alert-shaped text | mitigated | Decrypt fails: `Unreadable`, reported as a status notification, no alarm | `garbage is unreadable` | done |
| A3-7 | SIM swap or number reassignment: a stranger now holds a contact's number | partly | Panics to the old number cannot be decrypted by the new holder, and the stranger cannot sign. The owner must revoke the contact to stop paging a stranger's phone. If the owner's own number moves, every contact re-pairs with the new QR | `alert from a revoked contact is rejected` | spec 08 says revoke; T13 rotation helps after a device loss |
| A3-8 | Another app forges an `SMS_RECEIVED` broadcast | mitigated | Receiver requires `BROADCAST_SMS`, held only by the telephony stack | manifest check in T05 | done |
| A3-9 | A QR that points panics at a premium short code | mitigated | Numbers must be full E.164 (`PhoneNumbers`) | `PhoneNumbersTest`, `AddContactUseCaseTest` | done |
| A3-10 | Flood the receiver with alert-shaped texts to drain the battery | partly | Each costs one decrypt attempt under a 30 s wake lock cap; every failure is a visible notification, so the flood is noticed | n/a | T07 (trial decryption would make this worse; stays prefixed) |

### A4: OSINT and metadata watcher

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A4-1 | Read the stock Messages inbox and see Impart texts between two numbers | mitigated with SMS app mode, partly otherwise | Android stores a copy for every app that is not the default SMS app. Since v3 the copies are 612 characters of base64 behind a 12-character tag: they do not name the app and cannot be decrypted or replayed, but they still show that two numbers exchanged something encrypted. In SMS app mode (T14) Impart receives `SMS_DELIVER` and `SmsDeliverDispatcher` routes an alert to the verifier without ever calling the provider, so no copy exists on that phone. The other phone and the carrier are unchanged | `DefaultSmsModeTest.an alert received while default never reaches the provider` | T07, T14 (done) |
| A4-2 | Tell a panic from an ACK by the message length or part count | mitigated | Every alert is padded inside the signed, encrypted payload to exactly 612 characters, four parts (section 4.2, 6.1) | `AlertSizeTest.every alert kind fits in four parts and sizes are reported` | T07 (done) |
| A4-3 | Carrier records show that number A texted number B at a time, in N parts | out of reach | SMS has no metadata privacy. Stated in section 5, the README and the privacy policy | n/a | T19 |
| A4-4 | A screenshot or screen recording of the app | mitigated | `FLAG_SECURE` on both activities and, through `SecureDialog`/`SecureAlertDialog`, on every dialog window | `WindowHardeningSourceTest` | T04 (done) |
| A4-5 | Learn that the phone runs Impart from the launcher | partly | The Calculator cloak (`CloakManager`) renames the launcher entry; the package name still says `com.uncoalesced.impart` to anything that lists packages | manual | documented |
| A4-6 | Learn the user's location from an alert | mitigated | Coordinates are opt-in, inside the ciphertext, and present only when location sharing is on. The fix is asked from Play Services' fused provider in Google's own process; that process, not Impart, has network access | `PanicPayloadCodecTest` | T19 documents the Play Services point |
| A4-7 | Logcat over ADB | mitigated | Numbers become `***67`, contacts become UUID prefixes (`Redact`); names, payloads and keys are never logged | `RedactTest`; T19 grep of the final diff | done |

### A5: hostile app on the same phone

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A5-1 | A malicious keyboard records the PIN | mitigated | Every PIN is typed on the in-app `PinPad`; no IME is ever opened for a PIN. Optional shuffled layout in Settings. Digits live in a `PinEntry` CharArray and are zeroed after use | `WindowHardeningSourceTest.no PIN entry uses a text field`, `PinEntryTest` | T04 (done) |
| A5-2 | An overlay or tapjacking window drives the panic button, the acknowledge button or the PIN pad | mitigated | `Modifier.rejectObscuredTouches` refuses a touch whose `MotionEvent` carries `FLAG_WINDOW_IS_OBSCURED` or `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`, and says so, on the PIN pad, both panic buttons, acknowledge, accept-new-keys and revoke. On API 31+ every Impart window also calls `setHideOverlayWindows(true)` (permission `HIDE_OVERLAY_WINDOWS`, approved by Joel 2026-11-04), so overlays are hidden outright | `ObscuredTouchPolicyTest`; device test in spec 08 phase 2 step 9 | T04 (done) |
| A5-3 | An accessibility service reads the screen | out of reach, partly | A granted accessibility service can read any window the user can; `FLAG_SECURE` does not stop it. The PIN pad describes only the count ("PIN, 3 digits entered"); the keys are labelled, the value is never in the tree | `PinPad` semantics | T04 (done), T19 |
| A5-4 | Read the clipboard after the user copies something | mitigated | Only the UUID and the own phone number can be copied, through `SensitiveClipboard`: flagged `EXTRA_IS_SENSITIVE` (no preview on Android 13+) and cleared after 60 s. Keys, PINs and fingerprints have no copy action. Names and numbers in text fields use keyboard classes that do not learn (`Password` class with visible text, `Phone` class) and `autoCorrect = false` | spec 08 phase 2 step 11 | T04 (done) |
| A5-5 | Send an intent to an exported component | mitigated | `MainActivity` and its alias are the only exported components without a permission, and they are the launcher. `ImpartSmsReceiver` requires `BROADCAST_SMS`; the library components that are exported require `BIND_JOB_SERVICE` or `DUMP` (spec 04 section 4.1). `PreviewActivity` (exported, debug) removed. `IntrusionActivity` and `SmsSentReceiver` are not exported | `manifest-check.sh` in both workflows | T05 (done) |
| A5-6 | Screen-record the intrusion screen or the QR | mitigated | `FLAG_SECURE` | T04 | done |
| A5-7 | Read the SMS itself from the inbox (`READ_SMS` apps) | partly | The body is ciphertext; the metadata is A4-1. In SMS app mode there is no inbox row at all | `DefaultSmsModeTest` | T14 (done) |
| A5-8 | Post a fake "panic" notification that looks like Impart's | out of reach | Any app with notification permission can post anything. The real alarm is the full-screen red activity, not a notification | n/a | documented |

### A6: coercer

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A6-1 | Force the owner to unlock the app | mitigated | The duress PIN wipes everything in section 6.2, then shows the decoy, with no toast, sound, log line or delay | `DuressManagerTest`, flight check phase 8 | T08 (done) |
| A6-2 | Fail on purpose to lock the app so the duress PIN cannot be entered | mitigated | The duress PIN wins even during a lockout (`AppLockManager.submitPin` checks the verdict before the lockout) | `AppLockManagerTest.duress pin works in the middle of a lockout` | T03 (done) |
| A6-3 | Watch the icon change or a message appear during the wipe | mitigated | Nothing announces the wipe; the launcher cloak state is left untouched (Joel, 2026-11-04); the wipe does not log | `DuressManagerTest.the decoy flag is committed before anything is deleted` | T08 (done) |
| A6-4 | Recover what the wipe left behind | mitigated | See A2-4 and section 6.2 | `DuressWiperTest` | T08 (done) |
| A6-5 | Force the owner to hand over both PINs | out of reach | Stated in the brief and `PRIVACY.md` | n/a | T19 |
| A6-6 | Stop an outgoing alert by taking the phone | partly | The SMS leaves the radio in seconds; the dead man's switch fires if the owner is prevented from checking in | flight check | done |

### A7: pairing eavesdropper

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A7-1 | Photograph a QR and impersonate its owner | mitigated | The QR carries only public keys. Signing needs the private Ed25519 key, which never leaves the phone | `forged panic claiming to be a trusted contact is rejected` | done |
| A7-2 | Photograph a QR and decrypt alerts sent to its owner | mitigated | HPKE uses the recipient's private key; the public key in the QR cannot decrypt | `alert encrypted to someone else is unreadable` | done |
| A7-3 | Show a crafted QR that reuses a contact's UUID with the attacker's keys | mitigated | `AddContactUseCase` returns `KeyChanged`; nothing is written until the user compares both fingerprints in the dialog | `AddContactUseCaseTest` | done |
| A7-4 | Show a crafted QR with the attacker's number under a contact's identity | mitigated | Same as A7-3: a known UUID with the same keys updates the number only in a re-pair, which requires the same private keys to be useful; different keys trigger the dialog | `AddContactUseCaseTest` | done |
| A7-5 | Pair with the victim by being shown their QR on a photo instead of in person | partly | The fingerprint is read aloud at pairing (spec 08 phase 3). Someone who pairs with the victim's public QR can send alerts only if the victim also scans theirs | spec 08 | documented |
| A7-6 | A contact's phone is compromised after pairing | mitigated | Revoke stops both directions. "Rotate my keys" replaces this node's keysets in one action and flags every contact for a new handshake (section 7) | `alert from a revoked contact is rejected`; `RotateKeysUseCaseTest` | T13 (done) |

### A8: supply chain

| ID | Attack | Status | Defense | Test | Owner |
|---|---|---|---|---|---|
| A8-1 | A tampered dependency from a repository | open | Versions are pinned in `gradle/libs.versions.toml` but there is no checksum verification yet; CodeQL (`codeql.yml`) scans the sources on every PR | none yet | T09 step 1, after T17 (dependency verification metadata) |
| A8-2 | A tampered Gradle wrapper or distribution | mitigated | `distributionSha256Sum` pins the Gradle 8.10.2 distribution; `gradle/actions/wrapper-validation` checks `gradle-wrapper.jar` in both workflows | `wrapper-validation` job | T09 (done) |
| A8-3 | A compromised GitHub Action | mitigated | Every action is pinned to a full commit SHA with the version in a comment; every workflow runs with `contents: read` and only the CodeQL job may write security events | review of `.github/workflows/*.yml` | T09 (done) |
| A8-4 | A fake update APK | mitigated for release builds | A release signed with Joel's private key (REPORT section 9) cannot be replaced by an APK signed with any other key; the deployment workflow fails without all four secrets and on a debug-signed output; the readiness card shows the certificate | `verify-release-signature.sh`, `SigningIdentityTest` | T11 (done) |
| A8-5 | A change that adds a network path | mitigated | `manifest-check.sh` fails CI on `INTERNET`, `ACCESS_NETWORK_STATE` or `ACCESS_WIFI_STATE` in the merged manifest of either variant, on a debuggable release, on backups, on cleartext traffic and on an exported component without a permission | `manifest` job in both workflows; deliberate INTERNET commit (ledger) | T05 (done) |
| A8-6 | A dependency that phones home | mitigated | Found during T05: ML Kit's telemetry backend was adding `INTERNET` to the merged manifest, so until then the APK could open a socket. It is now removed at merge time, so no socket can be opened from this process whatever a library wants. The telemetry pipeline (datatransport) is still in the APK and fails at the socket; Play Services location runs in Google's process (A4-6). Replacing ML Kit and fused location is pending Joel | `manifest` job | T05 (done), T19 |
| A8-7 | Hostile code hidden in a shrunk release | partly | Every release build's `mapping.txt` is a CI artifact, so a shrunk APK can be read back against the source that built it; the build itself runs only from this repository's workflows | `release-mapping` artifacts | T10 (done) |

### 6.1 Metadata

What the carrier, the stock Messages app and anyone reading either can learn from an alert without decrypting it (A4-1, A4-2). Measured by `AlertSizeTest` on the v2 wire format, with a 153-character part size:

| Alert | Characters | SMS parts |
|---|---|---|
| Panic without location | 413 | 3 |
| Panic with location (4 decimal places) | 453 | 3 |
| Panic, worst case (full-precision coordinates) | 481 | 4 |
| Acknowledgement | 457 | 3 |

Before v3: the body started with `IMPART_ALERT:`, which names the app; a panic and an ACK were usually both 3 parts and within 50 characters of each other, so the part count rarely told them apart, but the exact length did (a watcher who saw 413 then 457 could guess "panic without location, then ACK"); and the timing (a reply within a minute) says which was which anyway.

Options, each with what it hides and what it costs:

1. **Pad every alert to a fixed length (4 parts, 612 characters).** Hides the kind and whether location was attached. Costs: one extra SMS part for most alerts (about 25 percent more SMS charges and airtime), a wire format change (random base64 padding after the ciphertext, stripped before decryption, or inside the plaintext before signing), so every phone must update. Pairing keys are unaffected, so no re-pairing. Does not hide timing or the prefix.
2. **Replace `IMPART_ALERT:` with a per-pair tag.** For example the first 8 base64 characters of `HMAC-SHA256(shared, "impart-tag")` where `shared` is derived from the two public keys at pairing. The body no longer names the app and the tag differs per pair, so an inbox full of alerts does not say "Impart" and two pairs cannot be linked by the prefix. Costs: a wire format change, a new field in the contact row, every phone re-pairs or at least re-scans; a tag can still be matched to a pair by anyone who holds both public keys (a former contact); and the receiver must compute the tag for every active contact per incoming text (cheap: a few HMACs). The text still looks like 450 characters of base64, which is itself a signature of "some encrypted app".
3. **Recognise alerts by trial decryption, no prefix at all.** Hides the most. Costs: every incoming SMS on the phone (two-factor codes, chats) is base64-decoded and run through HPKE decryption inside the receiver's 30 s wake lock; on a busy phone that is a measurable battery cost and a wider attack surface (every text reaches the decryptor); false starts on long base64 texts are harmless but logged. Requires a wire format change only to drop the prefix.
4. **Default SMS app mode (T14, shipped).** As the default SMS app Impart receives `SMS_DELIVER` first and never writes alerts to the Telephony provider, so there is no inbox copy at all on that phone. This removes A4-1 completely on phones that opt in, without any wire change. It does nothing for the carrier's view, for the sender's sent box on a phone that is not default, or for a watcher of the other phone. The price: Impart must then be a basic SMS client for ordinary texts (`SmsStore`, the Texts tab) and declares `READ_SMS`, `RECEIVE_MMS` and `RECEIVE_WAP_PUSH`, which the role requires.

Recommendation: ship T14 (option 4) first, because it removes the inbox copy without touching the frozen wire format. Then, in one wire format bump (v3), do options 1 and 2 together: pad to 4 parts and use a per-pair tag, so a single re-pair buys both. Option 3 is not recommended: the battery and surface cost lands on the most important code path for a small gain over option 2. Nothing hides the carrier metadata (A4-3).

Decision (Joel, 2026-11-04): options 1 and 2 together, now, as wire format v3. Implemented: `PanicPayloadCodec.VERSION = 3`, the `p` padding field, `AlertWire` (612 characters, syntactic filter), `PairTag`, `AlertRecognizer`, and the `TagMismatch` verdict. Every phone must update and re-pair (spec 08). Option 4 remains T14. The measurement after the change: every alert kind is 612 characters, 4 parts (`AlertSizeTest`).

### 6.2 What the duress wipe removes

The inventory (`WipeInventory`, asserted by `DuressWiperTest` and `DuressManagerTest` against a fake data directory laid out like Android's):

| Location | What | Why it exists |
|---|---|---|
| `shared_prefs/impart_decoy.xml` | one boolean | The only survivor. Written first, synchronously, so a crash at any later step still leaves the decoy |
| `shared_prefs/impart_node_secure_prefs.xml` | encrypted preferences | Identity, PIN hashes, lockout, settings, the SQLCipher key. Cleared, then deleted |
| `shared_prefs/__androidx_security_crypto_encrypted_prefs__.xml` | the two Tink keysets EncryptedSharedPreferences uses | Deleted |
| `shared_prefs/impart_key_prefs.xml` | the hybrid and signing keysets | Deleted |
| `shared_prefs/androidx.work.util.preferences.xml`, `androidx.work.util.id.xml` | WorkManager bookkeeping | Deleted |
| `databases/impart_database` and `-wal`, `-shm`, `-journal` | Room, SQLCipher-encrypted | Closed, then deleted with every sidecar |
| `databases/androidx.work.workdb*`, `no_backup/androidx.work.workdb*` | WorkManager's queue (the dead man's switch) | Work cancelled first, then deleted |
| `databases/com.google.android.datatransport.events*` | ML Kit's telemetry queue | Deleted (gone for good with T21) |
| `files/`, `cache/`, `code_cache/`, `no_backup/` | everything inside | Deleted recursively |
| Keystore `_androidx_security_master_key_` | wraps the encrypted preferences' keysets | Deleted |
| Keystore `impart_master_key` | wraps the Tink keysets | Deleted |
| Keystore `impart_pin_mac` | the PIN HMAC key | Deleted |
| Notifications | every posted notification | `cancelAll()` last |

Order: decoy flag, clear the encrypted preferences, drop the in-memory keysets, cancel all work, close the database, delete the databases and sidecars, delete the preference files, empty the directories, delete the Keystore aliases, cancel notifications. Each step is best-effort and silent: a failure does not stop the next step and nothing is logged, because a logcat line is a trace. The launcher cloak state is not touched (Joel, 2026-11-04), so the home screen does not change at the moment of the wipe.

What this does not do: Impart makes no claim of secure overwrite. Flash storage is wear-levelled, so a deleted file can physically survive until the block is reused. What makes a surviving database or preferences file unreadable is that the Keystore keys that wrapped them are gone: the file is ciphertext with no key anywhere. The copies of alerts in the stock Messages app are outside Impart's reach (A4-1, T14).

## 7. Key rotation and revocation

There is no central authority, so a rotation is a mutual re-handshake. Since T13 it is one action rather than a reinstall:

1. Settings, "Rotate my keys", behind the standard PIN and a confirmation that spells out the cost.
2. `RotateKeysUseCase` calls `KeysetSource.rotateKeys()`: `KeystoreHelper` removes both stored keysets in one commit and generates new ones at once. There is no state where one keyset is new and the other old on disk, and nothing encrypted to or signed by the old keys can be decrypted or produced again, by this phone or by anyone who copied them.
3. Every active contact is marked `needsRepair`. Until they scan the new QR, `PanicEligibility` refuses to page them with a visible reason, the dashboard and the contact list say so, and their alerts to this node fail step 5 on their side (they hold the old signing key for this node) and step 1 here.
4. The QR and the fingerprint rebuild (`KeyGeneration`). The contact scans the new QR and sees the key-change dialog; confirming it in person replaces the keys. Scanning their QR here is a plain re-pair that clears the flag.

A rotation does not help against a thief who still holds the unlocked phone (A1-6): they rotate with you. It helps after a suspected copy of the keys (A2-2, A7-6).

Reinstalling Impart still works as a rotation too, at the cost of every contact and setting.

Revoking a contact stops both directions at once: your panics no longer go to them, and theirs fail step 4 on your phone.
