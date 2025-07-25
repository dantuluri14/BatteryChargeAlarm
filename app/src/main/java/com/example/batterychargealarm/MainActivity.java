package com.example.batterychargealarm;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BatteryAlarmPrefs";
    private static final String MONITORING_KEY = "isMonitoring";

    private Button btnToggleMonitoring;
    private TextView tvStatus;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    setMonitoringState(true);
                } else {
                    Toast.makeText(this, "Notification permission is required.", Toast.LENGTH_LONG).show();
                    setMonitoringState(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the toolbar and set it as the action bar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btnToggleMonitoring = findViewById(R.id.btnToggleMonitoring);
        tvStatus = findViewById(R.id.tvStatus);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnToggleMonitoring.setOnClickListener(v -> {
            boolean isCurrentlyMonitoring = prefs.getBoolean(MONITORING_KEY, false);
            // Toggle the state
            if (isCurrentlyMonitoring) {
                setMonitoringState(false);
            } else {
                checkPermissionsAndStart();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set initial UI state when app opens or returns to this screen
        updateUI(prefs.getBoolean(MONITORING_KEY, false));
    }

    private void checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return; // Wait for permission result
            }
        }
        // If permission is already granted or not needed, start monitoring
        setMonitoringState(true);
    }

    private void setMonitoringState(boolean enable) {
        prefs.edit().putBoolean(MONITORING_KEY, enable).apply();
        updateUI(enable);

        Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
        if (enable) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            stopService(serviceIntent);
        }
    }

    private void updateUI(boolean isMonitoring) {
        if (isMonitoring) {
            tvStatus.setText("Monitoring is Enabled");
            btnToggleMonitoring.setText("Disable Monitoring");
        } else {
            tvStatus.setText("Monitoring is Disabled");
            btnToggleMonitoring.setText("Enable Monitoring");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}