package com.example.omrscanner.camera;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.omr.AnchorDetector;
import com.example.omrscanner.omr.ArucoAnchorDetector;
import com.example.omrscanner.ui.PreviewActivity;
import com.example.omrscanner.ui.ResultActivity;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone "Flat Scan" mode: sheet lies flat on a table, phone is held in
 * portrait, camera pointing straight down at the sheet like taking a photo
 * of a document. Kept fully independent of CameraActivity and
 * BasicCameraActivity -- shares no code with Handheld / Tilt Agnostic /
 * Fixed Mount / Basic modes, and does not modify any of them.
 *
 * Why this mode exists: when the phone is held flat and pointed down,
 * Android's accelerometer-based OrientationEventListener reports
 * ORIENTATION_UNKNOWN, because gravity points through the screen rather
 * than along an in-plane edge. That makes the device-tilt-bucket approach
 * used by CameraActivity fundamentally unreliable for this posture. This
 * mode never asks the accelerometer anything -- it relies purely on ArUco
 * marker IDENTITY (rotation-invariant by construction) to resolve
 * orientation, exactly like Tilt Agnostic Mode's "trusted, no guessing"
 * path already does once ArUco succeeds -- just entered directly instead
 * of via a device-tilt bucket that doesn't apply here.
 *
 * Live detection UI (hint pill, status bar, green tracking/lock boxes) is
 * intentionally copied to match Tilt Agnostic Mode's look and feel exactly
 * -- same messages, same animations, same overlay behavior -- just wired
 * to this activity's own fields so nothing in CameraActivity is touched.
 *
 * Handoff: this activity does NOT duplicate ResultActivity's OMR pipeline.
 * It hands off using the exact same intent contract Tilt Agnostic Mode
 * already uses (EXTRA_TILT_AGNOSTIC_MODE = true), with
 * EXTRA_CAPTURE_ROTATION_BUCKET forced to 0 (no-op) since there is no
 * accelerometer bucket to apply here -- ArUco identity detection inside
 * ResultActivity resolves the real orientation from the raw capture
 * directly, which works at any physical rotation, including upside-down.
 * Zero changes were needed in ResultActivity or TemplateManager.
 */
public class FlatScanCameraActivity extends AppCompatActivity {
    private static final String TAG = "FlatScanCameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 2002;

    private static final int REQUIRED_CONSECUTIVE_DETECTIONS = 10;
    private static final int MAX_MISSING_FRAMES = 8;
    private static final float SMOOTHING_FACTOR = 0.25f;
    private static final long HINT_DISPLAY_DURATION_MS = 5000;

    // Copied verbatim from Tilt Agnostic Mode's FLOATING_HINTS.
    private static final String[] FLOATING_HINTS = {
            "\ud83d\udcc4  Point camera at the OMR sheet",
            "\ud83d\udd0d  Move closer to the paper",
            "\ud83d\udca1  Ensure good lighting, avoid shadows",
            "\ud83d\udccf  Keep the sheet flat and straight",
            "\ud83d\udd32  Make sure all 4 corner squares are visible",
            "\ud83d\udeab  Avoid glare on the paper",
            "\ud83d\udcd0  Hold phone parallel to the paper"
    };

    private androidx.camera.view.PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraControl cameraControl;

    private FrameLayout btnCapture;
    private FrameLayout btnCameraBack;
    private FrameLayout btnFlash;
    private ImageView iconFlash;
    private AnchorOverlayView anchorOverlay;
    private TextView floatingHintText;
    private TextView anchorStatusText;
    private ImageView anchorStatusIcon;

    private final int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isTorchOn = false;
    private boolean isCapturing = false;

    private int consecutiveDetections = 0;
    private int missingFramesCount = 0;
    private PointF[] smoothedAnchors = null;
    private int currentHintIndex = 0;
    private long lastHintChangeTime = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String selectedSheetType;
    private String classId;
    private String activityId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_camera);

            selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
            classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
            activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);

            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat insetsController =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.hide(WindowInsetsCompat.Type.systemBars());

            if (!OpenCVLoader.initDebug()) {
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
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        btnCameraBack = findViewById(R.id.btnCameraBack);
        btnCapture = findViewById(R.id.btnCapture);
        btnFlash = findViewById(R.id.btnFlash);
        iconFlash = findViewById(R.id.iconFlash);
        anchorOverlay = findViewById(R.id.anchorOverlay);
        floatingHintText = findViewById(R.id.floatingHintText);
        anchorStatusText = findViewById(R.id.anchorStatusText);
        anchorStatusIcon = findViewById(R.id.anchorStatusIcon);

        // No tilt gate, no rotation-lock icon, no guide squares -- none of
        // those concepts apply to a flat, top-down capture. The anchor
        // overlay, status bar, and floating hint ARE used here though,
        // same as Tilt Agnostic Mode.
        hide(R.id.btnRotationLock);
        hide(R.id.tiltWarningOverlay);
        hide(R.id.gridOverlay);

        if (btnCapture != null) {
            btnCapture.setEnabled(true);
            btnCapture.setAlpha(1.0f);
        }
    }

    private void hide(int viewId) {
        View v = findViewById(viewId);
        if (v != null) v.setVisibility(View.GONE);
    }

    private void setupListeners() {
        if (btnCapture != null) btnCapture.setOnClickListener(v -> takePhoto());
        if (btnCameraBack != null) btnCameraBack.setOnClickListener(v -> finish());
        if (btnFlash != null) btnFlash.setOnClickListener(v -> toggleFlash());
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
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

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
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

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        if (previewView.getWidth() == 0 || previewView.getHeight() == 0) {
            previewView.post(this::bindCameraUseCases);
            return;
        }

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build();

        cameraProvider.unbindAll();

        ViewPort viewPort = previewView.getViewPort();
        if (viewPort != null) {
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .setViewPort(viewPort)
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                    .addUseCase(imageAnalysis)
                    .build();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup);
        } else {
            Log.w(TAG, "PreviewView ViewPort was null -- binding without shared crop rect");
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
        }

        cameraControl = camera.getCameraControl();

        isTorchOn = false;
        cameraControl.enableTorch(false);
        updateFlashButton();

        Log.d(TAG, "Flat scan camera bound successfully, facing=" + cameraFacing);
    }

    private void toggleFlash() {
        isTorchOn = !isTorchOn;
        if (cameraControl != null) cameraControl.enableTorch(isTorchOn);
        updateFlashButton();
    }

    private void updateFlashButton() {
        if (iconFlash == null) return;
        iconFlash.setImageResource(R.drawable.ic_flash);
        iconFlash.setAlpha(isTorchOn ? 1.0f : 0.4f);
        if (isTorchOn) {
            iconFlash.setColorFilter(
                    ContextCompat.getColor(this, R.color.primary_blue),
                    android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            iconFlash.clearColorFilter();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  REAL-TIME ANCHOR DETECTION (ported from Tilt Agnostic Mode)
    // ─────────────────────────────────────────────────────────────

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        try {
            if (isCapturing) return;

            Mat gray = AnchorDetector.toGrayMat(imageProxy);
            if (gray == null) {
                onDetectionMiss();
                onAnchorsNotDetected();
                updateTrackedMarkerOverlay(null, 0, 0, 0);
                return;
            }

            int imageWidth = imageProxy.getWidth();
            int imageHeight = imageProxy.getHeight();
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

            Map<Integer, Point[]> quadsById;
            Point[] anchors;
            try {
                quadsById = ArucoAnchorDetector.detectMarkerQuads(gray);
                anchors = ArucoAnchorDetector.identityAnchorsFromQuads(quadsById);
            } finally {
                gray.release();
            }

            updateTrackedMarkerOverlay(quadsById, imageWidth, imageHeight, rotationDegrees);

            if (anchors != null) {
                onDetectionSuccess();
                PointF[] viewPoints = scaleAnchorsToView(anchors, imageWidth, imageHeight, rotationDegrees);
                onAnchorsDetected(viewPoints);

                if (consecutiveDetections >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                    consecutiveDetections = 0;
                    mainHandler.post(this::takePhoto);
                }
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

    private void onDetectionSuccess() {
        missingFramesCount = 0;
    }

    private void onDetectionMiss() {
        missingFramesCount++;
    }

    /**
     * Same EMA smoothing + green-box + status-bar behavior as Tilt
     * Agnostic Mode's onAnchorsDetected.
     */
    private void onAnchorsDetected(PointF[] viewPoints) {
        if (smoothedAnchors == null) {
            smoothedAnchors = new PointF[4];
            for (int i = 0; i < 4; i++) {
                smoothedAnchors[i] = new PointF(viewPoints[i].x, viewPoints[i].y);
            }
        } else {
            int viewW = anchorOverlay != null ? anchorOverlay.getWidth() : previewView.getWidth();
            int viewH = anchorOverlay != null ? anchorOverlay.getHeight() : previewView.getHeight();
            float diagonal = (float) Math.sqrt(viewW * viewW + viewH * viewH);
            float maxAllowedJump = diagonal * 0.20f;

            float maxJump = 0;
            for (int i = 0; i < 4; i++) {
                float dx = viewPoints[i].x - smoothedAnchors[i].x;
                float dy = viewPoints[i].y - smoothedAnchors[i].y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > maxJump) maxJump = dist;
            }

            if (maxJump > maxAllowedJump) {
                for (int i = 0; i < 4; i++) {
                    smoothedAnchors[i] = new PointF(viewPoints[i].x, viewPoints[i].y);
                }
            } else {
                for (int i = 0; i < 4; i++) {
                    smoothedAnchors[i].x = smoothedAnchors[i].x * (1.0f - SMOOTHING_FACTOR) + viewPoints[i].x * SMOOTHING_FACTOR;
                    smoothedAnchors[i].y = smoothedAnchors[i].y * (1.0f - SMOOTHING_FACTOR) + viewPoints[i].y * SMOOTHING_FACTOR;
                }
            }
        }

        consecutiveDetections++;
        lastHintChangeTime = 0;
        Log.d(TAG, "Anchors detected! Consecutive count: " + consecutiveDetections);

        final PointF[] finalAnchors = new PointF[4];
        for (int i = 0; i < 4; i++) {
            finalAnchors[i] = new PointF(smoothedAnchors[i].x, smoothedAnchors[i].y);
        }

        mainHandler.post(() -> {
            if (anchorOverlay != null) {
                anchorOverlay.setAnchors(finalAnchors);
            }
            hideFloatingHint();
            if (anchorStatusText != null) {
                anchorStatusText.setText(consecutiveDetections >= REQUIRED_CONSECUTIVE_DETECTIONS
                        ? "\u2713 Anchors detected! Capturing\u2026"
                        : "\u2713 Anchors detected");
            }
            if (anchorStatusIcon != null) {
                anchorStatusIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.green),
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }
        });
    }

    /**
     * Same missing-frame tolerance + hint-cycling behavior as Tilt
     * Agnostic Mode's onAnchorsNotDetected.
     */
    private void onAnchorsNotDetected() {
        if (missingFramesCount <= MAX_MISSING_FRAMES && smoothedAnchors != null) {
            final PointF[] finalAnchors = new PointF[4];
            for (int i = 0; i < 4; i++) {
                finalAnchors[i] = new PointF(smoothedAnchors[i].x, smoothedAnchors[i].y);
            }
            mainHandler.post(() -> {
                if (anchorOverlay != null) {
                    anchorOverlay.setAnchors(finalAnchors);
                }
                if (anchorStatusText != null) {
                    anchorStatusText.setText("Tracking anchors\u2026");
                }
                if (anchorStatusIcon != null) {
                    anchorStatusIcon.setColorFilter(
                            ContextCompat.getColor(this, R.color.green),
                            android.graphics.PorterDuff.Mode.SRC_IN);
                }
            });
            return;
        }

        smoothedAnchors = null;
        consecutiveDetections = 0;

        long now = System.currentTimeMillis();
        if (now - lastHintChangeTime >= HINT_DISPLAY_DURATION_MS) {
            currentHintIndex = (currentHintIndex + 1) % FLOATING_HINTS.length;
            lastHintChangeTime = now;
        }

        mainHandler.post(() -> {
            if (anchorOverlay != null) {
                anchorOverlay.setAnchors(null);
            }
            if (anchorStatusText != null) {
                anchorStatusText.setText("Scanning for anchors\u2026");
            }
            if (anchorStatusIcon != null) {
                anchorStatusIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.yellow),
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }
            showFloatingHint(FLOATING_HINTS[currentHintIndex]);
        });
    }

    /**
     * Converts the raw per-marker ArUco quads into green "searching" boxes,
     * one per marker currently visible -- identical behavior to Tilt
     * Agnostic Mode's updateTrackedMarkerOverlay.
     */
    private void updateTrackedMarkerOverlay(
            @Nullable Map<Integer, Point[]> quadsById,
            int imageWidth,
            int imageHeight,
            int rotationDegrees
    ) {
        List<AnchorOverlayView.TrackedMarker> markers = null;

        if (quadsById != null && !quadsById.isEmpty()) {
            markers = new ArrayList<>();
            for (Map.Entry<Integer, Point[]> entry : quadsById.entrySet()) {
                Point[] quad = entry.getValue();
                if (quad == null || quad.length != 4) continue;

                PointF[] viewQuad = new PointF[4];
                for (int i = 0; i < 4; i++) {
                    viewQuad[i] = transformPointToView(quad[i], imageWidth, imageHeight, rotationDegrees);
                }

                String label = ArucoAnchorDetector.labelForMarkerId(entry.getKey());
                if (label == null) {
                    label = "#" + entry.getKey();
                }
                markers.add(new AnchorOverlayView.TrackedMarker(viewQuad, label));
            }
        }

        List<AnchorOverlayView.TrackedMarker> finalMarkers = markers;
        mainHandler.post(() -> {
            if (anchorOverlay != null) {
                anchorOverlay.setTrackedMarkers(finalMarkers);
            }
        });
    }

    private PointF transformPointToView(
            Point point,
            int imageWidth,
            int imageHeight,
            int rotationDegrees
    ) {
        int viewWidth = anchorOverlay != null ? anchorOverlay.getWidth() : 0;
        int viewHeight = anchorOverlay != null ? anchorOverlay.getHeight() : 0;

        if (viewWidth == 0 || viewHeight == 0) {
            viewWidth = previewView.getWidth();
            viewHeight = previewView.getHeight();
        }
        if (viewWidth == 0 || viewHeight == 0) {
            return new PointF((float) point.x, (float) point.y);
        }

        boolean swapDimensions = rotationDegrees == 90 || rotationDegrees == 270;
        int rotatedWidth = swapDimensions ? imageHeight : imageWidth;
        int rotatedHeight = swapDimensions ? imageWidth : imageHeight;

        float scaleX = (float) viewWidth / rotatedWidth;
        float scaleY = (float) viewHeight / rotatedHeight;

        PointF rotated = rotatePointToDisplay(point, imageWidth, imageHeight, rotationDegrees);
        return new PointF(rotated.x * scaleX, rotated.y * scaleY);
    }

    private PointF[] scaleAnchorsToView(
            Point[] anchors,
            int imageWidth,
            int imageHeight,
            int rotationDegrees
    ) {
        PointF[] viewPoints = new PointF[4];

        int viewWidth = anchorOverlay != null ? anchorOverlay.getWidth() : 0;
        int viewHeight = anchorOverlay != null ? anchorOverlay.getHeight() : 0;

        if (viewWidth == 0 || viewHeight == 0) {
            viewWidth = previewView.getWidth();
            viewHeight = previewView.getHeight();
        }
        if (viewWidth == 0 || viewHeight == 0) {
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
            PointF rotatedPoint = rotatePointToDisplay(anchors[i], imageWidth, imageHeight, rotationDegrees);
            viewPoints[i] = new PointF(rotatedPoint.x * scaleX, rotatedPoint.y * scaleY);
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

    // ─────────────────────────────────────────────────────────────
    //  FLOATING HINT HELPERS (copied verbatim from Tilt Agnostic Mode)
    // ─────────────────────────────────────────────────────────────

    private void showFloatingHint(String message) {
        if (floatingHintText == null) return;

        String current = floatingHintText.getText().toString();

        if (floatingHintText.getVisibility() != View.VISIBLE) {
            floatingHintText.setText(message);
            floatingHintText.setAlpha(0f);
            floatingHintText.setVisibility(View.VISIBLE);
            floatingHintText.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .start();
        } else if (!current.equals(message)) {
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
    //  CAPTURE
    // ─────────────────────────────────────────────────────────────

    private void takePhoto() {
        if (imageCapture == null || isCapturing) return;
        isCapturing = true;
        if (btnCapture != null) btnCapture.setEnabled(false);

        File photoFile = new File(getExternalFilesDir(null), "flat_scan_capture.jpg");

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        cameraExecutor.execute(() -> verifyAndProceed(photoFile));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed", exception);
                        runOnUiThread(() -> {
                            isCapturing = false;
                            consecutiveDetections = 0;
                            if (btnCapture != null) btnCapture.setEnabled(true);
                            Toast.makeText(FlatScanCameraActivity.this,
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void verifyAndProceed(File photoFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        Point[] anchors = (bitmap != null)
                ? ArucoAnchorDetector.detectIdentityAnchors(bitmap)
                : null;
        if (bitmap != null) bitmap.recycle();

        if (anchors == null) {
            runOnUiThread(() -> {
                isCapturing = false;
                consecutiveDetections = 0;
                if (btnCapture != null) btnCapture.setEnabled(true);
                Toast.makeText(FlatScanCameraActivity.this,
                        "\u26A0 Couldn't find all 4 corner markers -- move closer or improve lighting, then retake",
                        Toast.LENGTH_LONG).show();
            });
            return;
        }

        Log.d(TAG, "FlatScan: all 4 identity markers confirmed on full-res capture, handing off to ResultActivity");

        runOnUiThread(() -> {
            Intent intent = new Intent(FlatScanCameraActivity.this, ResultActivity.class);
            intent.putExtra(PreviewActivity.IMAGE_PATH, photoFile.getAbsolutePath());
            intent.putExtra(PreviewActivity.IMAGE_SOURCE, PreviewActivity.SOURCE_CAMERA);
            intent.putExtra(CameraActivity.EXTRA_FIXED_MOUNT_MODE, false);
            intent.putExtra(CameraActivity.EXTRA_TILT_AGNOSTIC_MODE, true);
            intent.putExtra(CameraActivity.EXTRA_GUIDE_CORNER_ROTATION_STEPS, 0);
            intent.putExtra(CameraActivity.EXTRA_CAPTURE_ROTATION_BUCKET, 0);
            if (selectedSheetType != null)
                intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
            if (classId != null)
                intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
            if (activityId != null)
                intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTorchOn && cameraControl != null) {
            isTorchOn = false;
            cameraControl.enableTorch(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}