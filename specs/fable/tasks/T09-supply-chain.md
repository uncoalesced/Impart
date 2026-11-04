<!-- Engineered by uncoalesced -->
# T09: Supply chain: verified dependencies and CI scanning

Adversaries: A8. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

A tampered dependency or Gradle wrapper fails the build instead of shipping.

## Files

- `gradle/verification-metadata.xml` (new)
- `gradle/wrapper/gradle-wrapper.properties`
- `.github/workflows/`

## Steps

1. Generate Gradle dependency verification metadata with SHA-256 checksums (`./gradlew --write-verification-metadata sha256 help assembleDebug assembleRelease testDebugUnitTest lintDebug`). Without a local SDK, add a `workflow_dispatch` job that writes the file and uploads it as an artifact, then commit it.
2. Pin `distributionSha256Sum` in `gradle-wrapper.properties`.
3. Add `gradle/actions/wrapper-validation` to `development.yml` and `main.yml`.
4. Add a CodeQL workflow for `java-kotlin` on PRs into `development` and `main`, with a manual build step (`./gradlew assembleDebug`).
5. Pin every GitHub Action in every workflow to a full commit SHA, with the version in a comment.
6. Set `permissions:` to the minimum in every workflow.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Verification metadata committed, and an altered checksum fails the build (link the run, then revert).
- Wrapper validation and CodeQL jobs green.
- Every action SHA-pinned.
