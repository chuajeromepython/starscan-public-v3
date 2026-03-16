package com.example.omrscanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;

/**
 * BetaExpiredActivity
 *
 * Full-screen, non-bypassable gate shown when the APK beta period has ended.
 * Back-press is intentionally disabled — users must download the Play Store version.
 *
 * TODO: Replace PLAY_STORE_URL with your real Play Store listing URL once published.
 */
public class BetaExpiredActivity extends AppCompatActivity {

    // TODO: Update this URL with your real Play Store listing after publishing.
    private static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.example.omrscanner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beta_expired);

        // Full-screen immersive (same as splash)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        MaterialButton btnPlayStore = findViewById(R.id.btnPlayStore);
        btnPlayStore.setOnClickListener(v -> openPlayStore());
    }

    private void openPlayStore() {
        try {
            // Try to open the Play Store app directly
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.example.omrscanner")));
        } catch (android.content.ActivityNotFoundException e) {
            // Fall back to browser if Play Store app is not installed
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)));
        }
    }

    /**
     * Disable back-press so users cannot bypass the expiry gate.
     */
    @Override
    public void onBackPressed() {
        // Intentionally empty — back is disabled on this screen.
    }
}
