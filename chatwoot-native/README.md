# Волшебно тут CRM Native

Independent, **fully native Android** client for an existing self-hosted Chatwoot server.
There is **no WebView**. No changes to the Windows Docker setup or database.

## Current features (MVP)
- HTTPS Chatwoot URL and account ID configuration
- Authentication using a personal Chatwoot API access token
- Encrypted token in Android Keystore (AES-GCM)
- Open / pending / resolved / all conversation filters and API-backed search
- Conversation list with contact, last message, channel inbox ID, unread count
- Native message bubbles, text replies, internal notes
- Conversation resolve / reopen, read receipt, attachment link opening
- Foreground chat refresh every ~25 seconds

## Setup
1. In Chatwoot web dashboard, open **Profile Settings > Access Token** and copy your personal API token. Do not share it in chats, logs, screenshots, or commits.
2. Install APK, open 'Волшебно тут CRM Native'.
3. Enter HTTPS base URL, e.g. https://desktop-to6ives.taile101ff.ts.net, and account ID (for the existing project, 2).
4. Paste token into the app; it validates /api/v1/profile and GET conversations before saving.
5. If the Funnel address is blocked without VPN in your mobile network, the native app has the same limitation until the server is moved to an accessible VPS.

## Limitations
- No background FCM notifications; refresh while the chat screen is open.
- Sending images/files, contact editing, assignment, push notifications, template sending, and full message history pagination are not implemented.
- WhatsApp channels may restrict free-text replies outside their approved customer service window; Chatwoot server governs delivery.
- Debug APK is signed with an ephemeral debug key; release signing must use a persistent private signing key (never commit it).
- This MVP needs real-device testing before production use.

## Build
Gradle 8.10.2, JDK 17, Android SDK 35. Run `gradle :app:assembleDebug` from this directory.
CI workflow: `.github/workflows/build-chatwoot-native-apk.yml`.
APK path: `app/build/outputs/apk/debug/app-debug.apk`.

No Chatwoot API token is embedded in this source. This project is independent and is not officially supported by Chatwoot.
