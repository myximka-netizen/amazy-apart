# Волшебно тут CRM Native

Independent **native Android** Chatwoot client (no WebView). Built-in FCM push is based on the Chatwoot subscription API.

## Build configuration
- Firebase project: `volshebno-tut-crm`.
- Android package ID: `ru.amazy.crm.nativeclient`.
- App uses the official Google Services Gradle plugin, processing `app/google-services.json` at build time.
- `google-services.json` has **public client identifiers only**. Do not commit private service-account JSON, Chatwoot access tokens or passwords.
- Build with Java 17 and Gradle 8.10.2: `gradle :app:assembleDebug`.
- CI: `.github/workflows/build-chatwoot-native-apk.yml`.

## Mobile setup
1. Install the version 0.4 APK. Old debug builds may use a different signing key, so you may need to uninstall v0.3 first.
2. Enter your Chatwoot HTTPS base URL and account ID (existing account: 2).
3. Enter your personal Chatwoot **API access token directly on the phone**. Do not share the token in a chat.
4. Allow Android notification permission. Firebase is already configured in the APK; **do not import files**.
5. In ⚙ > Подключение и уведомления, check registration status. "Проверить Push" retries token registration if needed.

## Required server configuration
FCM notifications will **not** arrive from Chatwoot until the Chatwoot server is configured with:
- `FIREBASE_PROJECT_ID` = `volshebno-tut-crm`
- `FIREBASE_CREDENTIALS` = private Firebase service-account JSON (server only). On a supported Chatwoot version, configure via super_admin app config.
- Account notification preferences enabling push for assigned / participating messages.
- Outbound access from Rails/Sidekiq to Google OAuth and FCM endpoints.

Chatwoot itself handles native FCM push via `POST /api/v1/notification_subscriptions`; no separate push forwarding service is required.
The phone needs Google Play Services and a network path to Google FCM. Network/VPN restrictions may affect push delivery.

## Current MVP
- Conversation list + filters/search + unread count
- Native messages and replies, internal notes
- Conversation status changes and read marking
- FCM device registration and message notifications (once server is configured)
- Encrypted personal token via Android Keystore
- No image attachment sends, full paging, or background refresh via WebView

## Testing notes
CI builds, installs, and launches the APK in an Android emulator.
**End-to-end FCM message delivery from the user's Chatwoot server has not been verified** until server configuration and a real-device test.
