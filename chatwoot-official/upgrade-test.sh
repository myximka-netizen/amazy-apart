#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
package=$(jq -r .package "$here/deployment.json")
version=$(jq -r .version "$here/deployment.json")
old=${1:?Pass the signed 0.7.0 APK}
new=${2:?Pass the new signed APK}
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
echo "43c13b508659d987c662128baf86592ec724430631c4944a20f60d128c242ae1  $old" | sha256sum -c -
tools="$ANDROID_HOME/build-tools/36.0.0"
cert() { "$tools/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'; }
old_cert=$(cert "$old")
[[ -n "$old_cert" && "$old_cert" == "$(cert "$new")" ]]
install_id() { adb shell dumpsys package "$package" | tr -d '\r' | awk '/firstInstallTime=/{print; exit}'; }
adb install "$old"
adb shell am start -W -n "$package/.MainActivity"
stamp=$(install_id)
[[ -n "$stamp" ]]
adb install -r "$new"
[[ "$stamp" == "$(install_id)" ]]
adb shell dumpsys package "$package" > "$temp/package"
grep -F "versionName=$version" "$temp/package"
login_screen() {
  adb shell am start -W -n "$package/.MainActivity"
  for _ in {1..35}; do
    adb shell uiautomator dump /sdcard/crm-login.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/crm-login.xml > "$temp/ui.xml" 2>/dev/null || true
    if grep -Fq 'Войти в свой аккаунт' "$temp/ui.xml" && grep -Fq 'desktop-to6ives.taile101ff.ts.net' "$temp/ui.xml"; then
      adb shell pidof "$package" >/dev/null && return 0
    fi
    sleep 2
  done
  echo 'Release did not reach the Russian sign-in screen with the configured server.' >&2
  return 1
}
login_screen
adb shell am force-stop "$package"
login_screen
