package com.example.omrscanner.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.omr.AnchorDetector;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

public class PreviewActivity extends AppCompatActivity {

    public static final String IMAGE_PATH = "image_path";
    public static final String ANCHOR_POINTS = "anchor_points";
    public static final String IMAGE_SOURCE = "image_source";

    // Source type constants (SOURCE_GALLERY kept for ResultActivity / LrnErrorActivity / CameraActivity)
    public static final String SOURCE_CAMERA = "camera";
    public static final String SOURCE_GALLERY = "gallery"; // no longer an active entry point

    private ImageView imagePreview;
    private Button btnRetake;
    private Button btnScan;
    private ProgressBar progressBar;

    private String imagePath;
    private String selectedSheetType;
    private String classId;
    private String activityId;
    private boolean isQuiz;
    private boolean fixedMountMode;
    private boolean tiltAgnosticMode;
    private Bitmap originalBitmap;
    private Point[] detectedAnchors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        // Full screen — hide status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize views
        imagePreview = findViewById(R.id.imagePreview);
        btnRetake = findViewById(R.id.btnRetake);
        btnScan = findViewById(R.id.btnScan);
        progressBar = findViewById(R.id.progressBar);

        // Get image path from intent
        imagePath = getIntent().getStringExtra(IMAGE_PATH);

        // Get sheet type from intent
        selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);

        // Get class/activity IDs for folder-based saving
        classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
        activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);
        isQuiz = getIntent().getBooleanExtra(DashboardActivity.EXTRA_IS_QUIZ, false);

        // Get camera mode so "Retake" can relaunch the camera in the same mode
        fixedMountMode = getIntent().getBooleanExtra(CameraActivity.EXTRA_FIXED_MOUNT_MODE, false);
        tiltAgnosticMode = getIntent().getBooleanExtra(CameraActivity.EXTRA_TILT_AGNOSTIC_MODE, false);

        if (imagePath != null) {
            loadAndProcessImage();
        } else {
            Toast.makeText(this, "No image to preview", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Button listeners
        btnRetake.setOnClickListener(v -> retakePhoto());
        btnScan.setOnClickListener(v -> proceedToAlignment());
    }

    private void loadAndProcessImage() {
        showLoading(true);

        // Load image in background thread
        new Thread(() -> {
            try {
                // Load bitmap
                originalBitmap = BitmapFactory.decodeFile(imagePath);

                if (originalBitmap == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // Detect anchors -- SKIPPED in Tilt Agnostic Mode. This
                // position-based AnchorDetector labels TL/TR/BL/BR purely by
                // where a candidate square lands in the frame, correct only
                // when the sheet is already right-side-up relative to the
                // camera. Forwarding its guess as ANCHOR_POINTS meant
                // ResultActivity's ArUco identity re-detection -- the
                // actually orientation-safe path -- never ran, since it only
                // fires when incoming anchors are null. Skipping this call
                // lets ResultActivity do the real detection fresh.
                detectedAnchors = tiltAgnosticMode
                        ? null
                        : AnchorDetector.detectAnchors(originalBitmap);

                // Update UI on main thread
                runOnUiThread(() -> {
                    showLoading(false);

                    if (tiltAgnosticMode) {
                        // No preview-stage detection in this mode -- just
                        // show the raw capture. ResultActivity resolves the
                        // real corners via ArUco identity detection and
                        // prompts a retake there if that fails.
                        imagePreview.setImageBitmap(originalBitmap);
                        btnScan.setEnabled(true);
                    } else if (detectedAnchors != null) {
                        // Draw anchors for visual feedback
                        Bitmap debugBitmap = AnchorDetector.drawAnchors(
                                originalBitmap.copy(originalBitmap.getConfig(), true),
                                detectedAnchors
                        );
                        imagePreview.setImageBitmap(debugBitmap);

                        Toast.makeText(
                                this,
                                "✓ 4 anchors detected!",
                                Toast.LENGTH_SHORT
                        ).show();

                        btnScan.setEnabled(true);
                    } else {
                        // Show original image if detection fails
                        imagePreview.setImageBitmap(originalBitmap);

                        Toast.makeText(
                                this,
                                "⚠ Anchor detection failed. Please retake.",
                                Toast.LENGTH_LONG
                        ).show();

                        btnScan.setEnabled(false);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(
                            this,
                            "Error processing image: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            }
        }).start();
    }

    private void retakePhoto() {
        // Go back to camera, preserving the mode and sheet/class/activity context
        // the user originally picked (previously this always fell back to Handheld).
        Intent intent = new Intent(this, CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(CameraActivity.EXTRA_FIXED_MOUNT_MODE, fixedMountMode);
        intent.putExtra(CameraActivity.EXTRA_TILT_AGNOSTIC_MODE, tiltAgnosticMode);
        if (selectedSheetType != null) intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
        if (classId != null) intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
        if (activityId != null) intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
        intent.putExtra(DashboardActivity.EXTRA_IS_QUIZ, isQuiz);
        startActivity(intent);
        finish();
    }

    private void proceedToAlignment() {
        // Tilt Agnostic Mode never has preview-stage anchors (detection is
        // skipped above, deferred to ResultActivity's ArUco identity pass),
        // so only require detectedAnchors for the other modes.
        if (!tiltAgnosticMode && detectedAnchors == null) {
            Toast.makeText(
                    this,
                    "Cannot proceed - anchors not detected",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // NEXT STEP: Pass to ResultActivity for perspective correction
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra(IMAGE_PATH, imagePath);

        // Convert Point[] to double[] for passing via Intent -- only when we
        // actually have preview-stage anchors. Tilt Agnostic Mode leaves
        // this unset so ResultActivity's finalAnchors starts null and its
        // ArUco identity re-detection actually runs.
        if (detectedAnchors != null) {
            double[] anchorData = new double[8]; // 4 points × 2 coordinates
            for (int i = 0; i < 4; i++) {
                anchorData[i * 2] = detectedAnchors[i].x;
                anchorData[i * 2 + 1] = detectedAnchors[i].y;
            }
            intent.putExtra(ANCHOR_POINTS, anchorData);
        }

        // Pass the selected sheet type
        if (selectedSheetType != null) {
            intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
        }

        // Pass class/activity IDs for folder-based saving
        if (classId != null) {
            intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
        }
        if (activityId != null) {
            intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
        }
        intent.putExtra(DashboardActivity.EXTRA_IS_QUIZ, isQuiz);

        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRetake.setEnabled(!show);
        btnScan.setEnabled(!show && (detectedAnchors != null || tiltAgnosticMode));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }
}