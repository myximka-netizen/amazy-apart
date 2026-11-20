#!/usr/bin/env bash
# Recreate tracked upstream inputs while allowing pnpm and Gradle to reuse their caches.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(git -C "$here" rev-parse --show-toplevel)
source_dir="$here/upstream"
app="$here/app"
rev=$(git -C "$repo" rev-parse :chatwoot-official/upstream)
[[ $(git -C "$source_dir" rev-parse HEAD) == "$rev" ]] || { echo 'Run git submodule update --init first.' >&2; exit 1; }
mkdir -p "$app"
git -C "$source_dir" archive "$rev" | tar -x -C "$app"
git -C "$repo" apply --directory=chatwoot-official/app "$here/mobile.patch"
mkdir -p "$app/modules/amazy-legacy-push"
cp -R "$here/legacy-push/." "$app/modules/amazy-legacy-push/"
cp "$here/deployment.json" "$app/amazy.json"
cp "$repo/public/icon-512x512.png" "$app/assets/icon.png"
cp "$repo/public/icon-maskable-512x512.png" "$app/assets/adaptive-icon.png"
cp "$repo/public/logo.png" "$app/assets/splash.png"
cp "$repo/public/icon-512x512.png" "$app/src/assets/images/logo.png"
node - "$here" "$repo" <<'JS'
const fs = require('node:fs');
const path = require('node:path');
const [directory, root] = process.argv.slice(2);
const config = require(path.join(directory, 'deployment.json'));
const client = require(path.join(root, 'chatwoot-native/app/google-services.json'));
if (client.project_info?.project_id !== config.firebaseProject ||
    !client.client?.some(c => c.client_info?.android_client_info?.package_name === config.package) ||
    client.private_key || client.type === 'service_account') {
  throw new Error('Incorrect Android Firebase client configuration');
}
fs.writeFileSync(path.join(directory, 'app/google-services.json'), JSON.stringify(client));
JS
