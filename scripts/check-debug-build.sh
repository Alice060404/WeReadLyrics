#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
cd "$project_root"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then
    echo "ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK." >&2
    exit 1
fi

apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
if [[ ! -x "$apkanalyzer" ]]; then
    echo "apkanalyzer not found at: $apkanalyzer" >&2
    exit 1
fi

./gradlew :core:test :app:assembleDebug :app:lintDebug --offline --no-daemon

apk="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$apk" ]]; then
    echo "Debug APK was not created: $apk" >&2
    exit 1
fi

application_id="$($apkanalyzer manifest application-id "$apk")"
version_name="$($apkanalyzer manifest version-name "$apk")"
version_code="$($apkanalyzer manifest version-code "$apk")"

[[ "$application_id" == "io.github.alice060404.wereadlyrics" ]]
[[ "$(unzip -p "$apk" META-INF/xposed/java_init.list)" == \
    "io.github.alice060404.wereadlyrics.WeReadLyricsModule" ]]
[[ "$(unzip -p "$apk" META-INF/xposed/scope.list)" == "com.tencent.weread" ]]

echo "APK: $apk"
echo "Application ID: $application_id"
echo "Version: $version_name ($version_code)"
sha256sum "$apk"
