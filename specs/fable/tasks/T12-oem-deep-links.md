<!-- Engineered by uncoalesced -->
# T12: OEM autostart and background settings

Adversaries: none (availability). The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

On Xiaomi, Oppo, Realme, Vivo, Huawei, Honor, OnePlus and Samsung, the readiness card opens the extra settings screens that otherwise kill the receiver.

## Files

- `core/util/NodeReadinessChecker.kt`
- `presentation/dashboard/`, `presentation/settings/`

## Steps

1. Detect `Build.MANUFACTURER` and `Build.BRAND`, and build a table of candidate component intents per vendor, using the dontkillmyapp.com list as reference. The table lives in Kotlin in the repo; nothing is fetched.
2. Check each candidate with `resolveActivity` before showing it, and fall back to the app details settings page.
3. These OEM settings cannot be read back, so the row is confirmed by the user ("I have allowed autostart") and stored as a flag.
4. No new permission.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Unit tests for the vendor table and the fallback order.
- A row shows only when an intent resolves or a fallback exists.
- No new permissions.
