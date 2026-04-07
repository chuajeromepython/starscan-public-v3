package com.example.omrscanner.camera;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.omr.AnchorDetector;
import com.example.omrscanner.ui.PreviewActivity;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CameraActivity";
    public static final String EXTRA_FIXED_MOUNT_MODE = "fixed_mount_mode";
    private static final Size ANALYSIS_TARGET_RESOLUTION = new Size(1280, 720);
    private static final long FIXED_MOUNT_ZOOM_COOLDOWN_MS = 250L;
    private static final int FIXED_MOUNT_MISS_THRESHOLD = 6;
    private static final float[] FIXED_MOUNT_ZOOM_STEPS = {1.0f, 1.25f, 1.5f, 1.75f};

    // How long each hint stays visible before cycling (milliseconds)
    private static final long HINT_DISPLAY_DURATION_MS = 5000;

    // Floating guidance hints (cycled when no anchors detected)
    private static final String[] FLOATING_HINTS = {
            "📄  Point camera at the OMR sheet",
            "🔍  Move closer to the paper",
            "💡  Ensure good lighting, avoid shadows",
            "📏  Keep the sheet flat and straight",
            "🔲  Make sure all 4 corner squares are visible",
            "🚫  Avoid glare on the paper",
            "📐  Hold phone parallel to the paper"
    };

    // ── Camera core ───────────────────────────────────────────────
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;

    // ── UI fields ─────────────────────────────────────────────────
    private FrameLayout btnCapture;
    private FrameLayout btnCameraBack;
    private FrameLayout gridOverlay;
    private View flashOverlay;
    private FrameLayout btnFlash;
    private ImageView iconFlash;
    private AnchorOverlayView anchorOverlay;
    private TextView anchorStatusText;
    private ImageView anchorStatusIcon;
    private TextView floatingHintText;

    // ── Camera state ──────────────────────────────────────────────
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isTorchOn = false;
    private boolean fixedMountMode = false;

    // ── Auto-capture state ────────────────────────────────────────
    private int consecutiveDetections = 0;
    private int currentHintIndex = 0;
    private long lastHintChangeTime = 0;
    private boolean autoCaptureTriggered = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // ── Anchor Smoothing State ────────────────────────────────────
    private PointF[] smoothedAnchors = null;
    private int missingFramesCount = 0;
    private static final int MAX_MISSING_FRAMES = 8;
    private static final float SMOOTHING_FACTOR = 0.25f;
    private int fixedMountMissCounter = 0;
    private int fixedMountZoomIndex = 0;
    private long lastZoomChangeTime = 0L;
    private final List<Float> fixedMountZoomRatios = new ArrayList<>();

    // ── Intent extras ─────────────────────────────────────────────
    private String selectedSheetType = null;
    private String classId = null;
    private String activityId = null;

    // ── Camera provider held at instance level to avoid re-binding ─
    private ProcessCameraProvider cameraProvider;

    // ── OpenCV init flag ──────────────────────────────────────────
    private boolean openCVReady = false;

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate started");

        try {
            setContentView(R.layout.activity_camera);
            Log.d(TAG, "Layout inflated successfully");

            selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
            classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
            activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);
            fixedMountMode = getIntent().getBooleanExtra(EXTRA_FIXED_MOUNT_MODE, false);
            Log.d(TAG, "Received sheet type: " + selectedSheetType + ", classId: " + classId);

            // Full screen — hide status bar and navigation bar
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat insetsController =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.hide(WindowInsetsCompat.Type.systemBars());

            // Initialize OpenCV
            openCVReady = OpenCVLoader.initDebug();
            if (!openCVReady) {
                Log.e(TAG, "OpenCV initialization failed!");
            }

            initializeViews();
            setupListeners();

            cameraExecutor = Executors.newSingleThreadExecutor();

            if (hasCameraPermission()) {
                startCamera();
            } else {
                requestCameraPermission();
            }

            Log.d(TAG, "onCreate completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ─────────────────────────────────────────────────────────────
    private void initializeViews() {
        previewView      = findViewById(R.id.previewView);
        btnCameraBack    = findViewById(R.id.btnCameraBack);
        flashOverlay     = findViewById(R.id.flashOverlay);
        gridOverlay      = findViewById(R.id.gridOverlay);
        btnCapture       = findViewById(R.id.btnCapture);
        btnFlash         = findViewById(R.id.btnFlash);
        iconFlash        = findViewById(R.id.iconFlash);
        anchorOverlay    = findViewById(R.id.anchorOverlay);
        anchorStatusText = findViewById(R.id.anchorStatusText);
        anchorStatusIcon = findViewById(R.id.anchorStatusIcon);
        floatingHintText = findViewById(R.id.floatingHintText);

        if (previewView   == null) Log.e(TAG, "previewView is null!");
        if (btnCapture    == null) Log.e(TAG, "btnCapture is null!");
    }

    // ─────────────────────────────────────────────────────────────
    private void setupListeners() {
        if (btnCapture != null) btnCapture.setOnClickListener(v -> takePhoto());
        if (btnCameraBack != null) btnCameraBack.setOnClickListener(v -> finish());
        if (btnFlash != null) btnFlash.setOnClickListener(v -> toggleFlash());
    }

    // ─────────────────────────────────────────────────────────────
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ─────────────────────────────────────────────────────────────
    private void startCamera() {
        Log.d(TAG, "startCamera called");
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera provider failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Camera failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Binds Preview, ImageCapture, AND ImageAnalysis use cases.
     * ImageAnalysis runs anchor detection on each frame for real-time feedback.
     */
    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // ── ImageAnalysis for real-time anchor detection ──────────
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(ANALYSIS_TARGET_RESOLUTION)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build();

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalysis);

        cameraControl = camera.getCameraControl();
        cameraInfo    = camera.getCameraInfo();
        initializeFixedMountZoomRatios();
        applyFixedMountZoom(true);

        updateFlashButton();

        Log.d(TAG, "Camera bound successfully with ImageAnalysis, facing=" + cameraFacing
                + ", fixedMountMode=" + fixedMountMode);
    }

    // ─────────────────────────────────────────────────────────────
    //  REAL-TIME ANCHOR DETECTION
    // ─────────────────────────────────────────────────────────────

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        // Skip if OpenCV not ready or already auto-captured
        if (!openCVReady || autoCaptureTriggered) {
            imageProxy.close();
            return;
        }

        try {
            AnchorDetector.LiveDetectionMode liveDetectionMode = fixedMountMode
                    ? AnchorDetector.LiveDetectionMode.FIXED_MOUNT
                    : AnchorDetector.LiveDetectionMode.HANDHELD;
            Point[] anchors = AnchorDetector.detectAnchors(imageProxy, liveDetectionMode);

            if (anchors != null && anchors.length == 4) {
                onDetectionSuccess();
                PointF[] viewPoints = scaleAnchorsToView(
                        anchors,
                        imageProxy.getWidth(),
                        imageProxy.getHeight(),
                        imageProxy.getImageInfo().getRotationDegrees()
                );
                boolean captureStarted = triggerAutoCapture();
                onAnchorsDetected(viewPoints, captureStarted);
            } else {
                onDetectionMiss();
                onAnchorsNotDetected();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in frame analysis", e);
            onDetectionMiss();
            onAnchorsNotDetected();
        } finally {
            imageProxy.close();
        }
    }

    private void initializeFixedMountZoomRatios() {
        fixedMountZoomRatios.clear();

        if (!fixedMountMode) {
            fixedMountZoomRatios.add(1.0f);
            fixedMountZoomIndex = 0;
            fixedMountMissCounter = 0;
            lastZoomChangeTime = 0L;
            return;
        }

        float minZoom = 1.0f;
        float maxZoom = 1.0f;
        if (cameraInfo != null && cameraInfo.getZoomState().getValue() != null) {
            ZoomState zoomState = cameraInfo.getZoomState().getValue();
            minZoom = zoomState.getMinZoomRatio();
            maxZoom = zoomState.getMaxZoomRatio();
        }

        for (float zoomStep : FIXED_MOUNT_ZOOM_STEPS) {
            float clamped = Math.max(minZoom, Math.min(maxZoom, zoomStep));
            if (fixedMountZoomRatios.isEmpty()
                    || Math.abs(fixedMountZoomRatios.get(fixedMountZoomRatios.size() - 1) - clamped) > 0.01f) {
                fixedMountZoomRatios.add(clamped);
            }
        }

        if (fixedMountZoomRatios.isEmpty()) {
            fixedMountZoomRatios.add(Math.max(minZoom, 1.0f));
        }

        fixedMountZoomIndex = 0;
        fixedMountMissCounter = 0;
        lastZoomChangeTime = 0L;
    }

    private void applyFixedMountZoom(boolean resetToBase) {
        if (!fixedMountMode || cameraControl == null || fixedMountZoomRatios.isEmpty()) {
            return;
        }

        if (resetToBase) {
            fixedMountZoomIndex = 0;
            fixedMountMissCounter = 0;
            lastZoomChangeTime = 0L;
        }

        float zoomRatio = fixedMountZoomRatios.get(fixedMountZoomIndex);
        cameraControl.setZoomRatio(zoomRatio);
        Log.d(TAG, "Fixed-mount zoom set to " + zoomRatio + "x");
    }

    private void onDetectionSuccess() {
        if (!fixedMountMode) {
            return;
        }

        fixedMountMissCounter = 0;
    }

    private void onDetectionMiss() {
        if (!fixedMountMode || autoCaptureTriggered || fixedMountZoomRatios.size() <= 1) {
            return;
        }

        fixedMountMissCounter++;
        long now = System.currentTimeMillis();
        if (fixedMountMissCounter < FIXED_MOUNT_MISS_THRESHOLD) {
            return;
        }
        if (now - lastZoomChangeTime < FIXED_MOUNT_ZOOM_COOLDOWN_MS) {
            return;
        }

        fixedMountMissCounter = 0;
        fixedMountZoomIndex = (fixedMountZoomIndex + 1) % fixedMountZoomRatios.size();
        lastZoomChangeTime = now;
        float zoomRatio = fixedMountZoomRatios.get(fixedMountZoomIndex);
        cameraControl.setZoomRatio(zoomRatio);
        Log.d(TAG, "Fixed-mount zoom advanced to " + zoomRatio + "x after repeated misses");
    }

    /**
     * Scale anchor coordinates from bitmap space to overlay view space.
     */
    private PointF[] scaleAnchorsToView(
            Point[] anchors,
            int imageWidth,
            int imageHeight,
            int rotationDegrees
    ) {
        PointF[] viewPoints = new PointF[4];

        // The overlay is the same size as the preview view
        int viewWidth = anchorOverlay != null ? anchorOverlay.getWidth() : 0;
        int viewHeight = anchorOverlay != null ? anchorOverlay.getHeight() : 0;

        if (viewWidth == 0 || viewHeight == 0) {
            viewWidth = previewView.getWidth();
            viewHeight = previewView.getHeight();
        }

        if (viewWidth == 0 || viewHeight == 0) {
            // Fallback: just use analysis-image coordinates
            for (int i = 0; i < 4; i++) {
                viewPoints[i] = new PointF((float) anchors[i].x, (float) anchors[i].y);
            }
            return viewPoints;
        }

        boolean swapDimensions = rotationDegrees == 90 || rotationDegrees == 270;
        int rotatedWidth = swapDimensions ? imageHeight : imageWidth;
        int rotatedHeight = swapDimensions ? imageWidth : imageHeight;

        float scaleX = (float) viewWidth / rotatedWidth;
        float scaleY = (float) viewHeight / rotatedHeight;

        for (int i = 0; i < 4; i++) {
            PointF rotatedPoint = rotatePointToDisplay(
                    anchors[i],
                    imageWidth,
                    imageHeight,
                    rotationDegrees
            );
            viewPoints[i] = new PointF(
                    rotatedPoint.x * scaleX,
                    rotatedPoint.y * scaleY
            );
        }

        return viewPoints;
    }

    private PointF rotatePointToDisplay(
            Point anchor,
            int imageWidth,
            int imageHeight,
            int rotationDegrees
    ) {
        switch (rotationDegrees) {
            case 90:
                return new PointF((float) (imageHeight - anchor.y), (float) anchor.x);
            case 180:
                return new PointF((float) (imageWidth - anchor.x), (float) (imageHeight - anchor.y));
            case 270:
                return new PointF((float) anchor.y, (float) (imageWidth - anchor.x));
            default:
                return new PointF((float) anchor.x, (float) anchor.y);
        }
    }

    private boolean triggerAutoCapture() {
        if (autoCaptureTriggered) {
            return false;
        }

        autoCaptureTriggered = true;
        Log.d(TAG, "Auto-capture triggered on first valid anchor detection");
        takePhoto();
        return true;
    }

    /**
     * Called when anchors ARE detected in a frame.
     */
    private void onAnchorsDetected(PointF[] viewPoints, boolean captureStarted) {
        missingFramesCount = 0; // reset missing counter

        if (smoothedAnchors == null) {
            smoothedAnchors = new PointF[4];
            for (int i = 0; i < 4; i++) {
                smoothedAnchors[i] = new PointF(viewPoints[i].x, viewPoints[i].y);
            }
        } else {
            // Compute view diagonal for relative jump threshold
            int viewW = anchorOverlay != null ? anchorOverlay.getWidth() : previewView.getWidth();
            int viewH = anchorOverlay != null ? anchorOverlay.getHeight() : previewView.getHeight();
            float diagonal = (float) Math.sqrt(viewW * viewW + viewH * viewH);
            float maxAllowedJump = diagonal * 0.20f; // 20% of diagonal

            // Apply Exponential Moving Average (EMA) for smoothing
            // Detect drastic jumps (e.g. from rapid camera movement)
            float maxJump = 0;
            for (int i = 0; i < 4; i++) {
                float dx = viewPoints[i].x - smoothedAnchors[i].x;
                float dy = viewPoints[i].y - smoothedAnchors[i].y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > maxJump) maxJump = dist;
            }

            if (maxJump > maxAllowedJump) {
                // If the anchors jump significantly, reset smoothing to avoid sliding effect
                for (int i = 0; i < 4; i++) {
                    smoothedAnchors[i] = new PointF(viewPoints[i].x, viewPoints[i].y);
                }
            } else {
                // Smooth points based on factor
                for (int i = 0; i < 4; i++) {
                    smoothedAnchors[i].x = smoothedAnchors[i].x * (1.0f - SMOOTHING_FACTOR) + viewPoints[i].x * SMOOTHING_FACTOR;
                    smoothedAnchors[i].y = smoothedAnchors[i].y * (1.0f - SMOOTHING_FACTOR) + viewPoints[i].y * SMOOTHING_FACTOR;
                }
            }
        }

        consecutiveDetections++;
        lastHintChangeTime = 0; // Reset hint timer
        Log.d(TAG, "Anchors detected! Consecutive count: " + consecutiveDetections);

        // Make a final copy so we don't pass reference directly to UI thread
        final PointF[] finalAnchors = new PointF[4];
        for (int i = 0; i < 4; i++) {
            finalAnchors[i] = new PointF(smoothedAnchors[i].x, smoothedAnchors[i].y);
        }

        mainHandler.post(() -> {
            // Update overlay to show anchor boxes
            if (anchorOverlay != null) {
                anchorOverlay.setAnchors(finalAnchors);
            }

            // Hide floating hint with fade-out
            hideFloatingHint();

            // Update status text
            if (anchorStatusText != null) {
                if (captureStarted) {
                    anchorStatusText.setText("✓ Anchors detected! Capturing…");
                } else {
                    anchorStatusText.setText("✓ Anchors detected");
                }
            }
            if (anchorStatusIcon != null) {
                anchorStatusIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.green),
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }
        });
    }

    /**
     * Called when anchors are NOT detected in a frame.
     */
    private void onAnchorsNotDetected() {
        missingFramesCount++;

        if (missingFramesCount <= MAX_MISSING_FRAMES && smoothedAnchors != null) {
            // Keep drawing the last smoothed anchors to avoid flickering.
            // IMPORTANT: Do NOT reset consecutiveDetections here.
            // This allows the counter to resume when detection comes back,
            // instead of starting over from 0 every time there's a brief gap.

            final PointF[] finalAnchors = new PointF[4];
            for (int i = 0; i < 4; i++) {
                finalAnchors[i] = new PointF(smoothedAnchors[i].x, smoothedAnchors[i].y);
            }

            mainHandler.post(() -> {
                if (anchorOverlay != null) {
                    anchorOverlay.setAnchors(finalAnchors);
                }
                if (anchorStatusText != null) {
                    anchorStatusText.setText("Tracking anchors…");
                }
                if (anchorStatusIcon != null) {
                    anchorStatusIcon.setColorFilter(
                            ContextCompat.getColor(this, R.color.green),
                            android.graphics.PorterDuff.Mode.SRC_IN);
                }
            });
            return;
        }

        // Only clear if missing for too many frames
        smoothedAnchors = null;
        consecutiveDetections = 0;

        // Cycle to next hint if enough time has passed
        long now = System.currentTimeMillis();
        if (now - lastHintChangeTime >= HINT_DISPLAY_DURATION_MS) {
            currentHintIndex = (currentHintIndex + 1) % FLOATING_HINTS.length;
            lastHintChangeTime = now;
        }

        mainHandler.post(() -> {
            // Clear overlay
            if (anchorOverlay != null) {
                anchorOverlay.setAnchors(null);
            }
            // Reset status
            if (anchorStatusText != null) {
                anchorStatusText.setText("Scanning for anchors…");
            }
            if (anchorStatusIcon != null) {
                anchorStatusIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.yellow),
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }

            // Show / cycle floating hint
            showFloatingHint(FLOATING_HINTS[currentHintIndex]);
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  FLOATING HINT HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Show the floating hint pill with a fade-in + crossfade animation.
     */
    private void showFloatingHint(String message) {
        if (floatingHintText == null) return;

        String current = floatingHintText.getText().toString();

        if (floatingHintText.getVisibility() != View.VISIBLE) {
            // First appearance — fade in
            floatingHintText.setText(message);
            floatingHintText.setAlpha(0f);
            floatingHintText.setVisibility(View.VISIBLE);
            floatingHintText.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .start();
        } else if (!current.equals(message)) {
            // Crossfade to new hint
            floatingHintText.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            floatingHintText.setText(message);
                            floatingHintText.animate()
                                    .alpha(1f)
                                    .setDuration(200)
                                    .setListener(null)
                                    .start();
                        }
                    })
                    .start();
        }
    }

    /**
     * Hide the floating hint pill with a fade-out animation.
     */
    private void hideFloatingHint() {
        if (floatingHintText == null) return;
        if (floatingHintText.getVisibility() == View.VISIBLE && floatingHintText.getAlpha() > 0) {
            floatingHintText.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            floatingHintText.setVisibility(View.GONE);
                            floatingHintText.animate().setListener(null);
                        }
                    })
                    .start();
        }
    }

    // ─────────────────────────────────────────────────────────────
    private void toggleFlash() {
        isTorchOn = !isTorchOn;
        if (cameraControl != null) {
            cameraControl.enableTorch(isTorchOn);
        }
        updateFlashButton();
    }

    private void updateFlashButton() {
        if (iconFlash == null) return;
        if (isTorchOn) {
            iconFlash.setImageResource(R.drawable.ic_flash);
            iconFlash.setAlpha(1.0f);
            iconFlash.setColorFilter(
                    ContextCompat.getColor(this, R.color.primary_blue),
                    android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            iconFlash.setImageResource(R.drawable.ic_flash);
            iconFlash.setAlpha(0.4f);
            iconFlash.clearColorFilter();
        }
    }

    // ─────────────────────────────────────────────────────────────
    private void takePhoto() {
        Log.d(TAG, "takePhoto called");
        if (imageCapture == null) {
            Log.e(TAG, "imageCapture is null");
            return;
        }

        File photoFile = new File(getExternalFilesDir(null), "omr_capture.jpg");

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        runOnUiThread(() -> {
                            Intent intent = new Intent(CameraActivity.this, com.example.omrscanner.ui.ResultActivity.class);
                            intent.putExtra(PreviewActivity.IMAGE_PATH, photoFile.getAbsolutePath());
                            intent.putExtra(PreviewActivity.IMAGE_SOURCE, PreviewActivity.SOURCE_CAMERA);
                            intent.putExtra(EXTRA_FIXED_MOUNT_MODE, fixedMountMode);
                            if (selectedSheetType != null)
                                intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
                            if (classId != null)
                                intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
                            if (activityId != null)
                                intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
                            startActivity(intent);
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed", exception);
                        runOnUiThread(() -> {
                            Toast.makeText(CameraActivity.this,
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            // Reset auto-capture if it fails
                            autoCaptureTriggered = false;
                            consecutiveDetections = 0;
                            applyFixedMountZoom(true);
                        });
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        // Reset auto-capture state when returning to camera
        autoCaptureTriggered = false;
        consecutiveDetections = 0;
        currentHintIndex = 0;
        lastHintChangeTime = 0;
        smoothedAnchors = null;
        missingFramesCount = 0;
        fixedMountMissCounter = 0;
        fixedMountZoomIndex = 0;
        lastZoomChangeTime = 0L;
        applyFixedMountZoom(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Turn off torch when leaving the activity
        if (isTorchOn && cameraControl != null) {
            isTorchOn = false;
            cameraControl.enableTorch(false);
            updateFlashButton();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}