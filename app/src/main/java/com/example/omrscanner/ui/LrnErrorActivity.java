package com.example.omrscanner.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.camera.CameraActivity;
import com.google.android.material.button.MaterialButton;

/**
 * Full-screen error activity shown when the LRN block has
 * double-shaded bubbles (2+ bubbles filled in the same column).
 *
 * <p>The LRN is the student's unique ID — double-shading makes it
 * impossible to determine the correct number, so the scan cannot proceed.
 * The user must fix the physical bubble sheet and re-scan.</p>
 */
public class LrnErrorActivity extends AppCompatActivity {

    public static final String EXTRA_DOUBLE_SHADED_POSITIONS = "double_shaded_positions";
    public static final String EXTRA_IMAGE_SOURCE = "image_source";
    public static final String EXTRA_SHEET_TYPE   = "sheet_type";
    public static final String EXTRA_CLASS_ID     = "class_id";
    public static final String EXTRA_ACTIVITY_ID  = "activity_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lrn_error);

        // Full screen — hide status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // Read extras
        int[] positions = getIntent().getIntArrayExtra(EXTRA_DOUBLE_SHADED_POSITIONS);
        String imageSource = getIntent().getStringExtra(EXTRA_IMAGE_SOURCE);
        String sheetType   = getIntent().getStringExtra(EXTRA_SHEET_TYPE);
        String classId     = getIntent().getStringExtra(EXTRA_CLASS_ID);
        String activityId  = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);

        // Build affected positions string (convert 0-based to 1-based)
        StringBuilder posStr = new StringBuilder();
        if (positions != null && positions.length > 0) {
            for (int i = 0; i < positions.length; i++) {
                if (i > 0) posStr.append(", ");
                posStr.append("Column ").append(positions[i] + 1);
            }
        } else {
            posStr.append("—");
        }

        // Populate affected positions
        TextView tvAffectedPositions = findViewById(R.id.tvAffectedPositions);
        tvAffectedPositions.setText(posStr.toString());

        // Update detail text with count
        TextView tvErrorDetail = findViewById(R.id.tvErrorDetail);
        int count = (positions != null) ? positions.length : 0;
        tvErrorDetail.setText(
                "May nadetect na " + count + " column(s) sa LRN na may dalawang "
                + "o higit pang naka-shade na bubble. Hindi mababasa nang tama "
                + "ang LRN ng estudyante dahil dito."
        );

        // Retake button
        MaterialButton btnRetake = findViewById(R.id.btnRetakeScan);
        btnRetake.setOnClickListener(v -> {
            if (PreviewActivity.SOURCE_GALLERY.equals(imageSource)) {
                // Go back to dashboard (user came from gallery)
                finish();
            } else {
                // Go back to camera
                Intent intent = new Intent(this, CameraActivity.class);
                if (sheetType != null)  intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, sheetType);
                if (classId != null)    intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
                if (activityId != null) intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
