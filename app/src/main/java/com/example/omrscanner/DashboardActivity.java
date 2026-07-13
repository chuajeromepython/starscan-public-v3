package com.example.omrscanner;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.camera.QrScannerActivity;
import com.example.omrscanner.dashboard.ActivityScreenRenderer;
import com.example.omrscanner.dashboard.ClassExporter;
import com.example.omrscanner.dashboard.ClassScreenRenderer;
import com.example.omrscanner.dashboard.DashboardDialogs;
import com.example.omrscanner.dashboard.DashboardUiHelper;
import com.example.omrscanner.dashboard.HomeScreenRenderer;
import com.example.omrscanner.database.DataMapper;
import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.entities.TeacherEntity;
import com.example.omrscanner.database.projections.AssessmentListRow;
import com.example.omrscanner.database.projections.ClassListRow;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.example.omrscanner.ui.ScanDetailActivity;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dashboard entry point — thin coordinator that delegates rendering and dialog
 * logic to the classes in the {@code dashboard} package.
 *
 * <ul>
 *   <li>{@link DashboardUiHelper}       — shared UI builder helpers</li>
 *   <li>{@link HomeScreenRenderer}      — home screen + filter/sort</li>
 *   <li>{@link ClassScreenRenderer}     — class screen + tabs/sort</li>
 *   <li>{@link ActivityScreenRenderer}  — activity screen + scan cards</li>
 *   <li>{@link DashboardDialogs}        — all bottom-sheet dialogs</li>
 *   <li>{@link ClassExporter}           — download/export pipeline</li>
 * </ul>
 */
public class DashboardActivity extends AppCompatActivity implements DashboardDialogs.DialogHost {

    private static final String TAG = "DashboardActivity";
    private static final String CAMERA_MODE_PREFS = "camera_mode_prefs";
    private static final String PREF_FIXED_MOUNT_MODE = "fixed_mount_mode";

    // ── Intent extras used by CameraActivity / PreviewActivity ──
    public static final String EXTRA_SHEET_TYPE = "sheet_type";
    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";
    public static final String EXTRA_ANSWER_KEY_ID = "answer_key_id";

    // ── Screen names ──
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_CLASS = "class";
    private static final String SCREEN_ACTIVITY = "activity";

    // ── Sort constants (delegated to renderers, kept here for initialisation) ──
    private static final String CLASS_SORT_NEWEST = HomeScreenRenderer.CLASS_SORT_NEWEST;
    private static final String ASSESSMENT_SORT_NEWEST = ClassScreenRenderer.ASSESSMENT_SORT_NEWEST;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private OMRRepository repo;
    private int currentTeacherId = -1;

    private String currentScreen = SCREEN_HOME;
    private List<ClassFolder> classFolders = new ArrayList<>();
    private ClassFolder selectedClass = null;
    private ActivityFolder selectedActivity = null;
    private String selectedSheetType = null;
    private String selectedSheetFilter = null;
    private String globalTeacherName = "";

    private String activeUserFirstName = "";
    private String activeUserLastName = ""; // TEMP: see loadClassesFromDb() comment re: advisor vs. syncing teacher
    /**
     * Cached list of all answer keys — refreshed on load and after CRUD operations.
     */
    private List<AnswerKeyEntity> answerKeys = new ArrayList<>();

    private String classSearchQuery = "";
    private String selectedClassGradeFilter = null;
    private String selectedClassSchoolYearFilter = null;
    private String selectedClassSort = CLASS_SORT_NEWEST;

    private String assessmentSearchQuery = "";
    private String selectedAssessmentSort = ASSESSMENT_SORT_NEWEST;

    private int homeQueryGeneration = 0;
    private int assessmentQueryGeneration = 0;

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingHomeSearchRunnable;
    private Runnable pendingAssessmentSearchRunnable;

    // ── Helpers ──
    private DashboardUiHelper ui;
    private HomeScreenRenderer homeRenderer;
    private ClassScreenRenderer classRenderer;
    private ActivityScreenRenderer activityRenderer;
    private DashboardDialogs dialogs;

    // ═══════════════════════════════════════════════════════════════
    // VIEWS
    // ═══════════════════════════════════════════════════════════════

    private ImageButton btnBack, btnUpload, btnScanner; // btnScanner for qr scan
    private TextView topBarTitle, topBarBadge;
    private TextView tvTeacherName;
    private LinearLayout teacherNameRow;

    private View screenHome;
    private ScrollView screenClass, screenActivity;

    private LinearLayout homeEmpty, homeClassList;
    private EditText homeClassSearchInput;
    private TextView homeClassSortPicker;
    private LinearLayout homeGradeFilterChips, homeSchoolYearFilterChips;
    private LinearLayout homeFilterPanel;
    private android.widget.ImageView homeFilterToggle;
    private boolean homeFilterPanelVisible = false;

    private TextView classTeacherLabel, classNameLabel, classActivityCount;
    private LinearLayout classEmpty, classActivityList, classSheetTabs;
    private TextView classAssessmentCount;
    private EditText classAssessmentSearchInput;
    private TextView classAssessmentSortPicker;

    private CardView scanCtaCard;
    private LinearLayout scansHeader, activityScanList, activityScansEmpty;
    private TextView scansTotalCount, scanCtaSub;

    private com.google.android.material.floatingactionbutton.FloatingActionButton fabMain;
    private View fabScrim;
    private LinearLayout fabMenu;
    private LinearLayout fabClassRow, fabAnswerKeyRow, fabTestRow, fabSyncRow, fabAssessmentSyncRow;
    private TextView fabClassLabel;
    private boolean fabMenuOpen = false;

    private LinearLayout breadcrumbBar;
    private View breadcrumbDivider;
    private TextView breadcrumbRoot, breadcrumbSep1, breadcrumbClass,
            breadcrumbSep2, breadcrumbActivity;

    /*private final java.util.concurrent.ExecutorService syncExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final String SYNC_URL = "http://172.17.211.2:8000/api/classrooms/sync"; // route to the STARS system (classes)
    private static final String ASSESSMENT_SYNC_URL = "http://172.17.211.2:8000/api/students/sync"; // (student_lrn)*/
    private final java.util.concurrent.ExecutorService syncExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final String SYNC_PATH = "/api/classrooms/sync"; // route to the STARS system (classes)
    private static final String ASSESSMENT_SYNC_PATH = "/api/students/sync"; // (student_lrn)
    private static final String UPLOAD_ASSESSMENT_PATH = "/api/upload/assessment"; // multipart CSV upload
    private static final String SYNC_PREFS = "omr_sync_prefs";
    private static final String SYNC_PREFS_KEY_PREFIX = "last_sync_millis_";
    private static final long STUDENT_SYNC_STALE_MS = 24L * 60 * 60 * 1000; // 24 hours


    // user ip from users is_active

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        enableFullScreen();

        repo = new OMRRepository(this);

        /*
        // --- DB TEST ---
        // Below handles storing data to users
        com.example.omrscanner.database.entities.UserEntity user =
                new com.example.omrscanner.database.entities.UserEntity();
        user.username = "jdelacruz";
        user.userId = 12345;
        user.passkey = "password123";
        user.serverIp = "192.168.1.1";
        user.firstName = "Juan";
        user.middleName = "Santos";
        user.lastName = "dela Cruz";
        user.suffix = "Jr.";
        user.school = "Rizal Elementary School";

        // Insert a user into user.db
        repo.insertUser(user, id -> {
            Log.d("DB_TEST", "User inserted! ID: " + id);
            repo.getAllUsers((List<com.example.omrscanner.database.entities.UserEntity> users) -> {
                Log.d("DB_TEST", "Total users in DB: " + users.size());
                for (com.example.omrscanner.database.entities.UserEntity u : users) {
                    Log.d("DB_TEST", "─────────────────────────");
                    Log.d("DB_TEST", "ID         : " + u.id);
                    Log.d("DB_TEST", "Username   : " + u.username);
                    Log.d("DB_TEST", "User ID    : " + u.userId);
                    Log.d("DB_TEST", "Passkey    : " + u.passkey);
                    Log.d("DB_TEST", "Server IP  : " + u.serverIp);
                    Log.d("DB_TEST", "First Name : " + u.firstName);
                    Log.d("DB_TEST", "Middle Name: " + u.middleName);
                    Log.d("DB_TEST", "Last Name  : " + u.lastName);
                    Log.d("DB_TEST", "Suffix     : " + u.suffix);
                    Log.d("DB_TEST", "School     : " + u.school);
                    Log.d("DB_TEST", "─────────────────────────");
                }
            });
        });
        // --- END DB TEST ---
        */


        // Initialise helpers
        ui = new DashboardUiHelper(this);
        homeRenderer = new HomeScreenRenderer(this, ui);
        classRenderer = new ClassScreenRenderer(this, ui);
        activityRenderer = new ActivityScreenRenderer(this, ui);
        dialogs = new DashboardDialogs(this, ui, repo, this);

        initViews();
        initBackHandler();
        loadDataFromDb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableFullScreen();
        loadDataFromDb();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingHomeSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingHomeSearchRunnable);
        if (pendingAssessmentSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingAssessmentSearchRunnable);
        syncExecutor.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // FULL SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void enableFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    // ═══════════════════════════════════════════════════════════════
    // BACK HANDLER
    // ═══════════════════════════════════════════════════════════════

    private void initBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (SCREEN_ACTIVITY.equals(currentScreen)) {
                    selectedActivity = null;
                    showScreen(SCREEN_CLASS);
                } else if (SCREEN_CLASS.equals(currentScreen)) {
                    selectedClass = null;
                    showScreen(SCREEN_HOME);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // INIT VIEWS
    // ═══════════════════════════════════════════════════════════════

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnUpload = findViewById(R.id.btnUpload);
        topBarTitle = findViewById(R.id.topBarTitle);
        topBarBadge = findViewById(R.id.topBarBadge);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        teacherNameRow = findViewById(R.id.teacherNameRow);

        screenHome = findViewById(R.id.screenHome);
        screenClass = findViewById(R.id.screenClass);
        screenActivity = findViewById(R.id.screenActivity);

        homeEmpty = findViewById(R.id.homeEmpty);
        homeClassList = findViewById(R.id.homeClassList);
        homeClassSearchInput = findViewById(R.id.homeClassSearchInput);
        homeClassSortPicker = findViewById(R.id.homeClassSortPicker);
        homeGradeFilterChips = findViewById(R.id.homeGradeFilterChips);
        homeSchoolYearFilterChips = findViewById(R.id.homeSchoolYearFilterChips);
        homeFilterPanel = findViewById(R.id.homeFilterPanel);
        homeFilterToggle = findViewById(R.id.homeFilterToggle);

        classTeacherLabel = findViewById(R.id.classTeacherLabel);
        classNameLabel = findViewById(R.id.classNameLabel);
        classActivityCount = findViewById(R.id.classActivityCount);
        classEmpty = findViewById(R.id.classEmpty);
        classActivityList = findViewById(R.id.classActivityList);
        classSheetTabs = findViewById(R.id.classSheetTabs);
        classAssessmentCount = findViewById(R.id.classAssessmentCount);
        classAssessmentSearchInput = findViewById(R.id.classAssessmentSearchInput);
        classAssessmentSortPicker = findViewById(R.id.classAssessmentSortPicker);

        scanCtaCard = findViewById(R.id.scanCtaCard);
        scanCtaSub = findViewById(R.id.scanCtaSub);
        scansHeader = findViewById(R.id.scansHeader);
        scansTotalCount = findViewById(R.id.scansTotalCount);
        activityScanList = findViewById(R.id.activityScanList);
        activityScansEmpty = findViewById(R.id.activityScansEmpty);

        fabMain = findViewById(R.id.fabMain);
        fabScrim = findViewById(R.id.fabScrim);
        fabMenu = findViewById(R.id.fabMenu);
        fabClassRow = findViewById(R.id.fabClassRow);
        fabAnswerKeyRow = findViewById(R.id.fabAnswerKeyRow);
        fabTestRow = findViewById(R.id.fabTestRow);
        fabClassLabel = findViewById(R.id.fabClassLabel);
        fabSyncRow = findViewById(R.id.fabSyncRow);
        fabAssessmentSyncRow = findViewById(R.id.fabAssessmentSyncRow);

        breadcrumbBar = findViewById(R.id.breadcrumbBar);
        breadcrumbDivider = findViewById(R.id.breadcrumbDivider);
        breadcrumbRoot = findViewById(R.id.breadcrumbRoot);
        breadcrumbSep1 = findViewById(R.id.breadcrumbSep1);
        breadcrumbClass = findViewById(R.id.breadcrumbClass);
        breadcrumbSep2 = findViewById(R.id.breadcrumbSep2);
        breadcrumbActivity = findViewById(R.id.breadcrumbActivity);

        btnScanner = findViewById(R.id.btnScanner);
        btnScanner.setOnClickListener(v -> showQrGuide());

        btnBack.setOnClickListener(v -> navigateBack());
        btnUpload.setOnClickListener(v -> dialogs.showGlobalUploadClassDialog());
        fabMain.setOnClickListener(v -> toggleFabMenu());
        fabScrim.setOnClickListener(v -> closeFabMenu());

        fabClassRow.setOnClickListener(v -> {
            closeFabMenu();
            onFabClicked();
        });

        fabAnswerKeyRow.setOnClickListener(v -> {
            closeFabMenu();
            showDisclaimerThen(() -> dialogs.showNewAnswerKeyDialog(null));
        });

        fabTestRow.setOnClickListener(v -> {
            closeFabMenu();
            new DataInspector(this).printAll();
            new DataExporter(this).exportAll();
        });

        fabSyncRow.setOnClickListener(v -> {
            closeFabMenu();
            onSyncClicked();
        });

        fabAssessmentSyncRow.setOnClickListener(v -> {
            closeFabMenu();
            onAssessmentSyncClicked();
        });

        //teacherNameRow.setOnClickListener(v -> dialogs.showEditTeacherNameDialog());

        breadcrumbRoot.setOnClickListener(v -> {
            selectedClass = null;
            selectedActivity = null;
            showScreen(SCREEN_HOME);
        });
        breadcrumbClass.setOnClickListener(v -> {
            if (SCREEN_ACTIVITY.equals(currentScreen)) {
                selectedActivity = null;
                showScreen(SCREEN_CLASS);
            }
        });
        // Go directly to camera — no scan method picker
        scanCtaCard.setOnClickListener(v -> {
            if (selectedActivity != null) selectedSheetType = selectedActivity.getSheetType();
            openCamera();
        });

        homeClassSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                classSearchQuery = s != null ? s.toString().trim() : "";
                scheduleHomeSearchRefresh();
            }
        });
        classAssessmentSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                assessmentSearchQuery = s != null ? s.toString().trim() : "";
                scheduleAssessmentSearchRefresh();
            }
        });

        homeClassSortPicker.setOnClickListener(v ->
                homeRenderer.showClassSortDialog(selectedClassSort, key -> {
                    selectedClassSort = key;
                    updateSortPickers();
                    if (SCREEN_HOME.equals(currentScreen)) renderHomeScreen();
                }));
        classAssessmentSortPicker.setOnClickListener(v ->
                classRenderer.showAssessmentSortDialog(selectedAssessmentSort, key -> {
                    selectedAssessmentSort = key;
                    updateSortPickers();
                    if (SCREEN_CLASS.equals(currentScreen)) renderClassScreen();
                }));

        updateSortPickers();

        homeFilterToggle.setOnClickListener(v -> {
            homeFilterPanelVisible = !homeFilterPanelVisible;
            homeFilterPanel.setVisibility(homeFilterPanelVisible ? View.VISIBLE : View.GONE);
            homeRenderer.updateFilterToggleAppearance(homeFilterToggle, homeFilterPanelVisible,
                    selectedClassGradeFilter, selectedClassSchoolYearFilter, selectedClassSort);
        });
    }

    private void onAssessmentSyncClicked() {
        if (selectedClass == null) {
            ui.showErrorDialog("No class selected", "Open a class before syncing its students.");
            return;
        }
        if (selectedClass.getClassroomId() == null) {
            ui.showErrorDialog("Missing classroom ID", "This class wasn't synced from the server, so it has no classroom ID to sync students for.");
            return;
        }
        repo.getActiveUser(user -> {
            if (user == null || user.serverIp == null || user.serverIp.trim().isEmpty()) {
                runOnUiThread(() -> ui.showErrorDialog("Scan required",
                        "Please scan your QR code from the website system before syncing."));
                return;
            }
            runOnUiThread(() ->
                    android.widget.Toast.makeText(this, "Syncing students…", android.widget.Toast.LENGTH_SHORT).show());
            performAssessmentSync(selectedClass.getClassroomId(), user.serverIp);
        });
    }

    private void performAssessmentSync(int classroomId, String serverIp) {
        String localClassId = (selectedClass != null) ? selectedClass.getId() : null;
        syncStudentsForClass(this, localClassId, classroomId, serverIp);
    }

    @Override
    public void uploadAssessment(ActivityFolder act, ClassFolder cls, int assessmentId) {
        if (cls.getClassroomId() == null) {
            ui.showErrorDialog("Missing classroom ID",
                    "This class wasn't synced from the server, so it has no classroom ID to upload against.");
            return;
        }

        java.io.File csvFile = ClassExporter.getAssessmentCsvFile(cls, act);
        if (!csvFile.exists()) {
            ui.showErrorDialog("No scans to upload",
                    "No CSV was found for this assessment yet — scan at least one sheet first.");
            return;
        }

        repo.getActiveUser(user -> {
            if (user == null || user.serverIp == null || user.serverIp.trim().isEmpty()) {
                runOnUiThread(() -> ui.showErrorDialog("Scan required",
                        "Please scan your QR code from the website system before uploading."));
                return;
            }
            runOnUiThread(() -> Toast.makeText(this, "Uploading…", Toast.LENGTH_SHORT).show());
            performAssessmentUpload(assessmentId, cls.getClassroomId(), csvFile, user.serverIp);
        });
    }

    private void performAssessmentUpload(int assessmentId, int classroomId, java.io.File csvFile, String serverIp) {
        syncExecutor.execute(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                android.util.Log.d("OMR_ASSESSMENT_UPLOAD",
                        "Sending → assessment_id=" + assessmentId
                                + " class_id=" + classroomId
                                + " file=" + csvFile.getAbsolutePath()
                                + " (" + csvFile.length() + " bytes)"
                                + " url=" + serverIp + UPLOAD_ASSESSMENT_PATH);
                String boundary = "----OMRBoundary" + System.currentTimeMillis();
                try {
                    String csvContents = new String(
                            java.nio.file.Files.readAllBytes(csvFile.toPath()), "UTF-8");
                    android.util.Log.d("OMR_ASSESSMENT_UPLOAD", "CSV contents:\n" + csvContents);
                } catch (Exception logEx) {
                    android.util.Log.w("OMR_ASSESSMENT_UPLOAD", "Could not read CSV for logging", logEx);
                }
                java.net.URL url = new java.net.URL(serverIp + UPLOAD_ASSESSMENT_PATH);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    writeMultipartField(os, boundary, "assessment_id", String.valueOf(assessmentId));
                    writeMultipartField(os, boundary, "class_id", String.valueOf(classroomId));

                    os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                    os.write(("Content-Disposition: form-data; name=\"file_assessment\"; filename=\""
                            + csvFile.getName() + "\"\r\n").getBytes("UTF-8"));
                    os.write("Content-Type: text/csv\r\n\r\n".getBytes("UTF-8"));

                    try (java.io.FileInputStream fis = new java.io.FileInputStream(csvFile)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = fis.read(buffer)) != -1) os.write(buffer, 0, n);
                    }

                    os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
                    os.flush();
                }

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                String responseBody = sb.toString();
                android.util.Log.d("OMR_ASSESSMENT_UPLOAD",
                        "HTTP " + code + " — assessment_id=" + assessmentId + " — raw response: " + responseBody);

                org.json.JSONObject root = new org.json.JSONObject(responseBody);
                boolean success = root.optBoolean("success", false);
                String message = root.optString("message", success ? "Uploaded." : "Upload failed.");

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "✓ " + message, Toast.LENGTH_LONG).show();
                    } else {
                        ui.showErrorDialog("Upload failed", message);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("OMR_ASSESSMENT_UPLOAD",
                        "Upload failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                runOnUiThread(() -> ui.showErrorDialog("Upload failed",
                        "Could not upload assessment: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void writeMultipartField(java.io.OutputStream os, String boundary, String name, String value)
            throws java.io.IOException {
        os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        os.write((value + "\r\n").getBytes("UTF-8"));
    }

    /**
     * Placeholder for the sync action. The actual server contract (routes,
     * request/response shape) isn't finalized yet, so this just acknowledges
     * the tap for now — no network call wired up.
     */
    /**
     * Placeholder for the sync action. The actual server contract (routes,
     * request/response shape) isn't finalized yet, so this just acknowledges
     * the tap for now — no network call wired up.
     */
    private void onSyncClicked() {
        if (activeUserFirstName == null || activeUserFirstName.isEmpty()) {
            ui.showErrorDialog("Scan required",
                    "Please scan your QR code from the website system before syncing.");
            return;
        }

        repo.getActiveUser(user -> {
            if (user == null || user.userId == null
                    || user.serverIp == null || user.serverIp.trim().isEmpty()) {
                runOnUiThread(() -> ui.showErrorDialog("Scan required",
                        "Please scan your QR code from the website system before syncing."));
                return;
            }
            ensureTeacherId(teacherId -> {
                if (teacherId <= 0) return;
                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Syncing…", android.widget.Toast.LENGTH_SHORT).show());
                performClassroomSync(user.userId, teacherId, user.serverIp);
            });
        });
    }

    private void toggleFabMenu() {
        if (fabMenuOpen) closeFabMenu();
        else openFabMenu();
    }

    private void openFabMenu() {
        fabMenuOpen = true;
        updateFabMenuRowsForScreen();
        fabScrim.setVisibility(View.VISIBLE);
        fabMenu.setVisibility(View.VISIBLE);
        fabMain.animate().rotation(45f).setDuration(150).start();
    }

    private void closeFabMenu() {
        fabMenuOpen = false;
        fabScrim.setVisibility(View.GONE);
        fabMenu.setVisibility(View.GONE);
        fabMain.animate().rotation(0f).setDuration(150).start();
    }

    /** Show only the rows relevant to the current screen, and relabel "Class" → "Assessment". */
    private void updateFabMenuRowsForScreen() {
        // Sync is available from every screen.
        //fabSyncRow.setVisibility(View.VISIBLE);

        switch (currentScreen) {
            case SCREEN_HOME:
                fabClassLabel.setText("New class");
                fabClassRow.setVisibility(View.GONE);
                fabAnswerKeyRow.setVisibility(View.VISIBLE);
                fabTestRow.setVisibility(View.GONE);
                fabSyncRow.setVisibility(View.VISIBLE);
                fabAssessmentSyncRow.setVisibility(View.GONE);
                break;
            case SCREEN_CLASS:
                fabClassLabel.setText("New assessment");
                fabClassRow.setVisibility(View.VISIBLE);
                fabAnswerKeyRow.setVisibility(View.GONE);
                fabTestRow.setVisibility(View.GONE);
                fabSyncRow.setVisibility(View.GONE);
                fabAssessmentSyncRow.setVisibility(View.VISIBLE);
                break;
            case SCREEN_ACTIVITY:
                fabClassRow.setVisibility(View.GONE);
                fabAnswerKeyRow.setVisibility(View.GONE);
                fabTestRow.setVisibility(View.GONE);
                fabSyncRow.setVisibility(View.GONE);
                fabAssessmentSyncRow.setVisibility(View.GONE);
                break;
        }
    }

    private void scheduleHomeSearchRefresh() {
        if (pendingHomeSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingHomeSearchRunnable);
        pendingHomeSearchRunnable = () -> {
            if (SCREEN_HOME.equals(currentScreen)) renderHomeScreen();
        };
        searchDebounceHandler.postDelayed(pendingHomeSearchRunnable, 220);
    }

    private void scheduleAssessmentSearchRefresh() {
        if (pendingAssessmentSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingAssessmentSearchRunnable);
        pendingAssessmentSearchRunnable = () -> {
            if (SCREEN_CLASS.equals(currentScreen)) renderClassScreen();
        };
        searchDebounceHandler.postDelayed(pendingAssessmentSearchRunnable, 220);
    }

    // Disclaimer Template
    private void showDisclaimerThen(Runnable onConfirm) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(20));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(android.graphics.Color.WHITE);
        bg.setCornerRadius(ui.dp(24));
        root.setBackground(bg);
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("⚠️ Disclaimer", "#D97706",
                android.view.Gravity.START, 16));

        android.widget.TextView msg = new android.widget.TextView(this);
        // Edit to the exact disclaimer verbatum
        msg.setText("Please ensure all information entered is accurate. Data created here will be used for official assessment records.");
        msg.setTextColor(android.graphics.Color.parseColor("#475569"));
        msg.setTextSize(14);
        msg.setPadding(ui.dp(24), ui.dp(4), ui.dp(24), ui.dp(16));
        root.addView(msg);

        android.widget.LinearLayout actions = ui.buildActionsRow(ui.dp(20));
        android.widget.TextView btnCancel = ui.createDialogButton("Cancel", false);
        android.widget.TextView btnConfirm = ui.createDialogButton("I Understand", true);
        actions.addView(btnCancel);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnConfirm);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.run();
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // "How to guide" carousel
    private void showQrGuide() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        //dialog.setCanceledOnTouchOutside(true);

        android.widget.LinearLayout root = ui.buildSheet();

        String[] titles = {"Open Your Camera", "Find a QR Code", "Get the Result"};
        String[] descs = {
                "Point your phone camera at any QR code. Make sure you have good lighting for best results.",
                "Align the QR code inside the frame. Hold your phone steady and keep the code fully visible.",
                "Once scanned, the contents of the QR code will appear on screen automatically."
        };

        // Dots
        android.widget.LinearLayout dotsRow = new android.widget.LinearLayout(this);
        dotsRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        dotsRow.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams dotsLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.bottomMargin = ui.dp(16);
        dotsRow.setLayoutParams(dotsLp);

        android.widget.TextView[] dots = new android.widget.TextView[3];
        for (int i = 0; i < 3; i++) {
            android.widget.TextView dot = new android.widget.TextView(this);
            dot.setText("●");
            dot.setTextSize(10);
            dot.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            dots[i] = dot;
            dotsRow.addView(dot);
        }

        // ViewPager
        androidx.viewpager2.widget.ViewPager2 viewPager =
                new androidx.viewpager2.widget.ViewPager2(this);
        android.widget.LinearLayout.LayoutParams vpLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        ui.dp(240));
        viewPager.setLayoutParams(vpLp);

        // Adapter
        androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> adapter =
                new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(
                            @NonNull android.view.ViewGroup parent, int viewType) {
                        android.widget.LinearLayout slide = new android.widget.LinearLayout(DashboardActivity.this);
                        slide.setOrientation(android.widget.LinearLayout.VERTICAL);
                        slide.setGravity(android.view.Gravity.CENTER);
                        slide.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
                        slide.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

                        android.widget.ImageView icon = new android.widget.ImageView(DashboardActivity.this);
                        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        android.widget.LinearLayout.LayoutParams iconLp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        ui.dp(64));
                        iconLp.bottomMargin = ui.dp(12);
                        icon.setLayoutParams(iconLp);
                        icon.setColorFilter(android.graphics.Color.parseColor("#0038A8"));
                        icon.setTag("icon");

                        android.widget.TextView title = new android.widget.TextView(DashboardActivity.this);
                        title.setTextSize(16);
                        title.setTypeface(null, android.graphics.Typeface.BOLD);
                        title.setTextColor(android.graphics.Color.parseColor("#0038A8"));
                        title.setGravity(android.view.Gravity.CENTER);
                        android.widget.LinearLayout.LayoutParams titleLp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        titleLp.bottomMargin = ui.dp(8);
                        title.setLayoutParams(titleLp);
                        title.setTag("title");

                        android.widget.TextView desc = new android.widget.TextView(DashboardActivity.this);
                        desc.setTextSize(14);
                        desc.setTextColor(android.graphics.Color.parseColor("#475569"));
                        desc.setGravity(android.view.Gravity.CENTER);
                        desc.setTag("desc");

                        slide.addView(icon);
                        slide.addView(title);
                        slide.addView(desc);

                        return new androidx.recyclerview.widget.RecyclerView.ViewHolder(slide) {
                        };
                    }

                    @Override
                    public void onBindViewHolder(
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder,
                            int position) {
                        android.widget.LinearLayout slide =
                                (android.widget.LinearLayout) holder.itemView;
                        android.widget.ImageView iconView = slide.findViewWithTag("icon");
                        int[] drawables = {
                                R.drawable.ic_camera,
                                R.drawable.ic_focus,
                                R.drawable.ic_check
                        };
                        iconView.setImageResource(drawables[position]);
                        ((android.widget.TextView) slide.findViewWithTag("title")).setText(titles[position]);
                        ((android.widget.TextView) slide.findViewWithTag("desc")).setText(descs[position]);
                    }

                    @Override
                    public int getItemCount() {
                        return 3;
                    }
                };

        viewPager.setAdapter(adapter);

        // Dots update on swipe
        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int d = 0; d < 3; d++) {
                    dots[d].setTextColor(android.graphics.Color.parseColor(
                            d == position ? "#0038A8" : "#CBD5E1"));
                }
            }
        });

        // Proceed button (only visible on last slide)
        android.widget.TextView btnProceed = ui.createDialogButton("Proceed", true);
        android.widget.LinearLayout.LayoutParams btnLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = ui.dp(12);
        btnProceed.setLayoutParams(btnLp);
        btnProceed.setVisibility(android.view.View.GONE);

        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                btnProceed.setVisibility(position == 2
                        ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        });

        btnProceed.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new android.content.Intent(this,
                    com.example.omrscanner.camera.QrScannerActivity.class));
        });

        root.addView(ui.createDialogHandle());
        root.addView(dotsRow);
        root.addView(viewPager);
        root.addView(btnProceed);

        dialog.setContentView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.show();
        }
    }

    // Method to communicate with the system
    private void performClassroomSync(int userId, int teacherId, String serverIp) {
        syncExecutor.execute(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL url = new java.net.URL(serverIp + SYNC_PATH);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("userId", userId);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                String responseBody = sb.toString();
                android.util.Log.d("OMR_CLASSROOM_SYNC", "HTTP " + code + " — raw response: " + responseBody);

                org.json.JSONObject root = new org.json.JSONObject(responseBody);
                boolean success = root.optBoolean("success", false);
                String message = root.optString("message", "");
                android.util.Log.d("OMR_CLASSROOM_SYNC", "success=" + success + " message=" + message);

                org.json.JSONObject data = root.optJSONObject("data");
                int written = 0;
                if (data != null) {
                    java.util.Iterator<String> gradeKeys = data.keys();
                    while (gradeKeys.hasNext()) {
                        String gradeLevel = gradeKeys.next();
                        org.json.JSONArray classrooms = data.optJSONArray(gradeLevel);
                        if (classrooms == null) continue;
                        for (int i = 0; i < classrooms.length(); i++) {
                            org.json.JSONObject c = classrooms.getJSONObject(i);

                            android.util.Log.d("OMR_CLASSROOM_SYNC",
                                    gradeLevel
                                            + " | classroom_id=" + c.optInt("classroom_id")
                                            + " section=" + c.optString("section")
                                            + " section_id=" + c.optInt("section_id")
                                            + " advisor=" + c.optString("advisor")
                                            + " subject=" + c.optString("subject")
                                            + " classes=" + c.optInt("classes")
                                            + " is_advisory=" + c.optInt("is_advisory")
                                            + " grade_level=" + c.optString("grade_level")
                                            + " school_year=" + c.optString("school_year")
                                            + " teacher_class_id=" + c.optInt("teacher_class_id"));

                            com.example.omrscanner.database.entities.ClassEntity entity =
                                    new com.example.omrscanner.database.entities.ClassEntity();
                            entity.teacherId = teacherId;
                            entity.grade = c.optString("grade_level", gradeLevel);
                            entity.section = c.optString("section");
                            entity.schoolYear = c.optString("school_year");
                            entity.classroomId = c.optInt("classroom_id");
                            entity.sectionId = c.optInt("section_id");
                            entity.advisor = c.optString("advisor");
                            entity.subject = c.optString("subject");
                            entity.classes = String.valueOf(c.optInt("classes"));
                            entity.isAdvisory = c.optInt("is_advisory") == 1;
                            entity.teacherClassId = c.optInt("teacher_class_id");

                            repo.upsertClassFromSync(entity, null);
                            written++;
                        }
                    }
                }

                final int totalWritten = written;
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this,
                            "Synced " + totalWritten + " class" + (totalWritten == 1 ? "" : "es"),
                            android.widget.Toast.LENGTH_SHORT).show();
                    loadDataFromDb(); // refresh class cards with the new/updated rows
                });
            } catch (Exception e) {
                android.util.Log.e("OMR_CLASSROOM_SYNC", "Sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                runOnUiThread(() -> ui.showErrorDialog("Sync failed",
                        "Could not sync classrooms: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════════

    private void showScreen(String screen) {
        closeFabMenu();
        currentScreen = screen;

        screenHome.setVisibility(View.GONE);
        screenClass.setVisibility(View.GONE);
        screenActivity.setVisibility(View.GONE);

        switch (screen) {
            case SCREEN_HOME:
                screenHome.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                fabMain.setVisibility(View.VISIBLE);
                topBarTitle.setText("SagotSuri");
                topBarBadge.setVisibility(View.GONE);
                if (!classSearchQuery.equals(homeClassSearchInput.getText().toString())) {
                    homeClassSearchInput.setText(classSearchQuery);
                    homeClassSearchInput.setSelection(homeClassSearchInput.getText().length());
                }
                updateSortPickers();
                refreshTeacherNameHeader();
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                renderHomeScreen();
                break;

            case SCREEN_CLASS:
                if (selectedClass == null) {
                    showScreen(SCREEN_HOME);
                    return;
                }
                screenClass.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                fabMain.setVisibility(View.VISIBLE);
                topBarTitle.setText(selectedClass.getDisplayName());
                topBarBadge.setVisibility(View.VISIBLE);
                topBarBadge.setText("📁 " + selectedClass.getActivityCount());
                if (!assessmentSearchQuery.equals(classAssessmentSearchInput.getText().toString())) {
                    classAssessmentSearchInput.setText(assessmentSearchQuery);
                    classAssessmentSearchInput.setSelection(classAssessmentSearchInput.getText().length());
                }
                updateSortPickers();
                breadcrumbBar.setVisibility(View.VISIBLE);
                breadcrumbDivider.setVisibility(View.VISIBLE);
                breadcrumbSep1.setVisibility(View.VISIBLE);
                breadcrumbClass.setVisibility(View.VISIBLE);
                breadcrumbClass.setText(selectedClass.getDisplayName());
                breadcrumbClass.setTextColor(Color.parseColor("#1E293B"));
                breadcrumbSep2.setVisibility(View.GONE);
                breadcrumbActivity.setVisibility(View.GONE);
                renderClassScreen();
                break;

            case SCREEN_ACTIVITY:
                if (selectedClass == null || selectedActivity == null) {
                    showScreen(SCREEN_HOME);
                    return;
                }
                screenActivity.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                fabMain.setVisibility(View.GONE);
                topBarTitle.setText(selectedActivity.getName());
                topBarBadge.setVisibility(View.VISIBLE);
                topBarBadge.setText(selectedActivity.getSheetType());
                breadcrumbBar.setVisibility(View.VISIBLE);
                breadcrumbDivider.setVisibility(View.VISIBLE);
                breadcrumbSep1.setVisibility(View.VISIBLE);
                breadcrumbClass.setVisibility(View.VISIBLE);
                breadcrumbClass.setText(selectedClass.getDisplayName());
                breadcrumbClass.setTextColor(Color.parseColor("#0038A8"));
                breadcrumbSep2.setVisibility(View.VISIBLE);
                breadcrumbActivity.setVisibility(View.VISIBLE);
                breadcrumbActivity.setText(selectedActivity.getName());
                renderActivityScreen();
                break;
        }
    }

    private void navigateBack() {
        switch (currentScreen) {
            case SCREEN_CLASS:
                selectedClass = null;
                showScreen(SCREEN_HOME);
                break;
            case SCREEN_ACTIVITY:
                selectedActivity = null;
                showScreen(SCREEN_CLASS);
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FAB
    // ═══════════════════════════════════════════════════════════════

    private void onFabClicked() {
        switch (currentScreen) {
            case SCREEN_HOME:
                if (globalTeacherName == null || globalTeacherName.trim().isEmpty()) {
                    // Delegate to a small inline dialog via ui.buildSheet()
                    android.app.Dialog noNameDialog = new android.app.Dialog(this);
                    noNameDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                    noNameDialog.setCancelable(true);
                    android.widget.LinearLayout root = ui.buildSheet();
                    //root.addView(ui.createDialogHandle());
                    root.addView(ui.buildSheetTitle("⚠ Teacher Name Required", "#D97706",
                            android.view.Gravity.START, 20));
                    android.widget.TextView msg = new android.widget.TextView(this);
                    msg.setText("Please set your teacher name first before creating a class.");
                    msg.setTextColor(android.graphics.Color.parseColor("#475569"));
                    msg.setTextSize(14);
                    msg.setPadding(ui.dp(24), ui.dp(4), ui.dp(24), ui.dp(16));
                    root.addView(msg);
                    android.widget.LinearLayout actions = ui.buildActionsRow(ui.dp(20));
                    android.widget.TextView btnCancel = ui.createDialogButton("Cancel", false);
                    android.widget.TextView btnSet = ui.createDialogButton("Set Now", true);
                    actions.addView(btnCancel);
                    actions.addView(ui.spacer(ui.dp(10)));
                    actions.addView(btnSet);
                    root.addView(actions);
                    btnCancel.setOnClickListener(v -> noNameDialog.dismiss());
                    btnSet.setOnClickListener(v -> {
                        noNameDialog.dismiss();
                        dialogs.showEditTeacherNameDialog();
                    });
                    noNameDialog.setContentView(root);
                    ui.configureBottomDialog(noNameDialog);
                    noNameDialog.show();
                    return;
                }
                showDisclaimerThen(() -> dialogs.showNewClassDialog());
                break;
            case SCREEN_CLASS:
                dialogs.showNewActivityDialog();
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — HOME
    // ═══════════════════════════════════════════════════════════════

    private void renderHomeScreen() {
        homeClassList.removeAllViews();

        TextView statClasses = findViewById(R.id.statClasses);
        TextView statActivities = findViewById(R.id.statActivities);
        TextView statScans = findViewById(R.id.statScans);
        TextView homeClassCount = findViewById(R.id.homeClassCount);

        int totalActivities = 0, totalScans = 0;
        for (ClassFolder cls : classFolders) {
            if (cls.getActivities() != null) {
                totalActivities += cls.getActivities().size();
                for (ActivityFolder act : cls.getActivities())
                    totalScans += act.getScanCount();
            }
        }
        if (statClasses != null) statClasses.setText(String.valueOf(classFolders.size()));
        if (statActivities != null) statActivities.setText(String.valueOf(totalActivities));
        if (statScans != null) statScans.setText(String.valueOf(totalScans));

        homeRenderer.updateFilterToggleAppearance(homeFilterToggle, homeFilterPanelVisible,
                selectedClassGradeFilter, selectedClassSchoolYearFilter, selectedClassSort);

        final int requestId = ++homeQueryGeneration;
        repo.queryClassList(classSearchQuery, selectedClassGradeFilter,
                selectedClassSchoolYearFilter, selectedClassSort, rows -> runOnUiThread(() -> {
                    if (requestId != homeQueryGeneration || !SCREEN_HOME.equals(currentScreen))
                        return;

                    List<String> grades = homeRenderer.getDistinctGrades(classFolders);
                    List<String> years = homeRenderer.getDistinctSchoolYears(classFolders);

                    boolean stale = homeRenderer.buildHomeFilterChips(
                            homeGradeFilterChips, homeSchoolYearFilterChips,
                            grades, years,
                            selectedClassGradeFilter, selectedClassSchoolYearFilter,
                            v -> {
                                selectedClassGradeFilter = v;
                                if (SCREEN_HOME.equals(currentScreen)) renderHomeScreen();
                            },
                            v -> {
                                selectedClassSchoolYearFilter = v;
                                if (SCREEN_HOME.equals(currentScreen)) renderHomeScreen();
                            },
                            () -> SCREEN_HOME.equals(currentScreen));
                    if (stale) return;

                    int rowCount = (rows != null) ? rows.size() : 0;
                    if (homeClassCount != null) homeClassCount.setText(rowCount + " total");

                    if (rowCount == 0) {
                        homeEmpty.setVisibility(View.VISIBLE);
                        homeClassList.setVisibility(View.GONE);
                        return;
                    }
                    homeEmpty.setVisibility(View.GONE);
                    homeClassList.setVisibility(View.VISIBLE);
                    for (ClassListRow row : rows) {
                        homeClassList.addView(homeRenderer.createClassCard(
                                row, globalTeacherName,
                                () -> {
                                    ClassFolder c = findClassById(row.id);
                                    if (c != null) dialogs.showEditClassDialog(c);
                                },
                                () -> {
                                    ClassFolder c = findClassById(row.id);
                                    if (c != null) dialogs.showDeleteClassConfirmation(c);
                                },
                                () -> {
                                    selectedClass = findClassById(row.id);
                                    if (selectedClass == null) {
                                        ui.showErrorDialog("Class unavailable",
                                                "The selected class could not be loaded. Please try again.");
                                        return;
                                    }
                                    selectedSheetFilter = null;
                                    assessmentSearchQuery = "";
                                    selectedAssessmentSort = ASSESSMENT_SORT_NEWEST;
                                    showScreen(SCREEN_CLASS);
                                }));
                    }
                }));
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — CLASS
    // ═══════════════════════════════════════════════════════════════

    private void renderClassScreen() {
        classActivityList.removeAllViews();
        String displayTeacher = (selectedClass.getTeacher() != null
                && !selectedClass.getTeacher().trim().isEmpty())
                ? selectedClass.getTeacher()
                : (globalTeacherName != null && !globalTeacherName.isEmpty()
                ? globalTeacherName : "Unknown Teacher");

        classTeacherLabel.setText("Teacher: " + displayTeacher);
        classNameLabel.setText(selectedClass.getDisplayName());
        int activityCount = selectedClass.getActivityCount();
        classActivityCount.setText(activityCount + " assessment" + (activityCount == 1 ? "" : "s"));

        classRenderer.buildClassSheetTabs(classSheetTabs, selectedClass.getActivities(),
                selectedSheetFilter, filterVal -> {
                    selectedSheetFilter = filterVal;
                    renderClassScreen();
                });

        final int requestId = ++assessmentQueryGeneration;
        repo.queryAssessmentList(selectedClass.getId(), selectedSheetFilter,
                assessmentSearchQuery, selectedAssessmentSort, rows -> runOnUiThread(() -> {
                    if (requestId != assessmentQueryGeneration || !SCREEN_CLASS.equals(currentScreen))
                        return;

                    int rowCount = (rows != null) ? rows.size() : 0;
                    if (classAssessmentCount != null)
                        classAssessmentCount.setText(rowCount + " total");

                    if (rowCount == 0) {
                        classEmpty.setVisibility(View.VISIBLE);
                        classActivityList.setVisibility(View.GONE);
                        return;
                    }
                    classEmpty.setVisibility(View.GONE);
                    classActivityList.setVisibility(View.VISIBLE);
                    for (AssessmentListRow row : rows) {
                        classActivityList.addView(classRenderer.createActivityCard(
                                row,
                                () -> {
                                    ActivityFolder a = findActivityById(selectedClass, row.id);
                                    if (a != null) dialogs.showEditActivityDialog(a);
                                },
                                () -> {
                                    ActivityFolder a = findActivityById(selectedClass, row.id);
                                    if (a != null) dialogs.showAnswerKeyFolderDialog(a);
                                },
                                () -> {
                                    ActivityFolder a = findActivityById(selectedClass, row.id);
                                    if (a != null) dialogs.showDeleteActivityConfirmation(a);
                                },
                                () -> {
                                    ActivityFolder a = findActivityById(selectedClass, row.id);
                                    if (a != null) dialogs.showUploadAssessmentDialog(a, selectedClass);
                                },
                                () -> {
                                    ActivityFolder a = findActivityById(selectedClass, row.id);
                                    if (a == null) {
                                        ui.showErrorDialog("Assessment unavailable",
                                                "The selected assessment could not be loaded. Please try again.");
                                        return;
                                    }
                                    selectedActivity = a;
                                    showScreen(SCREEN_ACTIVITY);
                                }));
                    }
                }));
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — ACTIVITY
    // ═══════════════════════════════════════════════════════════════

    private void renderActivityScreen() {
        scanCtaSub.setText(selectedActivity.getSheetType()
                + " · " + selectedActivity.getNumItems() + " items");

        activityRenderer.renderActivityScreen(
                activityScanList, activityScansEmpty, scansHeader, scansTotalCount,
                selectedActivity, selectedClass.getId(), selectedActivity.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    // SORT PICKERS
    // ═══════════════════════════════════════════════════════════════

    private void updateSortPickers() {
        if (homeClassSortPicker != null)
            homeClassSortPicker.setText(homeRenderer.getClassSortLabel(selectedClassSort) + " \u25be");
        if (classAssessmentSortPicker != null)
            classAssessmentSortPicker.setText(classRenderer.getAssessmentSortLabel(selectedAssessmentSort) + " \u25be");
    }

    // ═══════════════════════════════════════════════════════════════
    // CAMERA
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void openCamera() {
        try {
            showCameraModeDialog();
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showCameraModeDialog() {
        final String[] cameraModes = {
                "Handheld Scan\nUse this when holding the phone and moving it closer to the sheet.",
                "Fixed Mount Scan\nUse this for elevated phone mounts where sheets slide underneath automatically."
        };

        android.content.SharedPreferences prefs =
                getSharedPreferences(CAMERA_MODE_PREFS, MODE_PRIVATE);
        int defaultSelection = prefs.getBoolean(PREF_FIXED_MOUNT_MODE, false) ? 1 : 0;
        final int[] selectedMode = {defaultSelection};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choose Camera Mode")
                .setSingleChoiceItems(cameraModes, defaultSelection, (dialog, which) -> selectedMode[0] = which)
                .setPositiveButton("Open Camera", (dialog, which) -> {
                    boolean fixedMountMode = selectedMode[0] == 1;
                    prefs.edit().putBoolean(PREF_FIXED_MOUNT_MODE, fixedMountMode).apply();
                    launchCamera(fixedMountMode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchCamera(boolean fixedMountMode) {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra(CameraActivity.EXTRA_FIXED_MOUNT_MODE, fixedMountMode);
        if (selectedSheetType != null) intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
        if (selectedClass != null) intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
        if (selectedActivity != null) intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
        startActivity(intent);
    }

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE — Room Database
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void loadDataFromDb() {
        reloadAnswerKeys(); // keep the answer-key cache fresh
        final String prevClassId = (selectedClass != null) ? selectedClass.getId() : null;
        final String prevActivityId = (selectedActivity != null) ? selectedActivity.getId() : null;
        final String prevScreen = currentScreen;

        // Always check for a scanned-in active user, regardless of local teacher state.
        repo.getActiveUser(user -> {
            activeUserFirstName = (user != null && user.firstName != null) ? user.firstName : "";
            activeUserLastName = (user != null && user.lastName != null) ? user.lastName : ""; // TEMP

            if (!activeUserFirstName.isEmpty()) {
                globalTeacherName = activeUserFirstName
                        + (!activeUserLastName.isEmpty() ? " " + activeUserLastName : "");
            }

            runOnUiThread(this::refreshTeacherNameHeader);
        });

        repo.getFirstTeacher(teacher -> {
            // Don't let the "teachers" table (name is always "" now that manual editing is
            // gone) clobber the name we just set from the scanned-in active user above.
            boolean hasScannedName = activeUserFirstName != null && !activeUserFirstName.isEmpty();

            if (teacher != null) {
                if (!hasScannedName) {
                    globalTeacherName = teacher.name != null ? teacher.name : "";
                }
                currentTeacherId = teacher.id;
                loadClassesFromDb(prevClassId, prevActivityId, prevScreen);
                return;
            }
            repo.upsertTeacher("", ensuredTeacher -> {
                if (ensuredTeacher != null) {
                    if (!hasScannedName) {
                        globalTeacherName = ensuredTeacher.name != null ? ensuredTeacher.name : "";
                    }
                    currentTeacherId = ensuredTeacher.id;
                } else {
                    if (!hasScannedName) {
                        globalTeacherName = "";
                    }
                    currentTeacherId = -1;
                }
                loadClassesFromDb(prevClassId, prevActivityId, prevScreen);
            });
        });
    }

    private void loadClassesFromDb(String prevClassId, String prevActivityId, String prevScreen) {
        repo.getAllClasses(classEntities -> {
            List<ClassFolder> loadedClasses = new ArrayList<>();
            if (classEntities == null || classEntities.isEmpty()) {
                publishResult(loadedClasses, prevClassId, prevActivityId, prevScreen);
                return;
            }
            AtomicInteger classCountdown = new AtomicInteger(classEntities.size());
            for (ClassEntity ce : classEntities) {
                //ClassFolder cf = DataMapper.toClassFolder(ce, globalTeacherName);
                // TEMP: showing the scanned-in user's full name (from UserEntity) on class cards.
// Revisit if it turns out "advisor" (the per-classroom homeroom teacher from
// sync data) is actually what should be shown instead — advisor can differ
// from the syncing teacher when they're a subject teacher, not the adviser,
// for that section. Ask OJT trainor to confirm before removing this fallback.
                String displayTeacherName = (activeUserFirstName != null && !activeUserFirstName.isEmpty())
                        ? (activeUserFirstName + (activeUserLastName != null && !activeUserLastName.isEmpty() ? " " + activeUserLastName : ""))
                        : globalTeacherName;
                ClassFolder cf = DataMapper.toClassFolder(ce, displayTeacherName);
                repo.getAssessmentsByClass(ce.id, assessmentEntities -> {
                    List<ActivityFolder> activities = new ArrayList<>();
                    if (assessmentEntities == null || assessmentEntities.isEmpty()) {
                        cf.setActivities(activities);
                        loadedClasses.add(cf);
                        if (classCountdown.decrementAndGet() == 0)
                            publishResult(loadedClasses, prevClassId, prevActivityId, prevScreen);
                        return;
                    }
                    AtomicInteger assessmentCountdown = new AtomicInteger(assessmentEntities.size());
                    for (AssessmentEntity ae : assessmentEntities) {
                        ActivityFolder af = DataMapper.toActivityFolder(ae);
                        af.setAnswerKeyId(ae.answerKeyId); // carry the soft-link into the in-memory model
                        repo.getScansByAssessment(ae.id, scanEntities -> {
                            List<ScanEntry> scanEntries = new ArrayList<>();
                            if (scanEntities == null || scanEntities.isEmpty()) {
                                af.setScans(scanEntries);
                                activities.add(af);
                                if (assessmentCountdown.decrementAndGet() == 0) {
                                    cf.setActivities(activities);
                                    loadedClasses.add(cf);
                                    if (classCountdown.decrementAndGet() == 0)
                                        publishResult(loadedClasses, prevClassId, prevActivityId, prevScreen);
                                }
                                return;
                            }
                            AtomicInteger scanCountdown = new AtomicInteger(scanEntities.size());
                            for (ScanEntity se : scanEntities) {
                                repo.getAnswersByScan(se.id, answerEntities -> {
                                    Map<Integer, String> answers = DataMapper.toAnswerMap(answerEntities);
                                    scanEntries.add(DataMapper.toScanEntry(se, answers));
                                    if (scanCountdown.decrementAndGet() == 0) {
                                        af.setScans(scanEntries);
                                        activities.add(af);
                                        if (assessmentCountdown.decrementAndGet() == 0) {
                                            cf.setActivities(activities);
                                            loadedClasses.add(cf);
                                            if (classCountdown.decrementAndGet() == 0)
                                                publishResult(loadedClasses, prevClassId, prevActivityId, prevScreen);
                                        }
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void ensureTeacherId(OMRRepository.Callback<Integer> callback) {
        if (callback == null) return;
        if (currentTeacherId > 0) {
            callback.onResult(currentTeacherId);
            return;
        }
        String fallbackName = globalTeacherName != null ? globalTeacherName : "";
        repo.upsertTeacher(fallbackName, teacher -> {
            if (teacher != null) {
                currentTeacherId = teacher.id;
                globalTeacherName = teacher.name != null ? teacher.name : "";
                callback.onResult(currentTeacherId);
            } else {
                callback.onResult(-1);
            }
        });
    }

    private void refreshTeacherNameHeader() {
        String displayName = (activeUserFirstName != null && !activeUserFirstName.isEmpty())
                ? activeUserFirstName
                : globalTeacherName;
        if (displayName != null && !displayName.isEmpty()) {
            tvTeacherName.setText(displayName);
            tvTeacherName.setTextColor(Color.parseColor("#FFFFFF"));
            tvTeacherName.setTypeface(null, Typeface.BOLD);
        } else {
            tvTeacherName.setText("Scan your QR code\nto set your name");
            tvTeacherName.setTextColor(Color.parseColor("#BFDBFE"));
            tvTeacherName.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void publishResult(List<ClassFolder> loaded,
                               String prevClassId, String prevActivityId, String prevScreen) {
        runOnUiThread(() -> {
            classFolders = loaded;
            Log.d(TAG, "Loaded " + classFolders.size() + " classes from Room");
            if (prevClassId != null) {
                selectedClass = findClassById(prevClassId);
                if (selectedClass != null && prevActivityId != null)
                    selectedActivity = findActivityById(selectedClass, prevActivityId);
                else
                    selectedActivity = null;
            }
            showScreen(prevScreen != null ? prevScreen : SCREEN_HOME);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC HELPERS for CameraActivity / PreviewActivity
    // ═══════════════════════════════════════════════════════════════

    public static boolean isLrnExists(android.content.Context context,
                                      String classId, String activityId, String lrn) {
        if (activityId == null || lrn == null) return false;
        OMRRepository r = new OMRRepository(context);
        return r.isLrnExistsSync(activityId, lrn);
    }

    public static void saveScanResult(android.content.Context context,
                                      String classId, String activityId, ScanEntry scanEntry, boolean replace) {
        if (activityId == null || scanEntry == null) return;
        OMRRepository r = new OMRRepository(context);
        ScanEntity existing = (replace && scanEntry.getLrn() != null)
                ? r.getScanByAssessmentAndLrnSync(activityId, scanEntry.getLrn()) : null;
        ScanEntity entity = DataMapper.toScanEntity(scanEntry, activityId);

        // ── Auto-score: if the assessment has an answer key, compute real score now ──
        com.example.omrscanner.database.entities.AssessmentEntity assessment =
                r.getAssessmentByIdSync(activityId);
        if (assessment != null && assessment.answerKeyId != null) {
            com.example.omrscanner.database.entities.AnswerKeyEntity key =
                    r.getAnswerKeyByIdSync(assessment.answerKeyId);
            if (key != null && key.answers != null && !key.answers.isEmpty()) {
                String[] correctAnswers = key.answers.split(",");
                java.util.Map<Integer, String> studentAnswers = scanEntry.getAnswers();
                int score = 0;
                for (int i = 0; i < correctAnswers.length; i++) {
                    String k = correctAnswers[i].trim();
                    if (k.isEmpty() || k.equals("?")) continue;
                    String s = (studentAnswers != null && studentAnswers.containsKey(i + 1))
                            ? studentAnswers.get(i + 1) : "";
                    if (k.equals(s)) score++;
                }
                entity.score = score;
            }
        }

        if (existing != null) {
            entity.id = existing.id;
            r.updateScan(entity, null);
            r.deleteAnswersByScan(existing.id,
                    done -> {
                        r.insertAnswersFromMap(existing.id, scanEntry.getAnswers(), null);
                        ClassExporter.autoSaveClassData(context, classId, activityId);
                    });
        } else {
            r.insertScan(entity, newId -> {
                if (newId != null && newId > 0)
                    r.insertAnswersFromMap(newId.intValue(), scanEntry.getAnswers(), null);
                ClassExporter.autoSaveClassData(context, classId, activityId);
            });
        }

// ── Standalone write to student_lrn ──
        r.insertStudentLrn(scanEntry.getLrn(), classId, null);
    }

    // ═══════════════════════════════════════════════════════════════
    // FIND HELPERS
    // ═══════════════════════════════════════════════════════════════

    private ClassFolder findClassById(String classId) {
        if (classId == null) return null;
        for (ClassFolder cls : classFolders)
            if (cls.getId().equals(classId)) return cls;
        return null;
    }

    private ActivityFolder findActivityById(ClassFolder cls, String activityId) {
        if (cls == null || activityId == null || cls.getActivities() == null) return null;
        for (ActivityFolder act : cls.getActivities())
            if (act.getId().equals(activityId)) return act;
        return null;
    }

    /** Records "students were successfully synced for this class right now." */
    public static void markStudentsSynced(android.content.Context context, String localClassId) {
        if (localClassId == null) return;
        context.getSharedPreferences(SYNC_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putLong(SYNC_PREFS_KEY_PREFIX + localClassId, System.currentTimeMillis())
                .apply();
    }

    /** True if this class's roster was synced within the last STUDENT_SYNC_STALE_MS. */
    public static boolean hasSyncedStudentsRecently(android.content.Context context, String localClassId) {
        if (localClassId == null) return false;
        long last = context.getSharedPreferences(SYNC_PREFS, android.content.Context.MODE_PRIVATE)
                .getLong(SYNC_PREFS_KEY_PREFIX + localClassId, 0L);
        if (last == 0L) return false;
        return (System.currentTimeMillis() - last) < STUDENT_SYNC_STALE_MS;
    }
    /**
     * Reusable version of the students-sync network call, so other screens
     * (e.g. the "LRN Not Recognized" dialog in ResultActivity) can trigger a
     * sync without needing a DashboardActivity instance. Identical
     * request/response handling to performAssessmentSync — keep them in sync
     * if this logic ever changes.
     */
    public static void syncStudentsForClass(android.content.Context context, String localClassId, int classroomId, String serverIp) {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> android.widget.Toast.makeText(context, "Syncing students…", android.widget.Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {

                java.net.URL url = new java.net.URL(serverIp + ASSESSMENT_SYNC_PATH);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("classroom_id", classroomId);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                String responseBody = sb.toString();
                android.util.Log.d("OMR_STUDENT_SYNC", "HTTP " + code + " — classroom_id=" + classroomId + " — raw response: " + responseBody);

                org.json.JSONObject root = new org.json.JSONObject(responseBody);
                org.json.JSONArray data = root.optJSONArray("data");

                // A successful response — even with 0 students — means the
                // roster is confirmed up to date as of right now.
                markStudentsSynced(context, localClassId);

                com.example.omrscanner.database.OMRRepository repo =
                        new com.example.omrscanner.database.OMRRepository(context);

                if (data == null || data.length() == 0) {
                    mainHandler.post(() -> android.widget.Toast.makeText(context,
                            "No students found for this class.", android.widget.Toast.LENGTH_SHORT).show());
                } else {
                    int count = data.length();
                    for (int i = 0; i < count; i++) {
                        org.json.JSONObject s = data.getJSONObject(i);
                        String lrn = s.optString("lrn", null);
                        int sectionId = s.optInt("sectionId");
                        int gradeLevelId = s.optInt("gradeLevelId");
                        int studentClassroomId = s.optInt("classroomId");
                        if (lrn != null) {
                            repo.insertStudentLrnFromSync(lrn, localClassId, sectionId, gradeLevelId, studentClassroomId, null);
                        }
                    }
                    final int savedCount = count;
                    mainHandler.post(() -> android.widget.Toast.makeText(context,
                            "Synced " + savedCount + " student" + (savedCount != 1 ? "s" : ""), android.widget.Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                android.util.Log.e("OMR_STUDENT_SYNC", "Sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                mainHandler.post(() -> new android.app.AlertDialog.Builder(context)
                        .setTitle("Sync failed")
                        .setMessage("Could not sync students: " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // DialogHost INTERFACE IMPL
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String getGlobalTeacherName() {
        return globalTeacherName;
    }

    @Override
    public void setGlobalTeacherName(String n) {
        globalTeacherName = n;
        tvTeacherName.setText(n);
        tvTeacherName.setTextColor(Color.parseColor("#FFFFFF"));
        tvTeacherName.setTypeface(null, Typeface.BOLD);
        if (SCREEN_CLASS.equals(currentScreen) && selectedClass != null)
            classTeacherLabel.setText("Teacher: " + n);
    }

    @Override
    public void setCurrentTeacherId(int id) {
        currentTeacherId = id;
    }

    @Override
    public int getCurrentTeacherId() {
        return currentTeacherId;
    }

    @Override
    public List<ClassFolder> getClassFolders() {
        return classFolders;
    }

    @Override
    public ClassFolder getSelectedClass() {
        return selectedClass;
    }

    @Override
    public void setSelectedClass(ClassFolder c) {
        selectedClass = c;
    }

    @Override
    public ActivityFolder getSelectedActivity() {
        return selectedActivity;
    }

    @Override
    public void setSelectedActivity(ActivityFolder a) {
        selectedActivity = a;
    }

    @Override
    public void setSelectedSheetType(String t) {
        selectedSheetType = t;
    }

    @Override
    public List<AnswerKeyEntity> getAnswerKeys() {
        return answerKeys;
    }

    @Override
    public void reloadAnswerKeys() {
        repo.getAllAnswerKeys(keys -> runOnUiThread(() ->
                answerKeys = (keys != null) ? keys : new ArrayList<>()));
    }
}
