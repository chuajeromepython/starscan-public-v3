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
    private FrameLayout btnFlash;
    private FrameLayout btnTimer;
    private FrameLayout btnGrid;
    private FrameLayout btnFlipCamera;
    private FrameLayout btnCapture;
    private FrameLayout btnZoom1x;
    private FrameLayout btnZoom2x;
    private FrameLayout btnZoom5x;
    private FrameLayout btnCameraBack;
    private FrameLayout btnGallery;
    private ImageView   iconGallery;

    private ImageView   iconFlash;
    private FrameLayout gridOverlay;

    private SeekBar      zoomSeekBar;
    private TextView     zoomValueLabel;
    private LinearLayout zoomSliderBar;

    private View flashOverlay;

    // ── Camera state ──────────────────────────────────────────────
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private float currentZoomRatio = 1.0f;
    private float maxZoomRatio = 1.0f;
    private float minZoomRatio = 1.0f;

    // ── Intent extras ─────────────────────────────────────────────
    private String selectedSheetType = null;
    private String classId = null;
    private String activityId = null;

    private ScaleGestureDetector scaleGestureDetector;

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

            scaleGestureDetector = new ScaleGestureDetector(this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
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

    // ─────────────────────────────────────────────────────────────
    private void initializeViews() {
        previewView    = findViewById(R.id.previewView);
        btnCameraBack  = findViewById(R.id.btnCameraBack);
        btnGallery     = findViewById(R.id.btnGallery);
        iconGallery    = findViewById(R.id.iconGallery);
        flashOverlay   = findViewById(R.id.flashOverlay);

        btnFlash    = findViewById(R.id.btnFlash);
        btnTimer    = findViewById(R.id.btnTimer);
        btnGrid     = findViewById(R.id.btnGrid);
        iconFlash   = findViewById(R.id.iconFlash);
        gridOverlay = findViewById(R.id.gridOverlay);

        zoomSliderBar  = findViewById(R.id.zoomSliderBar);
        zoomSeekBar    = findViewById(R.id.zoomSeekBar);
        zoomValueLabel = findViewById(R.id.zoomValueLabel);

        btnCapture    = findViewById(R.id.btnCapture);
        btnZoom1x     = findViewById(R.id.btnZoom1x);
        btnZoom2x     = findViewById(R.id.btnZoom2x);
        btnZoom5x     = findViewById(R.id.btnZoom5x);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);

        if (previewView   == null) Log.e(TAG, "previewView is null!");
        if (btnCapture    == null) Log.e(TAG, "btnCapture is null!");
        if (btnFlash      == null) Log.e(TAG, "btnFlash is null!");
        if (btnFlipCamera == null) Log.e(TAG, "btnFlipCamera is null!");
        if (zoomSeekBar   == null) Log.e(TAG, "zoomSeekBar is null!");
    }

    // ─────────────────────────────────────────────────────────────
    private void setupListeners() {
        btnCapture.setOnClickListener(v -> takePhoto());
        btnFlash.setOnClickListener(v -> toggleFlash());

        // FIXED: flipCamera() now rebinds the same Activity instead of calling
        //        startCamera() which previously pushed extra entries onto the back stack.
        btnFlipCamera.setOnClickListener(v -> flipCamera());

        if (btnCameraBack != null) btnCameraBack.setOnClickListener(v -> finish());

        // Gallery button — opens the device photo picker so the user can review shots
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivity(intent);
            });
        }

        if (btnTimer != null) {
            btnTimer.setOnClickListener(v ->
                    Toast.makeText(this, "Timer feature coming soon", Toast.LENGTH_SHORT).show());
        }

        if (btnGrid != null) {
            btnGrid.setOnClickListener(v -> {
                if (gridOverlay != null) {
                    boolean isVisible = gridOverlay.getVisibility() == View.VISIBLE;
                    gridOverlay.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                }
            });
        }

        btnZoom1x.setOnClickListener(v -> setZoomRatio(1.0f));
        btnZoom2x.setOnClickListener(v -> setZoomRatio(2.0f));
        btnZoom5x.setOnClickListener(v -> setZoomRatio(5.0f));

        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && camera != null) {
                    float ratio = minZoomRatio + (maxZoomRatio - minZoomRatio) * (progress / 100.0f);
                    setZoomRatio(ratio);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                if (zoomSliderBar != null) zoomSliderBar.setVisibility(View.VISIBLE);
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) { /* keep visible */ }
        });
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

        setupZoom();
        updateFlashButton();

        Log.d(TAG, "Camera bound successfully, facing=" + cameraFacing);
    }

    // ─────────────────────────────────────────────────────────────
    private void setupZoom() {
        if (cameraInfo != null) {
            cameraInfo.getZoomState().observe(this, zoomState -> {
                if (zoomState != null) {
                    minZoomRatio = zoomState.getMinZoomRatio();
                    maxZoomRatio = zoomState.getMaxZoomRatio();
                    currentZoomRatio = zoomState.getZoomRatio();
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
        if (zoomValueLabel != null) {
            zoomValueLabel.setText(String.format("%.1fx", currentZoomRatio));
        }
        if (maxZoomRatio - minZoomRatio > 0) {
            int progress = (int) ((currentZoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio) * 100);
            zoomSeekBar.setProgress(progress);
        }
        updateZoomButtonStates();
    }

    private void updateZoomButtonStates() {
        updateZoomPill(btnZoom1x, Math.abs(currentZoomRatio - 1.0f) < 0.1f);
        updateZoomPill(btnZoom2x, Math.abs(currentZoomRatio - 2.0f) < 0.1f);
        updateZoomPill(btnZoom5x, Math.abs(currentZoomRatio - 5.0f) < 0.1f);
    }

    private void updateZoomPill(FrameLayout pill, boolean active) {
        if (pill == null) return;
        pill.setBackgroundResource(active
                ? R.drawable.bg_zoom_pill_active
                : R.drawable.bg_zoom_pill_inactive);
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
    /**
     * FIXED: No longer calls startCamera() (which fetches a new ProcessCameraProvider
     * and can add to the back stack). Instead, toggles the facing field and calls
     * bindCameraUseCases() directly — same Activity, same back-stack depth.
     */
    private void flipCamera() {
        cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        bindCameraUseCases();
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
                            // Show last captured photo as thumbnail on the gallery button
                            if (iconGallery != null) {
                                android.graphics.Bitmap thumb = android.graphics.BitmapFactory
                                        .decodeFile(photoFile.getAbsolutePath());
                                if (thumb != null) {
                                    iconGallery.setImageBitmap(thumb);
                                    iconGallery.clearColorFilter(); // remove white tint when showing photo
                                }
                            }

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