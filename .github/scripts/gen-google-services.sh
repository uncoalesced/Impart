#!/usr/bin/env bash
# Writes app/google-services.json for CI. Real file is gitignored, so the
# Google Services Gradle plugin has nothing to read on a fresh checkout and
# processDebugGoogleServices fails. Prefer the real config from a secret;
# fall back to a structurally-valid stub for build/test/lint that never talks
# to Firebase.
set -euo pipefail

DEST="app/google-services.json"

if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
  echo "$GOOGLE_SERVICES_JSON" | base64 -d > "$DEST"
  echo "google-services.json: written from GOOGLE_SERVICES_JSON secret"
else
  cat > "$DEST" <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "impart-ci-placeholder",
    "storage_bucket": "impart-ci-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.uncoalesced.impart"
        }
      },
      "oauth_client": [],
      "api_key": [
        { "current_key": "AIzaSyPLACEHOLDER-CI-KEY-000000000000000" }
      ],
      "services": {
        "appinvite_service": { "other_platform_oauth_client": [] }
      }
    }
  ],
  "configuration_version": "1"
}
JSON
  echo "google-services.json: generated CI stub (no Firebase connectivity)"
fi
