<!-- Engineered by uncoalesced -->
# T21: No Google components in the APK

Adversaries: A4, A8. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

Added on 2026-11-04 after the T05 audit, with Joel's approval in chat.

## Goal

The T05 audit found that ML Kit's telemetry backend (`com.google.android.datatransport`) added `android.permission.INTERNET` to the merged manifest. The permission is now removed at merge time, but ML Kit and Play Services location are still the only Google code in the APK, and the telemetry pipeline still ships. Replace both so nothing from Google Play Services, Firebase or ML Kit is in the build (brief rule 10).

## Steps

1. QR scanning: decode CameraX `ImageAnalysis` frames with ZXing (`com.google.zxing:core`, already a dependency for drawing QR codes). Use the luminance plane only, try the frame as delivered and rotated, and keep the single-shot guard.
2. Location: replace `FusedLocationProviderClient` with `android.location.LocationManager`. Prefer GPS, then network, with a bounded timeout; best effort exactly as before: a missing fix never delays the panic. No new permission.
3. Remove `mlkit-barcode-scanning` and `play-services-location` from `gradle/libs.versions.toml` and `app/build.gradle.kts`, and the manifest entries that only existed for them.
4. Keep the `tools:node="remove"` lines for network permissions as defence in depth; the merged manifest must still pass `manifest-check.sh`.
5. Update the F-Droid readiness step in `main.yml` (no more warning), spec 04 section 4.1, spec 03 rows A4-6 and A8-6, PRIVACY.md (location is no longer asked from Google Play services), and REPORT.md.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- No `com.google.android.gms`, `com.google.mlkit`, `firebase` or `datatransport` in the dependency graph or the merged manifest.
- QR decode logic unit-tested on the JVM with a generated QR image.
- Location timeout and provider order unit-tested.
- Docs updated.
