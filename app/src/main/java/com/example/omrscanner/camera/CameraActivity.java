package com.example.omrscanner.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CameraActivity";

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;

    // UI Elements
    private ImageButton btnFlash, btnTimer, btnRatio, btnGallery, btnFlipCamera;
    private MaterialButton btnCapture, btnZoom1x, btnZoom2x, btnZoom5x;
    private SeekBar zoomSeekBar;
    private TextView tvZoomValue;
    private MaterialCardView zoomContainer; // Changed from LinearLayout to MaterialCardView

    // Camera state
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private float currentZoomRatio = 1.0f;
    private float maxZoomRatio = 1.0f;
    private float minZoomRatio = 1.0f;

    // Sheet type passed from DashboardActivity
    private String selectedSheetType = null;

    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate started");

        try {
            setContentView(R.layout.activity_camera);
            Log.d(TAG, "Layout inflated successfully");

            // Get sheet type from intent
            selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
            Log.d(TAG, "Received sheet type: " + selectedSheetType);

            // Set status bar color to blue
            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));
            Log.d(TAG, "Status bar color set");

            initializeViews();
            Log.d(TAG, "Views initialized");

            setupListeners();
            Log.d(TAG, "Listeners set up");

            cameraExecutor = Executors.newSingleThreadExecutor();
            Log.d(TAG, "Camera executor created");

            if (hasCameraPermission()) {
                Log.d(TAG, "Camera permission granted, starting camera");
                startCamera();
            } else {
                Log.d(TAG, "Camera permission not granted, requesting");
                requestCameraPermission();
            }

            // Setup pinch to zoom
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    if (camera != null) {
                        float scale = detector.getScaleFactor();
                        float newZoom = currentZoomRatio * scale;
                        newZoom = Math.max(minZoomRatio, Math.min(newZoom, maxZoomRatio));
                        setZoomRatio(newZoom);
                    }
                    return true;
                }
            });

            previewView.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            });

            Log.d(TAG, "onCreate completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        try {
            previewView = findViewById(R.id.previewView);
            btnCapture = findViewById(R.id.btnCapture);
            btnFlash = findViewById(R.id.btnFlash);
            btnTimer = findViewById(R.id.btnTimer);
            btnRatio = findViewById(R.id.btnRatio);
            btnGallery = findViewById(R.id.btnGallery);
            btnFlipCamera = findViewById(R.id.btnFlipCamera);
            btnZoom1x = findViewById(R.id.btnZoom1x);
            btnZoom2x = findViewById(R.id.btnZoom2x);
            btnZoom5x = findViewById(R.id.btnZoom5x);
            zoomSeekBar = findViewById(R.id.zoomSeekBar);
            tvZoomValue = findViewById(R.id.tvZoomValue);
            zoomContainer = findViewById(R.id.zoomContainer);

            // Check if any view is null
            if (previewView == null) Log.e(TAG, "previewView is null!");
            if (btnCapture == null) Log.e(TAG, "btnCapture is null!");
            if (btnFlash == null) Log.e(TAG, "btnFlash is null!");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e;
        }
    }

    private void setupListeners() {
        try {
            btnCapture.setOnClickListener(v -> takePhoto());

            btnFlash.setOnClickListener(v -> toggleFlash());

            btnFlipCamera.setOnClickListener(v -> flipCamera());

            // Changed from gallery to back button
            btnGallery.setOnClickListener(v -> {
                // Go back to previous activity
                finish();
            });

            btnTimer.setOnClickListener(v -> {
                Toast.makeText(this, "Timer feature coming soon", Toast.LENGTH_SHORT).show();
            });

            btnRatio.setOnClickListener(v -> {
                Toast.makeText(this, "Aspect ratio feature coming soon", Toast.LENGTH_SHORT).show();
            });

            // Zoom buttons
            btnZoom1x.setOnClickListener(v -> setZoomRatio(1.0f));
            btnZoom2x.setOnClickListener(v -> setZoomRatio(2.0f));
            btnZoom5x.setOnClickListener(v -> setZoomRatio(5.0f));

            // Zoom seekbar
            zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && camera != null) {
                        float ratio = minZoomRatio + (maxZoomRatio - minZoomRatio) * (progress / 100.0f);
                        setZoomRatio(ratio);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    zoomContainer.setVisibility(View.VISIBLE);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Keep visible
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners", e);
            throw e;
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted");
            startCamera();
        } else {
            Log.e(TAG, "Camera permission denied");
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startCamera() {
        Log.d(TAG, "startCamera called");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                Log.d(TAG, "Camera provider listener started");
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

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
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                );

                cameraControl = camera.getCameraControl();
                cameraInfo = camera.getCameraInfo();

                // Setup zoom capabilities
                setupZoom();

                // Update flash button state
                updateFlashButton();

                Log.d(TAG, "Camera started successfully");

            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Camera failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupZoom() {
        if (cameraInfo != null) {
            cameraInfo.getZoomState().observe(this, zoomState -> {
                if (zoomState != null) {
                    minZoomRatio = zoomState.getMinZoomRatio();
                    maxZoomRatio = zoomState.getMaxZoomRatio();
                    currentZoomRatio = zoomState.getZoomRatio();

                    // Update UI
                    updateZoomUI();
                }
            });
        }
    }

    private void setZoomRatio(float ratio) {
        ratio = Math.max(minZoomRatio, Math.min(ratio, maxZoomRatio));
        if (cameraControl != null) {
            cameraControl.setZoomRatio(ratio);
            currentZoomRatio = ratio;
            updateZoomUI();
        }
    }

    private void updateZoomUI() {
        tvZoomValue.setText(String.format("%.1fx", currentZoomRatio));

        // Update seekbar
        int progress = (int) ((currentZoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio) * 100);
        zoomSeekBar.setProgress(progress);

        // Update zoom buttons
        updateZoomButtonStates();
    }

    private void updateZoomButtonStates() {
        btnZoom1x.setBackgroundTintList(ContextCompat.getColorStateList(this,
                Math.abs(currentZoomRatio - 1.0f) < 0.1f ? R.color.primary_blue : android.R.color.transparent));
        btnZoom2x.setBackgroundTintList(ContextCompat.getColorStateList(this,
                Math.abs(currentZoomRatio - 2.0f) < 0.1f ? R.color.primary_blue : android.R.color.transparent));
        btnZoom5x.setBackgroundTintList(ContextCompat.getColorStateList(this,
                Math.abs(currentZoomRatio - 5.0f) < 0.1f ? R.color.primary_blue : android.R.color.transparent));
    }

    private void toggleFlash() {
        if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            flashMode = ImageCapture.FLASH_MODE_ON;
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            flashMode = ImageCapture.FLASH_MODE_AUTO;
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF;
        }

        if (imageCapture != null) {
            imageCapture.setFlashMode(flashMode);
        }

        updateFlashButton();
    }

    private void updateFlashButton() {
        // Update flash button icon and alpha based on state
        if (flashMode == ImageCapture.FLASH_MODE_ON) {
            btnFlash.setImageResource(R.drawable.ic_flashlight);
            btnFlash.setAlpha(1.0f);
        } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            btnFlash.setImageResource(R.drawable.ic_flashlight);
            btnFlash.setAlpha(0.7f);
        } else {
            btnFlash.setImageResource(R.drawable.ic_flashlight_off);
            btnFlash.setAlpha(1.0f);
        }
    }

    private void flipCamera() {
        if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
            cameraFacing = CameraSelector.LENS_FACING_FRONT;
        } else {
            cameraFacing = CameraSelector.LENS_FACING_BACK;
        }

        startCamera();
    }

    private void takePhoto() {
        Log.d(TAG, "takePhoto called");
        if (imageCapture == null) {
            Log.e(TAG, "imageCapture is null");
            return;
        }

        File photoFile = new File(
                getExternalFilesDir(null),
                "omr_capture.jpg"
        );

        Log.d(TAG, "Photo file path: " + photoFile.getAbsolutePath());

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                options,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults output) {
                        Log.d(TAG, "Photo saved successfully");

                        runOnUiThread(() -> {
                            Intent intent = new Intent(
                                    CameraActivity.this,
                                    PreviewActivity.class
                            );
                            intent.putExtra(
                                    PreviewActivity.IMAGE_PATH,
                                    photoFile.getAbsolutePath()
                            );
                            intent.putExtra(
                                    PreviewActivity.IMAGE_SOURCE,
                                    PreviewActivity.SOURCE_CAMERA
                            );
                            // Pass selected sheet type to PreviewActivity
                            if (selectedSheetType != null) {
                                intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
                            }
                            startActivity(intent);
                            // Don't call finish() here - keep CameraActivity in back stack
                        });
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed", exception);
                        runOnUiThread(() ->
                                Toast.makeText(CameraActivity.this,
                                        "Capture failed: " + exception.getMessage(),
                                        Toast.LENGTH_SHORT).show()
                        );
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}