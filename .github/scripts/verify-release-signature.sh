#!/usr/bin/env bash
# Checks a release artifact (APK or AAB) is signed and that its certificate is not the
# public debug key committed at debug.keystore (T11, A8-4). An APK must carry the v2 and
# v3 schemes. Fails on any doubt.
set -uo pipefail

artifact="${1:?artifact path}"
if [ ! -f "$artifact" ]; then
  echo "::error::artifact not found: $artifact"
  exit 1
fi

debug_sha=$(keytool -list -v -keystore debug.keystore -storepass android 2>/dev/null | grep -m1 'SHA256:' | sed 's/.*SHA256: *//' | tr -d ':' | tr 'A-F' 'a-f')
if [ ${#debug_sha} -ne 64 ]; then
  echo "::error::could not read the debug certificate fingerprint from debug.keystore"
  exit 1
fi
echo "debug certificate sha256: $debug_sha"

case "$artifact" in
  *.apk)
    apksigner=$(ls "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)
    if [ -z "$apksigner" ]; then
      echo "::error::apksigner not found under the Android SDK build-tools"
      exit 1
    fi
    report=$("$apksigner" verify --verbose --print-certs "$artifact" 2>&1) || { echo "$report"; echo "::error::apksigner verification failed: the APK is unsigned or its signature is invalid"; exit 1; }
    echo "$report" | grep -E 'Verified using v[0-9]|Signer #1 certificate SHA-256'
    echo "$report" | grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' || { echo "::error::APK is not signed with the v2 scheme"; exit 1; }
    echo "$report" | grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' || { echo "::error::APK is not signed with the v3 scheme"; exit 1; }
    cert_sha=$(echo "$report" | grep -m1 'Signer #1 certificate SHA-256 digest:' | sed 's/.*digest: *//' | tr -d ' ' | tr 'A-F' 'a-f')
    ;;
  *.aab)
    report=$(keytool -printcert -jarfile "$artifact" 2>&1) || { echo "$report"; echo "::error::the AAB is not signed"; exit 1; }
    cert_sha=$(echo "$report" | grep -m1 'SHA256:' | sed 's/.*SHA256: *//' | tr -d ':' | tr 'A-F' 'a-f')
    ;;
  *)
    echo "::error::unknown artifact type: $artifact"
    exit 1
    ;;
esac

if [ ${#cert_sha} -ne 64 ]; then
  echo "::error::could not read the signing certificate of $artifact"
  exit 1
fi
echo "artifact certificate sha256: $cert_sha"
if [ "$cert_sha" = "$debug_sha" ]; then
  echo "::error::$artifact is signed with the public debug key. This must never ship."
  exit 1
fi
echo "signature check: PASS"
