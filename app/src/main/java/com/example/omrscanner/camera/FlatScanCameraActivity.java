package com.example.omrscanner.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
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

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.Map;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone "Flat Scan" mode: sheet lies flat on a table, phone is held in
 * portrait, camera pointing straight down at the sheet like taking a photo
 * of a document. Kept fully independent of CameraActivity and
 * BasicCameraActivity -- shares no code with Handheld / Tilt Agnostic /
 * Fixed Mount / Basic modes, and does not modify any of them.
 *
 * Why this mode exists (see conversation history): when the phone is held
 * flat and pointed down, Android's accelerometer-based
 * OrientationEventListener reports ORIENTATION_UNKNOWN, because gravity
 * points through the screen rather than along an in-plane edge. That makes
 * the device-tilt-bucket approach used by CameraActivity fundamentally
 * unreliable for this specific posture -- there is no meaningful "tilt" to
 * read. This mode never asks the accelerometer anything. It relies purely
 * on ArUco marker IDENTITY (rotation-invariant by construction) to resolve
 * orientation, exactly like Tilt Agnostic Mode's "trusted, no guessing"
 * path already does once ArUco succeeds -- just entered directly instead
 * of via a device-tilt bucket that doesn't apply here.
 *
 * Handoff: this activity does NOT duplicate ResultActivity's OMR pipeline.
 * It hands off to ResultActivity using the exact same intent contract
 * Tilt Agnostic Mode already uses (EXTRA_TILT_AGNOSTIC_MODE = true), with
 * EXTRA_CAPTURE_ROTATION_BUCKET forced to 0 (no-op) since there is no
 * accelerometer bucket to apply here -- ArUco identity detection inside
 * ResultActivity resolves the real orientation from the raw capture
 * directly, which works at any physical rotation, including upside-down.
 * This means zero changes were needed in ResultActivity or TemplateManager
 * to support this mode.
 */
public class FlatScanCameraActivity extends AppCompatActivity {
    private static final String TAG = "FlatScanCameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 2002;

    private static final int REQUIRED_CONSECUTIVE_DETECTIONS = 10;

    private androidx.camera.view.PreviewView previewView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraControl cameraControl;
    private int consecutiveDetections = 0;
    private android.widget.TextView hintText;

    private FrameLayout btnCapture;
    private FrameLayout btnCameraBack;
    private FrameLayout btnFlash;
    private ImageView iconFlash;

    private final int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isTorchOn = false;
    private boolean isCapturing = false;

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

            if (!org.opencv.android.OpenCVLoader.initDebug()) {
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

        // No tilt gate, no rotation-lock icon, no guide squares -- none of
        // those concepts apply to a flat, top-down capture.
        hide(R.id.anchorOverlay);
        hide(R.id.anchorStatusBar);
        hide(R.id.btnRotationLock);
        hide(R.id.tiltWarningOverlay);
        hide(R.id.gridOverlay);

        hintText = findViewById(R.id.floatingHintText);
        if (hintText != null) {
            hintText.setVisibility(View.VISIBLE);
            hintText.setText("Lay the sheet flat on the table and frame all 4 corner markers");
        }

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

        isTorchOn = true;
        cameraControl.enableTorch(true);
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

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        try {
            if (isCapturing) return;

            Mat gray = AnchorDetector.toGrayMat(imageProxy);
            if (gray == null) {
                consecutiveDetections = 0;
                return;
            }

            Map<Integer, Point[]> quadsById;
            Point[] anchors;
            try {
                quadsById = ArucoAnchorDetector.detectMarkerQuads(gray);
                anchors = ArucoAnchorDetector.identityAnchorsFromQuads(quadsById);
            } finally {
                gray.release();
            }

            if (anchors != null) {
                consecutiveDetections++;
                runOnUiThread(() -> {
                    if (hintText != null) {
                        hintText.setText(consecutiveDetections >= REQUIRED_CONSECUTIVE_DETECTIONS
                                ? "Capturing..." : "All 4 markers found -- hold steady");
                    }
                });
                if (consecutiveDetections >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                    consecutiveDetections = 0;
                    runOnUiThread(this::takePhoto);
                }
            } else {
                consecutiveDetections = 0;
                int found = (quadsById != null) ? quadsById.size() : 0;
                runOnUiThread(() -> {
                    if (hintText != null) {
                        hintText.setText("Found " + found + "/4 corner markers -- adjust framing");
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in frame analysis", e);
            consecutiveDetections = 0;
        } finally {
            imageProxy.close();
        }
    }

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
                        // Quick sanity check on a background thread: confirm all 4
                        // identity markers are actually readable on the full-res
                        // capture before handing off, so a bad shot is caught here
                        // with a clear "retake" message instead of surfacing as a
                        // generic failure later in ResultActivity.
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
            // Reuses Tilt Agnostic Mode's existing, already-validated
            // "trusted, no guessing" ArUco identity path in ResultActivity.
            // No changes to ResultActivity were needed for this mode.
            intent.putExtra(CameraActivity.EXTRA_TILT_AGNOSTIC_MODE, true);
            intent.putExtra(CameraActivity.EXTRA_GUIDE_CORNER_ROTATION_STEPS, 0);
            // 0 = no-op in rotateToNormalReadingOrientation(). Deliberate:
            // there is no accelerometer bucket to apply when the phone is
            // flat (OrientationEventListener reports ORIENTATION_UNKNOWN in
            // this posture), so we skip it entirely rather than trust a
            // stale or arbitrary value. ArUco identity detection inside
            // ResultActivity resolves the real orientation directly from
            // the raw capture instead.
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