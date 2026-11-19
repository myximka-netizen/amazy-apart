package ru.amazy.crm.nativeclient;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class CrmApplication extends Application {
    static final String PUSH_CHANNEL = "amazy_chatwoot_messages_v1";

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel channel = new NotificationChannel(
                PUSH_CHANNEL, "Новые сообщения Chatwoot", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Сообщения гостей и уведомления о диалогах");
            nm.createNotificationChannel(channel);
        }
        // FCM may start this process while the activity isn't running.
        // Restore its PUBLIC Firebase config before MessagingService is created.
        PushBridge.initializeFirebase(this);
    }
}
