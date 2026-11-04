<!-- Engineered by uncoalesced -->
# T03: Lockout counters resist tampering

Adversaries: A1, A6. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Wrong-PIN lockouts must not be skippable by changing the clock, rebooting, force-stopping or clearing state, short of a full data wipe.

## Files

- `core/security/LockoutPolicy.kt`
- `core/security/AppLockManager.kt`
- `data/local/prefs/SecurePrefsManager.kt`

## Steps

1. Read how the failed-attempt count and lockout deadline are stored today.
2. Store the count and the deadline in the encrypted prefs, written with `commit()` before the result is shown, so a force-stop right after a wrong guess still counts it.
3. Measure lockout time with both `SystemClock.elapsedRealtime()` and the wall clock. Store the remaining duration and the boot count (`Settings.Global.BOOT_COUNT`). After a reboot or a wall-clock jump backwards, the lockout resumes at the full remaining duration and never shortens.
4. Keep the existing rule: the duress PIN works even during a lockout.
5. Propose a hard cap to Joel: after N failed attempts (suggest 20) the app performs the duress wipe. It is a behavior change, so ask first. If he agrees, ship it behind a setting that defaults off.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Tests: a wall clock moved back does not shorten a lockout; a simulated reboot keeps the remaining time; a force-stop after a wrong guess still counts it; duress works mid-lockout.
- The attempt counter cannot be reset without wiping app data.
- Joel's answer on the hard cap recorded in the ledger.
