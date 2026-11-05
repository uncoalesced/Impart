<!-- Engineered by uncoalesced -->
# Impart Privacy Policy

Effective 24 September 2026

## The short version

Impart has no servers, no accounts, no analytics, no ads and no internet permission. We, the people who make it, never receive anything from your phone. Everything Impart knows stays on your phone, except the encrypted text messages you choose to send to people you paired with.

## What Impart stores on your phone

Impart keeps two kinds of storage, both inside its private app folder, which other apps cannot read.

An encrypted settings file, locked with a key held in your phone's Android Keystore, holds:

- A random ID for your phone, created on first launch. It is not based on your number or your device.
- Your own phone number, as you typed it on the My QR screen.
- A random salt and a keyed hash for each of your two PINs, plus a count of recent wrong PIN attempts. The PINs themselves are not stored. The key for the hash lives in the Android Keystore and cannot be copied out, so a copy of the settings file cannot be used to guess your PINs on another computer.
- Your dead man's switch interval and last check-in time.
- Whether location sharing is on.
- Which version of these documents you accepted.

A database holds:

- Your trusted contacts: their random ID, their two public keys, their phone number, the nickname you gave them, the date you paired, and whether you revoked them. Revoking a contact stops them paging you and you paging them, but the entry stays in the database, marked as revoked, until you use the duress PIN or uninstall.
- The IDs of alerts you received in the last 48 hours, so that a copied alert cannot raise the alarm twice.

The database is encrypted with a random key that is itself encrypted with a key held in the Android Keystore. Copying the database file off the phone does not reveal your contact list. The duress PIN destroys that key along with the database.

Your private keys, one for decrypting alerts and one for signing them, are stored encrypted with a key held in the Android Keystore. They never leave your phone and are never shown in the app.

## What leaves your phone, and who can see it

Your QR code shows your random ID, your phone number and your two public keys. Anyone who can see your screen while it is open, or who photographs it, learns those. Public keys are meant to be shared. They cannot be used to read your alerts or to send alerts in your name.

When you send a panic, an acknowledgement, or the dead man's switch fires, Impart sends an SMS to each chosen contact. The text inside is encrypted so that only that contact's phone can read it, and it is signed so their phone can check it came from you. Inside the encryption it carries your random ID, their random ID, a random message ID, the time it was sent and, only if you turned on location sharing, your coordinates.

Your mobile carrier, and any network the SMS passes through, can see that your number texted theirs and when. Every Impart message is the same length (four SMS parts) and starts with a short code that is the same for every message between the two of you but does not name Impart. They cannot read what is inside, or tell a panic from an acknowledgement.

Unless Impart is your SMS app, a copy of every alert you send and receive also appears in your normal Messages app, because Android saves texts from apps that are not your default messaging app. Anyone who can open your Messages app can see that you exchanged encrypted messages with those numbers, though not what they said, and not that the app was Impart. Impart cannot delete these copies. You can delete them yourself, or turn on SMS app mode so that no copy is made on your phone in the first place.

Impart sends nothing to us, to Google, or to anyone else.

## What Impart reads

- Text messages: Impart looks at every incoming SMS, but only to check whether it has the exact shape of an Impart alert (a fixed length and a short code that matches one of your trusted contacts). Any other message is ignored straight away. Impart never stores it, logs it or reads further into it.
- Text messages in SMS app mode: if you choose "Make Impart your SMS app" in Settings, Android delivers every incoming text to Impart first. An Impart alert is checked and never written to the phone's message store, so no copy of it exists in any inbox. Every other text is saved in the phone's normal message store, where any SMS app you later switch to can read it, and shown on the Texts tab behind your PIN. Texts you send from that tab are saved there too. A picture message is not downloaded; Impart only records that one arrived, from whom and when. Nothing of this leaves your phone. The notification for a new text shows the sender's number and not the message. Switching back to another SMS app in the system settings ends this mode; from then on copies of alerts appear in that app's inbox again.
- Camera: used only on the Scan QR tab. Images are analysed on your phone to find a QR code and are never saved.
- Location: used only if you turn on location sharing, and only at the moment you send a panic. Impart asks Android's own location service for one fix, from GPS or, if that is off, from the network location provider, and waits at most 8 seconds; without a fix the panic goes out without coordinates. A position older than two minutes is never sent. Impart contains no Google Play services code. Depending on your phone, Android's network location provider may itself be run by Google or your phone maker, under their privacy policy.

## System logs

Impart writes short diagnostic lines to Android's system log, such as "alert verdict accepted" or "SMS part 2 of 4 failed". Phone numbers in these lines are cut to their last two digits and contacts appear as the first eight characters of their random ID. The system log stays on your phone. It can only be read by someone who connects the phone to a computer with USB debugging turned on.

## Permissions, and why Impart asks for each

- Send SMS and receive SMS: to send and receive alerts. Without them Impart cannot work.
- Camera: to scan a contact's QR code when pairing.
- Precise and approximate location: only if you turn on location sharing.
- Notifications and full-screen alerts: to wake your screen and sound the alarm when a panic arrives.
- Wake lock and vibrate: to keep the phone awake while an alert is processed, and to vibrate during the alarm.
- Ignore battery optimizations: so your phone's battery saver does not stop Impart from receiving alerts. You are asked before this is granted.
- Read SMS, receive MMS and receive WAP push: Android grants the SMS app role only to an app that declares these. They are used only in SMS app mode, to show your ordinary texts and to notice a picture message. Impart never reads the message store for alerts.
- Hide overlay windows: on Android 12 and later, other apps' overlays are hidden while Impart is on screen, so nothing can be drawn over the PIN pad or the panic button.

## How long data is kept, and how to delete it

- Everything stays on your phone until you delete it. We hold nothing, so there is nothing for us to delete.
- Alert IDs are deleted automatically after 48 hours.
- Uninstalling Impart deletes everything it stored.
- The duress PIN deletes your contacts, your keys, the alert IDs, any scheduled dead man's switch, and every setting, leaving only a marker that keeps the fake error screen showing. It cannot delete the copies of alerts in your Messages app.

## Backups

Impart turns off Android's backup for its data. Your keys only work on the phone that made them, so a backup restored to another phone would be useless and could stop the app from starting. If you change phones, install Impart fresh and pair with your contacts again.

## Children

Impart is not meant for children under 13 and we do not knowingly have any information about anyone, of any age.

## Your choices and rights

Because we never receive your data, we have nothing to give you, correct or delete on request. You control all of it on your phone: rename or revoke contacts, turn location sharing off, change your number, or uninstall. The phone numbers of the people you pair with are stored on your phone because you added them. Keep them private.

## Security

Alerts are encrypted with Google Tink using HPKE (X25519 and AES-256-GCM) and signed with Ed25519. Private keys are protected by the Android Keystore. The app blocks screenshots and screen recording, asks for a PIN when it starts and after it has been in the background, and only raises the alarm for alerts that are signed by a paired contact, addressed to you, recent, and not seen before. No system is perfectly secure, and Impart has not been independently audited.

## Changes to this policy

If we change this policy, the new version ships inside the app and sits in the source repository. When a change matters, Impart asks you to accept it before you can keep using the app.

## Contact

- Email: mail@uncoalesced.com
- Issues: https://github.com/uncoalesced/Impart/issues
