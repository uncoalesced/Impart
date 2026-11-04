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

1. Generate Gradle dependency verification metadata with SHA-256 checksums (`./gradlew --write-verification-metadata sha256 help assembleDebug assembleRelease testDebugUnitTest lintDebug`). Without a local SDK, add a temporary job, triggered by your pull request, that writes the file and uploads it as an artifact; commit the artifact, then remove the job. `workflow_dispatch` will not work: it only runs workflows that exist on the default branch. Do this step after T17, because T17 adds androidTest dependencies and would change the checksums. If you add dependencies after that, regenerate.
2. Pin `distributionSha256Sum` in `gradle-wrapper.properties`.
3. Add `gradle/actions/wrapper-validation` to `development.yml` and `main.yml`.
4. Add a CodeQL workflow for `java-kotlin` on PRs into `development` and `main`, with a manual build step (`./gradlew assembleDebug`). The repo is private: CodeQL results upload only if GitHub Advanced Security or code scanning is available for it. If the upload is refused, keep the workflow but make the upload step tolerate it, record that in REPORT.md, and ask Joel whether to enable code scanning.
5. Pin every GitHub Action in every workflow to a full commit SHA, with the version in a comment.
6. Set `permissions:` to the minimum in every workflow.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Verification metadata committed, and an altered checksum fails the build (link the run, then revert).
- Wrapper validation green; CodeQL green or its upload limit recorded in REPORT.md.
- Every action SHA-pinned.
