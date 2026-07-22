package com.victor.monkitrip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;

public class TimerForegroundService extends Service {

    public static final String ACTION_START = "com.victor.monkitrip.action.START";
    public static final String ACTION_STOP  = "com.victor.monkitrip.action.STOP";

    private static final String CHANNEL_ID_ONGOING = "monkitrip_timer_chronometer";
    private static final String CHANNEL_ID_ALERT   = "monkitrip_timer_alert";
    private static final int NOTIFICATION_ID = 555;

    private Handler handler;
    private Runnable checkRunnable;
    private long endTimeMillis;
    private String title;
    private int color;
    private boolean finished = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopTimer();
            return START_NOT_STICKY;
        }

        // ACTION_START (default)
        String titleExtra = intent.getStringExtra("title");
        int durationSecs = intent.getIntExtra("durationSecs", 0);
        String colorHex = intent.getStringExtra("color");

        title = (titleExtra != null) ? titleExtra : "Monkitrip";
        try { color = Color.parseColor(colorHex); }
        catch (Exception e) { color = Color.parseColor("#A87EF5"); }

        finished = false;
        endTimeMillis = System.currentTimeMillis() + (durationSecs * 1000L);

        Notification notification = buildOngoingNotification();
        startForeground(NOTIFICATION_ID, notification);

        scheduleCheck();
        return START_STICKY;
    }

    private void scheduleCheck() {
        if (checkRunnable != null) handler.removeCallbacks(checkRunnable);
        checkRunnable = () -> {
            if (finished) return;
            long remaining = endTimeMillis - System.currentTimeMillis();
            if (remaining <= 0) {
                onTimerFinished();
            } else {
                handler.postDelayed(checkRunnable, 500);
            }
        };
        handler.postDelayed(checkRunnable, 500);
    }

    private void onTimerFinished() {
        finished = true;

        // Stop being a foreground service and remove the chronometer notification
        stopForeground(true);

        // Fire the final alert: sound + vibration, high-priority channel
        NotificationCompat.Builder alert = new NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏱ " + title)
            .setContentText("¡Tiempo terminado!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 300, 100, 300, 100, 300})
            .setDefaults(Notification.DEFAULT_SOUND);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, alert.build());

        // Explicit vibration call as a backup, in case the channel's own
        // vibration is suppressed by the OS for any reason
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 300, 100, 300, 100, 300};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }

        stopSelf();
    }

    private void stopTimer() {
        finished = true;
        if (checkRunnable != null) handler.removeCallbacks(checkRunnable);
        stopForeground(true);
        stopSelf();
    }

    private Notification buildOngoingNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Tiempo restante")
            .setColor(color)
            .setColorized(true) // works correctly now — this IS a foreground service notification
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endTimeMillis)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH);
        return builder.build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager.getNotificationChannel(CHANNEL_ID_ONGOING) == null) {
                NotificationChannel ongoing = new NotificationChannel(
                    CHANNEL_ID_ONGOING, "Cronómetro de descanso", NotificationManager.IMPORTANCE_LOW
                );
                ongoing.setDescription("Muestra el tiempo restante de descanso con un cronómetro nativo");
                ongoing.enableVibration(false);
                manager.createNotificationChannel(ongoing);
            }

            if (manager.getNotificationChannel(CHANNEL_ID_ALERT) == null) {
                NotificationChannel alert = new NotificationChannel(
                    CHANNEL_ID_ALERT, "Alerta de fin de tiempo", NotificationManager.IMPORTANCE_HIGH
                );
                alert.setDescription("Suena y vibra cuando el temporizador llega a cero");
                alert.enableVibration(true);
                alert.setVibrationPattern(new long[]{0, 300, 100, 300, 100, 300});
                manager.createNotificationChannel(alert);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkRunnable != null) handler.removeCallbacks(checkRunnable);
    }
}
