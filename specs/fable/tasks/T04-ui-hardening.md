<!-- Engineered by uncoalesced -->
# T04: UI hardening against hostile apps on the device

Adversaries: A5, A6. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

No other app can see, record, overlay, type into or click Impart's sensitive controls.

## Files

- `MainActivity.kt`
- `presentation/intrusion/IntrusionActivity.kt`
- `presentation/security/`
- `presentation/components/HoldToFireButton.kt`
- `presentation/handshake/`, `presentation/contacts/`

## Steps

1. Build an in-app numeric PIN pad in Compose and use it for every PIN entry (setup, change, lock, auth dialogs). The system keyboard never sees a PIN. Offer a shuffled digit layout as a Settings option, default off.
2. Where a text field must stay (contact names, own phone number), set `autoCorrect = false` and the IME flag `IME_FLAG_NO_PERSONALIZED_LEARNING` so keyboards do not learn names or numbers.
3. Tapjacking: reject touches when the window is obscured on the PIN pad, panic button, acknowledge button, fingerprint confirm and revoke buttons. In Compose, check `MotionEvent.FLAG_WINDOW_IS_OBSCURED` and `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` through `pointerInteropFilter`. Also call `setHideOverlayWindows(true)` on API 31 and above.
4. `FLAG_SECURE` on every window, including dialogs and the decoy.
5. Recents: disable the task snapshot (`setRecentsScreenshotEnabled(false)` on API 33 and above).
6. Never put a PIN, key, fingerprint or phone number on the clipboard. If a copy action exists, mark the clip sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`) and clear it after 60 seconds.
7. Accessibility: sensitive fields describe state without reading the value ("PIN, 4 digits entered").
8. Lock when the screen turns off, not only after 30 seconds in the background.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- No PIN entry uses a `TextField`.
- Obscured-touch rejection unit-tested where possible and listed in the flight check for device testing.
- Every Activity and Dialog sets FLAG_SECURE, asserted by a test.
- Recents snapshot disabled on supported API levels.
