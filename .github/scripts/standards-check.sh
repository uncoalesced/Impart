#!/usr/bin/env bash
# Enforces specs/05_CODING_STANDARDS.md in CI. Exits non-zero on any violation.
# Emits GitHub Actions ::error annotations so failures surface inline on the PR.
set -uo pipefail

fail=0

echo "== Signature check (// Engineered by uncoalesced on line 1) =="
while IFS= read -r f; do
  if [ "$(head -n 1 "$f")" != "// Engineered by uncoalesced" ]; then
    echo "::error file=$f,line=1::missing '// Engineered by uncoalesced' on line 1"
    fail=1
  fi
done < <(find app/src/main/java -name '*.kt')

echo "== Palette check (no hardcoded colors outside theme/Color.kt) =="
palette=$(grep -rnE 'Color\.(Red|White|Black|Blue|Green|Gray|Cyan|Magenta|Yellow)|0xFF[0-9A-Fa-f]{6}|"#[0-9A-Fa-f]{6}"|Indigo|Teal|Slate|Purple' \
  --include='*.kt' app/src/main/java/com/uncoalesced/impart/presentation 2>/dev/null \
  | grep -v 'presentation/theme/Color.kt' || true)
if [ -n "$palette" ]; then
  echo "$palette"
  echo "::error::hardcoded color(s) found outside presentation/theme/Color.kt"
  fail=1
fi

echo "== Pure Compose check (no XML layouts / Fragments / findViewById) =="
compose=$(grep -rnE 'findViewById|R\.layout\.|setContentView\(R\.layout|import androidx\.fragment|: *Fragment\(' \
  --include='*.kt' app/src/main 2>/dev/null || true)
if [ -n "$compose" ]; then
  echo "$compose"
  echo "::error::legacy View-system usage found (Compose-only is required)"
  fail=1
fi

echo "== Secret scan =="
if git ls-files | grep -qE '(^|/)google-services\.json$'; then
  echo "::error::google-services.json is tracked in git (there is no Firebase; this file must not exist)"
  fail=1
fi
keys=$(git grep -nE -- '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' -- '*.kt' '*.ts' '*.json' '*.kts' 2>/dev/null || true)
if [ -n "$keys" ]; then
  echo "$keys"
  echo "::error::private key material committed"
  fail=1
fi

if [ "$fail" -eq 0 ]; then echo "standards-check: PASS"; else echo "standards-check: FAIL"; fi
exit $fail
