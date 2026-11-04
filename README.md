<!-- Engineered by uncoalesced -->
# Impart

Impart is a panic button for a small circle of people who trust each other. Hold the button on one phone and a paired phone wakes up, takes over the lock screen and sounds an alarm, even if it was asleep in a pocket with Do Not Disturb on.

It runs over ordinary SMS. There is no server, no account, no push service and no internet permission. The only thing that ever leaves a phone is a text message of fixed length whose body is a short per-pair tag followed by ciphertext. Your carrier sees that two numbers texted each other. It does not see what was said, who inside the message sent it, whether it was a panic or a reply, or that the app is Impart.

Impart is not an emergency service. If someone is in danger, call your local emergency number first.

## How it works

Two phones become trusted by scanning each other's QR codes in person. The QR carries a random ID, a phone number and two public keys: one for encrypting to that phone, and one for checking its signatures. Each side shows a short key fingerprint, and you read it aloud to be sure the QR came from the phone in front of you. Nothing remote can add a contact.

When you hold a panic button, Impart builds a small message (who it is from, who it is for, a random ID, the time and, if you turned it on, your location), signs it with your Ed25519 key, encrypts it to the recipient with Tink HPKE (X25519 and AES-256-GCM), and sends it as a multipart SMS.

On the other phone a broadcast receiver wakes, decrypts, and checks five things before it raises the alarm:

- The signature matches the key stored for that contact when you paired.
- The message is addressed to this phone.
- It is less than a day old.
- It is not dated in the future.
- It has not been seen before.

Then it launches the red intrusion screen with a looping alarm on the alarm audio stream. Tapping acknowledge sends a signed, encrypted reply to the number stored for that contact.

SMS was chosen because an incoming text is delivered by the modem and wakes the receiver even under Doze, and because it keeps every third party out of the loop. The cost is honest: you need a SIM with SMS service, delivery usually takes seconds but the carrier makes no promises, and both phone numbers are visible to the network. Impart gives you confidentiality and authenticity of content, not privacy of metadata.

There is also:

- A dead man's switch: miss a check-in and a panic goes out to everyone.
- An app lock, and a duress PIN that wipes the node and leaves a fake "system error" screen behind.
- A launcher disguise that renames the app to Calculator.

## Building

You need JDK 17 and an Android SDK with API 35. Nothing else: no `google-services.json`, no secrets, no backend to deploy.

```bash
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # onto a connected phone
./gradlew test                     # JVM unit tests, including the attack cases
./gradlew lint                     # Android Lint
bash .github/scripts/standards-check.sh   # signature, palette and Compose-only rules
```

Minimum Android is 8.0 (API 26). Target is 35.

The debug build signs with `debug.keystore` at the repo root (password `android`), so every machine produces the same debug signature. That is convenient for testing and dangerous for real use: anyone can sign an APK that installs over your debug build and inherits its keys. For real use, build a release signed with your own key. The steps are at the end of `specs/08_FLIGHT_CHECK_PROTOCOL.md`.

Upgrading from a build older than the signed-alert change clears your contacts, because the handshake format changed. Everyone pairs again once.

## First run on a phone

1. Read and accept the Terms of Service and Privacy Policy.
2. Choose a standard PIN and a duress PIN. The standard PIN unlocks Impart. The duress PIN, entered anywhere Impart asks for a PIN, silently wipes it.
3. Grant the SMS, camera and notification permissions when asked.
4. Open My QR and type your own number in international form, starting with `+` and your country code. Android cannot read it reliably from the SIM, so you are the source of truth.
5. On the Dashboard, open the Node Readiness card and tap every amber row until it turns green. Two matter most on real hardware: Android 14 hides the full-screen alert unless you allow it, and most manufacturers' battery savers stop the SMS receiver unless the app is exempt. The Build row stays amber on a debug build.
6. Pair with another phone: they scan your QR, then you scan theirs, and you compare the fingerprints. Tap a contact's name to give it a nickname you will recognise on a red screen at 3am.

The full test protocol for three phones, including the deep-sleep test and a replay test, is in `specs/08_FLIGHT_CHECK_PROTOCOL.md`.

## Security, in short

- Keys: each phone makes its own Tink keysets on first launch. They are stored encrypted with a key held in the Android Keystore, and they never leave the phone. The QR carries only public keys.
- Someone who photographed your QR learns your number and public keys. That lets them encrypt a message to you, but not sign one as any of your contacts, so it cannot raise your alarm.
- Copies of alert texts, including the ones Android saves in your Messages app, cannot raise the alarm a second time.
- The app has no network access at all. Someone on the same Wi-Fi has nothing to connect to.
- Screenshots and screen recording are blocked, the app locks after 30 seconds in the background, and repeated wrong PINs lock it out for up to 15 minutes.
- Limits: copies of alerts in the Messages app reveal who you exchanged alerts with until you delete them, and nothing protects a phone that is already rooted or running malware. Impart has not been independently audited.

The full threat model and the attack table are in `specs/03_SECURITY_PROTOCOL.md`.

## Legal

- Terms of Service: `TERMS.md`
- Privacy Policy: `PRIVACY.md`
- License: `LICENSE` (Apache 2.0)

All three ship inside the app under Settings. The build copies them from the repository root, so edit them here. If a change matters to users, bump `LegalDocument.CURRENT_VERSION` so the app asks everyone to accept again.

Questions: mail@uncoalesced.com, or open an issue at https://github.com/uncoalesced/Impart/issues.

## Repository layout

```
app/src/main/java/com/uncoalesced/impart/
  core/         security (Tink, signing, alert verification, PINs and lock, cloak, dead man's
                switch), SMS receivers, notifications, workers, Hilt modules, readiness checks
  data/         Room database, encrypted preferences, legal assets, the SMS relay
  domain/       pure Kotlin models, rules, repository interfaces, use cases
  presentation/ Compose screens and ViewModels, one folder per feature
app/src/test/   JVM unit tests
specs/          the design documents; read them before changing anything structural
```

Coding rules that CI enforces: every Kotlin and Gradle script file starts with `// Engineered by uncoalesced`, colors come from `presentation/theme/Color.kt` and nowhere else, and there is no XML layout, Fragment or `findViewById` anywhere. Details are in `specs/05_CODING_STANDARDS.md`.

## Branches

Work lands on `development`, is promoted to `main` when the release gate passes, and `deployment` is what stores build from. CI on `development` builds debug, runs the unit tests and lint, and runs the standards script.

## License

Copyright 2026 uncoalesced. Apache 2.0, see `LICENSE`.
