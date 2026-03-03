#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${VECTRAS_GOOGLE_SERVICES_JSON_B64:-}" ]]; then
  echo "${VECTRAS_GOOGLE_SERVICES_JSON_B64}" | base64 --decode > app/google-services.json
  echo "Firebase config loaded from VECTRAS_GOOGLE_SERVICES_JSON_B64"
elif [[ ! -f app/google-services.json ]]; then
  cat > app/google-services.json <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "vectras-vm-placeholder",
    "storage_bucket": "vectras-vm-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.vectras.vm"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "AIzaSyDummyKeyForBuildPurposesOnly000000"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
JSON
  echo "Using local placeholder Firebase config for non-release verification"
fi
