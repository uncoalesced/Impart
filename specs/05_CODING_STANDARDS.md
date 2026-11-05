<!-- Engineered by uncoalesced -->
# Coding Standards

---

These are not style suggestions. They are load-bearing constraints. An AI agent or contributor operating on this codebase follows every rule in this document without exception, and any generated code that violates one of these rules should be treated as a defect, not a stylistic difference of opinion.

## 1. The Signature Rule

Every generated `.kt`, `.kts`, and `.ts` file begins with:

```kotlin
// Engineered by uncoalesced
```

This is not decorative. Impart's entire premise is trust and provenance; the codebase should hold itself to the same standard it demands of message authenticity.

## 2. Emoji and Commenting Constraints

- **No emojis anywhere.** Emojis are strictly forbidden in code, UI text, logs, and markdown documentation files.
- **Minimal, necessary comments only.** Do not over-comment code or write obvious comments. Keep comments concise and focused purely on non-obvious operational context or constraints.

## 3. Absolute Bans

The following are forbidden, with no exceptions carved out for "just this one screen" or "just for a quick prototype":

- **No XML layouts.** Not even for legacy compatibility shims.
- **No Fragments.** Navigation is Compose state (tab index in `MainActivity`), and would move to Compose Navigation if it ever needs a back stack.
- **No DataBinding or ViewBinding.** These exist to bridge XML and code; if there's no XML, there's nothing to bind.
- **No `findViewById`.** If this ever appears in a diff, it's a sign the constraint above was violated somewhere upstream.

## 3. Jetpack Compose Conventions

- **State hoisting is mandatory.** Composables that need state receive it as a parameter and expose changes via a lambda (`onValueChange: (String) -> Unit`), rather than holding their own mutable state for anything a ViewModel should own.
- **`ViewModel` owns business state; the Composable owns only ephemeral UI state** (e.g. whether a dropdown is expanded) that has no meaning outside that single render tree.
- **Previews are not optional for reusable components.** Any Composable placed in a shared location (e.g. a reusable button or card) ships with a `@Preview` function.
- **Side effects use the correct API for their lifecycle:** `LaunchedEffect` for suspend-function side effects tied to composition, `DisposableEffect` for anything requiring explicit cleanup (e.g. registering/unregistering a broadcast receiver). Never a raw `LaunchedEffect(Unit)` as a substitute for `onCreate`-style one-time setup unless the cleanup semantics genuinely don't matter.

## 4. The Color System

Colors are defined once, in `presentation/theme/Color.kt`, and referenced everywhere else. The palette and its rules are `specs/09_DESIGN_LANGUAGE.md`; if this section and spec 09 disagree, spec 09 wins and this section is the defect.

```kotlin
// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.theme

object ImpartPalette {                 // ARGB values, read by ContrastTest on the JVM
    const val GROUND = 0xFF000000      // Every screen background
    const val SURFACE = 0xFF262626     // Cards, list rows
    const val SURFACE_RAISED = 0xFF333333 // Dialogs, nav bar, sheets
    const val BORDER = 0xFF4B4B4B      // Dividers, outlines
    const val TEXT_PRIMARY = 0xFFFDFFFC // Porcelain
    const val TEXT_MUTED = 0xFF9A9B99  // Labels, unselected, timestamps
    const val ACCENT_SHADE = 0xFFAD2F4A // Pressed states, broadcast panic
    const val ACCENT = 0xFFEF2D56      // Watermelon: Impart and emergencies
    const val ACCENT_TINT = 0xFFF47690 // Accent text and icons on surfaces
    const val WARNING = 0xFFF19143     // Sandy Brown
    const val VERIFIED = 0xFF748E54    // Palm Leaf, dots and fills
    const val VERIFIED_TINT = 0xFFA3B48F // Palm Leaf text on surfaces
}
// ImpartGround, ImpartSurface, ImpartSurfaceRaised, ImpartBorder, ImpartTextPrimary,
// ImpartTextMuted, ImpartAccentShade, ImpartAccent, ImpartAccentTint, ImpartWarning,
// ImpartVerified and ImpartVerifiedTint wrap these as Compose Colors.
```

Rules:

- Screen backgrounds are `ImpartGround`, true black, so AMOLED pixels stay off. `ImpartSurface` is for cards and rows, `ImpartSurfaceRaised` for dialogs, the navigation bar and sheets.
- Watermelon (`ImpartAccent`) means "this is Impart" or "this is an emergency". It never means error or warning. A failed send, a refused part, a destructive action or a stale contact uses `ImpartWarning`.
- `IntrusionScreen` is full-bleed `ImpartAccent` with `ImpartGround` text. Porcelain on Watermelon is only 4.03:1 and is allowed only at 24sp bold or larger.
- Text drawn on Watermelon is black; accent-colored text on a surface uses `ImpartAccentTint`; Palm Leaf text uses `ImpartVerifiedTint`.
- Every pairing the UI uses has a row in `ContrastTest` and in the spec 09 table. Body text needs 4.5:1, large text and graphics 3:1.
- The decoy and the Calculator cloak use no Watermelon, no mark and no wordmark (spec 09 section 8). The cloak icon has its own neutral `cloak_*` colors.
- `res/values/colors.xml` mirrors only what XML resources need (`impart_ground` for the window and system bars) plus the cloak's neutral colors. It is the one sanctioned duplicate.
- Fonts are `ImpartMono` (JetBrains Mono) for headings, numbers, PINs, fingerprints, timestamps and phone numbers, and `ImpartSans` (IBM Plex Sans) for body text, from `presentation/theme/Type.kt`. Never `FontFamily.Monospace` or a downloadable font.

## 5. Naming & Style Conventions

- Composable functions: `PascalCase`, noun-first (`IntrusionScreen`, `QrScannerScreen`, not `ScreenForIntrusion`).
- Regular functions and properties: `camelCase`.
- Use cases: verb-first, suffixed `UseCase` (`SendPanicSignalUseCase`).
- Repository interfaces live in `domain/repository/`; implementations live in `data/repository/` with an `Impl` suffix.
- Prefer `sealed interface` over `sealed class` for state hierarchies with no shared constructor logic; it is a marginally lighter-weight idiom for the same purpose in modern Kotlin.

## 6. Error Handling Doctrine

Silent failure is the one unforgivable sin in this codebase, given Impart's purpose. Concretely:

- Every use case that can fail returns a `Result<T>` or a project-specific sealed `ImpartResult<T>` type, never a nullable that collapses "didn't find it" and "something broke" into the same `null`.
- Cryptographic operations (`CryptoManager.encrypt/decrypt`) must never fail silently into a fallback path. A decryption failure surfaces as a distinct, loggable error state, not a dropped message.
- Any failure in the intrusion pipeline itself (WakeLock acquisition failure, notification build failure) should still attempt a degraded-but-visible fallback (e.g. a maximum-priority heads-up notification even if the full-screen intent path errors) rather than failing completely silent. A failed panic alert with no trace is the single worst failure mode this system can produce.
- The SMS relay returns `Result` and reports radio-level failures through `SmsSentReceiver` as notifications. `printStackTrace` is banned; log with a tag so `adb logcat -s` finds it.

## 7. Dependency Injection Rules

- Constructor injection via `@Inject` is the default; field injection is reserved only for Android framework classes that Hilt requires it for (`Activity`, `Service` via `@AndroidEntryPoint`).
- No `object` singletons for anything that holds state relevant to testing (e.g. no singleton `CryptoManager` implemented as a Kotlin `object`; it must be Hilt-provided so it can be swapped for a test double).

## 8. Testing Expectations

- `domain/` and `core/security/` are held to the highest test bar in the codebase: pure logic, no Android dependency, no excuse not to unit test. `CryptoManager` encryption/decryption round-trips and `QrPayloadCodec` serialization/deserialization should have dedicated unit tests before any UI work depends on them.
- Compose UI is tested with `ComposeTestRule` for the handshake and dashboard flows at minimum; `IntrusionScreen` is exempted from full instrumentation testing initially given its reliance on system-level keyguard/wake-lock state, but should still have a Preview-based visual regression check.
