<!-- Engineered by uncoalesced -->
## What and why

<!-- What this change does, and the problem it solves. Link the issue. -->

## Guarantees

- [ ] No network permission, library or call added (`manifest-check.sh` passes on the merged manifest).
- [ ] Alerts are still verified against stored keys only; nothing routes to an address taken from an alert.
- [ ] Every new failure path in the receive pipeline ends in something the user sees.
- [ ] Logs go through `Redact`; no name, number, location, PIN, key or payload is logged.
- [ ] The duress wipe stays silent and complete, and works during a lockout.
- [ ] The wire format is unchanged, or the maintainer approved the change and every phone must re-pair.
- [ ] No new dependency, or it is pinned in `gradle/libs.versions.toml` and justified in the commit.

## Tests

- [ ] JVM tests added or updated (`./gradlew testDebugUnitTest`).
- [ ] Compose tests added or updated where a screen changed (`./gradlew connectedDebugAndroidTest`).
- [ ] Flight check phases run on real phones, if the receive pipeline, the lock or the wipe changed: <!-- which phases -->

## Docs

- [ ] Specs updated where behavior changed.
- [ ] `PRIVACY.md` updated if what the app stores, sends, reads or logs changed; `LegalDocument.CURRENT_VERSION` bumped if users should see it.
- [ ] `CHANGELOG.md` entry added.
