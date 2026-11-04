<!-- Engineered by uncoalesced -->
# T11: Release signing that cannot be confused with debug

Adversaries: A8. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

A release APK is never signed with the public debug key, uses the v2 and v3 signature schemes, and the app warns when it runs a debug-signed build.

## Files

- `app/build.gradle.kts`
- `.github/workflows/deployment.yml`
- `core/util/NodeReadinessChecker.kt`

## Steps

1. Make the release build fail outright in `deployment.yml` when the signing env vars are missing, instead of falling back.
2. Enable v2 and v3 signing explicitly. v1 is not needed at minSdk 26.
3. In `deployment.yml`, run `apksigner verify --print-certs` on the output and fail if the certificate matches the debug keystore's SHA-256.
4. Make the readiness card's debug-build warning check the signing certificate, not only `BuildConfig.DEBUG`.
5. Write key custody notes for Joel in REPORT.md: generate the release key offline, back it up, never commit it, rotate the GitHub secrets if exposed.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Deployment job fails without signing secrets and fails on a debug-signed output.
- Readiness card checks the certificate.
- Key custody notes in REPORT.md.
