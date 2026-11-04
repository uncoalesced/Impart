<!-- Engineered by uncoalesced -->
# design

Brand assets for Impart. The rules are in `specs/09_DESIGN_LANGUAGE.md`.

- `tokens.json` and `generate_mark.py`: the shared uncoalesced family tokens and mark generator, copied from the branding folder on 2026-11-04. Impart's accent there is recorded as Watermelon.
- `impart_mark.py`: Impart's own mark, a pointer arrowhead on the 9x9 grid with the center cell empty. Stdlib Python, no dependencies.
- `impart-mark.svg`, `impart-mark-transparent.svg`, `impart-mark.cells.json`: its output for seed 4, the shipped mark.
- `fonts/`: the SIL Open Font License texts for the bundled JetBrains Mono and IBM Plex Sans in `app/src/main/res/font/`.

Regenerate the mark and the Android icon layers:

```bash
python design/impart_mark.py --seed 4 --out-dir design --android app/src/main/res/drawable
```

Do not hand-edit `app/src/main/res/drawable/ic_launcher_*.xml` or `ic_stat_impart.xml`; rerun the script.
