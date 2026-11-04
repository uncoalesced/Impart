<!-- Engineered by uncoalesced -->
# Fable handback report

Fable fills every section before handing back (task T20). Joel builds and tests from this file alone.

## 1. Summary

- Branch and head commit:
- PR:
- Latest green CI run:
- One paragraph on what changed:

## 2. How to build and install

Write the exact commands. Start from: pull `fable/finish-impart`, uninstall any older Impart build from every test phone (the T02 PIN migration and any T07 wire change can make old installs incompatible), then:

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

Add the release APK steps from T10 and T11.

## 3. What Joel must test on phones

Point to the phases of `specs/08_FLIGHT_CHECK_PROTOCOL.md`, new phases first. Include the release APK checks from T10.

## 4. Security changes, by adversary

One line per attack table row that changed status, with the commit.

## 5. Decisions Joel made, and questions still open

## 6. New and changed classes, and the tests that cover them (T18)

## 7. CI jobs

Every job, what it checks, and its latest run.

## 8. Known limits and residual risks

Copy the out-of-reach rows from spec 03 so they are in front of Joel.

## 9. Release key custody (T11)

## 10. Notes

### T00 pipeline call map

Send path:

1. `DashboardViewModel.initiatePanic` / `broadcastPanic` (or `DeadMansSwitchWorker.doWork`) -> `RelayManagerImpl.dispatchPanic` / `broadcastPanic`.
2. `RelayManagerImpl` builds `PanicPayload.Panic(from = localUuid, to = contact.uuid, id = PanicPayloadCodec.newMessageId(), sentAt = clock.now(), lat, lng)` and calls `sendSigned`.
3. `AlertEnvelope.seal`: `PanicPayloadCodec.encode` (JSON once) -> `CryptoManager.sign` (Ed25519 over the UTF-8 bytes) -> `PanicPayloadCodec.seal` (`<sig>.<json>`) -> `CryptoManager.encrypt` (HPKE to the contact's stored hybrid key) -> base64.
4. `RelayManagerImpl.send`: `IMPART_ALERT:` prefix, `SubscriptionManager.getDefaultSmsSubscriptionId`, `SmsManager.divideMessage`, one `SmsSentReceiver.pendingIntent` per part, `sendMultipartTextMessage`. `SmsSentReceiver` turns a refused part into a notification.

Receive path:

1. `ImpartSmsReceiver.onReceive` (manifest receiver, `BROADCAST_SMS`, priority 999): joins the parts, matches the prefix, returns if `DuressManager.isCorrupted`, takes a 30 s partial wake lock and `goAsync()`.
2. `AlertVerifier.verify(ciphertext, localUuid)` in order: decrypt (`Unreadable`), unseal (`Outdated` for v1), parse (`Malformed`), `ContactRepository.findActiveByUuid(from)` (`UnknownSender`), `CryptoManager.verify` against the stored signing key (`BadSignature`), `to == localUuid` (`NotForThisNode`), future skew 10 min (`FromFuture`), age 24 h (`Stale`), `SeenAlertStore.markSeen` (`Replayed`), then `Accepted(sender, payload, delayMinutes)`.
3. `ImpartSmsReceiver.handle`: every non-accepted verdict posts a status or security notification through `ImpartNotifications.notifyHeadsUp`. An accepted ACK posts on the ACK channel. An accepted panic calls `fireIntrusion`: a `CATEGORY_ALARM` full-screen-intent notification on the panic channel whose intent is `IntrusionActivity.createIntent(senderName, contactUuid, messageId, sentAt, delayMinutes, lat, lng)`.
4. `IntrusionActivity.onCreate` (`singleTop`, `showWhenLocked`, `turnScreenOn`, `FLAG_SECURE`, keyguard dismiss) cancels the panic notification and starts `IntrusionAlarm` (looping `MediaPlayer` on `USAGE_ALARM` with the alarm stream raised, plus vibration). `IntrusionScreen` shows the sender, the send time and optional coordinates.
5. Acknowledge: `IntrusionActivity.acknowledgeAndFinish` -> `ContactRepository.findActiveByUuid(contactUuid)` (the contact as stored, never from the alert) -> `RelayManagerImpl.dispatchAck(contact, messageId)` -> the same seal and send path.

The gate in `MainActivity.onCreate`: `NodeCorruptedScreen` if `DuressManager.isCorrupted`; `LegalAcceptanceScreen` while `acceptedLegalVersion < LegalDocument.CURRENT_VERSION`; `PinSetupDialog(required = true)` while `hasCustomPins` is false; `AppLockScreen` while `AppLockManager.isUnlocked` is false; then `MainAppScreen` and the permission request. `IntrusionActivity` sits outside this gate.

### T00 spec-versus-code disagreements

Listed in `specs/fable/LEDGER.md` under T00 and owned by T01 or T19.
