#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

if [[ -z "${HUNTIT_UPLOAD_STORE_FILE:-}" ]]; then
    HUNTIT_UPLOAD_STORE_FILE="${HOME}/.android/huntit-upload.jks"
fi

if [[ -z "${HUNTIT_UPLOAD_STORE_PASSWORD:-}" || -z "${HUNTIT_UPLOAD_KEY_PASSWORD:-}" ]]; then
    if ! command -v security >/dev/null 2>&1; then
        echo "Set HUNTIT_UPLOAD_STORE_PASSWORD and HUNTIT_UPLOAD_KEY_PASSWORD before building." >&2
        exit 1
    fi

    HUNTIT_UPLOAD_STORE_PASSWORD="$(
        security find-generic-password \
            -a "com.ghostdev.huntit" \
            -s "huntit-upload-keystore" \
            -w
    )"
    HUNTIT_UPLOAD_KEY_PASSWORD="${HUNTIT_UPLOAD_STORE_PASSWORD}"
fi

if [[ -z "${HUNTIT_UPLOAD_KEY_ALIAS:-}" ]]; then
    HUNTIT_UPLOAD_KEY_ALIAS="huntit-upload"
fi

if [[ ! -f "${HUNTIT_UPLOAD_STORE_FILE}" ]]; then
    echo "Upload keystore not found: ${HUNTIT_UPLOAD_STORE_FILE}" >&2
    exit 1
fi

export HUNTIT_UPLOAD_STORE_FILE
export HUNTIT_UPLOAD_STORE_PASSWORD
export HUNTIT_UPLOAD_KEY_ALIAS
export HUNTIT_UPLOAD_KEY_PASSWORD

cd "${PROJECT_DIR}"
./gradlew :composeApp:clean :composeApp:bundleRelease

BUNDLE_PATH="${PROJECT_DIR}/composeApp/build/outputs/bundle/release/composeApp-release.aab"
EXPECTED_UPLOAD_SHA256="87:EC:FA:F6:0A:45:08:FA:90:85:1E:81:90:70:2D:D6:66:A4:DA:A7:9E:91:B6:E3:4B:EF:55:29:8A:EC:FE:8C"
SIGNATURE_RESULT="$(jarsigner -verify -certs "${BUNDLE_PATH}" 2>&1)"
CERTIFICATE_RESULT="$(keytool -printcert -jarfile "${BUNDLE_PATH}")"

if [[ "${SIGNATURE_RESULT}" != *"jar verified."* ]]; then
    echo "${SIGNATURE_RESULT}" >&2
    exit 1
fi

if [[ "${CERTIFICATE_RESULT}" != *"${EXPECTED_UPLOAD_SHA256}"* ]]; then
    echo "Bundle signer does not match Hunt.it's registered upload certificate." >&2
    exit 1
fi

echo "Verified Play bundle: ${BUNDLE_PATH}"
