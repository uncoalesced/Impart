<!-- Engineered by uncoalesced -->
# T05: Manifest and component audit, plus the no-INTERNET CI job

Adversaries: A5, A8. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Every component is exported only if it must be, every exported component is permission-protected, and CI fails the moment INTERNET appears in the merged manifest of any variant.

## Files

- `app/src/main/AndroidManifest.xml`
- `.github/workflows/development.yml`, `main.yml`
- `.github/scripts/` (new `manifest-check.sh`)

## Steps

1. List every component in the merged manifest (`app/build/intermediates/merged_manifest/<variant>/`), including ones added by libraries (WorkManager, Room, startup, CameraX).
2. For each: exported or not, permission, intent filters, and whether Impart needs it. Disable or protect anything unneeded with `tools:node`.
3. Review `android:priority="999"` on the SMS receiver. Keep it only with a comment explaining why.
4. Add `.github/scripts/manifest-check.sh`. It reads the merged debug and release manifests and fails on: `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `android:debuggable="true"` in release, `allowBackup="true"`, `usesCleartextTraffic="true"`, or an exported component without a permission (other than the launcher activity and its alias).
5. Run it as a job in `development.yml` and `main.yml` after the build.
6. Write the component list into spec 04.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- `manifest-check.sh` runs in CI on both workflows.
- A deliberate test commit adding INTERNET made the job fail (link the failed run, then revert).
- Component list documented in spec 04.
