<!-- Engineered by uncoalesced -->
# Roadmap and next steps

---

## 1. Where things stand

Everything the manifesto asks for exists in code and builds from a clean checkout:

- Physical QR handshake in both directions, with a Contact Matrix in Room.
- Tink hybrid encryption (X25519 HPKE, AES-256-GCM) with private keys wrapped by the Android Keystore.
- Encrypted SMS as the only transport, including sent-intent failure reporting and default-SIM selection.
- A receiver that decrypts under a wake lock, checks the sender against the Contact Matrix, and fires a full-screen intent; a red intrusion screen with a looping alarm on the alarm stream and vibration; an encrypted ACK on dismiss.
- Dead man's switch, duress PIN with nuclear wipe and decoy, launcher cloak.
- A readiness card that walks the user through the four things OEMs and Android 14 do to break all of the above.
- JVM unit tests for the crypto round trip, both wire codecs and the contact use cases.

What has never happened: an end-to-end run on three physical phones over a real carrier. That is the next step and it is not a code task. The protocol is `08_FLIGHT_CHECK_PROTOCOL.md`.

## 2. Alpha means

Alpha is reached when, with no manual intervention:

1. Three phones complete the handshake pairwise and each shows the other two in its Contact Matrix.
2. Phone A holds INITIATE PANIC for B. Within a few seconds B, locked, screen off, Do Not Disturb on, wakes, shows the red screen with A's name, and sounds the alarm until dismissed. A receives the ACK notification.
3. The same holds after B has been on battery, force-stopped, and untouched for 15 minutes.
4. BROADCAST PANIC from A reaches B and C.
5. Nothing in the flow was readable in plaintext outside A and B.

## 3. After alpha, in priority order

### Sender authentication

Today the receiver trusts the `senderPublicKey` inside the decrypted payload. Anyone who has a recipient's public key could claim to be a trusted contact. Fix: each node also holds a Tink `PublicKeySign` keypair, the handshake QR carries the verification key, and every payload carries a signature the receiver checks before the Contact Matrix lookup. `ContactEntity` gains a `signingKeyBase64` column (schema version 4).

### Replay window

`03_SECURITY_PROTOCOL.md` section 6 describes a timestamp check with a tolerance window. The payload has no timestamp yet. Add one inside the encrypted body, reject alerts older than a few minutes, and keep a short list of recently seen nonces.

### Encrypted contact database

Room is a plain SQLite file inside the app sandbox. Wire `net.zetetic:android-database-sqlcipher` with a passphrase held in the Keystore, or accept the sandbox as the boundary and say so. Either is defensible; leaving it unstated is not.

### OEM deep links

The readiness card opens the stock battery-optimization prompt. Xiaomi, Oppo, Vivo and some Samsung builds have a second layer (autostart, background pop-ups) with their own settings activities. Detect the manufacturer and offer the right intent, using the "Don't kill my app" list as the reference.

### Release hardening

`isMinifyEnabled` is off. Turning R8 on needs keep rules for Tink, Hilt-generated workers and Room, plus a release build tested on a phone. Do this before anything reaches a store.

### Instrumented tests

`05_CODING_STANDARDS.md` asks for `ComposeTestRule` coverage of the handshake and dashboard flows. None exist.

### Key rotation

Rotation is a manual re-handshake by design (`03` section 7). A "rotate my keys" action that purges the keyset and tells the user which contacts now need re-pairing would make that survivable.

## 4. Things that were considered and dropped

- Firebase Cloud Messaging as a fallback transport. Rejected: it puts a third party back in the path and reintroduces the Doze throttling SMS was chosen to escape.
- Covert audio capture on the receiving phone when a panic arrives. Removed: it recorded into files nothing read, ran in a coroutine the OS could kill mid-record, and cost a microphone permission on first launch.
- Reading the phone's own number from the SIM. Dead on API 30 and above without `READ_PHONE_NUMBERS`, and unreliable below. The user types it once.
