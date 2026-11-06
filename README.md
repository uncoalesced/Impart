<!-- Engineered by uncoalesced -->
# Impart

<p align="center"><img src="design/impart-mark.svg" width="120" alt="The Impart mark: an upward pointer made of separate circles and rounded squares on a 9x9 grid, with the center cell empty"></p>

Impart is a panic button for a small circle of people who trust each other. Hold the button on one phone and a paired phone wakes up, takes over the lock screen and sounds an alarm, even if it was asleep in a pocket with Do Not Disturb on.

It runs over ordinary SMS. There is no server, no account, no push service and no internet permission. The only thing that ever leaves a phone is a text message of fixed length whose body is a short per-pair tag followed by ciphertext. Your carrier sees that two numbers texted each other. It does not see what was said, who inside the message sent it, whether it was a panic or a reply, or that the app is Impart.

Impart is not an emergency service. If someone is in danger, call your local emergency number first.

## How it works

Two phones become trusted by scanning each other's QR codes in person. The QR carries a random ID, a phone number and two public keys: one for encrypting to that phone, and one for checking its signatures. Each side shows a short key fingerprint, and you read it aloud to be sure the QR came from the phone in front of you. Nothing remote can add a contact.

When you hold a panic button, Impart builds a small message (who it is from, who it is for, a random ID, the time and, if you turned it on, your location), signs it with your Ed25519 key, encrypts it to the recipient with Tink HPKE (X25519 and AES-256-GCM), and sends it as a multipart SMS.

Every alert is padded to the same size (612 characters, four SMS parts) and starts with a short tag that only the two paired phones can compute, so the text itself does not say it is Impart or whether it is a panic or a reply.

On the other phone a broadcast receiver wakes, matches the tag against its contacts, decrypts, and checks six things before it raises the alarm:

- The signature matches the key stored for that contact when you paired.
- The tag belongs to that contact.
- The message is addressed to this phone.
- It is less than a day old.
- It is not dated in the future.
- It has not been seen before.

Then it launches the full-screen Watermelon intrusion screen with a looping alarm on the alarm audio stream. Tapping acknowledge sends a signed, encrypted reply to the number stored for that contact.

SMS was chosen because an incoming text is delivered by the modem and wakes the receiver even under Doze, and because it keeps every third party out of the loop. The cost is honest: you need a SIM with SMS service, delivery usually takes seconds but the carrier makes no promises, and both phone numbers are visible to the network. Impart gives you confidentiality and authenticity of content, not privacy of metadata.

There is also:

- A dead man's switch: miss a check-in and a panic goes out to everyone.
- An app lock with an in-app PIN pad, and a duress PIN that wipes the node and leaves a fake "system error" screen behind.
- A launcher disguise that renames the app to Calculator.
- "Rotate my keys", for when you think your keys were copied.
- An optional SMS app mode: make Impart your default SMS app and no copy of any alert is kept in a Messages inbox on your phone.

## Building

You need JDK 17 and an Android SDK with API 35. Nothing else: no `google-services.json`, no secrets, no backend to deploy.

```bash
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # onto a connected phone
./gradlew test                     # JVM unit tests, including the attack cases
./gradlew connectedDebugAndroidTest   # Compose UI tests on a device or emulator
./gradlew lint                     # Android Lint
./gradlew assembleRelease          # minified with R8; signed only with your RELEASE_* variables
bash .github/scripts/standards-check.sh   # signature, palette and Compose-only rules
bash .github/scripts/manifest-check.sh    # after a build: no network permission, no open components
```

Minimum Android is 8.0 (API 26). Target is 35.

The debug build signs with `debug.keystore` at the repo root (password `android`), so every machine produces the same debug signature. That is convenient for testing and dangerous for real use: anyone can sign an APK that installs over your debug build and inherits its keys. For real use, build a release signed with your own key: the deployment workflow refuses to produce one without the four `RELEASE_*` secrets and refuses one signed with the debug key. Key custody notes are in `specs/fable/REPORT.md`.

The wire format changed to version 3 in November 2026 (fixed-length alerts under a per-pair tag). Every phone in a group must run a build from this version on, and everyone pairs again once. A phone on an older build sees "alert from an outdated version" instead of an alarm.

## First run on a phone

1. Read and accept the Terms of Service and Privacy Policy.
2. Choose a standard PIN and a duress PIN. The standard PIN unlocks Impart. The duress PIN, entered anywhere Impart asks for a PIN, silently wipes it.
3. Grant the SMS, camera and notification permissions when asked.
4. Open My QR and type your own number in international form, starting with `+` and your country code. Android cannot read it reliably from the SIM, so you are the source of truth.
5. On the Dashboard, open the Node Readiness card and tap every amber row until it turns green. Two matter most on real hardware: Android 14 hides the full-screen alert unless you allow it, and most manufacturers' battery savers stop the SMS receiver unless the app is exempt. On Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus and Samsung phones there is one more row for the maker's own autostart screen. The Build row stays amber on a debug build.
6. Pair with another phone: they scan your QR, then you scan theirs, and you compare the fingerprints. Tap a contact's name to give it a nickname you will recognise on a red screen at 3am.

The full test protocol for three phones, including the deep-sleep test and a replay test, is in `specs/08_FLIGHT_CHECK_PROTOCOL.md`.

## What Impart resists, and what it does not

The adversaries and every attack considered are in the attack table, `specs/03_SECURITY_PROTOCOL.md` section 6. In short:

| Who | What they try | What happens |
|---|---|---|
| Someone who steals the phone | Open the app, guess the PIN, wait out the lockout | The app locks on every start, when the screen turns off and after 30 seconds away. PINs are typed on an in-app pad and stored only as salted keyed hashes whose key cannot leave the phone's Keystore. Wrong PINs lock it out for up to 15 minutes, counted on a clock that a date change or a reboot cannot rewind; optionally 20 wrong PINs wipe it. |
| A forensic examiner with the phone | Read contacts, keys, PINs, alert history | Contacts are in a SQLCipher-encrypted database; keys and the database key are wrapped by the Android Keystore. The duress PIN deletes every file, database and Keystore key the app has. No backups are allowed. |
| Someone who can send any SMS | Fake a panic, replay an old one, impersonate a contact | Only a message signed with the key stored at pairing can raise the alarm. Replays, forwards, misdirected, stale and future-dated alerts are refused, and each refusal is shown. |
| Someone reading your inbox or your carrier's records | Learn that you use Impart and with whom | Texts are fixed-length ciphertext behind a per-pair tag that does not name the app. In SMS app mode no inbox copy exists on your phone. The carrier still sees which numbers texted each other and when. |
| A hostile app on the same phone | Read the PIN, tap through an overlay, screenshot | No system keyboard ever sees a PIN; touches under an overlay are refused, and overlays are hidden on Android 12 and later; screenshots, recordings and the recents preview are blocked; the clipboard is cleared after a minute. |
| Someone forcing you to open the app | See your contacts | The duress PIN works at any time, even during a lockout, and wipes everything silently behind a fake error screen. |
| Someone who photographed your QR | Impersonate you | The QR holds only public keys. They can encrypt to you but cannot sign as you or as any of your contacts. |
| A tampered build or dependency | Ship hostile code | Every CI action is pinned to a commit, the Gradle distribution is checksummed, CI fails if any network permission appears in the shipped manifest, and the APK contains no Google Play services, Firebase or ML Kit code. |

What no app can protect against, and Impart does not claim to:

- Code running with Impart's own privileges on a rooted or exploited phone can read what Impart can read while it is unlocked.
- A phone whose operating system or bootloader is compromised.
- Someone who makes you hand over both PINs.
- The carrier's view: which number texted which, when, and how many parts.
- Delivery: SMS can be late or lost, and some phone makers' battery savers can still stop the receiver.

Impart has not been independently audited. To report a vulnerability, see `SECURITY.md`.

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
app/src/androidTest/  Compose UI tests and the database migration test, run on an emulator in CI
design/         the mark, its generator and the design tokens
specs/          the design documents; read them before changing anything structural
```

Coding rules that CI enforces: every Kotlin and Gradle script file starts with `// Engineered by uncoalesced`, colors come from `presentation/theme/Color.kt` and nowhere else, and there is no XML layout, Fragment or `findViewById` anywhere. Details are in `specs/05_CODING_STANDARDS.md`.

## Branches

Work lands on `development`, is promoted to `main` when the release gate passes, and `deployment` is what stores build from. CI on `development` builds debug, runs the unit tests and lint, builds the minified release, checks the merged manifest, validates the Gradle wrapper, runs the Compose tests on API 26 and 34 emulators, runs the standards script and runs CodeQL. Contributions: `CONTRIBUTING.md`. Conduct: `CODE_OF_CONDUCT.md`. Help: `SUPPORT.md`. Changes: `CHANGELOG.md`.

## License

Copyright 2026 uncoalesced. Apache 2.0, see `LICENSE`.
