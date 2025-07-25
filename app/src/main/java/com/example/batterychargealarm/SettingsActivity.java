// File: app/src/main/java/com/example/batterychargealarm/SettingsActivity.java
package com.example.batterychargealarm;

import android.content.Intent; // NEW: Import Intent
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BatteryAlarmPrefs";
    private static final String MAX_PERCENT_KEY = "maxPercent";
    private static final String MIN_PERCENT_KEY = "minPercent";
    public static final String REPEAT_INTERVAL_KEY = "repeatInterval";

    // NEW: A custom action for our broadcast
    public static final String ACTION_SETTINGS_UPDATED = "com.example.batterychargealarm.SETTINGS_UPDATED";

    private TextInputEditText etMaxPercentage, etMinPercentage, etRepeatInterval;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etMaxPercentage = findViewById(R.id.etMaxPercentage);
        etMinPercentage = findViewById(R.id.etMinPercentage);
        etRepeatInterval = findViewById(R.id.etRepeatInterval);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadSettings();

        btnSaveSettings.setOnClickListener(v -> {
            saveSettings();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadSettings() {
        etMaxPercentage.setText(String.valueOf(prefs.getInt(MAX_PERCENT_KEY, 85)));
        etMinPercentage.setText(String.valueOf(prefs.getInt(MIN_PERCENT_KEY, 20)));
        long intervalSeconds = prefs.getLong(REPEAT_INTERVAL_KEY, 300);
        etRepeatInterval.setText(String.valueOf(intervalSeconds));
    }

    private void saveSettings() {
        int maxPercent = Integer.parseInt(etMaxPercentage.getText().toString());
        int minPercent = Integer.parseInt(etMinPercentage.getText().toString());
        long intervalSeconds = Long.parseLong(etRepeatInterval.getText().toString());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(MAX_PERCENT_KEY, maxPercent);
        editor.putInt(MIN_PERCENT_KEY, minPercent);
        editor.putLong(REPEAT_INTERVAL_KEY, intervalSeconds);
        editor.apply();

        // NEW: Send a broadcast to notify the service of the change
        Intent intent = new Intent(ACTION_SETTINGS_UPDATED);
        sendBroadcast(intent);

        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
    }
}