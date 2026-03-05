package com.example.omrscanner.camera;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.omr.AnchorDetector;
import com.example.omrscanner.ui.PreviewActivity;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CameraActivity";

    // Number of consecutive frames with anchors before auto-capture
    private static final int STABLE_FRAME_THRESHOLD = 5;

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
    private LinearLayout orientationGuideOverlay;

    // ── Orientation detection ─────────────────────────────────────
    private OrientationEventListener orientationListener;
    private boolean isInLandscape = false;

    // ── Camera state ──────────────────────────────────────────────
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isTorchOn = false;

    // ── Auto-capture state ────────────────────────────────────────
    private int consecutiveDetections = 0;
    private int currentHintIndex = 0;
    private long lastHintChangeTime = 0;
    private boolean autoCaptureTriggered = false;
    private boolean isAnalyzing = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // ── Anchor Smoothing State ────────────────────────────────────
    private PointF[] smoothedAnchors = null;
    private int missingFramesCount = 0;
    private static final int MAX_MISSING_FRAMES = 8;
    private static final float SMOOTHING_FACTOR = 0.25f;

    // ── Frame skipping ───────────────────────────────────────────
    private int frameSkipCounter = 0;
    private static final int FRAME_SKIP_COUNT = 2; // analyze every 3rd frame

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
            Log.d(TAG, "Received sheet type: " + selectedSheetType + ", classId: " + classId);

            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

            // Initialize OpenCV
            openCVReady = OpenCVLoader.initDebug();
            if (!openCVReady) {
                Log.e(TAG, "OpenCV initialization failed!");
            }

            initializeViews();
            setupListeners();
            setupOrientationListener();

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
        orientationGuideOverlay = findViewById(R.id.orientationGuideOverlay);

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

        updateFlashButton();

        Log.d(TAG, "Camera bound successfully with ImageAnalysis, facing=" + cameraFacing);
    }

    // ─────────────────────────────────────────────────────────────
    //  REAL-TIME ANCHOR DETECTION
    // ─────────────────────────────────────────────────────────────

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        // Skip if OpenCV not ready, already auto-captured, or orientation overlay showing
        if (!openCVReady || autoCaptureTriggered || !isInLandscape) {
            imageProxy.close();
            return;
        }

        // ── Frame skipping: only analyze every (FRAME_SKIP_COUNT + 1)th frame ──
        frameSkipCounter++;
        if (frameSkipCounter <= FRAME_SKIP_COUNT) {
            imageProxy.close();
            return;
        }
        frameSkipCounter = 0;

        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                imageProxy.close();
                onAnchorsNotDetected();
                return;
            }

            // Run anchor detection
            Point[] anchors = AnchorDetector.detectAnchors(bitmap);

            if (anchors != null && anchors.length == 4) {
                // Scale anchor points from bitmap coordinates to overlay view coordinates
                PointF[] viewPoints = scaleAnchorsToView(anchors, bitmap.getWidth(), bitmap.getHeight());
                onAnchorsDetected(viewPoints);
            } else {
                onAnchorsNotDetected();
            }

            bitmap.recycle();

        } catch (Exception e) {
            Log.e(TAG, "Error in frame analysis", e);
            onAnchorsNotDetected();
        } finally {
            imageProxy.close();
        }
    }

    /**
     * Convert CameraX ImageProxy to a Bitmap.
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            if (imageProxy.getImage() == null) return null;

            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            if (planes.length < 1) return null;

            // Get YUV data and convert to JPEG then Bitmap
            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            // Use NV21 format for YuvImage
            byte[] nv21 = yuv420ToNv21(imageProxy);
            if (nv21 == null) return null;

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80, out);
            byte[] jpegBytes = out.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

            // Apply rotation
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation != 0 && bitmap != null) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                return rotated;
            }

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    /**
     * Convert YUV_420_888 ImageProxy to NV21 byte array.
     */
    private byte[] yuv420ToNv21(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
            ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
            ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

            ByteBuffer yBuffer = yPlane.getBuffer();
            ByteBuffer uBuffer = uPlane.getBuffer();
            ByteBuffer vBuffer = vPlane.getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Y plane
            yBuffer.get(nv21, 0, ySize);

            // Interleave V and U (NV21 = YYYYVUVU)
            byte[] vBytes = new byte[vSize];
            byte[] uBytes = new byte[uSize];
            vBuffer.get(vBytes);
            uBuffer.get(uBytes);

            // If pixel stride is 2, the data is already interleaved
            int pixelStride = vPlane.getPixelStride();
            if (pixelStride == 2) {
                // Already semi-planar, just copy V plane (which includes interleaved U)
                System.arraycopy(vBytes, 0, nv21, ySize, vSize);
            } else {
                // Pixel stride is 1: manually interleave V and U
                int uvIndex = ySize;
                int uvSize = Math.min(vBytes.length, uBytes.length);
                for (int i = 0; i < uvSize; i++) {
                    nv21[uvIndex++] = vBytes[i];
                    nv21[uvIndex++] = uBytes[i];
                }
            }

            return nv21;
        } catch (Exception e) {
            Log.e(TAG, "Error converting YUV to NV21", e);
            return null;
        }
    }

    /**
     * Scale anchor coordinates from bitmap space to overlay view space.
     */
    private PointF[] scaleAnchorsToView(Point[] anchors, int bitmapWidth, int bitmapHeight) {
        PointF[] viewPoints = new PointF[4];

        // The overlay is the same size as the preview view
        int viewWidth = anchorOverlay != null ? anchorOverlay.getWidth() : 0;
        int viewHeight = anchorOverlay != null ? anchorOverlay.getHeight() : 0;

        if (viewWidth == 0 || viewHeight == 0) {
            viewWidth = previewView.getWidth();
            viewHeight = previewView.getHeight();
        }

        if (viewWidth == 0 || viewHeight == 0) {
            // Fallback: just use bitmap coordinates
            for (int i = 0; i < 4; i++) {
                viewPoints[i] = new PointF((float) anchors[i].x, (float) anchors[i].y);
            }
            return viewPoints;
        }

        float scaleX = (float) viewWidth / bitmapWidth;
        float scaleY = (float) viewHeight / bitmapHeight;

        for (int i = 0; i < 4; i++) {
            viewPoints[i] = new PointF(
                    (float) anchors[i].x * scaleX,
                    (float) anchors[i].y * scaleY
            );
        }

        return viewPoints;
    }

    /**
     * Called when anchors ARE detected in a frame.
     */
    private void onAnchorsDetected(PointF[] viewPoints) {
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
                if (consecutiveDetections >= STABLE_FRAME_THRESHOLD && !autoCaptureTriggered) {
                    anchorStatusText.setText("✓ Anchors detected! Capturing…");
                    if (anchorStatusIcon != null) {
                        anchorStatusIcon.setColorFilter(
                                ContextCompat.getColor(this, R.color.green),
                                android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                } else {
                    anchorStatusText.setText("✓ Anchors detected (" + consecutiveDetections + "/" + STABLE_FRAME_THRESHOLD + ")");
                }
            }

            // Auto-capture after stable detections
            if (consecutiveDetections >= STABLE_FRAME_THRESHOLD && !autoCaptureTriggered) {
                autoCaptureTriggered = true;
                Log.d(TAG, "Auto-capture triggered after " + consecutiveDetections + " stable frames");
                takePhoto();
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
                    anchorStatusText.setText("Tracking anchors... (" + consecutiveDetections + "/" + STABLE_FRAME_THRESHOLD + ")");
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
                        runOnUiThread(() -> {
                            Toast.makeText(CameraActivity.this,
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            // Reset auto-capture if it fails
                            autoCaptureTriggered = false;
                            consecutiveDetections = 0;
                        });
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    //  ORIENTATION GUIDE
    // ─────────────────────────────────────────────────────────────

    /**
     * Sets up an OrientationEventListener that monitors the device's physical
     * rotation. When the phone is held in portrait (or near-portrait), a
     * full-screen overlay is shown telling the user to rotate to landscape.
     * The overlay hides automatically once landscape orientation is detected.
     */
    private void setupOrientationListener() {
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;

                // Landscape ranges: 60-120 (right landscape) or 240-300 (left landscape)
                boolean landscape = (orientation >= 60 && orientation <= 120)
                        || (orientation >= 240 && orientation <= 300);

                if (landscape != isInLandscape) {
                    isInLandscape = landscape;
                    runOnUiThread(() -> updateOrientationGuide(!landscape));
                }
            }
        };
    }

    /**
     * Show or hide the orientation guide overlay with a fade animation.
     *
     * @param show true to display the "rotate your phone" overlay
     */
    private void updateOrientationGuide(boolean show) {
        if (orientationGuideOverlay == null) return;

        if (show) {
            if (orientationGuideOverlay.getVisibility() != View.VISIBLE) {
                orientationGuideOverlay.setAlpha(0f);
                orientationGuideOverlay.setVisibility(View.VISIBLE);
                orientationGuideOverlay.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
            }
        } else {
            if (orientationGuideOverlay.getVisibility() == View.VISIBLE) {
                orientationGuideOverlay.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                orientationGuideOverlay.setVisibility(View.GONE);
                                orientationGuideOverlay.animate().setListener(null);
                            }
                        })
                        .start();
            }
        }
    }

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
        frameSkipCounter = 0;

        // Check current orientation immediately and show guide if portrait
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        boolean currentlyLandscape = (rotation == android.view.Surface.ROTATION_90
                || rotation == android.view.Surface.ROTATION_270);
        isInLandscape = currentlyLandscape;
        updateOrientationGuide(!currentlyLandscape);

        // Start listening for orientation changes
        if (orientationListener != null && orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
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
        // Stop listening when activity is not visible
        if (orientationListener != null) {
            orientationListener.disable();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orientationListener != null) {
            orientationListener.disable();
        }
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}