<!-- Engineered by uncoalesced -->
# Roadmap and next steps

---

## 1. Where things stand

Everything the manifesto asks for exists in code and builds from a clean checkout:

- A physical QR handshake in both directions, with key fingerprints to compare out loud, and a confirmation step when a known contact's keys change.
- Tink hybrid encryption (X25519 HPKE, AES-256-GCM) for confidentiality, and Ed25519 signatures on every alert so only a paired contact can raise your alarm.
- Replay protection: every alert has a unique ID and a timestamp, and the receiver refuses anything seen before, older than a day, or dated in the future.
- Encrypted SMS as the only transport, with default-SIM selection and sent-intent failure reports.
- A receiver that verifies under a wake lock and turns every outcome into something visible. A red intrusion screen with a looping alarm on the alarm stream and vibration, and a signed ACK to the stored contact.
- An app lock with lockout, a duress PIN that wipes everything the app can reach, a decoy, the launcher cloak and the dead man's switch.
- A SQLCipher-encrypted contact database whose key lives under the Keystore and dies with the duress wipe.
- A readiness card for the settings that OEMs and Android 14 use to break the wake path.
- Terms of Service, a Privacy Policy and the Apache license, shown on first launch and in Settings.
- JVM tests for the crypto, both wire formats, the verifier's attack cases, the handshake rules and the lock policy.

What has never happened is a run on three physical phones over a real carrier. That is next, and it is not a code task. The protocol is `08_FLIGHT_CHECK_PROTOCOL.md`.

## 2. Alpha means

Alpha is reached when, with no manual intervention:

1. Three phones complete the handshake pairwise, and each shows the other two in its Contact Matrix with matching fingerprints.
2. Phone A holds INITIATE PANIC for B. Within seconds B, locked, screen off, Do Not Disturb on, wakes, shows the red screen with A's name, and sounds the alarm until dismissed. A receives the acknowledgement.
3. The same holds after B has been on battery, force-stopped and untouched for 15 minutes.
4. BROADCAST PANIC from A reaches B and C.
5. Re-sending a copy of one of those alert texts to B produces "repeated alert ignored" and no alarm.

## 3. After alpha, in priority order

### Inbox copies

Android stores a copy of every alert in the stock Messages app because Impart is not the default SMS app. Those copies show who exchanged alerts with whom. The only complete fix is an optional mode where Impart becomes the default SMS app, which is a large piece of work and changes the Play Store story. Until then the privacy policy and the flight check tell users to delete the threads.

### OEM deep links

The readiness card opens the stock battery-optimization prompt. Xiaomi, Oppo, Vivo and some Samsung builds have a second layer (autostart, background pop-ups) with their own settings screens. Detect the manufacturer and offer the right intent, using the "Don't kill my app" list as the reference.

### Release hardening

`isMinifyEnabled` is off. Turning R8 on needs keep rules for Tink, the Hilt-generated workers, Room and SQLCipher (`net.zetetic.database.**`, which is reached through JNI), plus a release build tested on a phone. Do this, and sign with a private key, before anything reaches users beyond the test group. Debug builds are signed with a public key and can be replaced by anyone.

### Instrumented tests

`05_CODING_STANDARDS.md` asks for `ComposeTestRule` coverage of the handshake and dashboard flows. None exist yet. The gate order in `MainActivity` (terms, PIN setup, lock) is the first candidate.

### Key rotation

Rotation is a manual re-handshake by design (`03` section 7). A "rotate my keys" action that purges both keysets and tells the user which contacts now need re-pairing would make that survivable.

### Play Store

Google Play restricts `SEND_SMS` and `RECEIVE_SMS` to default SMS apps and a short list of approved exceptions. It also restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Distribution through IzzyOnDroid or direct APK is unaffected. A Play listing would need a policy declaration and may be refused.

## 4. Considered and dropped

- Firebase Cloud Messaging as a fallback transport. It puts a third party back in the path and brings back the Doze throttling SMS was chosen to escape.
- Covert audio capture on the receiving phone when a panic arrives. It recorded into files nothing read, ran in a coroutine the OS could kill mid-record, and cost a microphone permission on first launch.
- Reading the phone's own number from the SIM. It stopped working on API 30 and above without `READ_PHONE_NUMBERS`, and was unreliable below that. The user types it once.
- Trusting the sender key inside an alert. That was how v1 identified senders, and the security review showed that anyone who had seen two QR codes could forge a panic. v2 checks signatures against the key stored at pairing.
