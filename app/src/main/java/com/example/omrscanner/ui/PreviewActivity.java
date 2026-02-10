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

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.R;
import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.omr.AnchorDetector;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

public class PreviewActivity extends AppCompatActivity {

    public static final String IMAGE_PATH = "image_path";
    public static final String ANCHOR_POINTS = "anchor_points";
    public static final String IMAGE_SOURCE = "image_source";

    // Source types
    public static final String SOURCE_CAMERA = "camera";
    public static final String SOURCE_GALLERY = "gallery";

    private ImageView imagePreview;
    private Button btnRetake;
    private Button btnScan;
    private ProgressBar progressBar;

    private String imagePath;
    private String imageSource;
    private Bitmap originalBitmap;
    private Point[] detectedAnchors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

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

        // Get image path and source from intent
        imagePath = getIntent().getStringExtra(IMAGE_PATH);
        imageSource = getIntent().getStringExtra(IMAGE_SOURCE);

        // Default to camera if not specified (backward compatibility)
        if (imageSource == null) {
            imageSource = SOURCE_CAMERA;
        }

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

                // Detect anchors
                detectedAnchors = AnchorDetector.detectAnchors(originalBitmap);

                // Update UI on main thread
                runOnUiThread(() -> {
                    showLoading(false);

                    if (detectedAnchors != null) {
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
        if (SOURCE_GALLERY.equals(imageSource)) {
            // Redirect to dashboard for gallery retake
            finish();
        } else {
            // Go back to camera for retake
            startActivity(new Intent(this, CameraActivity.class));
            finish();
        }
    }

    private void proceedToAlignment() {
        if (detectedAnchors == null) {
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

        // Convert Point[] to double[] for passing via Intent
        double[] anchorData = new double[8]; // 4 points × 2 coordinates
        for (int i = 0; i < 4; i++) {
            anchorData[i * 2] = detectedAnchors[i].x;
            anchorData[i * 2 + 1] = detectedAnchors[i].y;
        }
        intent.putExtra(ANCHOR_POINTS, anchorData);

        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRetake.setEnabled(!show);
        btnScan.setEnabled(!show && detectedAnchors != null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }
}