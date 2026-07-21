package com.example.omrscanner.camera;

import android.util.DisplayMetrics;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import androidx.camera.core.AspectRatio;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.omr.ArucoAnchorDetector;
import com.example.omrscanner.R;
import com.example.omrscanner.omr.AnchorDetector;
import com.example.omrscanner.ui.PreviewActivity;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class CameraActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CameraActivity";
    public static final String EXTRA_FIXED_MOUNT_MODE = "fixed_mount_mode";
    // "Tilt Agnostic Mode" from the scan-entry dialog. When true, skips the
    // fixed on-screen guide squares and the single-orientation tilt gate,
    // using free-floating whole-frame anchor detection instead.
    public static final String EXTRA_TILT_AGNOSTIC_MODE = "tilt_agnostic_mode";
    // Steps (0-3, clockwise) that the guide-square identities were rotated
    // by at the moment of capture. Downstream processing needs this to
    // know which physical guide box was truthfully "TL"/"TR"/"BL"/"BR" —
    // the on-screen label reflects this same value, so this is what makes
    // the label swap more than cosmetic.
    public static final String EXTRA_GUIDE_CORNER_ROTATION_STEPS = "guide_corner_rotation_steps";
    //private static final Size ANALYSIS_TARGET_RESOLUTION = new Size(1280, 720);
    private static final long FIXED_MOUNT_ZOOM_COOLDOWN_MS = 250L;
    private static final int FIXED_MOUNT_MISS_THRESHOLD = 6;
    private static final float[] FIXED_MOUNT_ZOOM_STEPS = {1.0f, 1.25f, 1.5f, 1.75f};
    private static final int HANDHELD_RECOVERY_MISS_THRESHOLD = 3;
    private static final int HANDHELD_RECOVERY_FRAME_INTERVAL = 2;

    // ~150-200ms of stable detection before capturing
    //private static final int REQUIRED_CONSECUTIVE_DETECTIONS = 5;
    //private static final int REQUIRED_CONSECUTIVE_DETECTIONS = 8;
    private static final int REQUIRED_CONSECUTIVE_DETECTIONS = 1;

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

    private FrameLayout btnRotationLock;
    private ImageView iconRotationLock;

    private View tiltWarningOverlay;

    // ── Camera state ──────────────────────────────────────────────
    // ── Camera state ──────────────────────────────────────────────
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isTorchOn = false;
    private boolean fixedMountMode = false;
    private boolean tiltAgnosticMode = false;
    private boolean isRotationLocked = false;

    /**
     * Carries the rotation-lock choice across CameraActivity instances.
     * A fresh CameraActivity is created for every scan (see onImageSaved,
     * which finishes this activity after each capture), so a plain instance
     * field would silently reset to unlocked on the very next scan. This
     * static field survives that recreation and only resets if the app
     * process itself is killed.
     */
    private static boolean rotationLockPersisted = false;

    // ── Auto-capture state ────────────────────────────────────────
    private int consecutiveDetections = 0;
    private int currentHintIndex = 0;
    private long lastHintChangeTime = 0;
    private boolean autoCaptureTriggered = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Anchor Smoothing State ────────────────────────────────────
    // ── Anchor Smoothing State ────────────────────────────────────
    private PointF[] smoothedAnchors = null;
    private int missingFramesCount = 0;
    private static final int MAX_MISSING_FRAMES = 8;
    private static final float SMOOTHING_FACTOR = 0.25f;
    private int fixedMountMissCounter = 0;
    private int fixedMountZoomIndex = 0;
    private long lastZoomChangeTime = 0L;
    private int handheldMissCounter = 0;
    private int handheldRecoveryFrameCounter = 0;
    private final List<Float> fixedMountZoomRatios = new ArrayList<>();

    // ── Guide-Square Mode State ──────────────────────────────────
    // When true, live detection is restricted to 4 fixed on-screen guide
    // squares instead of searching the whole frame. Teachers align the
    // sheet's real anchors inside these squares. This is a deliberate
    // trade-off: it removes the false-positive risk from handwriting or
    // other dark marks elsewhere on the sheet, at the cost of requiring
    // the teacher to align the sheet to fixed positions rather than just
    // "somewhere in frame."
    private static final boolean GUIDE_SQUARE_MODE_ENABLED = true;

    // Fraction of the shorter overlay dimension used as each guide square's
    // side length. Placeholder default — recalibrate visually against a
    // real printed sheet at normal handheld scanning distance.
    private static final float GUIDE_SQUARE_SIZE_FRACTION = 0.10f;

    // Guide square centers, as fractions of overlay [width, height].
    // Order matches [TL, TR, BL, BR]. Placeholder defaults — recalibrate
    // visually; these should roughly track where the anchor squares land
    // on a supported sheet at normal handheld scanning distance/framing.
    // Portrait layout — the original tuning.
    private static final float[] GUIDE_CENTER_X_FRACTION_PORTRAIT = {0.30f, 0.70f, 0.30f, 0.70f};
    private static final float[] GUIDE_CENTER_Y_FRACTION_PORTRAIT = {0.07f, 0.07f, 0.93f, 0.93f};

    // Landscape layout — a portrait-tuned layout stretched across a wide,
    // short overlay puts the squares in the wrong place relative to the
    // sheet, so landscape gets its own set. Placeholder — recalibrate
    // visually against a real sheet held in landscape.
    private static final float[] GUIDE_CENTER_X_FRACTION_LANDSCAPE = {0.12f, 0.88f, 0.12f, 0.88f};
    private static final float[] GUIDE_CENTER_Y_FRACTION_LANDSCAPE = {0.20f, 0.20f, 0.80f, 0.80f};

    // ZPH50/ZPH60 group ("WIDE") — ZPH50/60 sheets are physically larger
    // and a different aspect ratio (1.415:1) than ZPH30/40 (1.336:1), so
    // the COMPACT boxes above never land on a ZPH60 sheet's real anchors.
    // ⚠ PLACEHOLDER — derived only from the JSON aspect ratio, NOT yet
    // measured against a real printed ZPH50/60 sheet the way COMPACT was.
    // Recalibrate against a real device photo before relying on this in
    // production. Portrait layout.
    private static final float[] GUIDE_CENTER_X_FRACTION_PORTRAIT_WIDE = {0.08f, 0.92f, 0.08f, 0.92f};
    private static final float[] GUIDE_CENTER_Y_FRACTION_PORTRAIT_WIDE = {0.08f, 0.08f, 0.92f, 0.92f};

    private static final float[] GUIDE_CENTER_X_FRACTION_LANDSCAPE_WIDE = {0.08f, 0.92f, 0.08f, 0.92f};
    private static final float[] GUIDE_CENTER_Y_FRACTION_LANDSCAPE_WIDE = {0.08f, 0.08f, 0.92f, 0.92f};

    // How many consecutive successful in-region detections make up a full
    // "lap" of the progress ring for that corner. Higher = slower/steadier
    // requirement, lower = faster but more prone to false locks on a
    // shaky hand. Tune alongside analysis frame rate.
    private static final int GUIDE_REQUIRED_CONSECUTIVE_HITS = 8;

    // How long (ms) a locked corner is allowed to go undetected before its
    // lock is dropped. Short misses (motion blur, a stray shadow) shouldn't
    // break the lock — only a sustained absence, like the sheet actually
    // being pulled out of the guide box, should.
    private static final long GUIDE_LOCK_GRACE_PERIOD_MS = 1000L;

    private RectF[] guideSquaresViewSpace = null; // computed once overlay is laid out
    private boolean guideLandscapeMode = false;
    private final int[] guideConsecutiveHits = new int[4];
    private final long[] guideLastSeenTimestamp = new long[4];

    // ── Intent extras ─────────────────────────────────────────────
    private String selectedSheetType = null;
    private String classId = null;
    private String activityId = null;

    // ── Camera provider held at instance level to avoid re-binding ─
    private ProcessCameraProvider cameraProvider;

    // ── OpenCV init flag ──────────────────────────────────────────
    private boolean openCVReady = false;

    // ── Icon-rotation state ───────────────────────────────────────
// The screen itself is locked to portrait (see manifest), so the
// camera preview and guide squares never move. Instead we track the
// phone's physical tilt and rotate just the icons/status text to stay
// upright for the user, the way most camera apps handle landscape.
    private OrientationEventListener orientationEventListener;
    private int currentIconRotation = 0;

    // ── Fixed scanning orientation ──────────────────────────────────
    // This app is only designed to be scanned with the phone tilted to
    // the right — that's the one physical orientation the anchor/corner
    // math below assumes. We no longer try to auto-detect arbitrary tilt
    // and re-map corner identities on the fly (that guesswork was the
    // source of the "upside-down capture" bug when someone tilted the
    // other way). Instead: labels are fixed, and any other orientation
    // is treated as unsupported and blocks capture (see updateTiltGate).
    //
    // NOT YET VERIFIED ON A PHYSICAL DEVICE: Android's orientation-sensor
    // convention for "which bucket is tilted right" has already tripped
    // this codebase up once (see the old -90/90 swap this replaced). If
    // the warning overlay shows while the phone IS correctly tilted
    // right, flip this constant to 90.
    private static final int REQUIRED_TILT_ROTATION = 90;

    // Fixed corner-label rotation steps (0-3, clockwise) matching the
    // phone always being held tilted right. A full 180° swap (steps=2)
    // is the only value that's mathematically a valid rotation of all
    // four corners at once (TL<->BR AND TR<->BL) — VERIFY this against
    // a real printed-sheet test before trusting graded results; if the
    // trainer's test showed only TL/BR swapping and TR/BL staying put,
    // that isn't achievable through this steps-based mapping and needs
    // a different fix (see identityForSlotAtRotation).
    private static final int FIXED_LABEL_ROTATION_STEPS = 1;

    // This is what gets passed downstream via EXTRA_GUIDE_CORNER_ROTATION_STEPS
    // at capture time, so the aligner rotates the image the same way the
    // on-screen labels claim. Fixed at FIXED_LABEL_ROTATION_STEPS — no
    // longer recomputed per-frame from sensor tilt.
    private int currentLabelRotationSteps = FIXED_LABEL_ROTATION_STEPS;

    // Whether the phone is currently in the one supported tilt bucket.
    // Starts true (optimistic) so the warning overlay doesn't flash on
    // launch before the first sensor reading arrives.
    private boolean isTiltCorrect = true;

    // Corner-label rotation. Slot order below matches guideSquaresViewSpace's
    // [TL, TR, BL, B   R] slot order. CLOCKWISE_SLOT_INDEX gives each slot's
    // position going clockwise around the rectangle starting at TL
    // (TL=0, TR=1, BR=2, BL=3) — used to shift labels around the fixed
    // boxes as the phone tilts.
    private static final int[] CLOCKWISE_SLOT_INDEX = {0, 1, 3, 2};
    private static final String[] CLOCKWISE_LABELS = {"TL", "TR", "BR", "BL"};

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate started");

        try {
            setContentView(R.layout.activity_camera);
            setContentView(R.layout.activity_camera);
            Log.d(TAG, "Layout inflated successfully");

// Device/screen diagnostics — remove once the anchor-detection
// issue on other devices is root-caused.
            DisplayMetrics dm = getResources().getDisplayMetrics();
            Log.d(TAG, String.format(
                    "DEVICE INFO: model=%s manufacturer=%s sdk=%d | "
                            + "screenPx=%dx%d density=%.2f densityDpi=%d scaledDensity=%.2f",
                    android.os.Build.MODEL,
                    android.os.Build.MANUFACTURER,
                    android.os.Build.VERSION.SDK_INT,
                    dm.widthPixels, dm.heightPixels,
                    dm.density, dm.densityDpi, dm.scaledDensity
            ));
            Log.d(TAG, "Layout inflated successfully");

            selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
            classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
            activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);
            fixedMountMode = getIntent().getBooleanExtra(EXTRA_FIXED_MOUNT_MODE, false);
            tiltAgnosticMode = getIntent().getBooleanExtra(EXTRA_TILT_AGNOSTIC_MODE, false);
            Log.d(TAG, "Received sheet type: " + selectedSheetType + ", classId: " + classId
                    + ", tiltAgnosticMode: " + tiltAgnosticMode);

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
            // Both of these exist to support the single fixed scanning
            // orientation (rotation-lock persistence, fixed TL/TR/BL/BR
            // guide-square labels) — neither applies in Tilt Agnostic Mode.
            if (!tiltAgnosticMode) {
                applyPersistedRotationLock();
                applyFixedCornerLabels();
            }

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
        btnRotationLock  = findViewById(R.id.btnRotationLock);
        iconRotationLock = findViewById(R.id.iconRotationLock);
        tiltWarningOverlay = findViewById(R.id.tiltWarningOverlay);

        // Tilt Agnostic Mode doesn't track phone tilt at all, so none of
        // the single-orientation UI applies: the rotation-lock control
        // (which exists to freeze tilt-based corner relabeling) and the
        // "tilt the phone to the right" warning banner both have nothing
        // to do here. Hide them and make sure capture is never disabled
        // for tilt reasons in this mode.
        if (tiltAgnosticMode) {
            if (btnRotationLock != null) btnRotationLock.setVisibility(View.GONE);
            if (tiltWarningOverlay != null) tiltWarningOverlay.setVisibility(View.GONE);
            if (btnCapture != null) {
                btnCapture.setEnabled(true);
                btnCapture.setAlpha(1.0f);
            }
        }

        if (previewView   == null) Log.e(TAG, "previewView is null!");
        if (btnCapture    == null) Log.e(TAG, "btnCapture is null!");
    }

    // ─────────────────────────────────────────────────────────────
    private void setupListeners() {
        if (btnCapture != null) btnCapture.setOnClickListener(v -> takePhoto());
        if (btnCameraBack != null) btnCameraBack.setOnClickListener(v -> finish());
        if (btnFlash != null) btnFlash.setOnClickListener(v -> toggleFlash());
        if (btnRotationLock != null && !tiltAgnosticMode) {
            btnRotationLock.setOnClickListener(v -> toggleRotationLock());
        }
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

        if (previewView.getWidth() == 0 || previewView.getHeight() == 0) {
            previewView.post(this::bindCameraUseCases);
            return;
        }

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)   // was MINIMIZE_LATENCY
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
            Log.w(TAG, "PreviewView ViewPort was null — binding without shared crop rect");
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis);
        }

        cameraControl = camera.getCameraControl();
        cameraInfo    = camera.getCameraInfo();
        initializeFixedMountZoomRatios();
        applyFixedMountZoom(true);

// Flashlight defaults to ON every time the scanner opens — including
// right after a scan, since a new CameraActivity is created for each
// capture. The user can still tap the flash button to turn it off.
        isTorchOn = true;
        cameraControl.enableTorch(true);
        updateFlashButton();

        Log.d(TAG, "Camera bound successfully with ImageAnalysis, facing=" + cameraFacing
                + ", fixedMountMode=" + fixedMountMode);
    }

    // ─────────────────────────────────────────────────────────────
    //  REAL-TIME ANCHOR DETECTION
    // ─────────────────────────────────────────────────────────────

    private boolean loggedFrameDimensions = false;

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        // Skip if OpenCV not ready or already auto-captured
        if (!openCVReady || autoCaptureTriggered) {
            imageProxy.close();
            return;
        }

        // One-time log of the actual analysis frame size CameraX bound us to.
        // This is what AnchorDetector's thresholds are evaluated against —
        // not the screen size — so this is the number to compare across devices.
        if (!loggedFrameDimensions) {
            loggedFrameDimensions = true;
            Log.d(TAG, String.format(
                    "ANALYSIS FRAME INFO: %dx%d rotationDegrees=%d aspect=%.3f",
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    imageProxy.getImageInfo().getRotationDegrees(),
                    (double) imageProxy.getWidth() / imageProxy.getHeight()
            ));
        }

        try {
            if (tiltAgnosticMode) {
                analyzeFrameArucoIdentityMode(imageProxy);
            } else if (GUIDE_SQUARE_MODE_ENABLED) {
                analyzeFrameGuideSquareMode(imageProxy);
            } else {
                analyzeFrameWholeFrameLegacy(imageProxy);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in frame analysis", e);
            onDetectionMiss();
            onAnchorsNotDetected();
        } finally {
            imageProxy.close();
        }
    }

    /**
     * Tilt Agnostic Mode's detection path. Identifies corners by ArUco
     * marker ID rather than frame position, so an upside-down or rotated
     * capture still produces correctly-ordered [TL, TR, BL, BR] anchors
     * for PerspectiveAligner.
     */
    private void analyzeFrameArucoIdentityMode(@NonNull ImageProxy imageProxy) {
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
            // Single detection pass per frame: quadsById drives the live
            // "searching" boxes for whatever markers are currently visible,
            // anchors is non-null only once all 4 identity markers are found.
            quadsById = ArucoAnchorDetector.detectMarkerQuads(gray);
            anchors = ArucoAnchorDetector.identityAnchorsFromQuads(quadsById);
        } finally {
            gray.release();
        }

        updateTrackedMarkerOverlay(quadsById, imageWidth, imageHeight, rotationDegrees);

        if (anchors != null) {
            onDetectionSuccess();
            PointF[] viewPoints = scaleAnchorsToView(anchors, imageWidth, imageHeight, rotationDegrees);

            onAnchorsDetected(viewPoints, false);

            boolean captureStarted = false;
            if (consecutiveDetections >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                captureStarted = triggerAutoCapture();
                if (captureStarted) {
                    updateStatusTextForCaptureStarted();
                }
            }
        } else {
            onDetectionMiss();
            onAnchorsNotDetected();
        }
    }

    /**
     * Guide-square detection path. Only pixels inside the 4 fixed guide
     * squares are ever inspected — nothing else in the frame can trigger
     * a false positive, because nothing else is searched.
     */
    private void analyzeFrameGuideSquareMode(@NonNull ImageProxy imageProxy) {
        if (guideSquaresViewSpace == null) {
            guideSquaresViewSpace = computeGuideSquaresViewSpace();
            if (guideSquaresViewSpace != null) {
                RectF[] squaresForUi = guideSquaresViewSpace;
                mainHandler.post(() -> {
                    if (anchorOverlay != null) {
                        anchorOverlay.setGuideSquares(squaresForUi);
                    }
                });
            } else {
                // Overlay not laid out yet — nothing to do this frame.
                return;
            }
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();

        Mat gray = AnchorDetector.toGrayMat(imageProxy);
        if (gray == null) {
            return;
        }

        AnchorDetector.LiveDetectionMode mode = fixedMountMode
                ? AnchorDetector.LiveDetectionMode.FIXED_MOUNT
                : AnchorDetector.LiveDetectionMode.HANDHELD;

        boolean allLocked = true;
        try {
            long now = System.currentTimeMillis();
            for (int i = 0; i < 4; i++) {
                boolean wasLocked = guideConsecutiveHits[i] >= GUIDE_REQUIRED_CONSECUTIVE_HITS;

                // Unlike the old sticky-forever lock, we keep detecting on
                // locked corners too — otherwise we'd never notice the
                // anchor being pulled back out of the box.
                Rect imageRoi = viewRectToImageRect(
                        guideSquaresViewSpace[i], imageWidth, imageHeight, rotation);
                boolean found = AnchorDetector.detectAnchorInRegion(gray, imageRoi, mode);

                if (found) {
                    guideConsecutiveHits[i] = Math.min(
                            GUIDE_REQUIRED_CONSECUTIVE_HITS, guideConsecutiveHits[i] + 1);
                    guideLastSeenTimestamp[i] = now;
                } else if (wasLocked) {
                    // Grace period for an already-locked corner: a single
                    // missed frame (motion blur, brief occlusion) shouldn't
                    // break the lock. Only drop it once the anchor has been
                    // genuinely absent from the box for a sustained stretch
                    // of real time.
                    if (now - guideLastSeenTimestamp[i] >= GUIDE_LOCK_GRACE_PERIOD_MS) {
                        guideConsecutiveHits[i] = 0;
                    }
                } else {
                    // Not yet locked — same decay behavior as before.
                    // Moderate decay: strong enough that sparse, occasional
                    // false-positive hits (background clutter, shadows,
                    // texture) can't quietly accumulate into a false lock
                    // over time, but gentle enough that a real anchor with
                    // normal hand shake doesn't get punished too hard for
                    // the occasional dropped frame.
                    guideConsecutiveHits[i] = Math.max(0, guideConsecutiveHits[i] - 3);
                }

                if (guideConsecutiveHits[i] < GUIDE_REQUIRED_CONSECUTIVE_HITS) {
                    allLocked = false;
                }
            }
        } finally {
            gray.release();
        }

        final float[] progress = new float[4];
        final boolean[] locked = new boolean[4];
        for (int i = 0; i < 4; i++) {
            progress[i] = guideConsecutiveHits[i] / (float) GUIDE_REQUIRED_CONSECUTIVE_HITS;
            locked[i] = guideConsecutiveHits[i] >= GUIDE_REQUIRED_CONSECUTIVE_HITS;
        }

        mainHandler.post(() -> {
            if (anchorOverlay != null) {
                anchorOverlay.setCornerProgress(progress, locked);
            }
            if (anchorStatusText != null) {
                int lockedCount = 0;
                for (boolean l : locked) if (l) lockedCount++;
                anchorStatusText.setText(lockedCount == 4
                        ? "✓ Anchors detected! Capturing…"
                        : String.format("Aligning… %d/4", lockedCount));
            }
        });

        if (allLocked && !autoCaptureTriggered) {
            autoCaptureTriggered = true;
            mainHandler.post(this::takePhoto);
        }
    }

    /**
     * Computes the 4 static guide squares in the overlay view's own
     * coordinate space. Returns null if the overlay hasn't been laid out
     * yet (width/height still 0).
     */
    @Nullable
    private RectF[] computeGuideSquaresViewSpace() {
        if (anchorOverlay == null) return null;
        int viewW = anchorOverlay.getWidth();
        int viewH = anchorOverlay.getHeight();
        if (viewW == 0 || viewH == 0) return null;

        boolean isLandscape = viewW > viewH;
        guideLandscapeMode = isLandscape;

        boolean isWide = usesWideGuideGroup(selectedSheetType);
        float[] centerXFractions;
        float[] centerYFractions;
        if (isWide) {
            centerXFractions = isLandscape ? GUIDE_CENTER_X_FRACTION_LANDSCAPE_WIDE : GUIDE_CENTER_X_FRACTION_PORTRAIT_WIDE;
            centerYFractions = isLandscape ? GUIDE_CENTER_Y_FRACTION_LANDSCAPE_WIDE : GUIDE_CENTER_Y_FRACTION_PORTRAIT_WIDE;
        } else {
            centerXFractions = isLandscape ? GUIDE_CENTER_X_FRACTION_LANDSCAPE : GUIDE_CENTER_X_FRACTION_PORTRAIT;
            centerYFractions = isLandscape ? GUIDE_CENTER_Y_FRACTION_LANDSCAPE : GUIDE_CENTER_Y_FRACTION_PORTRAIT;
        }

        float size = GUIDE_SQUARE_SIZE_FRACTION * Math.min(viewW, viewH);
        RectF[] squares = new RectF[4];
        for (int i = 0; i < 4; i++) {
            float cx = centerXFractions[i] * viewW;
            float cy = centerYFractions[i] * viewH;
            squares[i] = new RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        }
        return squares;
    }

    /**
     * Maps a guide square defined in overlay view-space into a Rect in the
     * analysis frame's image-space, by inverse-transforming its 4 corners
     * through the same rotation/scale used to go the other direction in
     * scaleAnchorsToView, then taking the bounding box.
     */
    private Rect viewRectToImageRect(RectF viewRect, int imageWidth, int imageHeight, int rotationDegrees) {
        PointF p1 = viewPointToImagePoint(viewRect.left, viewRect.top, imageWidth, imageHeight, rotationDegrees);
        PointF p2 = viewPointToImagePoint(viewRect.right, viewRect.top, imageWidth, imageHeight, rotationDegrees);
        PointF p3 = viewPointToImagePoint(viewRect.left, viewRect.bottom, imageWidth, imageHeight, rotationDegrees);
        PointF p4 = viewPointToImagePoint(viewRect.right, viewRect.bottom, imageWidth, imageHeight, rotationDegrees);

        float minX = Math.min(Math.min(p1.x, p2.x), Math.min(p3.x, p4.x));
        float maxX = Math.max(Math.max(p1.x, p2.x), Math.max(p3.x, p4.x));
        float minY = Math.min(Math.min(p1.y, p2.y), Math.min(p3.y, p4.y));
        float maxY = Math.max(Math.max(p1.y, p2.y), Math.max(p3.y, p4.y));

        return new Rect(
                (int) minX,
                (int) minY,
                Math.max(1, (int) Math.ceil(maxX - minX)),
                Math.max(1, (int) Math.ceil(maxY - minY))
        );
    }

    /** Inverse of rotatePointToDisplay + view scaling — view-space to image-space. */
    private PointF viewPointToImagePoint(
            float viewX, float viewY, int imageWidth, int imageHeight, int rotationDegrees
    ) {
        int viewWidth = anchorOverlay != null ? anchorOverlay.getWidth() : previewView.getWidth();
        int viewHeight = anchorOverlay != null ? anchorOverlay.getHeight() : previewView.getHeight();

        boolean swapDimensions = rotationDegrees == 90 || rotationDegrees == 270;
        int rotatedWidth = swapDimensions ? imageHeight : imageWidth;
        int rotatedHeight = swapDimensions ? imageWidth : imageHeight;

        float scaleX = (float) viewWidth / rotatedWidth;
        float scaleY = (float) viewHeight / rotatedHeight;

        float rx = viewX / scaleX;
        float ry = viewY / scaleY;

        switch (rotationDegrees) {
            case 90:
                return new PointF(ry, imageHeight - rx);
            case 180:
                return new PointF(imageWidth - rx, imageHeight - ry);
            case 270:
                return new PointF(imageWidth - ry, rx);
            default:
                return new PointF(rx, ry);
        }
    }

    /**
     * Legacy whole-frame detection path, kept for quick rollback. Set
     * GUIDE_SQUARE_MODE_ENABLED = false to use this instead.
     */
    private void analyzeFrameWholeFrameLegacy(@NonNull ImageProxy imageProxy) {
        Point[] anchors;
        if (fixedMountMode) {
            anchors = AnchorDetector.detectAnchors(
                    imageProxy,
                    AnchorDetector.LiveDetectionMode.FIXED_MOUNT
            );
        } else {
            boolean useRecoveryPass = shouldRunHandheldRecoveryPass();
            anchors = AnchorDetector.detectAnchors(
                    imageProxy,
                    AnchorDetector.LiveDetectionMode.HANDHELD,
                    useRecoveryPass
            );
            if (useRecoveryPass) {
                Log.d(TAG, "Running handheld recovery pass after repeated anchor misses");
            }
        }

        if (anchors != null && anchors.length == 4) {
            onDetectionSuccess();
            PointF[] viewPoints = scaleAnchorsToView(
                    anchors, imageProxy.getWidth(), imageProxy.getHeight(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            onAnchorsDetected(viewPoints, false);

            boolean captureStarted = false;
            if (consecutiveDetections >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                captureStarted = triggerAutoCapture();
                if (captureStarted) {
                    updateStatusTextForCaptureStarted();
                }
            }
        } else {
            onDetectionMiss();
            onAnchorsNotDetected();
        }
    }

    private void updateStatusTextForCaptureStarted() {
        mainHandler.post(() -> {
            if (anchorStatusText != null) {
                anchorStatusText.setText("✓ Anchors detected! Capturing…");
            }
        });
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
            handheldMissCounter = 0;
            handheldRecoveryFrameCounter = 0;
            return;
        }

        fixedMountMissCounter = 0;
    }

    private void onDetectionMiss() {
        if (!fixedMountMode) {
            handheldMissCounter++;
            return;
        }

        if (autoCaptureTriggered || fixedMountZoomRatios.size() <= 1) {
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

    private boolean shouldRunHandheldRecoveryPass() {
        if (fixedMountMode) {
            return false;
        }
        if (handheldMissCounter < HANDHELD_RECOVERY_MISS_THRESHOLD) {
            return false;
        }

        handheldRecoveryFrameCounter++;
        return shouldRunHandheldRecoveryPass(
                handheldMissCounter,
                handheldRecoveryFrameCounter,
                HANDHELD_RECOVERY_MISS_THRESHOLD,
                HANDHELD_RECOVERY_FRAME_INTERVAL
        );
    }

    static boolean shouldRunHandheldRecoveryPass(int missCounter,
                                                 int recoveryFrameCounter,
                                                 int missThreshold,
                                                 int frameInterval) {
        return missCounter >= missThreshold
                && frameInterval > 0
                && recoveryFrameCounter % frameInterval == 0;
    }

    /**
     * Converts the raw per-marker ArUco quads (image space) into
     * AnchorOverlayView.TrackedMarker objects (view space) and pushes them
     * to the overlay. This is what makes the green boxes appear and follow
     * each marker live while it's being searched, independent of whether
     * all 4 identity anchors have been found yet. Only used by Tilt
     * Agnostic Mode -- guide-square mode's red/green boxes are untouched.
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

    /**
     * Transforms a single point from image (analysis-frame) space into this
     * view's coordinate space -- same rotation/scale math as
     * scaleAnchorsToView below, but for one arbitrary point instead of
     * exactly 4. Used only by the ArUco live tracking boxes above.
     */
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
        Log.d(TAG, "Auto-capture triggered after " + consecutiveDetections + " stable detections");
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

    /** Sheet types that use the WIDE guide-square group instead of COMPACT. */
    private static boolean usesWideGuideGroup(@Nullable String sheetType) {
        String base = com.example.omrscanner.models.ActivityFolder.parseBaseTemplateId(sheetType);
        return "ZPH50".equals(base) || "ZPH60".equals(base);
    }

    // ─────────────────────────────────────────────────────────────
//  ICON ROTATION (screen stays portrait-locked; only icons turn)
//  + TILT GATE (this app only supports scanning tilted right)
// ─────────────────────────────────────────────────────────────

    private void setupOrientationListener() {
        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;
                if (isRotationLocked) return; // frozen — ignore tilt entirely until unlocked

                int rotation;
                if (orientation >= 315 || orientation < 45) {
                    rotation = 0;     // held upright, natural portrait
                } else if (orientation >= 45 && orientation < 135) {
                    rotation = -90;   // rotated so the right edge is "up"
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = 180;   // upside-down portrait
                } else {
                    rotation = 90;    // rotated so the left edge is "up"
                }

                updateTiltGate(rotation);

                if (rotation != currentIconRotation) {
                    currentIconRotation = rotation;
                    applyIconRotation(rotation);
                }
            }
        };
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    /**
     * Smoothly rotates the interactive icons/status text to stay upright
     * relative to the user's hand, without touching the camera preview,
     * guide squares, or overall layout — those stay portrait-locked.
     * Editing to ensure that the anchor labels rotate on phone rotation
     */
    private void applyIconRotation(int rotationDegrees) {
        View[] rotatableViews = {
                btnCameraBack, iconFlash, iconRotationLock, anchorStatusIcon, floatingHintText
        };
        for (View v : rotatableViews) {
            if (v != null) {
                v.animate().rotation(rotationDegrees).setDuration(200).start();
            }
        }

        // The corner-box labels drawn by AnchorOverlayView don't live in
        // the normal view hierarchy (they're painted onto a Canvas), so
        // View.animate().rotation() doesn't touch them — they need to be
        // told the same tilt angle explicitly, or they stay stuck at their
        // original orientation while every other icon turns with the phone.
        if (anchorOverlay != null) {
            anchorOverlay.setLabelRotationDegrees(rotationDegrees);
        }
    }

    /**
     * Given a rotation-steps value (as passed via
     * EXTRA_GUIDE_CORNER_ROTATION_STEPS) and a physical guide-square slot
     * index (0=TL-pos, 1=TR-pos, 2=BL-pos, 3=BR-pos — the same order as
     * guideSquaresViewSpace/GUIDE_CENTER_X_FRACTION_*), returns the true
     * corner identity ("TL"/"TR"/"BL"/"BR") that slot represented at
     * capture time. Public + static so downstream consumers (e.g.
     * ResultActivity) use the exact same mapping as the on-screen label,
     * rather than re-deriving it and risking the two drifting apart.
     */
    public static String identityForSlotAtRotation(int physicalSlotIndex, int rotationSteps) {
        int clockwisePos = CLOCKWISE_SLOT_INDEX[physicalSlotIndex];
        int sourceIndex = ((clockwisePos - rotationSteps) % 4 + 4) % 4;
        return CLOCKWISE_LABELS[sourceIndex];
    }

    /**
     * Sets the guide-square corner labels ONCE, to the fixed mapping for
     * the one supported scanning orientation (tilted right). Unlike the
     * old per-tilt system, this never changes at runtime — there's
     * nothing to recompute since only one orientation is supported.
     */
    private void applyFixedCornerLabels() {
        if (anchorOverlay == null) return;
        String[] labels = new String[4];
        for (int i = 0; i < 4; i++) {
            labels[i] = identityForSlotAtRotation(i, FIXED_LABEL_ROTATION_STEPS);
        }
        anchorOverlay.setCornerLabels(labels);
    }

    /**
     * Shows/hides the full-screen "tilt the phone to the right" warning
     * and enables/disables capture accordingly. This replaces the old
     * approach of trying to auto-correct for every possible tilt — since
     * only one orientation is actually supported, any other orientation
     * now blocks capture with a clear message instead of silently
     * producing a bad (e.g. upside-down) scan.
     */
    private void updateTiltGate(int rotationBucket) {
        boolean correct = (rotationBucket == REQUIRED_TILT_ROTATION);
        if (correct == isTiltCorrect) return;
        isTiltCorrect = correct;

        if (tiltWarningOverlay != null) {
            tiltWarningOverlay.setVisibility(correct ? View.GONE : View.VISIBLE);
        }
        if (btnCapture != null) {
            btnCapture.setEnabled(correct);
            btnCapture.setAlpha(correct ? 1.0f : 0.4f);
        }
        if (!correct) {
            // Don't let a stale in-flight auto-capture fire the instant
            // the phone swings back into the correct orientation.
            autoCaptureTriggered = false;
        }
    }

    /**
     * Restores whatever rotation-lock state the user left the scanner in
     * on a previous scan, so pressing the lock stays in effect across
     * captures instead of unlocking again on the next scan.
     */
    private void applyPersistedRotationLock() {
        isRotationLocked = rotationLockPersisted;
        updateRotationLockButton();

        if (isRotationLocked) {
            isTiltCorrect = true;
            if (tiltWarningOverlay != null) tiltWarningOverlay.setVisibility(View.GONE);
            if (btnCapture != null) {
                btnCapture.setEnabled(true);
                btnCapture.setAlpha(1.0f);
            }
        }
    }

    private void toggleRotationLock() {
        isRotationLocked = !isRotationLocked;
        rotationLockPersisted = isRotationLocked;
        updateRotationLockButton();

        if (isRotationLocked) {
            // Trust the manual lock — suspend the tilt gate immediately so
            // a warning that happened to be showing at lock-time doesn't
            // stay stuck, and capture isn't second-guessed while locked.
            isTiltCorrect = true;
            if (tiltWarningOverlay != null) tiltWarningOverlay.setVisibility(View.GONE);
            if (btnCapture != null) {
                btnCapture.setEnabled(true);
                btnCapture.setAlpha(1.0f);
            }
        }
        // On unlock, nothing to do here — the next onOrientationChanged
        // callback naturally resumes evaluating and restores the warning
        // if the phone is genuinely mistilted.
    }

    private void updateRotationLockButton() {
        if (iconRotationLock == null) return;
        if (isRotationLocked) {
            iconRotationLock.setImageResource(R.drawable.ic_lock);
            iconRotationLock.setAlpha(1.0f);
            iconRotationLock.setColorFilter(
                    ContextCompat.getColor(this, R.color.primary_blue),
                    android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            iconRotationLock.setImageResource(R.drawable.ic_lock_open);
            iconRotationLock.setAlpha(0.7f);
            iconRotationLock.clearColorFilter();
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
        if (!tiltAgnosticMode && !isTiltCorrect) {
            Log.d(TAG, "takePhoto blocked: phone is not tilted to the required orientation");
            autoCaptureTriggered = false;
            return;
        }
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
                            intent.putExtra(EXTRA_TILT_AGNOSTIC_MODE, tiltAgnosticMode);
                            intent.putExtra(EXTRA_GUIDE_CORNER_ROTATION_STEPS, currentLabelRotationSteps);
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
                        runOnUiThread(() -> {
                            Toast.makeText(CameraActivity.this,
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            // Reset auto-capture if it fails
                            autoCaptureTriggered = false;
                            consecutiveDetections = 0;
                            handheldMissCounter = 0;
                            handheldRecoveryFrameCounter = 0;
                            applyFixedMountZoom(true);
                        });
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // ───────────────────────────────────────────────────────────
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
        handheldMissCounter = 0;
        handheldRecoveryFrameCounter = 0;
        applyFixedMountZoom(true);

        // Reset guide-square lock state too — otherwise a previously
        // locked-in scan attempt stays "locked" if this same CameraActivity
        // instance becomes visible again (e.g. back navigation, or a
        // paused instance left in the back stack after capture).
        java.util.Arrays.fill(guideConsecutiveHits, 0);
        java.util.Arrays.fill(guideLastSeenTimestamp, 0L);
        if (anchorOverlay != null) {
            anchorOverlay.resetProgress();
        }

        // Tilt Agnostic Mode doesn't gate on phone orientation at all, so
        // there's no reason to run the sensor listener that drives the
        // tilt warning / capture-blocking / icon-rotation behavior.
        if (!tiltAgnosticMode) {
            if (orientationEventListener == null) {
                setupOrientationListener();
            } else {
                orientationEventListener.enable();
            }
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