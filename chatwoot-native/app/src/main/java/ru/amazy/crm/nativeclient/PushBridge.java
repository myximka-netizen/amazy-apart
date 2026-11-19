package ru.amazy.crm.nativeclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side registration for Chatwoot's OWN /api/v1/notification_subscriptions API.
 * NO new relay, NO shared server credentials, NO secret account keys in the APK.
 *
 * google-services.json contains only Firebase PUBLIC client configuration.
 * Private Firebase service-account credentials belong in Chatwoot super_admin
 * configuration, never in Android or GitHub.
 */
final class PushBridge {
    private static final String TAG = "AmazyPush";
    private static final String PREF = "amazy_native_push_config";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private PushBridge() { }

    static boolean isConfigured(Context context) {
        SharedPreferences p = prefs(context);
        return !p.getString("firebase_app_id", "").isEmpty()
            && !p.getString("firebase_sender_id", "").isEmpty()
            && !p.getString("firebase_api_key", "").isEmpty()
            && !p.getString("firebase_project_id", "").isEmpty();
    }

    static boolean initializeFirebase(Context context) {
        if (!isConfigured(context)) return false;
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) return true;
            SharedPreferences p = prefs(context);
            FirebaseOptions opts = new FirebaseOptions.Builder()
                .setApplicationId(p.getString("firebase_app_id", ""))
                .setGcmSenderId(p.getString("firebase_sender_id", ""))
                .setApiKey(p.getString("firebase_api_key", ""))
                .setProjectId(p.getString("firebase_project_id", ""))
                .build();
            FirebaseApp.initializeApp(context.getApplicationContext(), opts);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Firebase initialization failed", ex);
            return false;
        }
    }

    static void importGoogleServices(Context context, String contents) throws Exception {
        JSONObject json = new JSONObject(contents);
        JSONObject project = json.getJSONObject("project_info");
        String projectId = project.getString("project_id");
        String senderId = project.getString("project_number");
        JSONArray clients = json.getJSONArray("client");
        String appId = null;
        String apiKey = null;
        for (int i = 0; i < clients.length(); i++) {
            JSONObject candidate = clients.getJSONObject(i);
            JSONObject ci = candidate.getJSONObject("client_info");
            String pkg = ci.getJSONObject("android_client_info").getString("package_name");
            if (context.getPackageName().equals(pkg)) {
                appId = ci.getString("mobilesdk_app_id");
                apiKey = candidate.getJSONArray("api_key").getJSONObject(0).getString("current_key");
                break;
            }
        }
        if (appId == null || apiKey == null)
            throw new IllegalArgumentException(
                "В Firebase нужно зарегистрировать Android-приложение: " + context.getPackageName());
        if (projectId.isBlank() || senderId.isBlank() || appId.isBlank() || apiKey.isBlank())
            throw new IllegalArgumentException("В google-services.json не хватает параметров Firebase");

        // Reject a silent attempt to reconfigure an initialized Firebase project.
        // A new config can be used after a full process restart.
        SharedPreferences p = prefs(context);
        String oldApp = p.getString("firebase_app_id", "");
        if (!oldApp.isEmpty() && !oldApp.equals(appId))
            throw new IllegalArgumentException("Другая конфигурация Firebase уже установлена. Для смены проекта требуется переустановка приложения.");

        if (!p.edit().putString("firebase_app_id", appId)
            .putString("firebase_sender_id", senderId)
            .putString("firebase_api_key", apiKey)
            .putString("firebase_project_id", projectId).commit())
            throw new IOException("Не удалось сохранить настройки Firebase");

        if (!initializeFirebase(context))
            throw new IllegalStateException("Не удалось инициализировать Firebase");
    }

    static String status(Context context) {
        if (!isConfigured(context))
            return "Push пока не настроен. Импортируй google-services.json из Firebase.";
        if (!initializeFirebase(context))
            return "Ошибка инициализации Firebase. Проверь конфигурацию.";
        String token = prefs(context).getString("last_push_token", "");
        if (token.isBlank())
            return "Firebase настроен. Получаем идентификатор телефона...";
        return "Firebase подключён. Токен телефона получен; проверяем регистрацию в Chatwoot.";
    }

    static void sync(Context context) {
        Context app = context.getApplicationContext();
        if (!initializeFirebase(app)) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Couldn't get FCM token", task.getException());
                    return;
                }
                String token = task.getResult();
                if (token == null || token.isBlank()) return;
                syncToken(app, token);
            });
        } catch (Exception ex) {
            Log.e(TAG, "FCM token lookup failed", ex);
        }
    }

    static void syncToken(Context context, String token) {
        Context app = context.getApplicationContext();
        // The callback can run before the user signs into Chatwoot.
        prefs(app).edit().putString("last_push_token", token).apply();
        IO.execute(() -> {
            try {
                SecureTokenStore store = new SecureTokenStore(app);
                String accessToken = store.loadToken();
                if (accessToken == null || accessToken.isEmpty()) return;
                ApiClient api = new ApiClient(store.server(), accessToken, store.account());
                api.registerPushToken(token, deviceId(app));
                Log.i(TAG, "Device successfully subscribed to Chatwoot notifications");
            } catch (Exception ex) {
                Log.w(TAG, "Chatwoot push subscription failed", ex);
            }
        });
    }

    static void onLogin(Context context) {
        Context app = context.getApplicationContext();
        if (!isConfigured(app)) return;
        String cached = prefs(app).getString("last_push_token", "");
        if (!cached.isEmpty()) syncToken(app, cached);
        sync(app);
    }

    static void unregisterBeforeLogout(Context context) {
        Context app = context.getApplicationContext();
        SecureTokenStore store = new SecureTokenStore(app);
        String access = store.loadToken();
        String fcmToken = prefs(app).getString("last_push_token", "");
        if (access == null || fcmToken.isEmpty()) return;
        String base = store.server();
        int account = store.account();
        IO.execute(() -> {
            try {
                new ApiClient(base, access, account).unregisterPushToken(fcmToken);
                Log.i(TAG, "Push subscription removed on logout");
            } catch (Exception ex) {
                Log.w(TAG, "Couldn't unregister push subscription", ex);
            }
        });
    }

    private static String deviceId(Context context) {
        SharedPreferences p = prefs(context);
        String id = p.getString("device_id", null);
        if (id != null) return id;
        id = UUID.randomUUID().toString();
        p.edit().putString("device_id", id).commit();
        return id;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static int conversationFromPayload(String payload, int currentAccount) {
        if (payload == null || payload.isBlank()) return -1;
        try {
            JSONObject json = new JSONObject(payload);
            JSONObject data = json.optJSONObject("data");
            JSONObject notif = data == null ? json.optJSONObject("notification") : data.optJSONObject("notification");
            if (notif == null) return -1;
            if (notif.optInt("account_id", -1) != currentAccount) return -1;
            JSONObject actor = notif.optJSONObject("primary_actor");
            if (actor == null) return -1;
            int conversation = actor.optInt("conversation_id", -1);
            return conversation > 0 ? conversation : actor.optInt("id", -1);
        } catch (Exception ignored) {
            return -1;
        }
    }
}
