<!-- Engineered by uncoalesced -->
# T14: Optional default SMS app mode

Adversaries: A4. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Today every alert also lands in the stock Messages inbox, where it shows who exchanged alerts with whom. As the default SMS app, Impart receives SMS first and never writes alerts to the Telephony provider. To hold that role, Impart must also work as a basic SMS app for ordinary texts.

## Files

- `app/src/main/AndroidManifest.xml`
- `core/services/` (new receivers and service)
- `presentation/` (new messages feature)
- `PRIVACY.md`, `TERMS.md`
- `specs/02`, `03`, `08`

## Steps

1. The mode is optional and off by default. Settings offers "Make Impart your SMS app", explains the trade-off, and requests the role with `RoleManager.ROLE_SMS` on API 29 and above or `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT` below.
2. Components the role requires: a receiver for `SMS_DELIVER` protected by `BROADCAST_SMS`; a receiver for `WAP_PUSH_DELIVER` with MIME `application/vnd.wap.mms-message` protected by `BROADCAST_WAP_PUSH`; a service for `RESPOND_VIA_MESSAGE` protected by `SEND_RESPOND_VIA_MESSAGE`; an activity for `SENDTO` with the `sms`, `smsto`, `mms` and `mmsto` schemes.
3. New permissions allowed for this task only: `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`. Ask Joel before adding any other.
4. In `SMS_DELIVER`: an alert goes to the existing verifier and is never written to the provider. Anything else is written to `Telephony.Sms.Inbox`, as a normal SMS app must.
5. When not default, `SMS_RECEIVED` keeps working as today. When default, never handle the same message twice.
6. Minimal Compose UI for ordinary texts: conversation list, thread view, compose box. MMS shows as "MMS received" with sender and time; full MMS rendering is out of scope. It sits behind the app lock and follows spec 09.
7. Outgoing alerts sent while default are not written to the sent box either.
8. When the user gives up the role, Impart tells them inbox copies come back from then on.
9. Update PRIVACY.md (in this mode Impart reads and stores ordinary SMS on the phone; nothing leaves it), TERMS.md, spec 02, the A4 rows of spec 03, and spec 08 (a flight-check phase for this mode).
10. Add a note for REPORT.md: this mode makes the SMS permissions easier to justify on Google Play, but Play submission remains Joel's decision.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Role request branch selection unit-tested for API 26 to 35.
- An alert received while default never reaches the Telephony provider (test through a provider wrapper).
- Ordinary SMS stored and shown.
- Only the three listed permissions added.
- Legal documents and specs updated.

## Notes

This is the largest task. Split it into several commits: role and components, delivery routing, UI, docs. The legal version bump happens once, in T19.
