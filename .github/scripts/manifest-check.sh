#!/usr/bin/env bash
# Checks the MERGED manifest of every variant, which is what ships: library manifests add
# permissions and components the source manifest never mentions. Fails on any network
# permission, a debuggable release, backups, cleartext traffic, or an exported component
# that is not protected by a permission (the launcher activity and its cloak alias excepted).
# Run after processDebugManifest and processReleaseManifest, or pass manifest paths.
set -uo pipefail

fail=0
ALLOWED_EXPORTED="com.uncoalesced.impart.MainActivity com.uncoalesced.impart.MainActivityAlias"

manifests=("$@")
if [ ${#manifests[@]} -eq 0 ]; then
  manifests=(
    app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
    app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
  )
fi

for manifest in "${manifests[@]}"; do
  echo "== $manifest =="
  if [ ! -f "$manifest" ]; then
    echo "::error::merged manifest not found: $manifest (run processDebugManifest / processReleaseManifest first)"
    fail=1
    continue
  fi
  variant=debug
  case "$manifest" in *release*) variant=release ;; esac

  # One line per element so attributes can be inspected together.
  flat=$(tr '\n' ' ' < "$manifest" | sed 's/>/>\n/g')

  for perm in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE android.permission.ACCESS_WIFI_STATE; do
    if echo "$flat" | grep -E '<uses-permission' | grep -q "android:name=\"$perm\""; then
      echo "::error file=app/src/main/AndroidManifest.xml::$variant merged manifest declares $perm"
      fail=1
    fi
  done

  app=$(echo "$flat" | grep -E '<application' | head -n 1)
  if [ "$variant" = release ] && echo "$app" | grep -q 'android:debuggable="true"'; then
    echo "::error::release merged manifest is debuggable"
    fail=1
  fi
  if echo "$app" | grep -q 'android:allowBackup="true"'; then
    echo "::error::merged manifest allows backup"
    fail=1
  fi
  if ! echo "$app" | grep -q 'android:allowBackup="false"'; then
    echo "::error::merged manifest does not set allowBackup=\"false\""
    fail=1
  fi
  if echo "$app" | grep -q 'android:usesCleartextTraffic="true"'; then
    echo "::error::merged manifest allows cleartext traffic"
    fail=1
  fi

  while IFS= read -r element; do
    name=$(echo "$element" | sed -n 's/.*android:name="\([^"]*\)".*/\1/p')
    if echo "$element" | grep -q 'android:exported="true"'; then
      if echo "$element" | grep -q 'android:permission="'; then
        continue
      fi
      allowed=0
      for ok in $ALLOWED_EXPORTED; do
        [ "$name" = "$ok" ] && allowed=1
      done
      if [ $allowed -eq 0 ]; then
        echo "::error::$variant: exported component without a permission: $name"
        fail=1
      fi
    fi
  done < <(echo "$flat" | grep -E '<(activity|activity-alias|service|receiver|provider)[ >]')

  echo "permissions:"
  echo "$flat" | grep -E '<uses-permission' | sed -n 's/.*android:name="\([^"]*\)".*/  \1/p'
  echo "exported components:"
  echo "$flat" | grep -E '<(activity|activity-alias|service|receiver|provider)[ >]' | grep 'android:exported="true"' | sed -n 's/.*android:name="\([^"]*\)".*/  \1/p'
done

if [ "$fail" -eq 0 ]; then echo "manifest-check: PASS"; else echo "manifest-check: FAIL"; fi
exit $fail
