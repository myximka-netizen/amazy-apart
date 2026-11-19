# Волшебно тут CRM Native · v0.5

Independent **native Android** Chatwoot client (no WebView). Uses the Chatwoot Application API and Firebase Cloud Messaging.

## Features

- Chatwoot-inspired conversation list, contact avatar, channel, last message, status, unread counter, labels and assigned operator.
- Scope filters: Mine / Unassigned / All; status filters: Open / Pending / Resolved / All; full-text message search and pagination.
- Automatic conversation list refresh every 15 seconds while in foreground. Refresh also runs immediately when the app reopens.
- Automatic foreground message refresh every 15 seconds without forcibly scrolling away from older messages.
- Native reply, internal note, attachments (open links), mark read, status Open / Pending / Resolved.
- Actions menu: contact details, operator assignment, priority and canned replies (insert into composer, never auto-send).
- Push subscription through FCM; on-device push registration status available from settings. **Foreground polling is not a replacement for background FCM.**
- Chatwoot access token encrypted by Android Keystore; HTTPS-only API.
- Android package ID `ru.amazy.crm.nativeclient`.

## Build

- JDK 17, Gradle 8.10.2, Android SDK 35.
- Firebase public `app/google-services.json` is processed with the Google Services plugin.
- Build debug locally with `gradle :app:assembleDebug`.
- CI workflow: `.github/workflows/build-chatwoot-native-apk.yml`.
- CI installs and starts the debug APK in an Android 15 emulator.
- Release version 0.5.0 (`versionCode 4`).

## Permanent signing for in-place updates

**Never commit the signing keystore or password to GitHub. The repository is public.**

Use one long-lived, backed-up signing keystore (alias: `amazy_crm`). In GitHub Repository Settings → Secrets and variables → Actions, create these two repository secrets:

- `AMAZY_ANDROID_KEYSTORE_B64`: Base64 of the raw signing keystore file, **one line, no whitespace**.
- `AMAZY_ANDROID_KEYSTORE_PASSWORD`: password for the keystore and its key.

The workflow decodes the keystore into a temporary file, signs the release APK, verifies its signature, deletes the local file and uploads artifact **Volshebno-Tut-CRM-v0.5-Signed-APK**. A debug APK is also built for emulator verification.

Without both signing secrets, CI builds and tests the debug APK only and prints a warning. Debug signatures from ephemeral GitHub runners **are not guaranteed stable across runs**.

The already-installed v0.4 debug APK cannot be upgraded in place using a new release signer. Android requires an APK with the same signing certificate. **Save the Chatwoot access token locally; uninstall old debug v0.4 once before installing the first stable signed release.** Subsequent signed releases with the same keystore will install on top, keeping app data.

Keep offline backups of the signing keystore and password. Losing the key prevents normal in-place updates of apps signed by it.

## Required server configuration for push

FCM will not be delivered end-to-end until the Chatwoot server is configured with:
- `FIREBASE_PROJECT_ID=volshebno-tut-crm`
- `FIREBASE_CREDENTIALS`: private Firebase service-account JSON (server only)
- Chatwoot user notification preferences enabled, and outbound Google FCM network connectivity.

Client Firebase configuration alone cannot prove that server-side push works. The Android device needs Google Play Services and access to Google FCM.

## Mobile setup

1. Install the **signed release** artifact from GitHub Actions after configuring signing.
2. Enter Chatwoot HTTPS base URL, account ID 2, and the personal API Access Token on the phone.
3. Grant Android notification permission.
4. Go to ⚙ → Подключение и уведомления to inspect push registration status.
5. Send a message to the connected Telegram Business account from another Telegram account. The conversation list should update on its own while the app is open.
