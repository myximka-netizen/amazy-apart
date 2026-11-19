package ru.amazy.crm.nativeclient;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives data/foreground FCM notifications. When app is backgrounded,
 * FCM handles the notification payload automatically via the configured channel.
 */
public final class CrmMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        PushBridge.syncToken(getApplicationContext(), token);
    }

    @Override public void onMessageReceived(RemoteMessage remote) {
        super.onMessageReceived(remote);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // For foreground delivery the SDK does not automatically display a notification.
        RemoteMessage.Notification incoming = remote.getNotification();
        String title = incoming != null ? incoming.getTitle() : null;
        String body = incoming != null ? incoming.getBody() : null;
        if (title == null || title.isBlank()) title = "Волшебно тут CRM";
        if (body == null || body.isBlank()) body = "Новое событие Chatwoot";

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Map<String, String> data = remote.getData();
        String payload = data.get("payload");
        if (payload != null) open.putExtra("payload", payload);

        int id = (int) (System.currentTimeMillis() % 2_000_000_000L);
        PendingIntent action = PendingIntent.getActivity(this, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new Notification.Builder(this, CrmApplication.PUSH_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(action)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(id, notif);
    }
}
