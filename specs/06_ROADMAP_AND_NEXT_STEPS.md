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
- JVM tests for the crypto, the wire format, the verifier's attack cases, the handshake rules, the lock, the wipe and every feature added since, and Compose UI tests on emulators.

What has never happened is a run on three physical phones over a real carrier. That is next, and it is not a code task. The protocol is `08_FLIGHT_CHECK_PROTOCOL.md`.

## 2. Alpha means

Alpha is reached when, with no manual intervention:

1. Three phones complete the handshake pairwise, and each shows the other two in its Contact Matrix with matching fingerprints.
2. Phone A holds INITIATE PANIC for B. Within seconds B, locked, screen off, Do Not Disturb on, wakes, shows the red screen with A's name, and sounds the alarm until dismissed. A receives the acknowledgement.
3. The same holds after B has been on battery, force-stopped and untouched for 15 minutes.
4. BROADCAST PANIC from A reaches B and C.
5. Re-sending a copy of one of those alert texts to B produces "repeated alert ignored" and no alarm.

## 3. Done on the completion branch (November 2026)

These were the post-alpha items; all of them now exist in code, with tests (`specs/fable/LEDGER.md` has the evidence):

- Inbox copies: optional default SMS app mode keeps alerts out of every inbox on the phone; wire format v3 makes the copies that remain on other phones fixed-length and free of the app's name.
- OEM deep links: readiness rows for Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus and Samsung.
- Release hardening: R8 on with the mapping kept, release signing that refuses the debug key and requires the release secrets, v2 and v3 signatures only.
- Instrumented tests: Compose UI tests on API 26 and 34 emulators in CI.
- Key rotation: "Rotate my keys" with contacts flagged for re-pairing.
- Also: PIN hashing in the Keystore, tamper-resistant lockouts, an in-app PIN pad, overlay and screenshot protection on every window, a complete duress wipe, no Google components, CI supply-chain checks, the re-skin and the in-app mark.

## 4. Next

### The flight check on release builds

Run `08_FLIGHT_CHECK_PROTOCOL.md` end to end with the signed release APK on three phones, including phases 8a to 8d and 9. This is the gate for alpha.

### Play Store

Google Play restricts `SEND_SMS`, `RECEIVE_SMS` and `READ_SMS` to default SMS apps and a short list of approved exceptions. SMS app mode makes Impart a candidate for the default-handler case; whether to submit, and the policy declaration, are Joel's decision. Play also restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Distribution through F-Droid (now possible: no proprietary dependencies), IzzyOnDroid or direct APK is unaffected.

### An independent audit

Nothing replaces an outside review of the crypto, the verifier and the duress wipe.

### Later, needs its own threat model

- Internet messaging (Joel's notes). It needs the INTERNET permission, which every guarantee in `03_SECURITY_PROTOCOL.md` assumes is absent. It gets its own spec and threat model before any code.

## 5. Considered and dropped

- Firebase Cloud Messaging as a fallback transport. It puts a third party back in the path and brings back the Doze throttling SMS was chosen to escape.
- Covert audio capture on the receiving phone when a panic arrives. It recorded into files nothing read, ran in a coroutine the OS could kill mid-record, and cost a microphone permission on first launch.
- Reading the phone's own number from the SIM. It stopped working on API 30 and above without `READ_PHONE_NUMBERS`, and was unreliable below that. The user types it once.
- Trusting the sender key inside an alert. That was how v1 identified senders, and the security review showed that anyone who had seen two QR codes could forge a panic. v2 checks signatures against the key stored at pairing.
