<!-- Engineered by uncoalesced -->
# Design language

Impart is one product in the uncoalesced family. This spec is the in-repo copy of the family design system, narrowed to Impart. The source of truth for the shared tokens is `design/tokens.json`, copied from the uncoalesced branding folder on 2026-11-04. The mark generator is `design/impart_mark.py`.

If this spec and `presentation/theme/Color.kt` disagree, this spec wins and `Color.kt` is the defect.

---

## 1. Concept

The family mark is a 9x9 field of separate circles and rounded squares. The center cell is always empty: the one place everything should converge is a deliberate gap. Parts that read as one, without fusing.

For Impart the mark is a pointer. It is an upward arrowhead with a notched base, built from 21 cells on the 9x9 grid, with the center cell empty. It points at a person who needs help.

## 2. Accent

Each product claims exactly one family hue. **Impart's hue is Watermelon `#EF2D56`.** It is an emergency product, so the alarm color is its identity. FluxBoard shares the hue; the fixed pointer shape keeps the two apart.

The Watermelon ramp is computed, never hand-picked: 35 percent toward Graphite for the shade and 35 percent toward Porcelain for the tint.

| Token | Hex | Use |
|---|---|---|
| `ImpartAccentShade` | `#AD2F4A` | Pressed states, the darker third of the mark |
| `ImpartAccent` | `#EF2D56` | The mark, the panic button, the intrusion screen, the selected nav item |
| `ImpartAccentTint` | `#F47690` | Accent-colored text and icons on Graphite surfaces |

## 3. Palette

Screens stay true black. This is an emergency device and black pixels are off pixels on AMOLED. Graphite is the logo's ground and the surface color, not the screen color.

| Kotlin name | Hex | Role | Replaces |
|---|---|---|---|
| `ImpartGround` | `#000000` | Every screen background | unchanged |
| `ImpartSurface` | `#262626` | Cards, list rows | navy `#2B2D42` |
| `ImpartSurfaceRaised` | `#333333` | Dialogs, nav bar, sheets | navy `#2B2D42` |
| `ImpartBorder` | `#4B4B4B` | Dividers, outlines | none |
| `ImpartTextPrimary` | `#FDFFFC` | Porcelain, body and headings | `#EDF2F4` |
| `ImpartTextMuted` | `#9A9B99` | Labels, unselected, timestamps | `#8D99AE` |
| `ImpartAccent*` | see section 2 | Brand and emergency | `ImpartPanicRed`, `ImpartPanicDark` |
| `ImpartWarning` | `#F19143` | Sandy Brown: stale, expiring, needs attention | Amber `#FFB000` |
| `ImpartVerified` | `#748E54` | Palm Leaf: healthy, verified, ready | `#2FBF71` |
| `ImpartVerifiedTint` | `#A3B48F` | Palm Leaf text on surfaces | none |

Moss `#B28F4B` is texture only and has no role in the app UI.

`res/values/colors.xml` stays the one sanctioned mirror of this table. The launcher icon layers in `res/drawable/ic_launcher_*.xml` are generated with literal hex values and are exempt.

### Rules

- Watermelon means "this is Impart" or "this is an emergency". It never means "error" or "warning". A failed send, a refused SMS part or a stale contact uses `ImpartWarning`.
- The intrusion screen is full-bleed `ImpartAccent`. Text on it is `ImpartGround` black (5.18:1). Porcelain on Watermelon is only 4.03:1, so it is allowed only at 24sp bold or larger.
- Accent-colored text on a Graphite surface uses `ImpartAccentTint`. Base Watermelon on `#262626` is 3.73:1 and fails body text.
- Palm Leaf text on a surface uses `ImpartVerifiedTint`. The base is fine as a dot or a fill.

### Measured contrast (WCAG 2.x)

| Foreground on background | Ratio | Body text |
|---|---|---|
| Porcelain on black | 20.89 | pass |
| Porcelain on `#333333` | 12.57 | pass |
| Muted on `#262626` | 5.42 | pass |
| Muted on `#333333` | 4.53 | pass, just |
| Watermelon on black | 5.18 | pass |
| Watermelon on `#262626` | 3.73 | large text only |
| Accent tint on `#262626` | 5.65 | pass |
| Accent tint on `#333333` | 4.72 | pass |
| Black on Watermelon | 5.18 | pass |
| Porcelain on Watermelon | 4.03 | 24sp bold only |
| Sandy Brown on `#262626` | 6.39 | pass |
| Palm Leaf tint on `#262626` | 6.83 | pass |
| Muted on black | 7.52 | pass |
| Accent tint on black | 7.84 | pass |
| Porcelain on accent shade (broadcast panic button) | 6.36 | pass |
| Sandy Brown on black | 8.87 | pass |
| Sandy Brown on `#333333` | 5.34 | pass |
| Black on Sandy Brown (revoke button) | 8.87 | pass |
| Palm Leaf tint on black | 9.48 | pass |
| Palm Leaf tint on `#333333` | 5.71 | pass |
| Black on Porcelain (QR code, maps button) | 20.89 | pass |
| Watermelon switch track on `#333333` | 3.1 | graphic only |

Added with T15. `ContrastTest` computes every row and fails below 4.5:1 for body text and 3:1 for large text and graphics.

Every new color pairing gets a ratio in this table and a JVM test that computes it.

## 4. Typography

Both families are bundled in `app/src/main/res/font/` under the SIL Open Font License; the license texts are in `design/fonts/`. Downloadable Google Fonts are not allowed: they need Play Services and the network.

| Family | Files | Use |
|---|---|---|
| JetBrains Mono | `jetbrains_mono_regular`, `_medium`, `_semibold`, `_bold` | Headings, the wordmark, numbers, PINs, key fingerprints, timestamps, phone numbers |
| IBM Plex Sans | `ibm_plex_sans_regular`, `_medium`, `_semibold` | Body text, buttons, dialogs, legal documents |

The wordmark is lowercase `impart` in JetBrains Mono SemiBold, Porcelain on dark grounds.

Suggested Material 3 mapping: display, headline and title styles use Mono; body and label styles use Plex Sans. Fingerprints always use Mono so groups line up.

## 5. The mark

- Source: `design/impart_mark.py --seed 4`. The output is stable. Do not reseed without Joel's approval.
- Cell list: `design/impart-mark.cells.json` (row, col, shape, tone, color). The app draws the mark from this fixed list in a Compose `Canvas`. It never randomizes at runtime.
- Geometry: 100-unit cells on a 900 canvas. Circles are 62 percent of the cell in diameter. Squares are 66 percent of the cell per side, with corner radius 12 percent of the side.
- Launcher icon: adaptive icon with a Graphite background, the mark in a 52-unit box centered on the 108 canvas (inside the 66 safe circle), and a white monochrome layer for themed icons.
- Status bar icon: `ic_stat_impart`, white only, 24dp.
- Clearspace: one cell-width on every side. Minimum size: 32px alone, 120px wide with the wordmark.
- The Calculator cloak icon is not part of the brand and must keep looking like a stock calculator.

## 6. Shape and spacing

- Corner radius: 4dp for small controls, 10dp for cards and dialogs.
- Spacing on a 4dp scale: 4, 8, 12, 16, 24, 32.
- Screen gutter: 16dp.
- Hairline borders in `ImpartBorder` instead of elevation shadows.
- Minimum touch target: 48dp. The panic control is much larger.

## 7. Do and don't

| Do | Don't |
|---|---|
| Keep the mark's center cell empty | Fill it "for balance" |
| Use Watermelon for the brand and for emergencies | Use Watermelon for errors, warnings or decoration |
| Draw the mark from the committed cell list | Generate a new mark at runtime |
| Keep screens true black | Put Graphite behind a whole screen |
| Use Mono for anything a person compares character by character | Set fingerprints or PINs in a proportional font |
| Bundle fonts | Fetch fonts or any asset over the network |

## 8. Decoy and cloak screens

`NodeCorruptedScreen` and the Calculator cloak exist to look like something else. They do not use the Impart mark, the wordmark or Watermelon. They keep their own neutral styling so nothing ties them back to Impart.
