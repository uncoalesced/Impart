<!-- Engineered by uncoalesced -->
# Security policy

Impart is an emergency pager. A flaw in it can mean an alarm that never sounds, or one that sounds for the wrong person. Reports are taken seriously and answered.

## Reporting a vulnerability

Do not open a public issue, pull request or discussion for a security problem.

- Email mail@uncoalesced.com with "Impart security" in the subject.
- If GitHub private vulnerability reporting is enabled on this repository, you can use "Report a vulnerability" under the Security tab instead.

Please include what you found, the build you tested (Settings, About shows the version and the signing certificate), the steps to reproduce it, and what an attacker gains. A proof of concept helps. Never include real phone numbers, keys or PINs; the test helpers in `app/src/test` build throwaway nodes.

What to expect:

- An acknowledgement within 7 days.
- An assessment, with the row of the attack table it belongs to, within 30 days.
- A fix, or a written explanation of why it cannot be fixed in an app, in the attack table in `specs/03_SECURITY_PROTOCOL.md` section 6.
- Credit in the changelog if you want it.

There is no bug bounty.

## Supported builds

| Build | Supported |
|---|---|
| The latest release signed with the uncoalesced release key, from the `deployment` branch | Yes |
| Older releases | No. Update; the wire format changes force every phone in a group onto the same build anyway |
| Debug builds | No. They are signed with the public key in `debug.keystore`, so anyone can install an "update" over them. They are for testing only, and the app says so on its readiness card |
| Forks and rebuilds signed with another key | No |

## In scope

- Anything that lets an alert raise the alarm without the sender's private signing key: forgery, replay, misdirection, tag or signature confusion.
- Anything that stops a genuine alert from being shown, or hides a refused one from the user.
- Reading contacts, keys, PIN material or alert content from the phone's storage, a backup or a device transfer.
- Bypassing the app lock, the lockout or the duress wipe, or making the wipe visible or incomplete.
- A network path from the app (the shipped manifest must carry no network permission).
- Overlay, keyboard, clipboard, intent or accessibility attacks from another app on the same phone.
- The build and release pipeline: dependencies, the Gradle wrapper, GitHub Actions, release signing.

## Out of scope

These are documented limits, not vulnerabilities (`specs/03_SECURITY_PROTOCOL.md`, section 6 and section 5):

- Code that already runs with Impart's privileges on a rooted or exploited phone.
- A compromised operating system or bootloader.
- A user who hands over both PINs.
- What the carrier sees: which number texted which, when, and how many parts.
- SMS delivery delays and losses, and phone makers' battery savers that stop the receiver.
- Copies of alerts in another app's inbox when Impart is not the default SMS app.

## What Impart is built on

Tink HPKE (X25519, HKDF-SHA256, AES-256-GCM) for encryption and Tink Ed25519 for signatures, keys wrapped by the Android Keystore, SQLCipher for the contact database, and a physical QR handshake as the only way to trust a phone. The design, the verification order and the full attack table are in `specs/03_SECURITY_PROTOCOL.md`. Impart has not been independently audited.
