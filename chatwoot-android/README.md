# Волшебно тут CRM: Android client for self-hosted Chatwoot

Independent, minimal Android WebView shell for your own self-hosted Chatwoot installation.
This project is not affiliated with or supported by Chatwoot.

## Included
- Configure or change the Chatwoot server URL at any time (gear icon).
- Existing server login and conversations via HTTPS.
- Android file picker and HTTPS downloads.
- Authentication stays on your Chatwoot server. No credentials or API keys are hardcoded.

## Limitations
- This is a WebView client, not a React Native reimplementation.
- Background Android push notifications are not implemented.
- Chatwoot must run on the PC and remain available over HTTPS.
- Cloudflare Quick Tunnel URLs may change; configure a persistent tunnel hostname later.
- Voice calls, camera capture, and some browser APIs may need separate integration.

## Build
JDK 17, Gradle 8.10.2, Android SDK API 35.

Run `gradle :app:assembleDebug` in this directory.
APK is created as `app/build/outputs/apk/debug/app-debug.apk`.

Repository workflow `.github/workflows/build-chatwoot-apk.yml` builds the APK and provides an artifact under GitHub Actions.
For updates distributed to users, use a persistent private release signing key. The first build uses standard debug signing.

This source does not alter the Docker project, database, or website.
