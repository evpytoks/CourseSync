package ru.katevpy.coursesync.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.RegisterDeviceRequest;
import ru.katevpy.coursesync.shared.network.DeviceApi;

public class CourseSyncFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "coursesync_push";

    @Override
    public void onNewToken(String token) {
        sendTokenToBackend(token);
    }

    public static void registerDeviceAfterLogin(Context context) {
        if (context == null) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token != null && !token.isEmpty()) {
                        sendTokenToBackend(token);
                    }
                })
                .addOnFailureListener(e -> {
                });
    }

    static void sendTokenToBackend(String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) return;
        try {
            App.getDeps();
        } catch (IllegalStateException e) {
            return;
        }
        String access = App.getDeps().tokenStorage.getAccess();
        if (access == null || access.isEmpty()) return;
        new Thread(() -> {
            try {
                DeviceApi api = App.getDeps().deviceApi;
                api.register(new RegisterDeviceRequest("android", fcmToken)).execute();
            } catch (Exception e) {
            }
        }).start();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = null;
        String body = null;
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }
        if ((title == null || body == null) && remoteMessage.getData() != null) {
            Map<String, String> data = remoteMessage.getData();
            if (title == null) title = data.get("title");
            if (body == null) body = data.get("body");
        }
        if (title == null) title = getString(R.string.app_name);
        if (body == null) body = "";
        Map<String, String> data = remoteMessage.getData();
        showNotification(title, body, data != null && !data.isEmpty() ? data : null);
    }

    private void showNotification(String title, String body, @Nullable Map<String, String> data) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null) {
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    intent.putExtra(e.getKey(), e.getValue());
                }
            }
        }
        intent.putExtra(MainActivity.EXTRA_OPEN_NEWS_FROM_PUSH, true);
        int requestCode = (int) (System.currentTimeMillis() & 0x7fff_ffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        nm.notify((int) System.currentTimeMillis() % 100000, builder.build());
    }
}
