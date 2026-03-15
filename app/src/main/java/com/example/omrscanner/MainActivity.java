package com.example.omrscanner;

import android.content.Intent;
import android.os.Bundle;

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
            Class<?> destination = BetaExpiryChecker.isExpired()
                    ? BetaExpiredActivity.class   // Beta over — show gate screen
                    : DashboardActivity.class;    // Still active — proceed normally
            startActivity(new Intent(MainActivity.this, destination));
            finish(); // Remove splash from back-stack
        }, 1500);
    }
}