#!/usr/bin/env bash
set -euo pipefail

apk="${1:-app/build/outputs/apk/release/app-release.apk}"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${sdk_root:?Set ANDROID_HOME or ANDROID_SDK_ROOT}"
: "${KEYSTORE_FILE:?Set KEYSTORE_FILE}"
: "${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD}"
: "${KEY_ALIAS:?Set KEY_ALIAS}"

test -s "$apk"
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
test -x "$apksigner"
certificate_info=$("$apksigner" verify --verbose --print-certs "$apk")
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$certificate_info")
expected=$(keytool -exportcert -keystore "$KEYSTORE_FILE" \
    -storepass:env KEYSTORE_PASSWORD -alias "$KEY_ALIAS" | sha256sum | cut -d ' ' -f 1)
if [[ -z "$actual" || "$actual" != "$expected" ]]; then
    echo "Release APK signing certificate does not match the configured key." >&2
    exit 1
fi

[[ "$(unzip -p "$apk" META-INF/xposed/java_init.list)" == \
    "io.github.alice060404.wereadlyrics.WeReadLyricsModule" ]]
[[ "$(unzip -p "$apk" META-INF/xposed/scope.list)" == "com.tencent.weread" ]]
echo "Release signature and Xposed metadata verified. Certificate SHA-256: $actual"
sha256sum "$apk"
