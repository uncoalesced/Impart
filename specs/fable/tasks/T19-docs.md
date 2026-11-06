<!-- Engineered by uncoalesced -->
# T19: Docs match the code

Adversaries: A4 (honesty). The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Every document a user or contributor reads is true for this build.

## Files

- `README.md`, `CLAUDE.md`
- `specs/02`, `03`, `04`, `05`, `06`, `08`, `09`
- `PRIVACY.md`, `TERMS.md`
- `domain/model/LegalDocument.kt`

## Steps

1. README: what Impart does, what it resists (a short adversary table), what it does not, how to build, the logo, and the existing Fable credit line.
2. CLAUDE.md: architecture, gate order, new components, new colors and fonts, new CI jobs.
3. Spec 04 file map. Spec 06 roadmap: move finished items to done, keep the messaging pivot under "later, needs its own threat model".
4. Spec 08: new phases for the PIN pad, overlay rejection, key rotation, default SMS mode, the release APK and the new icon.
5. PRIVACY.md and TERMS.md: everything stored, read or sent, including default-SMS mode, plus a fonts section crediting JetBrains Mono and IBM Plex Sans under the SIL OFL 1.1. Stay inside the `LegalDocumentParser` subset.
6. Bump `LegalDocument.CURRENT_VERSION` once for all legal changes on this branch.
7. Add a root `SECURITY.md` (Joel, 2026-11-04 in chat): what Impart resists and does not, how to report a vulnerability (the contact address in `LegalDocument`), supported builds, and a pointer to spec 03. Keep it inside the honesty rule.
8. Search every doc for claims the code does not back ("unbreakable", "cannot be traced", Keystore-only storage) and fix them.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- LegalDocumentParserTest green.
- CURRENT_VERSION bumped once.
- Search terms used for unbacked claims listed in the ledger, none left.
- `SECURITY.md` present at the repository root.
