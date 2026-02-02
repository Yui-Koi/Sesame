package co.median.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Debug-only foreground service used to keep the app in the foreground state
 * for testing WebRTC microphone behavior while the app is backgrounded.
 */
public class CallForegroundService extends Service {

    private static final String CHANNEL_ID = "median_call_debug";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        // The foregroundServiceType is declared in the manifest as "microphone".
        // Using the simpler startForeground API here keeps compatibility with
        // older AndroidX versions while still satisfying Android 14+ checks.
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Not a bound service.
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Debug Call Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Foreground service for testing WebRTC microphone behavior.");
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("WebRTC Debug Service")
                .setContentText("Foreground service running for WebRTC mic tests.")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }
}
