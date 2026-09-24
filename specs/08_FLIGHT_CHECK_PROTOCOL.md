<!-- Engineered by uncoalesced -->
# Flight check protocol: three phones, one carrier

Status: ready to run. Nothing in this document has been executed on hardware yet. The first person to run it should note results inline and commit them.

Do not use emulators for any of this. Emulators have no SIM, do not enter Doze the way phones do, and cannot show you what a Samsung battery saver does at 3am.

## What you need

- Three Android phones (call them A, B, C), Android 8.0 or newer, each with a SIM that can send and receive SMS. Different manufacturers is better, not worse: the point is to find the one that kills the receiver.
- The debug APK from `./gradlew assembleDebug`, installed on all three with `adb install -r` or by copying the file over.
- A laptop with `adb` for reading logs when something fails. Optional but it turns "it did nothing" into a line number.

Useful log filter, run against the receiving phone:

```bash
adb logcat -s ImpartSmsReceiver IntrusionActivity IntrusionAlarm RelayManager SmsSentReceiver DuressManager
```

## Phase 1: setup on each phone

1. Install and open Impart. Grant SMS, camera and notifications when asked. Deny nothing.
2. My QR tab: type the phone's own number with country code (`+44...`, `+1...`). The QR appears once the number is set. If it does not, the text under the card says why.
3. Dashboard, Node Readiness card. Tap every amber row and follow the prompt until all seven rows are green:
   - SMS permissions
   - Notifications
   - Full-screen alerts (Android 14 and later only; the row opens the system page where you allow it)
   - Battery optimization (accept the "allow" prompt; on Xiaomi, Oppo, Vivo and some Samsungs also find the OEM's own autostart or background-activity switch in App Info and turn it on)
   - SIM for SMS (on dual-SIM phones the default SMS SIM must be set)
   - Own phone number
   - Trusted contacts (stays amber until Phase 2)
4. Tap the lock icon, enter `1234`, set a standard PIN and a duress PIN. Remember both. Do not test the duress PIN until Phase 6.

Expected: seven green rows on each phone except "Trusted contacts".

## Phase 2: pairing

Pairing is one direction per scan. For each pair (A-B, A-C, B-C):

1. On the first phone open My QR. On the second open Scan QR and point it at the first. A toast says "Paired with +number" and the Contacts tab opens.
2. Swap roles and repeat.
3. On the Contacts tab, tap each contact's name and give it a nickname (the other phone's owner, or "Samsung S23"). That name is what the red screen shows.

Check a rejection while you are here: scan your own QR. Expected: a toast saying you scanned this node's own QR, nothing added, and the scanner keeps scanning.

Expected at the end: every phone lists the other two, each with a nickname, each badge reading "LAST VERIFIED TODAY". Readiness is now fully green.

## Phase 3: foreground alert and ACK

Both phones unlocked, Impart open on both, sitting on the table.

1. On A, hold "INITIATE PANIC: B" for the full 1.5 seconds. Releasing early cancels; that is the point of the hold.
2. A shows "Panic dispatched to B" for three seconds.

Expected on B within one to ten seconds (carrier dependent): the screen goes panic red, "FROM: A's nickname" is shown, the alarm tone loops at full alarm volume, the phone vibrates. If location sharing was on for A, coordinates and a NAVIGATE button appear.

3. On B tap ACKNOWLEDGE & DISMISS. The alarm stops, the screen closes.

Expected on A: a notification "PANIC ACKNOWLEDGED".

Also expected: the ciphertext SMS shows up in the stock Messages app on B as a long `IMPART_ALERT:...` text. That is normal; a non-default SMS app cannot suppress it.

If A instead shows an error under the panic buttons, read it. "No SIM is selected for sending SMS" and "Contact has no phone number" are the two it can produce before the radio is involved. A notification "PANIC SMS FAILED TO SEND" means the radio refused a part; the notification names the reason.

## Phase 4: broadcast

1. On A, hold "BROADCAST PANIC (ALL NODES)".

Expected: B and C both go red within a few seconds of each other. A shows "Panic dispatched to 2 nodes". If one fails, A shows "Sent to 1 of 2 nodes" and names the failure.

## Phase 5: the deep sleep test

This is the only test that proves the app does its job.

Prepare B:

1. Unplug it. Doze arrives faster on battery.
2. Swipe Impart away from Recents (force-stop from App Info is harsher and also worth trying as a second run).
3. Turn on Do Not Disturb.
4. Lock it, screen off, put it face down, and do not touch it for 20 minutes.

Then, from A: hold "INITIATE PANIC: B".

Expected: within a few seconds B lights up, shows the red screen over the lock screen, and sounds the alarm through Do Not Disturb. Dismissing it sends the ACK to A.

Run this once per phone as the receiver. Record the phone model, Android version, and the delay.

## Phase 6: duress and decoy

Do this on one phone you can reinstall on. The wipe is permanent for that install.

1. Tap the lock icon and enter the duress PIN.

Expected: no toast, no confirmation. The whole app becomes a grey "NODE CORRUPTED / SYSTEM ERROR" screen with no navigation. Killing and reopening the app shows the same screen. Sending a panic to this phone from A produces nothing on it at all.

2. Reinstall to recover. The other phones still list the old identity; revoke it and re-pair.

## When it fails

- **B gets the notification but the screen stays dark.** Android 14 full-screen intent not allowed. Readiness row three. On older Android, check that the lock screen is not a "secure lock screen with notifications hidden" setting.
- **B does nothing until unlocked, then the alert appears.** The OEM battery saver held the receiver. Readiness row four, then the OEM-specific autostart switch. On Samsung also check "Never sleeping apps" in Battery settings.
- **B shows "UNREADABLE ALERT RECEIVED".** B's keys changed since A scanned it (reinstall, wipe, or A scanned an old QR). Re-pair.
- **B shows "PANIC FROM UNRECOGNIZED NODE".** A is not in B's Contact Matrix, or was revoked. B must scan A's QR.
- **A shows "Panic dispatched" but nothing arrives and no failure notification.** The carrier accepted the SMS and has not delivered it. Wait a minute; check signal on both phones; check that neither number is in a "blocked" list in the stock Messages app. Send a plain text between the two numbers to confirm the route works at all.
- **Alarm is silent but the screen is red.** Look for "No playable alarm sound" in logcat; the phone has no default alarm tone set. Set one in the Clock app.
- **Nothing in logcat at all on B.** The broadcast never reached the app. Confirm RECEIVE_SMS is granted (readiness row one) and that no other SMS app is set to block unknown senders.

Record every failure with phone model, Android version, and the logcat lines. That list is what turns into the OEM deep links on the roadmap.
