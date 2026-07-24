package com.example.omrscanner.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
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
import com.example.omrscanner.ui.BasicPreviewActivity;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone "Basic Camera" mode: plain viewfinder + manual shutter, no
 * anchor detection, no auto-capture, no OMR pipeline. Kept fully
 * independent of CameraActivity -- reuses activity_camera.xml for a
 * consistent look, but shares no code with Handheld / Tilt Agnostic mode.
 *
 * Uses the phone's accelerometer (via OrientationEventListener) to record
 * which way the phone was held at capture time, so BasicPreviewActivity
 * can show the photo right-side-up.
 */
public class BasicCameraActivity extends AppCompatActivity {
    private static final String TAG = "BasicCameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 2001;

    public static final String EXTRA_CAPTURE_ROTATION_BUCKET = "basic_capture_rotation_bucket";

    private androidx.camera.view.PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraControl cameraControl;

    private FrameLayout btnCapture;
    private FrameLayout btnCameraBack;
    private FrameLayout btnFlash;
    private ImageView iconFlash;

    private final int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isTorchOn = false;
    private int currentIconRotation = 0;
    private OrientationEventListener orientationEventListener;

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

        hide(R.id.anchorOverlay);
        hide(R.id.anchorStatusBar);
        hide(R.id.floatingHintText);
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
                    .build();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup);
        } else {
            Log.w(TAG, "PreviewView ViewPort was null — binding without shared crop rect");
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        }

        cameraControl = camera.getCameraControl();

        isTorchOn = true;
        cameraControl.enableTorch(true);
        updateFlashButton();

        Log.d(TAG, "Basic camera bound successfully, facing=" + cameraFacing);
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

    private void setupOrientationListener() {
        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
                int rotation;
                if (orientation >= 315 || orientation < 45) {
                    rotation = 0;
                } else if (orientation >= 45 && orientation < 135) {
                    rotation = -90;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = 180;
                } else {
                    rotation = 90;
                }
                if (rotation != currentIconRotation) {
                    Log.d(TAG, "ORIENTATION_BUCKET: raw=" + orientation + " -> bucket=" + rotation);
                }
                currentIconRotation = rotation;
            }
        };
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null), "basic_capture.jpg");
        final int captureRotationBucket = currentIconRotation;
        Log.d(TAG, "CAPTURE: bucket=" + captureRotationBucket);

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        runOnUiThread(() -> {
                            Intent intent = new Intent(BasicCameraActivity.this,
                                    BasicPreviewActivity.class);
                            intent.putExtra(BasicPreviewActivity.EXTRA_IMAGE_PATH,
                                    photoFile.getAbsolutePath());
                            intent.putExtra(EXTRA_CAPTURE_ROTATION_BUCKET, captureRotationBucket);
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
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed", exception);
                        runOnUiThread(() -> Toast.makeText(BasicCameraActivity.this,
                                "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show());
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (orientationEventListener == null) {
            setupOrientationListener();
        } else {
            orientationEventListener.enable();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTorchOn && cameraControl != null) {
            isTorchOn = false;
            cameraControl.enableTorch(false);
        }
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}