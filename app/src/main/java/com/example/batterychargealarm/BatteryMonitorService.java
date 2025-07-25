package com.example.batterychargealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class BatteryMonitorService extends Service implements TextToSpeech.OnInitListener {

    private static final String PREFS_NAME = "BatteryAlarmPrefs";
    private static final String MAX_PERCENT_KEY = "maxPercent";
    private static final String MIN_PERCENT_KEY = "minPercent";
    private static final String SOUND_URI_KEY = "soundUri";

    private static final String CHANNEL_ID_FOREGROUND = "BatteryMonitorForegroundChannel";
    private static final String CHANNEL_ID_ALARM = "BatteryMonitorAlarmChannel";
    private static final int NOTIFICATION_ID_FOREGROUND = 1;
    private static final int NOTIFICATION_ID_ALARM = 2;

    private TextToSpeech tts;
    private Handler alarmHandler = new Handler();
    private Runnable voiceAlarmRunnable;
    private boolean isRepeatingAlarmActive = false;

    private final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) ((level / (float) scale) * 100);

            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            checkBatteryStatus(batteryPct, isCharging);
        }
    };

    private final BroadcastReceiver settingsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isRepeatingAlarmActive) {
                stopRepeatingVoiceAlarm();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(this, this);

        // UPDATED: Add the RECEIVER_NOT_EXPORTED flag
        registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_NOT_EXPORTED);
        registerReceiver(settingsUpdateReceiver, new IntentFilter(SettingsActivity.ACTION_SETTINGS_UPDATED), RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!");
            }
        } else {
            Log.e("TTS", "Initialization Failed!");
        }
    }

    private void speak(String text) {
        if (tts != null && !tts.isSpeaking()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannels();
        startForeground(NOTIFICATION_ID_FOREGROUND, createForegroundNotification("Monitoring..."));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRepeatingVoiceAlarm();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        unregisterReceiver(batteryInfoReceiver);
        unregisterReceiver(settingsUpdateReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startRepeatingVoiceAlarm(String message) {
        isRepeatingAlarmActive = true;
        voiceAlarmRunnable = new Runnable() {
            @Override
            public void run() {
                speak(message);

                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                long repeatIntervalSeconds = prefs.getLong(SettingsActivity.REPEAT_INTERVAL_KEY, 300);
                long repeatIntervalMs = repeatIntervalSeconds * 1000;

                alarmHandler.postDelayed(this, repeatIntervalMs);
            }
        };
        alarmHandler.post(voiceAlarmRunnable);
    }

    private void stopRepeatingVoiceAlarm() {
        if (voiceAlarmRunnable != null) {
            alarmHandler.removeCallbacks(voiceAlarmRunnable);
        }
        isRepeatingAlarmActive = false;
    }

    private void checkBatteryStatus(int currentLevel, boolean isCharging) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int maxPercent = prefs.getInt(MAX_PERCENT_KEY, 85);
        int minPercent = prefs.getInt(MIN_PERCENT_KEY, 20);

        String statusText = "Current Level: " + currentLevel + "%" + (isCharging ? " (Charging)" : " (Discharging)");
        updateForegroundNotification(statusText);

        boolean maxThresholdBreached = isCharging && currentLevel >= maxPercent;
        boolean minThresholdBreached = !isCharging && currentLevel <= minPercent;

        if (maxThresholdBreached) {
            if (!isRepeatingAlarmActive) {
                String message = "Battery is at " + currentLevel + " percent. Please unplug the charger.";
                sendAlarmNotification("High Battery Level", message);
                startRepeatingVoiceAlarm(message);
            }
        } else if (minThresholdBreached) {
            if (!isRepeatingAlarmActive) {
                String message = "Battery is at " + currentLevel + " percent. Please connect the charger.";
                sendAlarmNotification("Low Battery Level", message);
                startRepeatingVoiceAlarm(message);
            }
        } else {
            if (isRepeatingAlarmActive) {
                stopRepeatingVoiceAlarm();
            }
        }
    }

    private void sendAlarmNotification(String title, String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String soundUriString = prefs.getString(SOUND_URI_KEY, "");

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (!TextUtils.isEmpty(soundUriString)) {
            notificationBuilder.setSound(Uri.parse(soundUriString));
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_ALARM, notificationBuilder.build());
    }

    private Notification createForegroundNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
                .setContentTitle("Battery Monitor Active")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateForegroundNotification(String text) {
        Notification notification = createForegroundNotification(text);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND, notification);
    }

    private void createNotificationChannels() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel foregroundChannel = new NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "Battery Monitoring Service",
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(foregroundChannel);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String soundUriString = prefs.getString(SOUND_URI_KEY, "");
        Uri soundUri = TextUtils.isEmpty(soundUriString) ? null : Uri.parse(soundUriString);

        NotificationChannel alarmChannel = new NotificationChannel(
                CHANNEL_ID_ALARM,
                "Battery Alarms",
                NotificationManager.IMPORTANCE_HIGH);
        alarmChannel.setDescription("Notifications for high and low battery levels.");

        if (soundUri != null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            alarmChannel.setSound(soundUri, audioAttributes);
        }

        notificationManager.createNotificationChannel(alarmChannel);
    }
}