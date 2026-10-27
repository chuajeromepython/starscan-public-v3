package com.example.omrscanner;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.utils.BetaExpiryChecker;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Full screen — hide status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // Hide support action bar if present
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Wait for 1.5 s (splash display), then check beta expiry before navigating.
        new android.os.Handler().postDelayed(() -> {
            // Block the app if Developer Options is enabled
            if (Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0) {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Developer options enabled")
                        .setMessage("Please turn off Developer Options to use this app.")
                        .setCancelable(false)
                        .setPositiveButton("Open settings", (d, w) -> {
                            startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
                            finishAffinity();
                        })
                        .setNegativeButton("Exit", (d, w) -> finishAffinity())
                        .show();
                return; // stop here, don't go to the dashboard
            }
            // Temporarily disable beta gate for testing, uncomment to revert back
            // Class<?> destination = BetaExpiryChecker.isExpired()
            //       ? BetaExpiredActivity.class   // Beta over — show gate screen
            //      : DashboardActivity.class;    // Still active — proceed normally
            Class<?> destination = DashboardActivity.class;
            startActivity(new Intent(MainActivity.this, destination));
            finish(); // Remove splash from back-stack
        }, 1500);
    }
}