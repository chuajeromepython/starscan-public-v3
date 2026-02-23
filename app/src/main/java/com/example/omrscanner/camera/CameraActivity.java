package com.example.omrscanner.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.ui.PreviewActivity;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CameraActivity";

    // ── Camera core ───────────────────────────────────────────────
    private PreviewView previewView;
    private ImageCapture imageCapture;
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

    // ── Camera state ──────────────────────────────────────────────
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;

    // ── Intent extras ─────────────────────────────────────────────
    private String selectedSheetType = null;
    private String classId = null;
    private String activityId = null;

    // ── Camera provider held at instance level to avoid re-binding ─
    // FIXED: Holding a reference to the provider lets flipCamera() rebind without
    //        launching a new Activity — so the back stack never grows deeper than 1.
    private ProcessCameraProvider cameraProvider;

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
            Log.d(TAG, "Received sheet type: " + selectedSheetType + ", classId: " + classId);

            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

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
        previewView    = findViewById(R.id.previewView);
        btnCameraBack  = findViewById(R.id.btnCameraBack);
        flashOverlay   = findViewById(R.id.flashOverlay);
        gridOverlay    = findViewById(R.id.gridOverlay);
        btnCapture     = findViewById(R.id.btnCapture);
        btnFlash       = findViewById(R.id.btnFlash);
        iconFlash      = findViewById(R.id.iconFlash);

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
                // FIXED: store provider at instance level so flipCamera() can reuse it
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
     * FIXED: Extracted binding logic so flipCamera() can call this directly
     * without going through the ListenableFuture again, which was the root
     * cause of the extra back-stack entries.
     */
    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build();

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

        cameraControl = camera.getCameraControl();
        cameraInfo    = camera.getCameraInfo();

        updateFlashButton();

        Log.d(TAG, "Camera bound successfully, facing=" + cameraFacing);
    }

    // ─────────────────────────────────────────────────────────────
    private void toggleFlash() {
        if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            flashMode = ImageCapture.FLASH_MODE_ON;
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            flashMode = ImageCapture.FLASH_MODE_AUTO;
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF;
        }
        if (imageCapture != null) imageCapture.setFlashMode(flashMode);
        updateFlashButton();
    }

    private void updateFlashButton() {
        if (iconFlash == null) return;
        if (flashMode == ImageCapture.FLASH_MODE_ON) {
            iconFlash.setImageResource(R.drawable.ic_flash);
            iconFlash.setAlpha(1.0f);
            iconFlash.setColorFilter(
                    ContextCompat.getColor(this, R.color.primary_blue),
                    android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            iconFlash.setImageResource(R.drawable.ic_flash);
            iconFlash.setAlpha(0.7f);
            iconFlash.clearColorFilter();
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
                            Intent intent = new Intent(CameraActivity.this, PreviewActivity.class);
                            intent.putExtra(PreviewActivity.IMAGE_PATH, photoFile.getAbsolutePath());
                            intent.putExtra(PreviewActivity.IMAGE_SOURCE, PreviewActivity.SOURCE_CAMERA);
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
                        runOnUiThread(() ->
                                Toast.makeText(CameraActivity.this,
                                        "Capture failed: " + exception.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}