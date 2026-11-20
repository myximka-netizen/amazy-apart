#!/usr/bin/env bash
# Sign only the completed release APK. Build tooling does not receive the signing secrets.
set -euo pipefail
umask 077
here=$(cd "$(dirname "$0")" && pwd)
: "${AMAZY_ANDROID_KEYSTORE_B64:?Missing release keystore}" "${AMAZY_ANDROID_KEYSTORE_PASSWORD:?Missing signing password}"
tools="$ANDROID_HOME/build-tools/36.0.0"
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
expected=7ed3eeb141300d194d37b95e614cacb749f4fb47d879e14e6a329ca452ecd739
export SIGN_PASS
SIGN_PASS=$(printf '%s' "$AMAZY_ANDROID_KEYSTORE_PASSWORD" | tr -d '[:space:]')
printf '%s' "$AMAZY_ANDROID_KEYSTORE_B64" | tr -d '[:space:]' | base64 --decode > "$temp/release.jks"
unset AMAZY_ANDROID_KEYSTORE_B64 AMAZY_ANDROID_KEYSTORE_PASSWORD
version=$(jq -r .version "$here/deployment.json")
version_code=$(jq -r .versionCode "$here/deployment.json")
package=$(jq -r .package "$here/deployment.json")
release_dir="$here/app/android/app/build/outputs/apk/release"
mkdir -p "$here/dist"
apk="$here/dist/Volshebno-Tut-CRM-${version}.apk"
"$tools/zipalign" -P 16 -f 4 "$release_dir/app-release-unsigned.apk" "$temp/aligned.apk"
"$tools/apksigner" sign --ks "$temp/release.jks" --ks-key-alias amazy_crm --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --v4-signing-enabled false --out "$apk" "$temp/aligned.apk"
"$tools/zipalign" -c -P 16 4 "$apk"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$temp/cert"
grep -Fx "Signer #1 certificate SHA-256 digest: $expected" "$temp/cert"
[[ $(grep -c 'certificate SHA-256 digest:' "$temp/cert") == 1 ]]
"$tools/aapt" dump badging "$apk" > "$temp/badging"
grep -F "package: name='$package' versionCode='$version_code' versionName='$version'" "$temp/badging"
grep -Fq "application-label:'Волшебно тут CRM'" "$temp/badging"
unzip -p "$apk" assets/Chatwoot-LICENSE.txt | cmp - "$here/upstream/LICENSE"
(cd "$here/dist" && sha256sum "$(basename "$apk")" > "$(basename "$apk").sha256")
