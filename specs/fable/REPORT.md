<!-- Engineered by uncoalesced -->
# Fable handback report

Fable fills every section before handing back (task T20). Joel builds and tests from this file alone.

## 1. Summary

- Branch and head commit:
- PR:
- Latest green CI run:
- One paragraph on what changed:

## 2. How to build and install

Write the exact commands. Start from: pull `fable/finish-impart`, uninstall any older Impart build from every test phone (the T02 PIN migration and any T07 wire change can make old installs incompatible), then:

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

Add the release APK steps from T10 and T11.

## 3. What Joel must test on phones

Point to the phases of `specs/08_FLIGHT_CHECK_PROTOCOL.md`, new phases first. Include the release APK checks from T10.

## 4. Security changes, by adversary

One line per attack table row that changed status, with the commit.

## 5. Decisions Joel made, and questions still open

## 6. New and changed classes, and the tests that cover them (T18)

## 7. CI jobs

Every job, what it checks, and its latest run.

## 8. Known limits and residual risks

Copy the out-of-reach rows from spec 03 so they are in front of Joel.

## 9. Release key custody (T11)

## 10. Notes

The T00 pipeline call map goes here.
