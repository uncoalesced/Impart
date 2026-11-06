<!-- Engineered by uncoalesced -->
# Flight check protocol: three phones, one carrier

Status: ready to run. Nothing in this document has been executed on hardware yet. Whoever runs it first should note results inline and commit them.

Do not use emulators for any of this. Emulators have no SIM, do not enter Doze the way phones do, and cannot show you what a Samsung battery saver does at 3am.

## What you need

- Three Android phones (call them A, B and C), Android 8.0 or newer, each with a SIM that can send and receive SMS. Different manufacturers are better, not worse: the point is to find the one that kills the receiver.
- The APK from `./gradlew assembleDebug`, installed on all three with `adb install -r` or by copying the file over. Builds from before the signed-alert change use an older handshake, so uninstall those first. Old contacts cannot be kept.
- A laptop with `adb` for reading logs when something fails. It is optional, but it turns "it did nothing" into a line number.

Log filter to run against the receiving phone:

```bash
adb logcat -s ImpartSmsReceiver IntrusionActivity IntrusionAlarm RelayManager SmsSentReceiver DuressManager
```

A debug build is fine for this test. Do not hand debug builds to people for real use. Section "Before real use" explains why.

## Before you start: every phone on the same build

The wire format changed to v3 in November 2026 (padding and a per-pair tag, `specs/03` section 4.2). A phone on an older build cannot exchange alerts with a phone on this one: its texts are reported as "ALERT FROM AN OUTDATED VERSION" and its receiver ignores v3 bodies. Uninstall any older Impart from every test phone, install this build everywhere, and pair again from scratch. The T02 PIN change also means an old install's PINs are migrated on first run, which is fine, but a clean install is the simplest known state.

## Phase 1: first launch on each phone

1. Open Impart. The terms screen comes first. Open "Read the Terms of Service" and "Read the Privacy Policy", scroll to the end of each, go back, and tap I UNDERSTAND AND ACCEPT.
2. Choose a standard PIN and a duress PIN (4 to 8 digits, different from each other, not 1234 or 9999). Write both down for the test.
3. Grant SMS, camera and notifications when asked. Deny nothing.
4. My QR tab: type the phone's own number in international form (`+44...`, `+1...`). The QR and the KEY FINGERPRINT appear once the number is valid. Type a local number such as `07700900123` first and confirm the QR refuses it.
5. Dashboard, Node Readiness card. Tap every amber row and follow the prompt until they are green:
   - Build (stays amber on a debug build, which is expected)
   - SMS permissions
   - Notifications
   - Full-screen alerts (Android 14 and later: the row opens the page where you allow it)
   - Battery optimization (accept the prompt; on Xiaomi, Oppo, Vivo and some Samsungs also find the maker's own autostart or background switch in App Info)
   - SIM for SMS (on dual-SIM phones a default SMS SIM must be set)
   - Own phone number
   - Trusted contacts (stays amber until Phase 3)
6. Settings tab: open Terms of Service, Privacy Policy and Open source license. All three must render and scroll.

Expected: every row green except Build and Trusted contacts.

## Phase 2: the lock

1. Press Home, wait more than 30 seconds, and reopen Impart. Expected: IMPART LOCKED. The standard PIN opens it.
2. Press Home and come back within a few seconds. Expected: no lock.
3. Enter a wrong PIN five times. Expected: "Try again in 30s", and the countdown runs.
4. Swipe Impart away from Recents and reopen it. Expected: locked, and the lockout still counts down.
5. Try to screenshot any Impart screen. Expected: the phone refuses, or saves a black image.
6. Turn the screen off with the power button while Impart is open, turn it back on and unlock the phone. Expected: IMPART LOCKED at once, without waiting 30 seconds.
7. Set the phone's date a day back in Settings during a lockout and return to Impart. Expected: the countdown is unchanged. Reboot the phone during a lockout. Expected: after the reboot the lockout still shows at least the time that was left.
8. PIN pad: the system keyboard never appears on any PIN screen (lock, PIN setup, change PINs, the wipe-after-20 toggle). With "Shuffle the PIN pad" on in Settings, the digits are in a different order each time a pad appears.
9. Overlay rejection: install a test overlay (for example a screen filter app that draws over other apps), enable it, and try the PIN pad, the panic button and, during a test alert, ACKNOWLEDGE. Expected: a message "Touch blocked: another app is drawing over Impart" and nothing happens. Disable the overlay and repeat. Expected: the controls work.
10. Open the Recents screen with Impart in the background. Expected: Impart's card is blank or black, never the last screen.
11. Copy the UUID from the dashboard, then paste it anywhere after a minute. Expected: the clipboard is empty (Android 13 and later also shows no clipboard preview with the value).

## Phase 3: pairing

Pairing is one direction per scan. For each pair (A-B, A-C, B-C):

1. The first phone opens My QR. The second opens Scan QR and points at it. A toast says "Paired with +number" and the Contacts tab opens.
2. On the Contacts tab the new row shows KEY followed by a fingerprint. Read it aloud. It must match the KEY FINGERPRINT under the first phone's QR, character for character.
3. Swap roles and repeat.
4. Tap each contact's name and give it a nickname. That name is what the red screen shows.

Rejections to check while you are here:

- Scan your own QR. Expected: "You scanned this node's own QR code". Nothing is added, and the scanner keeps scanning.
- Scan any QR that is not Impart's (a website link). Expected: "not an Impart handshake".
- Scan a contact you already have again. Expected: "Re-paired with", with the nickname kept.

Expected at the end: every phone lists the other two, with nicknames, matching fingerprints and "LAST VERIFIED TODAY". Readiness is fully green except Build.

## Phase 4: foreground alert and acknowledgement

Both phones unlocked, Impart open on both, sitting on the table.

1. On A, hold "INITIATE PANIC: B" for the full 1.5 seconds. Releasing early cancels, which is the point of the hold.
2. A shows "Panic dispatched to B" for three seconds.

Expected on B within one to ten seconds, depending on the carrier:

- The screen goes panic red.
- "FROM: A's nickname" and "SENT hh:mm" are shown.
- The alarm tone loops at full alarm volume, and the phone vibrates.
- If location sharing was on for A, coordinates and a NAVIGATE button appear.

3. On B tap ACKNOWLEDGE & DISMISS.

Expected on A: a notification "A's nickname acknowledged your panic signal".

The ciphertext also appears in the stock Messages app on both phones as a 612-character text of random-looking letters that starts with the same 12 characters every time these two phones page each other. That is expected: nothing in it names Impart, and every alert has the same length.

If A shows an error under the panic buttons, read it. "No SIM is selected for sending SMS" and "Contact has no phone number" come before the radio is involved. A notification "PANIC SMS FAILED TO SEND" means the radio refused a part, and it names the reason.

## Phase 5: replay and misdirection

This checks the two attacks the signed-alert change closed.

1. On B, open the stock Messages app, find the 612-character text that A sent in Phase 4, and copy the whole text.
2. From C (or any phone), send that exact text to B as a normal SMS.

Expected on B: a notification "REPEATED ALERT IGNORED", and no alarm.

3. Send the same text to C.

Expected on C: "UNREADABLE ALERT RECEIVED", and no alarm. The alert was encrypted to B, so C cannot read it.

4. Wait until the Phase 4 alert is more than 24 hours old, then send it to B again (optional).

Expected: "OLD ALERT NOT RAISED".

## Phase 6: broadcast

1. On A, hold "BROADCAST PANIC (ALL NODES)".

Expected: B and C both go red within seconds of each other, and A shows "Panic dispatched to 2 nodes". If one fails, A shows "Sent to 1 of 2 nodes" and names it.

## Phase 7: the deep sleep test

This is the only test that proves the app does its job.

Prepare B:

1. Unplug it. Doze arrives faster on battery.
2. Swipe Impart away from Recents. Force-stopping from App Info is harsher, and worth a second run.
3. Turn on Do Not Disturb.
4. Lock it, screen off, face down, and leave it untouched for 20 minutes.

Then from A, hold "INITIATE PANIC: B".

Expected: within seconds B lights up, shows the red screen over the lock screen without asking for the Impart PIN, and sounds the alarm through Do Not Disturb. Dismissing it sends the acknowledgement to A.

Run this once with each phone as the receiver. Record the model, the Android version and the delay.

## Phase 8: duress and decoy

Do this on a phone you can reinstall. The wipe is permanent for that install.

1. Lock Impart (Home, wait 30 seconds, reopen) and enter the duress PIN at the lock screen.

Expected: no message at all. The whole app becomes a grey "NODE CORRUPTED / SYSTEM ERROR" screen with no navigation. Killing and reopening the app shows the same screen. A panic sent to this phone produces nothing on it.

2. Reinstall to recover. The other phones still list the old identity. Revoke it and pair again. On the other phones, scanning the reinstalled phone's new QR shows "KEYS CHANGED" with an old and a new fingerprint. Accept only after comparing the new fingerprint with the reinstalled phone's screen.

## Phase 8d: look and mark (T15, T16)

Every screen is true black with Graphite cards and dialogs; text is Porcelain; the only Watermelon is the mark, the panic buttons, selected chips and switches, and the intrusion screen, which is full-bleed Watermelon with black text. Warnings and the revoke button are Sandy Brown. Headings, PINs, fingerprints, numbers and times are in JetBrains Mono; body text in IBM Plex Sans. The lock screen, the first-run terms screen and Settings, About show the pointer mark with lowercase "impart" under it, and the center of the 9x9 grid is empty. Turn on the Calculator cloak: the lock screen then says "LOCKED" with no mark and About shows no mark. Trigger the duress PIN on a test install: the decoy shows no mark and no Watermelon. Notifications in the status bar use the white pointer icon.

## Phase 8c: SMS app mode (T14)

On B: Settings, "Make Impart your SMS app", accept the system prompt. Expected: the switch turns on, a Texts tab appears, and the readiness card is unchanged. Page B from A. Expected: the red screen as in phase 4, and afterwards no new text in B's old Messages app and nothing on the Texts tab: the alert was never written anywhere. Send an ordinary text to B from any phone. Expected: a "New text message" notification showing only the number, and the text on the Texts tab after the PIN. Reply from the Texts tab. Expected: the reply arrives and shows in the thread. Send B a picture message. Expected: a notice "MMS received" with the sender and time, nothing downloaded. Open the dialer during an incoming call and pick a quick text reply. Expected: it goes out from Impart. Finally, in Android's default apps settings, give the SMS role to the stock app. Expected: the switch turns off, the Texts tab disappears, and the next alert appears as a 612-character text in the stock app again.

## Phase 8b: key rotation (T13)

On A: Settings, "Rotate my keys", standard PIN, ROTATE. Expected: a toast saying how many contacts must re-pair; the My QR tab shows a new fingerprint; the Contacts tab marks B and C "RE-PAIR NEEDED"; holding B's panic button shows the refusal reason under it and sends nothing. On B: nothing changes yet. Page A from B: expected on A, "ALERT FAILED VERIFICATION" (B still holds A's old signing key) and no alarm. Then B scans A's new QR: expected the KEYS CHANGED dialog with the old and new fingerprints; accept after comparing with A's screen. A scans B's QR: expected "Re-paired". Page in both directions: expected the red screen both ways.

## Phase 8a: vendor background settings (T12)

On a Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus or Samsung phone the readiness card shows one more row, "<vendor> background settings", amber until confirmed. Tap it: the vendor's own autostart or battery screen opens (or the app details page if that model has no such screen). Do what the row says, come back, and answer YES, ALLOWED. Expected: the row turns green and stays green across restarts. Then repeat phase 7 (deep sleep) on that phone. On a Pixel, Fairphone or LineageOS phone the row does not exist.

## Phase 9: the release APK

The release build is minified by R8 (T10) and must be signed with Joel's private key (T11); the debug keystore is public. Do the whole protocol once more with the release APK from the deployment workflow's `release-apk` artifact, because a missing keep rule only shows at runtime:

1. Install the release APK on every phone (uninstall the debug build first; the signatures differ, so an in-place update is refused).
2. Phase 1 and 2: first launch, PIN setup, lock, lockout, PIN pad.
3. Phase 3: pairing in both directions, fingerprints shown and equal.
4. Phase 4 and 7: a panic in the foreground and one after 15 minutes of deep sleep, the red screen, the alarm, the ACK back.
5. Phase 8: the duress PIN and the decoy.
6. Settings: set the dead man's switch to one hour, do not check in, and confirm the warning notification arrives after the hour has passed and the panic goes out: this is the WorkManager path, the one most exposed to a missing keep rule.
7. The readiness card's Build row says "Release build" and shows no debug warning.

If any step crashes, pull the stack trace (`adb logcat -s AndroidRuntime`) and read it with the `release-mapping` artifact of the same run: `retrace mapping.txt trace.txt`.

## When it fails

- B gets the notification but the screen stays dark. Android 14 has not allowed the full-screen alert: check the readiness row. On older Android, check the lock screen is not set to hide notification content.
- B does nothing until unlocked, then the alert appears. The maker's battery saver held the receiver. Check the readiness row, then the maker's own autostart switch. On Samsung also check "Never sleeping apps" in the battery settings.
- B shows "UNREADABLE ALERT RECEIVED" for a genuine panic. B's keys changed since A scanned it (reinstall or wipe). Pair again.
- B shows "ALERT FROM UNRECOGNIZED NODE". A is not in B's Contact Matrix, or was revoked. B must scan A's QR.
- B shows "ALERT FAILED VERIFICATION". Either someone is impersonating A, or A reinstalled and B still holds A's old keys. Pair again in person and compare fingerprints.
- B shows "ALERT WITH AN IMPOSSIBLE TIME". One phone's clock is wrong. Turn on automatic date and time on both.
- A shows "Panic dispatched" but nothing arrives and no failure notification appears. The carrier accepted the SMS and has not delivered it. Wait a minute, check signal on both phones, and check that neither number is blocked in the stock Messages app. Send a plain text between the two numbers to confirm the route works at all.
- The screen is red but the alarm is silent. Look for "No playable alarm sound" in logcat: the phone has no default alarm tone. Set one in the Clock app.
- Nothing in logcat at all on B. The broadcast never reached the app. Confirm SMS permissions are granted and that no other SMS app blocks unknown senders.

Record every failure with the phone model, the Android version and the logcat lines. That list is what turns into the OEM deep links on the roadmap.

## Before real use

The debug build is signed with `debug.keystore`, which is published in this repository with its password. Anyone can sign an APK with it that installs over your debug build as an "update" and inherits your keys and contacts. For anything beyond this test, build a release signed with a key only you hold:

```bash
export RELEASE_KEYSTORE_PATH=/path/to/your.jks
export RELEASE_KEYSTORE_PASSWORD=...
export RELEASE_KEY_ALIAS=...
export RELEASE_KEY_PASSWORD=...
./gradlew assembleRelease
```

A release build cannot be installed over a debug build, so uninstall first and pair again. The readiness card's Build row turns green on a release build.
