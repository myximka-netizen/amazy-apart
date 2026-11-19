package ru.amazy.crm.nativeclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chatwoot FCM subscription for the signed-in user.
 * Firebase public Android configuration comes from google-services.json at build time.
 * Private Google service-account credentials must NEVER be bundled into the APK.
 */
final class PushBridge {
    private static final String TAG = "AmazyPush";
    private static final String PREF = "amazy_native_push_config";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private PushBridge() { }

    /** FirebaseInitProvider normally runs before Application.onCreate(). */
    static boolean initializeFirebase(Context context) {
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) return true;
            return FirebaseApp.initializeApp(context.getApplicationContext()) != null;
        } catch (Exception ex) {
            Log.e(TAG, "Firebase initialization failed", ex);
            return false;
        }
    }

    static boolean isConfigured(Context context) {
        return initializeFirebase(context);
    }

    static String status(Context context) {
        if (!initializeFirebase(context))
            return "Firebase не инициализирован. Проверь сборку APK.";
        SharedPreferences p = prefs(context);
        String token = p.getString("last_push_token", "");
        if (token.isEmpty())
            return "Firebase встроен в приложение. Получаем FCM-токен устройства.";
        String error = p.getString("last_push_error", "");
        if (!error.isEmpty())
            return "FCM-токен получен. Регистрация в Chatwoot не удалась: " + error;
        SecureTokenStore store = new SecureTokenStore(context);
        boolean registered = token.equals(p.getString("last_registered_token", ""))
            && store.server().equals(p.getString("last_registered_server", ""))
            && store.account() == p.getInt("last_registered_account", -1);
        return registered
            ? "Push-подписка телефона зарегистрирована в Chatwoot."
            : "Firebase подключён. Подписка Chatwoot ещё не подтверждена.";
    }

    static void sync(Context context) {
        Context app = context.getApplicationContext();
        if (!initializeFirebase(app)) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    prefs(app).edit().putString("last_push_error", "нет FCM-токена. Проверь Google Play и сеть.").apply();
                    Log.w(TAG, "Couldn't get FCM token", task.getException());
                    return;
                }
                String token = task.getResult();
                if (token == null || token.isBlank()) return;
                syncToken(app, token);
            });
        } catch (Exception ex) {
            prefs(app).edit().putString("last_push_error", "сбой Firebase Messaging").apply();
            Log.e(TAG, "FCM token lookup failed", ex);
        }
    }

    static void syncToken(Context context, String token) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        String oldToken = p.getString("last_push_token", "");
        SharedPreferences.Editor e = p.edit().putString("last_push_token", token);
        if (!token.equals(oldToken)) e.remove("last_registered_token");
        e.apply();
        IO.execute(() -> {
            try {
                SecureTokenStore store = new SecureTokenStore(app);
                String accessToken = store.loadToken();
                if (accessToken == null || accessToken.isEmpty()) return;
                ApiClient api = new ApiClient(store.server(), accessToken, store.account());
                api.registerPushToken(token, deviceId(app));
                p.edit().putString("last_registered_token", token)
                    .putString("last_registered_server", store.server())
                    .putInt("last_registered_account", store.account())
                    .remove("last_push_error").apply();
                Log.i(TAG, "Device successfully subscribed to Chatwoot notifications");
            } catch (Exception ex) {
                p.edit().putString("last_push_error", ex.getMessage() == null ? "ошибка соединения" : ex.getMessage()).apply();
                Log.w(TAG, "Chatwoot push subscription failed", ex);
            }
        });
    }

    static void onLogin(Context context) {
        sync(context.getApplicationContext());
    }

    static void unregisterBeforeLogout(Context context) {
        Context app = context.getApplicationContext();
        SecureTokenStore store = new SecureTokenStore(app);
        String access = store.loadToken();
        String fcmToken = prefs(app).getString("last_push_token", "");
        prefs(app).edit()
            .remove("last_registered_token")
            .remove("last_registered_server")
            .remove("last_registered_account")
            .remove("last_push_error").apply();
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
