<!-- Engineered by uncoalesced -->
# T15: Re-skin to the uncoalesced design language

Adversaries: none. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Every screen follows `specs/09_DESIGN_LANGUAGE.md`: true black grounds, Graphite surfaces, Watermelon as Impart's accent and emergency color, Sandy Brown warnings, Palm Leaf verified states, JetBrains Mono and IBM Plex Sans.

## Files

- `presentation/theme/Color.kt`, `Type.kt`, `Theme.kt`
- `app/src/main/res/values/colors.xml`
- every screen under `presentation/`
- `specs/05_CODING_STANDARDS.md` section 4
- `CLAUDE.md` (colors bullet)
- `.github/scripts/standards-check.sh`
- `core/services/ImpartNotifications.kt`, `ImpartSmsReceiver.kt`

## Steps

1. Rewrite `Color.kt` with the spec 09 names. Remove the navy and the old reds. Keep `ImpartGround`.
2. Mirror the colors `res/` needs in `colors.xml` and remove unused entries.
3. Build `FontFamily` objects from `R.font.*` in `Type.kt`, and a Material 3 `Typography` following spec 09 section 4.
4. Map the Material 3 `darkColorScheme` to the new tokens in `Theme.kt`.
5. Go screen by screen. The intrusion screen is full-bleed Watermelon with black text. The decoy and the cloak keep a neutral look (spec 09 section 8).
6. Fingerprints, PINs, numbers and timestamps use Mono.
7. Add a JVM test that computes WCAG contrast for every foreground and background pair used. It fails below 4.5:1 for body text and below 3:1 for 24sp bold.
8. Update spec 05 section 4 and the CLAUDE.md color bullets in the same commit. Update the standards script if any rule changes.
9. Use `R.drawable.ic_stat_impart` as the notification small icon instead of `android.R.drawable.ic_dialog_alert`.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- No `2B2D42`, `EF233C`, `D90429` or `FFB000` left anywhere.
- Contrast test green.
- Spec 05 and CLAUDE.md match Color.kt.
- Standards script green.
- Notification small icon switched.
