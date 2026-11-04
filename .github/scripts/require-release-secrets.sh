#!/usr/bin/env bash
# Fails the deployment workflow unless every release signing secret is set (T11). The
# alternative, falling back to an unsigned or debug-signed output, is exactly the artifact
# that must never exist.
set -uo pipefail
missing=0
for name in KS KSP KA KP; do
  if [ -z "${!name:-}" ]; then
    case $name in
      KS) echo "::error::RELEASE_KEYSTORE_BASE64 is not set" ;;
      KSP) echo "::error::RELEASE_KEYSTORE_PASSWORD is not set" ;;
      KA) echo "::error::RELEASE_KEY_ALIAS is not set" ;;
      KP) echo "::error::RELEASE_KEY_PASSWORD is not set" ;;
    esac
    missing=1
  fi
done
if [ $missing -ne 0 ]; then
  echo "::error::cannot produce a signed release. Configure the four RELEASE_* secrets on the repository first."
  exit 1
fi
echo "release secrets present"
