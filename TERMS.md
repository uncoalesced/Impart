<!-- Engineered by uncoalesced -->
# Impart Terms of Service

Effective 24 September 2026

## Impart is not an emergency service

If someone is in danger, call your local emergency number first: 112, 911, 999, or whatever works where you are. Impart pages the handful of people you paired with. It does not reach the police, an ambulance or anyone else, and it cannot promise that your page arrives. Treat it as an extra way to get help, never the only one.

## What these terms cover

These terms apply to the Impart app for Android, made by uncoalesced ("we", "us"). By installing or using Impart you agree to them. If you do not agree, uninstall the app. Nothing else is needed: there is no account to close.

## How Impart delivers a panic, and where that can fail

Impart sends every alert as an ordinary SMS through your mobile carrier. There is no Impart server. That keeps anyone else out of your messages, but it also means delivery depends on things we do not control:

- Your phone needs a SIM with SMS service and a signal. So does the phone you are paging.
- Carriers deliver SMS when they can. Most arrive in seconds. Some arrive minutes or hours later, and some never arrive.
- Phone makers add battery savers that can stop Impart from running in the background. The readiness card on the dashboard points you to the right settings, but some phones ignore them.
- Android 14 and later can block the full-screen alarm until you allow it in settings.
- Impart is not your default messaging app, so a copy of every alert also appears in your normal Messages app as a long encrypted text.

Test Impart with the people you pair with before you rely on it, and test again after you change phones or carriers.

## SMS costs

Every alert is a text message, usually split into three or four parts, and your carrier may charge for each part. A broadcast sends one alert to every trusted contact. The dead man's switch sends a broadcast by itself if you miss a check-in. Acknowledging a panic sends a text back. You pay whatever your carrier charges for these, so a plan with unlimited texts is recommended. We never charge you anything.

## Your responsibilities

- Only pair with people who have agreed to receive alerts from you, and keep their numbers private.
- Use Impart lawfully. Do not use it to harass anyone, to send false alarms, or to track someone without their consent.
- Keep your PINs to yourself. The standard PIN opens the app. The duress PIN wipes it.
- Keep your own phone number up to date on the My QR screen. Your contacts page the number you gave them when you paired.
- Keep your phone's operating system updated and do not install Impart builds from people you do not trust.

## Features that act on their own or cannot be undone

- The dead man's switch sends a panic to every trusted contact if you do not check in on time. It does not include your location.
- The duress PIN permanently deletes your contacts, your keys and your settings, and leaves a fake error screen behind. There is no undo and no backup to restore from.
- The disguise option renames the app to Calculator on your home screen. Opening it still opens Impart.
- If you turn on location sharing, each panic you send carries your coordinates to the people you page.
- The app lock asks for your PIN every time Impart starts and whenever it has been in the background for more than 30 seconds. After repeated wrong PINs it makes you wait before trying again, up to 15 minutes.

## Security has limits

Impart encrypts and signs every alert, and it only raises the alarm for alerts that come from a paired contact. It has not been independently audited. It cannot protect you on a phone that is already compromised, rooted, or running malware with accessibility access. Debug builds are signed with a key that is published in the source code, so anyone can make an update that installs over them. Use debug builds only for testing, and use a release build signed with your own key for real use.

## Copyright and licenses

Copyright 2026 uncoalesced.

Impart is open source under the Apache License, Version 2.0. The full license text is in the app under Settings, and in the LICENSE file of the source repository. The license governs your rights to use, copy, change and share the code. Nothing in these terms takes away a right the license gives you, and if the two ever disagree about the code, the license wins.

Impart is built on other open-source and third-party components:

- AndroidX, Jetpack Compose, Room, CameraX and WorkManager, under the Apache License 2.0.
- Kotlin, kotlinx.coroutines and Dagger Hilt, under the Apache License 2.0.
- Google Tink, the encryption library, under the Apache License 2.0.
- ZXing, for drawing QR codes, under the Apache License 2.0.
- Google ML Kit barcode scanning and Google Play services location, under Google's own terms.

The license does not give you the right to use the names Impart or uncoalesced to promote your own version. If you publish a changed version, give it a different name.

Impart does not host anything that you or anyone else uploads, so there is no content takedown process. If you have a copyright question about the code, use the contact details below.

## No warranty

Impart is provided "as is", without warranties or conditions of any kind, express or implied. That includes any warranty that it will deliver an alert, work on your phone, or be free of bugs. This matches section 7 of the Apache License.

## Limitation of liability

To the fullest extent the law allows, we are not liable for any loss or harm that comes from using Impart or from not being able to use it. That includes a panic that arrives late or never arrives, an alarm that does not sound, data deleted by the duress PIN, and SMS charges. Some places do not allow these limits. Where that is the case, they apply as far as the law there permits. This matches section 8 of the Apache License.

## Changes to these terms

We may update these terms. The current version always ships inside the app and sits in the source repository. When a change matters, Impart asks you to accept the new version before you can keep using it.

## Stopping

You can stop using Impart at any time by uninstalling it. Uninstalling deletes everything Impart stored on your phone. Copies of alerts in your Messages app stay there until you delete them.

## Contact

- Email: mail@uncoalesced.com
- Issues: https://github.com/uncoalesced/Impart/issues
