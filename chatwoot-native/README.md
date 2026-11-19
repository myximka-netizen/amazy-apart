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
- Foreground chat refresh every ~25 seconds\n- Native Firebase Cloud Messaging notifications (when configured), opening conversations from notifications\n- Direct Chatwoot FCM device subscription, no additional relay server

## Setup
1. In Chatwoot web dashboard, open **Profile Settings > Access Token** and copy your personal API token. Do not share it in chats, logs, screenshots, or commits.
2. Install APK, open 'Волшебно тут CRM Native'.
3. Enter HTTPS base URL, e.g. https://desktop-to6ives.taile101ff.ts.net, and account ID (for the existing project, 2).
4. Paste token into the app; it validates /api/v1/profile and GET conversations before saving.
5. If the Funnel address is blocked without VPN in your mobile network, the native app has the same limitation until the server is moved to an accessible VPS.

## Enable real background push (FCM)

**The APK alone does not activate push.** Chatwoot already supports FCM natively; no custom relay is required.

1. Create a Firebase project at https://console.firebase.google.com/ and add an Android app with exact package name `ru.amazy.crm.nativeclient`.
2. Download **google-services.json** from Firebase project settings. It contains public client settings, not private service-account keys. Save it on the phone.
3. In CRM Native, open ⚙ > Настроить Push and choose that `google-services.json`. Allow Android notification permission.
4. In the Firebase project Service Accounts area, create a **private service account credential** with Firebase messaging access. NEVER send its JSON in a chat or commit it to GitHub. Configure Chatwoot at `/super_admin/app_config` with **Firebase Project ID** and **Firebase Credentials** (server-side only).
5. In Chatwoot profile notification settings, enable relevant push types (new assigned/participating messages). Device registration uses existing `POST /api/v1/notification_subscriptions`.
6. Sign in to the CRM app, tap ⚙ to confirm Firebase is initialized, and have another person send a test message to a conversation assigned to your user.

The Chatwoot server needs outbound HTTPS access to Google OAuth and FCM endpoints. The phone requires Google Play Services and network access to Firebase; some mobile networks or VPN rules may block these connections. Firebase 26 uses the existing (deprecated) FCM registration token API for Chatwoot server compatibility.

## Limitations
- FCM requires a separately provisioned Firebase project and server credentials; not active out of the box.
- Sending images/files, contact editing, assignment, template sending, and full message history pagination are not implemented.
- WhatsApp channels may restrict free-text replies outside their approved customer service window; Chatwoot server governs delivery.
- Debug APK is signed with an ephemeral debug key; release signing must use a persistent private signing key (never commit it).
- This MVP needs real-device testing before production use.

## Build
Gradle 8.10.2, JDK 17, Android SDK 35. Run `gradle :app:assembleDebug` from this directory.
CI workflow: `.github/workflows/build-chatwoot-native-apk.yml`.
APK path: `app/build/outputs/apk/debug/app-debug.apk`.

No Chatwoot API token is embedded in this source. This project is independent and is not officially supported by Chatwoot.
