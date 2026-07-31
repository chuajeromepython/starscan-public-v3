package com.example.omrscanner;

// this page manages the dashboard

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import android.widget.ImageView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.example.omrscanner.database.projections.AnswerKeyLinkInfo;
import com.example.omrscanner.database.projections.AnswerKeyLinkedAssessment;
import com.example.omrscanner.database.projections.ScanListRow;
import com.example.omrscanner.dashboard.ScansScreenRenderer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
    private static final String PREF_FIXED_MOUNT_MODE = "fixed_mount_mode"; // kept, no longer surfaced in UI
    private static final String PREF_TILT_AGNOSTIC_MODE = "tilt_agnostic_mode";
    private static final String PREF_BASIC_MODE = "basic_mode";

    // ── Intent extras used by CameraActivity / PreviewActivity ──
    public static final String EXTRA_SHEET_TYPE = "sheet_type";
    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";
    public static final String EXTRA_ANSWER_KEY_ID = "answer_key_id";

    // ── Screen names ──
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_CLASS = "class";
    private static final String SCREEN_ACTIVITY = "activity";
    private static final String SCREEN_USER = "user";
    private static final String SCREEN_ASSESSMENTS = "assessments";
    private static final String SCREEN_ANSWERKEYS = "answerkeys";
    private static final String SCREEN_SCANS = "scans";

    // ── Sort constants (delegated to renderers, kept here for initialisation) ──
    private static final String CLASS_SORT_NEWEST = HomeScreenRenderer.CLASS_SORT_NEWEST;
    private static final String ASSESSMENT_SORT_NEWEST = ClassScreenRenderer.ASSESSMENT_SORT_NEWEST;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private OMRRepository repo;
    private int currentTeacherId = -1;

    private String currentScreen = SCREEN_HOME;
    private String screenBeforeChromeTab = SCREEN_HOME;
    private boolean activityOpenedFromAssessmentsTab = false;
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
    private Map<String, AnswerKeyLinkInfo> answerKeyLinkInfo = new java.util.HashMap<>();
    private Map<String, List<AnswerKeyLinkedAssessment>> answerKeyLinkedAssessments = new java.util.HashMap<>();

    private String classSearchQuery = "";
    private String selectedClassGradeFilter = null;
    private String selectedClassSchoolYearFilter = null;
    private String selectedClassSort = CLASS_SORT_NEWEST;
    private String homeGroupBy = "GRADE"; // GRADE or YEAR

    private String assessmentSearchQuery = "";
    private String selectedAssessmentSort = ASSESSMENT_SORT_NEWEST;
    private String classGroupBy = "SHEET"; // SHEET or TYPE
    private String selectedClassTypeFilter = null;

    private String myAssessmentsSearchQuery = "";
    private String selectedMyAssessmentsSort = ASSESSMENT_SORT_NEWEST;
    private String myAssessmentsGroupBy = "SHEET"; // SHEET, TYPE, or CLASS
    private String selectedMyAssessmentsSheetFilter = null;
    private String selectedMyAssessmentsTypeFilter = null;
    private String selectedMyAssessmentsClassFilter = null;

    private String answerKeysSearchQuery = "";
    private String selectedAnswerKeysSort = ASSESSMENT_SORT_NEWEST;
    private String selectedAnswerKeysSheetFilter = null;
    private String selectedAnswerKeysLinkFilter = null; // null, "LINKED", or "UNLINKED"
    private String selectedAnswerKeysAssessmentFilter = null; // linked assessment id, null = All
    private String answerKeysGroupBy = "SHEET"; // SHEET or STATUS

    private int homeQueryGeneration = 0;
    private int assessmentQueryGeneration = 0;

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingHomeSearchRunnable;
    private Runnable pendingAssessmentSearchRunnable;
    private Runnable pendingMyAssessmentsSearchRunnable;
    private Runnable pendingAnswerKeysSearchRunnable;

    private static final long LAST_SYNCED_TICK_MS = 60_000; // re-render "X ago" every minute
    private final Handler lastSyncedTicker = new Handler(Looper.getMainLooper());
    private final Runnable lastSyncedTickRunnable = new Runnable() {
        @Override
        public void run() {
            updateLastSyncedLabel();
            lastSyncedTicker.postDelayed(this, LAST_SYNCED_TICK_MS);
        }
    };

    // ── Helpers ──
    private DashboardUiHelper ui;
    private HomeScreenRenderer homeRenderer;
    private ClassScreenRenderer classRenderer;
    private ActivityScreenRenderer activityRenderer;
    private DashboardDialogs dialogs;

    // ═══════════════════════════════════════════════════════════════
    // VIEWS
    // ═══════════════════════════════════════════════════════════════

    private ImageButton btnBack, btnUpload, btnHelp;
    private TextView topBarTitle, topBarBadge;
    private TextView tvTeacherName;
    private TextView tvLastSynced;
    private LinearLayout teacherNameRow;

    private View screenHome, screenAssessments, screenAnswerKeys, screenScans;
    private ScrollView screenClass, screenActivity, screenUser;

    private android.widget.FrameLayout bottomNav;
    private LinearLayout navHomeTab, navUserTab, navAssessmentsTab, navAnswerKeysTab, navScansTab;
    private ImageView navHomeIcon, navUserIcon, navAssessmentsIcon, navAnswerKeysIcon, navScansIcon;
    private TextView navHomeLabel, navUserLabel, navAssessmentsLabel, navAnswerKeysLabel, navScansLabel;

    private LinearLayout scansAllList, scansAllEmpty;
    private TextView scansAllCount, scansAllSummaryCount, scansAllSummaryTeacher;
    private ScansScreenRenderer scansRenderer;

    private EditText scansSearchInput;
    private TextView scansSortPicker;
    private LinearLayout scansFilterPanel;
    private android.widget.ImageView scansFilterToggle;
    private LinearLayout scansSheetTabs;
    private LinearLayout scansClassTabs;
    private LinearLayout scansAssessmentTabs;
    private LinearLayout scansNeedsCorrectionTabs;
    private LinearLayout scansGroupSwitcher;
    private LinearLayout scansSheetFilterBlock, scansClassFilterBlock, scansAssessmentFilterBlock, scansNeedsCorrectionFilterBlock;
    private boolean scansFilterPanelVisible = false;

    private String scansSearchQuery = "";
    private String selectedScansSort = ClassScreenRenderer.ASSESSMENT_SORT_NEWEST;
    private String selectedScansSheetFilter = null;
    private String selectedScansClassFilter = null;
    private String selectedScansAssessmentFilter = null;
    private String selectedScansNeedsCorrectionFilter = null; // null, "YES", or "NO"
    private String scansGroupBy = "SHEET"; // SHEET, CLASS, ASSESSMENT, or CORRECTION
    private Runnable pendingScansSearchRunnable;
    private TextView userNameText, userSchoolText, userLastSynced;
    private TextView userStatClasses, userStatAssessments, userStatScans, userStatAnswerKeys;
    private TextView userDetailFullName, userDetailUsername, userDetailUserId, userDetailSchool,
            userDetailServerIp, userDetailStatus, userDetailMemberSince, userDetailLastUpdated;
    private LinearLayout userRescanRow;

    private LinearLayout homeEmpty, homeClassList;
    private TextView homeSummaryClassCount, homeSummaryAssessmentCount;
    private EditText homeClassSearchInput;
    private TextView homeClassSortPicker;
    private LinearLayout homeGradeFilterChips, homeSchoolYearFilterChips;
    private LinearLayout homeGroupSwitcher, homeGradeFilterBlock, homeSchoolYearFilterBlock;
    private LinearLayout homeFilterPanel;
    private android.widget.ImageView homeFilterToggle;
    private boolean homeFilterPanelVisible = false;

    private TextView classTeacherLabel, classNameLabel, classActivityCount, homeTeacherLabel;
    private LinearLayout classEmpty, classActivityList, classSheetTabs, classGroupSwitcher;
    private TextView classAssessmentCount;
    private EditText classAssessmentSearchInput;
    private TextView classAssessmentSortPicker;
    private LinearLayout classAssessmentFilterPanel;
    private android.widget.ImageView classAssessmentFilterToggle;
    private boolean classAssessmentFilterPanelVisible = false;
    private LinearLayout assessmentsAllList, assessmentsAllEmpty;
    private TextView assessmentsAllCount;
    private TextView assessmentsSummaryTeacher, assessmentsSummaryCount, assessmentsSummaryClassCount;
    private LinearLayout myAssessmentsGroupSwitcher, myAssessmentsSheetTabs;
    private EditText myAssessmentsSearchInput;
    private TextView myAssessmentsSortPicker;
    private LinearLayout myAssessmentsFilterPanel;
    private android.widget.ImageView myAssessmentsFilterToggle;
    private boolean myAssessmentsFilterPanelVisible = false;

    private LinearLayout answerKeysAllList, answerKeysAllEmpty;
    private TextView answerKeysAllCount;
    private TextView answerKeysSummaryCount, answerKeysSummarySheetTypes, answerKeysSummaryTeacher;
    private View answerKeysHeaderAddBtn;
    private LinearLayout answerKeysSheetTabs;
    private LinearLayout answerKeysLinkStatusTabs;
    private LinearLayout answerKeysGroupSwitcher;
    private LinearLayout answerKeysSheetFilterBlock, answerKeysLinkStatusFilterBlock;
    private TextView answerKeysAssessmentFilterPicker;
    private EditText answerKeysSearchInput;
    private TextView answerKeysSortPicker;
    private LinearLayout answerKeysFilterPanel;
    private android.widget.ImageView answerKeysFilterToggle;
    private boolean answerKeysFilterPanelVisible = false;
    private View classAssessmentsHeaderAddBtn;
    private View assessmentsHeaderAddBtn;

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
    private static final String PREF_LAST_GLOBAL_SYNC = "last_global_sync_millis";
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
        scansRenderer = new ScansScreenRenderer(this, ui);
        dialogs = new DashboardDialogs(this, ui, repo, this);

        initViews();
        initBackHandler();
        loadDataFromDb();
        checkServerReachability();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableFullScreen();
        loadDataFromDb();
        lastSyncedTicker.removeCallbacks(lastSyncedTickRunnable);
        lastSyncedTicker.postDelayed(lastSyncedTickRunnable, LAST_SYNCED_TICK_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        lastSyncedTicker.removeCallbacks(lastSyncedTickRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingHomeSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingHomeSearchRunnable);
        if (pendingAssessmentSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingAssessmentSearchRunnable);
        lastSyncedTicker.removeCallbacks(lastSyncedTickRunnable);
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

        // Let content draw under the display cutout (punch-hole camera)
        // instead of leaving a reserved black bar there. The theme sets
        // shortEdges for API 28-29; on API 30+ we explicitly request
        // ALWAYS so the cutout area stays available even in this
        // hidden-status-bar / immersive state.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().setAttributes(lp);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BACK HANDLER
    // ═══════════════════════════════════════════════════════════════

    private void initBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (SCREEN_USER.equals(currentScreen)
                        || SCREEN_ASSESSMENTS.equals(currentScreen)
                        || SCREEN_ANSWERKEYS.equals(currentScreen)) {
                    selectHomeTab();
                } else if (SCREEN_ACTIVITY.equals(currentScreen)) {
                    selectedActivity = null;
                    showScreen(activityOpenedFromAssessmentsTab ? SCREEN_ASSESSMENTS : SCREEN_CLASS);
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
        btnHelp = findViewById(R.id.btnHelp);
        topBarTitle = findViewById(R.id.topBarTitle);
        topBarBadge = findViewById(R.id.topBarBadge);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvLastSynced = findViewById(R.id.tvLastSynced);
        teacherNameRow = findViewById(R.id.teacherNameRow);

        screenHome = findViewById(R.id.screenHome);
        screenAssessments = findViewById(R.id.screenAssessments);
        screenAnswerKeys = findViewById(R.id.screenAnswerKeys);
        screenScans = findViewById(R.id.screenScans);
        screenClass = findViewById(R.id.screenClass);
        screenActivity = findViewById(R.id.screenActivity);
        screenUser = findViewById(R.id.screenUser);

        bottomNav = findViewById(R.id.bottomNav);
        navHomeTab = findViewById(R.id.navHomeTab);
        navUserTab = findViewById(R.id.navUserTab);
        navAssessmentsTab = findViewById(R.id.navAssessmentsTab);
        navAnswerKeysTab = findViewById(R.id.navAnswerKeysTab);
        navScansTab = findViewById(R.id.navScansTab);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navUserIcon = findViewById(R.id.navUserIcon);
        navAssessmentsIcon = findViewById(R.id.navAssessmentsIcon);
        navAnswerKeysIcon = findViewById(R.id.navAnswerKeysIcon);
        navScansIcon = findViewById(R.id.navScansIcon);
        navHomeLabel = findViewById(R.id.navHomeLabel);
        navUserLabel = findViewById(R.id.navUserLabel);
        navAssessmentsLabel = findViewById(R.id.navAssessmentsLabel);
        navAnswerKeysLabel = findViewById(R.id.navAnswerKeysLabel);
        navScansLabel = findViewById(R.id.navScansLabel);

        scansAllList = findViewById(R.id.scansAllList);
        scansAllEmpty = findViewById(R.id.scansAllEmpty);
        scansAllCount = findViewById(R.id.scansAllCount);
        scansAllSummaryCount = findViewById(R.id.scansAllSummaryCount);
        scansAllSummaryTeacher = findViewById(R.id.scansAllSummaryTeacher);
        scansSearchInput = findViewById(R.id.scansSearchInput);
        scansSortPicker = findViewById(R.id.scansSortPicker);
        scansFilterPanel = findViewById(R.id.scansFilterPanel);
        scansFilterToggle = findViewById(R.id.scansFilterToggle);
        scansSheetTabs = findViewById(R.id.scansSheetTabs);
        scansClassTabs = findViewById(R.id.scansClassTabs);
        scansAssessmentTabs = findViewById(R.id.scansAssessmentTabs);
        scansNeedsCorrectionTabs = findViewById(R.id.scansNeedsCorrectionTabs);
        scansGroupSwitcher = findViewById(R.id.scansGroupSwitcher);
        scansSheetFilterBlock = findViewById(R.id.scansSheetFilterBlock);
        scansClassFilterBlock = findViewById(R.id.scansClassFilterBlock);
        scansAssessmentFilterBlock = findViewById(R.id.scansAssessmentFilterBlock);
        scansNeedsCorrectionFilterBlock = findViewById(R.id.scansNeedsCorrectionFilterBlock);
        userNameText = findViewById(R.id.userNameText);
        userSchoolText = findViewById(R.id.userSchoolText);
        userLastSynced = findViewById(R.id.userLastSynced);
        userRescanRow = findViewById(R.id.userRescanRow);
        userStatClasses = findViewById(R.id.userStatClasses);
        userStatAssessments = findViewById(R.id.userStatAssessments);
        userStatScans = findViewById(R.id.userStatScans);
        userStatAnswerKeys = findViewById(R.id.userStatAnswerKeys);
        userDetailFullName = findViewById(R.id.userDetailFullName);
        userDetailUsername = findViewById(R.id.userDetailUsername);
        userDetailUserId = findViewById(R.id.userDetailUserId);
        userDetailSchool = findViewById(R.id.userDetailSchool);
        userDetailServerIp = findViewById(R.id.userDetailServerIp);
        userDetailStatus = findViewById(R.id.userDetailStatus);
        userDetailMemberSince = findViewById(R.id.userDetailMemberSince);
        userDetailLastUpdated = findViewById(R.id.userDetailLastUpdated);

        homeEmpty = findViewById(R.id.homeEmpty);
        homeClassList = findViewById(R.id.homeClassList);
        homeClassSearchInput = findViewById(R.id.homeClassSearchInput);
        homeClassSortPicker = findViewById(R.id.homeClassSortPicker);
        homeGradeFilterChips = findViewById(R.id.homeGradeFilterChips);
        homeSchoolYearFilterChips = findViewById(R.id.homeSchoolYearFilterChips);
        homeGroupSwitcher = findViewById(R.id.homeGroupSwitcher);
        homeGradeFilterBlock = findViewById(R.id.homeGradeFilterBlock);
        homeSchoolYearFilterBlock = findViewById(R.id.homeSchoolYearFilterBlock);
        homeFilterPanel = findViewById(R.id.homeFilterPanel);
        homeFilterToggle = findViewById(R.id.homeFilterToggle);
        homeSummaryClassCount = findViewById(R.id.homeSummaryClassCount);
        homeSummaryAssessmentCount = findViewById(R.id.homeSummaryAssessmentCount);

        classTeacherLabel = findViewById(R.id.classTeacherLabel);
        homeTeacherLabel = findViewById(R.id.homeTeacherLabel);
        classNameLabel = findViewById(R.id.classNameLabel);
        classActivityCount = findViewById(R.id.classActivityCount);
        classEmpty = findViewById(R.id.classEmpty);
        classActivityList = findViewById(R.id.classActivityList);
        classSheetTabs = findViewById(R.id.classSheetTabs);
        classGroupSwitcher = findViewById(R.id.classGroupSwitcher);
        classAssessmentCount = findViewById(R.id.classAssessmentCount);
        classAssessmentSearchInput = findViewById(R.id.classAssessmentSearchInput);
        classAssessmentSortPicker = findViewById(R.id.classAssessmentSortPicker);
        classAssessmentFilterPanel = findViewById(R.id.classAssessmentFilterPanel);
        classAssessmentFilterToggle = findViewById(R.id.classAssessmentFilterToggle);
        assessmentsAllList = findViewById(R.id.assessmentsAllList);
        assessmentsAllEmpty = findViewById(R.id.assessmentsAllEmpty);
        assessmentsAllCount = findViewById(R.id.assessmentsAllCount);
        assessmentsSummaryTeacher = findViewById(R.id.assessmentsSummaryTeacher);
        assessmentsSummaryCount = findViewById(R.id.assessmentsSummaryCount);
        assessmentsSummaryClassCount = findViewById(R.id.assessmentsSummaryClassCount);
        myAssessmentsGroupSwitcher = findViewById(R.id.myAssessmentsGroupSwitcher);
        myAssessmentsSheetTabs = findViewById(R.id.myAssessmentsSheetTabs);
        myAssessmentsSearchInput = findViewById(R.id.myAssessmentsSearchInput);
        myAssessmentsSortPicker = findViewById(R.id.myAssessmentsSortPicker);
        myAssessmentsFilterPanel = findViewById(R.id.myAssessmentsFilterPanel);
        myAssessmentsFilterToggle = findViewById(R.id.myAssessmentsFilterToggle);

        answerKeysAllList = findViewById(R.id.answerKeysAllList);
        answerKeysAllEmpty = findViewById(R.id.answerKeysAllEmpty);
        answerKeysAllCount = findViewById(R.id.answerKeysAllCount);
        answerKeysSheetTabs = findViewById(R.id.answerKeysSheetTabs);
        answerKeysLinkStatusTabs = findViewById(R.id.answerKeysLinkStatusTabs);
        answerKeysGroupSwitcher = findViewById(R.id.answerKeysGroupSwitcher);
        answerKeysSheetFilterBlock = findViewById(R.id.answerKeysSheetFilterBlock);
        answerKeysLinkStatusFilterBlock = findViewById(R.id.answerKeysLinkStatusFilterBlock);
        answerKeysAssessmentFilterPicker = findViewById(R.id.answerKeysAssessmentFilterPicker);
        answerKeysSearchInput = findViewById(R.id.answerKeysSearchInput);
        answerKeysSortPicker = findViewById(R.id.answerKeysSortPicker);
        answerKeysFilterPanel = findViewById(R.id.answerKeysFilterPanel);
        answerKeysFilterToggle = findViewById(R.id.answerKeysFilterToggle);
        answerKeysSummaryCount = findViewById(R.id.answerKeysSummaryCount);
        answerKeysSummarySheetTypes = findViewById(R.id.answerKeysSummarySheetTypes);
        answerKeysSummaryTeacher = findViewById(R.id.answerKeysSummaryTeacher);
        answerKeysHeaderAddBtn = findViewById(R.id.answerKeysHeaderAddBtn);
        classAssessmentsHeaderAddBtn = findViewById(R.id.classAssessmentsHeaderAddBtn);
        assessmentsHeaderAddBtn = findViewById(R.id.assessmentsHeaderAddBtn);
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

        userRescanRow.setOnClickListener(v -> showQrGuide());

        navHomeTab.setOnClickListener(v -> selectHomeTab());
        navUserTab.setOnClickListener(v -> selectUserTab());
        navAssessmentsTab.setOnClickListener(v -> selectAssessmentsTab());
        navAnswerKeysTab.setOnClickListener(v -> selectAnswerKeysTab());
        navScansTab.setOnClickListener(v -> selectScansTab());

        btnBack.setOnClickListener(v -> navigateBack());
        btnUpload.setOnClickListener(v -> dialogs.showGlobalUploadClassDialog());
        btnHelp.setOnClickListener(v -> showFaqDialog());
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

        answerKeysHeaderAddBtn.setOnClickListener(v ->
                showDisclaimerThen(() -> dialogs.showNewAnswerKeyDialog(null)));

        classAssessmentsHeaderAddBtn.setOnClickListener(v -> dialogs.showNewActivityDialog());

        assessmentsHeaderAddBtn.setOnClickListener(v -> showAssessmentClassPickerDialog());

        fabTestRow.setOnClickListener(v -> {
            closeFabMenu();
            new DataInspector(this).printAll();
            new DataExporter(this).exportAll();
        });

        fabSyncRow.setOnClickListener(v -> {
            closeFabMenu();
            onSyncClicked();
        });

        findViewById(R.id.homeSyncClassRow).setOnClickListener(v -> onSyncClicked());
        findViewById(R.id.classSyncStudentsRow).setOnClickListener(v -> onAssessmentSyncClicked());

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
                showScreen(activityOpenedFromAssessmentsTab ? SCREEN_ASSESSMENTS : SCREEN_CLASS);
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

        myAssessmentsSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                myAssessmentsSearchQuery = s != null ? s.toString().trim() : "";
                scheduleMyAssessmentsSearchRefresh();
            }
        });

        answerKeysSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                answerKeysSearchQuery = s != null ? s.toString().trim() : "";
                scheduleAnswerKeysSearchRefresh();
            }
        });

        scansSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scansSearchQuery = s != null ? s.toString().trim() : "";
                if (SCREEN_SCANS.equals(currentScreen)) renderScansScreen();
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
        myAssessmentsSortPicker.setOnClickListener(v ->
                classRenderer.showAssessmentSortDialog(selectedMyAssessmentsSort, key -> {
                    selectedMyAssessmentsSort = key;
                    updateSortPickers();
                    if (SCREEN_ASSESSMENTS.equals(currentScreen)) renderAssessmentsScreen();
                }));
        answerKeysSortPicker.setOnClickListener(v ->
                classRenderer.showAssessmentSortDialog(selectedAnswerKeysSort, key -> {
                    selectedAnswerKeysSort = key;
                    updateSortPickers();
                    if (SCREEN_ANSWERKEYS.equals(currentScreen)) renderAnswerKeysScreen();
                }));

        scansSortPicker.setOnClickListener(v ->
                classRenderer.showAssessmentSortDialog(selectedScansSort, key -> {
                    selectedScansSort = key;
                    updateSortPickers();
                    if (SCREEN_SCANS.equals(currentScreen)) renderScansScreen();
                }));

        updateSortPickers();

        homeFilterToggle.setOnClickListener(v -> {
            homeFilterPanelVisible = !homeFilterPanelVisible;
            homeFilterPanel.setVisibility(homeFilterPanelVisible ? View.VISIBLE : View.GONE);
            homeRenderer.updateFilterToggleAppearance(homeFilterToggle, homeFilterPanelVisible,
                    selectedClassGradeFilter, selectedClassSchoolYearFilter, selectedClassSort);
        });

        classAssessmentFilterToggle.setOnClickListener(v -> {
            classAssessmentFilterPanelVisible = !classAssessmentFilterPanelVisible;
            classAssessmentFilterPanel.setVisibility(classAssessmentFilterPanelVisible ? View.VISIBLE : View.GONE);
            classRenderer.updateAssessmentFilterToggleAppearance(classAssessmentFilterToggle,
                    classAssessmentFilterPanelVisible,
                    !ASSESSMENT_SORT_NEWEST.equals(selectedAssessmentSort)
                            || selectedSheetFilter != null || selectedClassTypeFilter != null);
        });

        myAssessmentsFilterToggle.setOnClickListener(v -> {
            myAssessmentsFilterPanelVisible = !myAssessmentsFilterPanelVisible;
            myAssessmentsFilterPanel.setVisibility(myAssessmentsFilterPanelVisible ? View.VISIBLE : View.GONE);
            classRenderer.updateAssessmentFilterToggleAppearance(myAssessmentsFilterToggle,
                    myAssessmentsFilterPanelVisible,
                    !ASSESSMENT_SORT_NEWEST.equals(selectedMyAssessmentsSort)
                            || selectedMyAssessmentsSheetFilter != null
                            || selectedMyAssessmentsTypeFilter != null
                            || selectedMyAssessmentsClassFilter != null);
        });

        answerKeysFilterToggle.setOnClickListener(v -> {
            answerKeysFilterPanelVisible = !answerKeysFilterPanelVisible;
            answerKeysFilterPanel.setVisibility(answerKeysFilterPanelVisible ? View.VISIBLE : View.GONE);
            classRenderer.updateAssessmentFilterToggleAppearance(answerKeysFilterToggle,
                    answerKeysFilterPanelVisible,
                    !ASSESSMENT_SORT_NEWEST.equals(selectedAnswerKeysSort)
                            || selectedAnswerKeysSheetFilter != null
                            || selectedAnswerKeysLinkFilter != null
                            || selectedAnswerKeysAssessmentFilter != null);
        });

        scansFilterToggle.setOnClickListener(v -> {
            scansFilterPanelVisible = !scansFilterPanelVisible;
            scansFilterPanel.setVisibility(scansFilterPanelVisible ? View.VISIBLE : View.GONE);
            classRenderer.updateAssessmentFilterToggleAppearance(scansFilterToggle,
                    scansFilterPanelVisible,
                    !ASSESSMENT_SORT_NEWEST.equals(selectedScansSort)
                            || selectedScansSheetFilter != null
                            || selectedScansClassFilter != null
                            || selectedScansAssessmentFilter != null
                            || selectedScansNeedsCorrectionFilter != null);
        });

        answerKeysAssessmentFilterPicker.setOnClickListener(v -> {
            java.util.LinkedHashMap<String, String> options = buildAnswerKeysLinkedAssessmentOptions();
            List<String> labels = new ArrayList<>();
            List<String> values = new ArrayList<>();
            labels.add("All");
            values.add(null);
            for (java.util.Map.Entry<String, String> e : options.entrySet()) {
                labels.add(e.getValue());
                values.add(e.getKey());
            }
            classRenderer.showChoiceFilterDialog("Filter by Linked Assessment", labels, values,
                    selectedAnswerKeysAssessmentFilter, id -> {
                        selectedAnswerKeysAssessmentFilter = id;
                        renderAnswerKeysScreen();
                    });
        });
    }

    /** Class id -> display name, for the Scans tab's class filter. */
    private java.util.LinkedHashMap<String, String> buildScansClassOptions() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (ClassFolder cf : classFolders) {
            map.put(cf.getId(), cf.getDisplayName());
        }
        return map;
    }

    /** Assessment id -> "Name — Class", for the Scans tab's assessment filter. */
    private java.util.LinkedHashMap<String, String> buildScansAssessmentOptions() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (ClassFolder cf : classFolders) {
            if (cf.getActivities() == null) continue;
            for (ActivityFolder af : cf.getActivities()) {
                map.put(af.getId(), af.getName() + " \u2014 " + cf.getDisplayName());
            }
        }
        return map;
    }

    /** Distinct sheet types currently in use, for the Scans tab's sheet-type filter. */
    private java.util.LinkedHashMap<String, String> buildScansSheetTypeOptions() {
        java.util.TreeSet<String> types = new java.util.TreeSet<>();
        for (ClassFolder cf : classFolders) {
            if (cf.getActivities() == null) continue;
            for (ActivityFolder af : cf.getActivities()) {
                if (af.getSheetType() != null && !af.getSheetType().isEmpty()) types.add(af.getSheetType());
            }
        }
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (String t : types) map.put(t, t);
        return map;
    }

    private void scheduleScansSearchRefresh() {
        if (pendingScansSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingScansSearchRunnable);
        pendingScansSearchRunnable = () -> {
            if (SCREEN_SCANS.equals(currentScreen)) renderScansScreen();
        };
        searchDebounceHandler.postDelayed(pendingScansSearchRunnable, 220);
    }

    /**
     * Entry point for creating a new assessment from the All Assessments tab, where
     * there's no class already in context. Shows a simple class picker first, then
     * reuses the normal new-assessment flow for whichever class was chosen.
     */
    private void showAssessmentClassPickerDialog() {
        if (classFolders == null || classFolders.isEmpty()) {
            ui.showErrorDialog("No classes yet",
                    "Create a class first before adding an assessment.");
            return;
        }

        String[] classNames = new String[classFolders.size()];
        for (int i = 0; i < classFolders.size(); i++) {
            classNames[i] = classFolders.get(i).getDisplayName();
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this, R.style.ThemeOverlay_OMRScanner_Dialog)
                .setTitle("Select a class")
                .setItems(classNames, (dialog, which) -> {
                    selectedClass = classFolders.get(which);
                    dialogs.showNewActivityDialog();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            performAssessmentSync(selectedClass.getClassroomId(), user.serverIp);
        });
    }

    private void performAssessmentSync(int classroomId, String serverIp) {
        String localClassId = (selectedClass != null) ? selectedClass.getId() : null;
        syncStudentsForClass(this, localClassId, classroomId, serverIp);
    }

    // Sends the aggregate data of students along with their LRNs and answers to the system
    @Override
    public void uploadAssessment(ActivityFolder act, ClassFolder cls, int assessmentId) {
        if (cls.getClassroomId() == null) {
            ui.showErrorDialog("Missing classroom ID",
                    "This class wasn't synced from the server, so it has no classroom ID to upload against.");
            return;
        }

        // ── Block upload while any scan still has an unresolved multi-letter
        //    answer (e.g. "AC") — these must be corrected in the scan list first.
        java.util.List<ScanEntry> scans = act.getScans();
        java.util.List<String> pending = new java.util.ArrayList<>();
        if (scans != null) {
            for (ScanEntry s : scans) {
                if (s.needsAnswerCorrection()) {
                    pending.add(s.getLrn() != null && !s.getLrn().trim().isEmpty()
                            ? s.getLrn() : "(missing LRN)");
                }
            }
        }
        if (!pending.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append(pending.size()).append(" scan(s) have questions with more than one bubble marked ")
                    .append("and must be corrected before this assessment can be uploaded:\n\n");
            for (String lrn : pending) {
                msg.append("• ").append(lrn).append("\n");
            }
            msg.append("\nOpen each highlighted scan (yellow border) in the scan list to fix it.");
            ui.showErrorDialog("Corrections needed", msg.toString());
            return;
        }


        java.io.File csvFile = ClassExporter.getAssessmentCsvFile(cls, act);
        if (!csvFile.exists() || csvFile.length() == 0) {
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
                fabClassRow.setVisibility(View.GONE);
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

    private void scheduleMyAssessmentsSearchRefresh() {
        if (pendingMyAssessmentsSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingMyAssessmentsSearchRunnable);
        pendingMyAssessmentsSearchRunnable = () -> {
            if (SCREEN_ASSESSMENTS.equals(currentScreen)) renderAssessmentsScreen();
        };
        searchDebounceHandler.postDelayed(pendingMyAssessmentsSearchRunnable, 220);
    }

    private void scheduleAnswerKeysSearchRefresh() {
        if (pendingAnswerKeysSearchRunnable != null)
            searchDebounceHandler.removeCallbacks(pendingAnswerKeysSearchRunnable);
        pendingAnswerKeysSearchRunnable = () -> {
            if (SCREEN_ANSWERKEYS.equals(currentScreen)) renderAnswerKeysScreen();
        };
        searchDebounceHandler.postDelayed(pendingAnswerKeysSearchRunnable, 220);
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
                    getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).edit()
                            .putLong(PREF_LAST_GLOBAL_SYNC, System.currentTimeMillis())
                            .apply();
                    android.widget.Toast.makeText(this,
                            "Synced " + totalWritten + " class" + (totalWritten == 1 ? "" : "es"),
                            android.widget.Toast.LENGTH_SHORT).show();
                    loadDataFromDb(); // refresh class cards with the new/updated rows
                    updateLastSyncedLabel();
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
    // SERVER REACHABILITY CHECK
    // ═══════════════════════════════════════════════════════════════

    private void checkServerReachability() {
        repo.getActiveUser(user -> {
            if (user == null || user.serverIp == null || user.serverIp.trim().isEmpty()) {
                return; // no server saved yet — nothing to check
            }
            final String serverIp = user.serverIp;
            syncExecutor.execute(() -> {
                boolean reachable;
                java.net.HttpURLConnection conn = null;
                try {
                    java.net.URL url = new java.net.URL(serverIp);
                    conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("GET");
                    conn.getResponseCode(); // any response at all = reachable
                    reachable = true;
                } catch (Exception e) {
                    android.util.Log.w("OMR_PING", "Server unreachable: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                    reachable = false;
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final boolean isReachable = reachable;
                runOnUiThread(() -> {
                    if (!isReachable && !isFinishing() && !isDestroyed()) {
                        showServerUnreachableCard();
                    }
                });
            });
        });
    }

    private void showServerUnreachableCard() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());
        root.addView(ui.buildSheetTitle("⚠️ Can't Reach Server", "#DC2626", android.view.Gravity.START, 12));

        TextView note = new TextView(this);
        note.setText("We couldn't connect to the last known server. If its IP address changed, rescan your QR code to reconnect.");
        note.setTextColor(Color.parseColor("#64748B"));
        note.setTextSize(13);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.bottomMargin = ui.dp(20);
        note.setLayoutParams(noteLp);
        root.addView(note);

        LinearLayout actions = ui.buildActionsRow(ui.dp(4));
        TextView btnDismiss = ui.createDialogButton("Not Now", false);
        TextView btnRescan  = ui.createDialogButton("Rescan QR Code", true);
        actions.addView(btnDismiss);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnRescan);
        root.addView(actions);

        btnDismiss.setOnClickListener(v -> dialog.dismiss());
        btnRescan.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, QrScannerActivity.class));
        });

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // HELP / FAQ
    // ═══════════════════════════════════════════════════════════════

    private void showFaqDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable rootBg = new android.graphics.drawable.GradientDrawable();
        rootBg.setColor(Color.WHITE);
        rootBg.setCornerRadii(new float[]{ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24), 0, 0, 0, 0});
        root.setBackground(rootBg);

        // ── Header row: title + close button ───────────────────────
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerRow.setPadding(ui.dp(20), ui.dp(20), ui.dp(12), ui.dp(12));

        TextView title = new TextView(this);
        title.setText("❓ Help & FAQ");
        title.setTextColor(Color.parseColor("#0038A8"));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(title);

        android.widget.ImageView btnClose = new android.widget.ImageView(this);
        btnClose.setImageResource(R.drawable.ic_close);
        btnClose.setColorFilter(Color.parseColor("#64748B"));
        btnClose.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        headerRow.addView(btnClose);
        root.addView(headerRow);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)));
        root.addView(divider);

        // ── Scrollable body ─────────────────────────────────────────
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24));
        scroll.addView(body);
        root.addView(scroll);

        // ── Getting started ─────────────────────────────────────────
        addFaqCategory(body, "GETTING STARTED");
        addFaqItem(body,
                "How do I connect the app to my account?",
                "Scan your QR code from the STARS website (your teacher account's QR page). This saves your name, school, and the server address on your device so the app knows where to sync.");
        addFaqItem(body,
                "What does \"Sync\" on the home screen do?",
                "It pulls your assigned classes and sections from the STARS system into the app.");
        addFaqItem(body,
                "Do I need internet access to use the app?",
                "No, not for scanning. Classes, assessments, answer keys, and scans are all stored locally on your device. You only need a connection to the server when syncing classes/students or uploading assessment results.");

        // ── Classes & syncing ────────────────────────────────────────
        addFaqCategory(body, "CLASSES & SYNCING");
        addFaqItem(body,
                "What does syncing students inside a class do?",
                "It pulls that class's student roster (LRNs) from STARS so scanned answer sheets can be matched against real students.");
        addFaqItem(body,
                "How often do I need to re-sync students?",
                "The app flags a class's student roster as stale after 24 hours, but you can manually re-sync anytime from that class's screen.");
        addFaqItem(body,
                "What does \"Upload Assessment\" do?",
                "It uploads that assessment's results (as CSV) to the STARS system for the matching classroom.");

        // ── Assessments & answer keys ────────────────────────────────
        addFaqCategory(body, "ASSESSMENTS & ANSWER KEYS");
        addFaqItem(body,
                "What sheet types are supported?",
                "ZPH40 (40 items) and ZPH60 (60 items) are the sheet types currently supported when creating an assessment.");
        addFaqItem(body,
                "Where do answer keys come from?",
                "Answer keys are created and stored locally in the app — they are not synced from the STARS website.");
        addFaqItem(body,
                "Why aren't my scans showing a score?",
                "A scan is only auto-graded if its assessment is already linked to an answer key at the time you scan it. Link the answer key to the assessment before scanning if you want scores calculated right away.");
        addFaqItem(body,
                "What does the badge on an answer key card mean?",
                "It shows whether that answer key is currently linked to an assessment, so you can tell at a glance which keys are active.");

        // ── Scanning ─────────────────────────────────────────────────
        addFaqCategory(body, "SCANNING");
        addFaqItem(body,
                "What's the difference between handheld and fixed-mount scanning?",
                "Handheld is for holding the phone directly over a sheet. Fixed-mount is for an elevated or mounted phone with sheets slid underneath — it adds extra distance compensation for detecting the sheet's corner anchors.");
        addFaqItem(body,
                "Can I import a photo from my gallery instead of scanning live?",
                "Not currently — scanning is live-camera only in this version of the app.");
        addFaqItem(body,
                "Where do my scanned results get saved?",
                "Every saved scan is automatically written to Downloads/OMRScanner on your device (images + CSV) — there's no manual export step.");
        addFaqItem(body,
                "Where can I review all my scans?",
                "The Scans tab shows a flat, read-only list of every scan across all your classes. To edit a scan, open it from its own class → assessment screen instead.");

        // ── Troubleshooting ──────────────────────────────────────────
        addFaqCategory(body, "TROUBLESHOOTING");
        addFaqItem(body,
                "Why does the app say \"Can't Reach Server\"?",
                "This means the app couldn't connect to the last server address it saved. This usually happens when the server's IP address has changed since you last scanned your QR code. Rescan your QR code to update it, or check that your phone and the server are on the same network.");
        addFaqItem(body,
                "Is my data private?",
                "Everything is stored locally on your device in its own database. Data only leaves the device when you explicitly sync or upload to your configured STARS server.");

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams wlp = dialog.getWindow().getAttributes();
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    (int) (dm.heightPixels * 0.85));
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addFaqCategory(LinearLayout container, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(Color.parseColor("#0038A8"));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setAllCaps(true);
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(16);
        lp.bottomMargin = ui.dp(8);
        label.setLayoutParams(lp);
        container.addView(label);
    }

    private void addFaqItem(LinearLayout container, String question, String answer) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        card.setClickable(true);
        card.setFocusable(true);

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.parseColor("#F8FAFC"));
        cardBg.setCornerRadius(ui.dp(12));
        cardBg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = ui.dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView q = new TextView(this);
        q.setText(question);
        q.setTextColor(Color.parseColor("#1E293B"));
        q.setTextSize(14);
        q.setTypeface(null, android.graphics.Typeface.BOLD);
        q.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(q);

        android.widget.ImageView chevron = new android.widget.ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(Color.parseColor("#94A3B8"));
        chevron.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        row.addView(chevron);
        card.addView(row);

        TextView a = new TextView(this);
        a.setText(answer);
        a.setTextColor(Color.parseColor("#64748B"));
        a.setTextSize(13);
        a.setLineSpacing(ui.dp(2), 1f);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = ui.dp(8);
        a.setLayoutParams(aLp);
        a.setVisibility(View.GONE);
        card.addView(a);

        card.setOnClickListener(v -> {
            boolean expanded = a.getVisibility() == View.VISIBLE;
            a.setVisibility(expanded ? View.GONE : View.VISIBLE);
            chevron.setRotation(expanded ? 0f : 90f);
        });

        container.addView(card);
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
        screenUser.setVisibility(View.GONE);
        screenAssessments.setVisibility(View.GONE);
        screenAnswerKeys.setVisibility(View.GONE);
        screenScans.setVisibility(View.GONE);

        switch (screen) {
            case SCREEN_HOME:
                screenHome.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                fabMain.setVisibility(View.GONE);
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
                fabMain.setVisibility(View.GONE);
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

            case SCREEN_USER:
                screenUser.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                fabMain.setVisibility(View.GONE);
                topBarTitle.setText("Profile");
                topBarBadge.setVisibility(View.GONE);
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                refreshUserScreen();
                break;

            case SCREEN_ASSESSMENTS:
                screenAssessments.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                fabMain.setVisibility(View.GONE);
                topBarTitle.setText("Assessments");
                topBarBadge.setVisibility(View.GONE);
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                renderAssessmentsScreen();
                break;

            case SCREEN_ANSWERKEYS:
                screenAnswerKeys.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                fabMain.setVisibility(View.GONE);
                topBarTitle.setText("Answer Keys");
                topBarBadge.setVisibility(View.GONE);
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                renderAnswerKeysScreen();
                break;

            case SCREEN_SCANS:
                screenScans.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                fabMain.setVisibility(View.GONE);
                topBarTitle.setText("Scans");
                topBarBadge.setVisibility(View.GONE);
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                renderScansScreen();
                break;
        }

        updateBottomNavSelection(screen);
    }

    /** True for the "chrome" tabs that sit alongside Home in the bottom nav. */
    private boolean isChromeTab(String screen) {
        return SCREEN_USER.equals(screen) || SCREEN_ASSESSMENTS.equals(screen)
                || SCREEN_ANSWERKEYS.equals(screen) || SCREEN_SCANS.equals(screen);
    }

    /** Switches to the Home tab's remembered screen (called by the tab tap or back button). */
    private void selectHomeTab() {
        if (isChromeTab(currentScreen)) {
            showScreen(screenBeforeChromeTab != null ? screenBeforeChromeTab : SCREEN_HOME);
        } else if (!SCREEN_HOME.equals(currentScreen)) {
            // Already in the Home tab's own stack (Class or Activity screen) — a second
            // tap on Home jumps straight back to the root instead of doing nothing.
            showScreen(SCREEN_HOME);
        }
    }

    /** Switches to the User tab, remembering whatever content screen was active before. */
    private void selectUserTab() {
        if (!SCREEN_USER.equals(currentScreen)) {
            if (!isChromeTab(currentScreen)) {
                screenBeforeChromeTab = currentScreen;
            }
            showScreen(SCREEN_USER);
        }
    }

    /** Switches to the Assessments tab, remembering whatever content screen was active before. */
    private void selectAssessmentsTab() {
        if (!SCREEN_ASSESSMENTS.equals(currentScreen)) {
            if (!isChromeTab(currentScreen)) {
                screenBeforeChromeTab = currentScreen;
            }
            showScreen(SCREEN_ASSESSMENTS);
        }
    }

    /** Switches to the Answer Keys tab, remembering whatever content screen was active before. */
    private void selectAnswerKeysTab() {
        if (!SCREEN_ANSWERKEYS.equals(currentScreen)) {
            if (!isChromeTab(currentScreen)) {
                screenBeforeChromeTab = currentScreen;
            }
            showScreen(SCREEN_ANSWERKEYS);
        }
    }

    /** Switches to the read-only Scans tab, remembering whatever content screen was active before. */
    private void selectScansTab() {
        if (!SCREEN_SCANS.equals(currentScreen)) {
            if (!isChromeTab(currentScreen)) {
                screenBeforeChromeTab = currentScreen;
            }
            showScreen(SCREEN_SCANS);
        }
    }

    /** Colors the active vs inactive tab icon/label. */
    private void updateBottomNavSelection(String screen) {
        int activeColor = Color.parseColor("#FFFFFF");
        int inactiveColor = Color.parseColor("#CCFFFFFF");
        // Icons use real alpha (setImageAlpha), not a color-filter blend — a colorFilter's
        // alpha has no visible effect on an already-white icon (white blended atop white
        // via SRC_ATOP is still white), so the icon must be dimmed by true transparency instead.
        int activeAlpha = 255;
        int inactiveAlpha = 130;

        boolean userActive = SCREEN_USER.equals(screen);
        boolean assessmentsActive = SCREEN_ASSESSMENTS.equals(screen);
        boolean answerKeysActive = SCREEN_ANSWERKEYS.equals(screen);
        boolean scansActive = SCREEN_SCANS.equals(screen);
        boolean homeActive = !userActive && !assessmentsActive && !answerKeysActive && !scansActive;

        navHomeIcon.setColorFilter(activeColor);
        navHomeIcon.setImageAlpha(homeActive ? activeAlpha : inactiveAlpha);
        navHomeLabel.setTextColor(homeActive ? activeColor : inactiveColor);

        navAssessmentsIcon.setColorFilter(activeColor);
        navAssessmentsIcon.setImageAlpha(assessmentsActive ? activeAlpha : inactiveAlpha);
        navAssessmentsLabel.setTextColor(assessmentsActive ? activeColor : inactiveColor);

        navAnswerKeysIcon.setColorFilter(activeColor);
        navAnswerKeysIcon.setImageAlpha(answerKeysActive ? activeAlpha : inactiveAlpha);
        navAnswerKeysLabel.setTextColor(answerKeysActive ? activeColor : inactiveColor);

        navScansIcon.setColorFilter(activeColor);
        navScansIcon.setImageAlpha(scansActive ? activeAlpha : inactiveAlpha);
        navScansLabel.setTextColor(scansActive ? activeColor : inactiveColor);

        navUserIcon.setColorFilter(activeColor);
        navUserIcon.setImageAlpha(userActive ? activeAlpha : inactiveAlpha);
        navUserLabel.setTextColor(userActive ? activeColor : inactiveColor);
    }

    /** Populates the User tab with the currently active user's info, activity stats, and account details. */
    private void refreshUserScreen() {
        String displayName = globalTeacherName != null ? globalTeacherName.trim() : "";
        userNameText.setText(!displayName.isEmpty() ? displayName : "Scan your QR code to set your name");
        updateLastSyncedLabel();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        // Activity stats
        repo.countClasses(count -> runOnUiThread(() ->
                userStatClasses.setText(String.valueOf(count))));
        repo.countAssessments(count -> runOnUiThread(() ->
                userStatAssessments.setText(String.valueOf(count))));
        repo.countScans(count -> runOnUiThread(() ->
                userStatScans.setText(String.valueOf(count))));
        repo.getAllAnswerKeys(keys -> runOnUiThread(() ->
                userStatAnswerKeys.setText(String.valueOf(keys != null ? keys.size() : 0))));

        // Local teacher profile timestamps
        repo.getFirstTeacher(teacher -> runOnUiThread(() -> {
            if (teacher != null) {
                userDetailMemberSince.setText(sdf.format(new java.util.Date(teacher.createdAt)));
                userDetailLastUpdated.setText(sdf.format(new java.util.Date(teacher.updatedAt)));
            } else {
                userDetailMemberSince.setText("—");
                userDetailLastUpdated.setText("—");
            }
        }));

        // Linked backend account details
        repo.getActiveUser(user -> runOnUiThread(() -> {
            if (user != null) {
                StringBuilder fullName = new StringBuilder();
                if (user.firstName != null && !user.firstName.trim().isEmpty())
                    fullName.append(user.firstName.trim());
                if (user.middleName != null && !user.middleName.trim().isEmpty())
                    fullName.append(" ").append(user.middleName.trim());
                if (user.lastName != null && !user.lastName.trim().isEmpty())
                    fullName.append(" ").append(user.lastName.trim());
                if (user.suffix != null && !user.suffix.trim().isEmpty())
                    fullName.append(" ").append(user.suffix.trim());
                userDetailFullName.setText(fullName.length() > 0 ? fullName.toString() : "—");

                userDetailUsername.setText(user.username != null && !user.username.trim().isEmpty()
                        ? user.username : "—");
                userDetailUserId.setText(user.userId != null ? String.valueOf(user.userId) : "—");
                userDetailServerIp.setText(user.serverIp != null && !user.serverIp.trim().isEmpty()
                        ? user.serverIp : "—");

                if (user.school != null && !user.school.trim().isEmpty()) {
                    userSchoolText.setText(user.school);
                    userSchoolText.setVisibility(View.VISIBLE);
                    userDetailSchool.setText(user.school);
                } else {
                    userSchoolText.setVisibility(View.GONE);
                    userDetailSchool.setText("—");
                }

                boolean active = user.isActive == 1;
                userDetailStatus.setText(active ? "Active" : "Inactive");
                userDetailStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        Color.parseColor(active ? "#16A34A" : "#94A3B8")));
            } else {
                userDetailFullName.setText("—");
                userDetailUsername.setText("—");
                userDetailUserId.setText("—");
                userDetailServerIp.setText("—");
                userDetailSchool.setText("—");
                userSchoolText.setVisibility(View.GONE);
                userDetailStatus.setText("Not linked");
                userDetailStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#94A3B8")));
            }
        }));
    }

    private void navigateBack() {
        switch (currentScreen) {
            case SCREEN_CLASS:
                selectedClass = null;
                showScreen(SCREEN_HOME);
                break;
            case SCREEN_ACTIVITY:
                selectedActivity = null;
                showScreen(activityOpenedFromAssessmentsTab ? SCREEN_ASSESSMENTS : SCREEN_CLASS);
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

        int totalAssessments = 0;
        for (ClassFolder c : classFolders) {
            totalAssessments += c.getActivityCount();
        }
        homeSummaryClassCount.setText(String.valueOf(classFolders.size()));
        homeSummaryAssessmentCount.setText(String.valueOf(totalAssessments));

        homeRenderer.updateFilterToggleAppearance(homeFilterToggle, homeFilterPanelVisible,
                selectedClassGradeFilter, selectedClassSchoolYearFilter, selectedClassSort);

        classRenderer.buildGroupBySwitcher(homeGroupSwitcher, new String[][]{
                {"Grade", "GRADE"},
                {"School Year", "YEAR"},
        }, homeGroupBy, key -> {
            homeGroupBy = key;
            renderHomeScreen();
        });
        homeGradeFilterBlock.setVisibility("GRADE".equals(homeGroupBy) ? View.VISIBLE : View.GONE);
        homeSchoolYearFilterBlock.setVisibility("YEAR".equals(homeGroupBy) ? View.VISIBLE : View.GONE);

        String activeGradeFilter = "GRADE".equals(homeGroupBy) ? selectedClassGradeFilter : null;
        String activeYearFilter = "YEAR".equals(homeGroupBy) ? selectedClassSchoolYearFilter : null;

        final int requestId = ++homeQueryGeneration;
        repo.queryClassList(classSearchQuery, activeGradeFilter,
                activeYearFilter, selectedClassSort, rows -> runOnUiThread(() -> {
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

        classRenderer.updateAssessmentFilterToggleAppearance(classAssessmentFilterToggle,
                classAssessmentFilterPanelVisible,
                !ASSESSMENT_SORT_NEWEST.equals(selectedAssessmentSort)
                        || selectedSheetFilter != null || selectedClassTypeFilter != null);

        classRenderer.buildGroupBySwitcher(classGroupSwitcher, new String[][]{
                {"Sheet Type", "SHEET"},
                {"Assessment Type", "TYPE"},
        }, classGroupBy, key -> {
            classGroupBy = key;
            renderClassScreen();
        });

        if ("TYPE".equals(classGroupBy)) {
            classRenderer.buildAssessmentTypeTabs(classSheetTabs, selectedClass.getActivities(),
                    selectedClassTypeFilter, filterVal -> {
                        selectedClassTypeFilter = filterVal;
                        renderClassScreen();
                    });
        } else {
            classRenderer.buildClassSheetTabs(classSheetTabs, selectedClass.getActivities(),
                    selectedSheetFilter, filterVal -> {
                        selectedSheetFilter = filterVal;
                        renderClassScreen();
                    });
        }

        String activeSheetFilter = "SHEET".equals(classGroupBy) ? selectedSheetFilter : null;
        String activeTypeFilter = "TYPE".equals(classGroupBy) ? selectedClassTypeFilter : null;

        final int requestId = ++assessmentQueryGeneration;
        repo.queryAssessmentList(selectedClass.getId(), activeSheetFilter, activeTypeFilter,
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
                                    activityOpenedFromAssessmentsTab = false;
                                    showScreen(SCREEN_ACTIVITY);
                                }));
                    }
                }));
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — SCANS (all classes, read-only)
    // ═══════════════════════════════════════════════════════════════

    private void renderScansScreen() {
        scansAllList.removeAllViews();

        String summaryTeacherName = (activeUserFirstName != null && !activeUserFirstName.isEmpty())
                ? (activeUserFirstName + (activeUserLastName != null && !activeUserLastName.isEmpty() ? " " + activeUserLastName : ""))
                : globalTeacherName;
        if (scansAllSummaryTeacher != null) {
            scansAllSummaryTeacher.setText(summaryTeacherName != null && !summaryTeacherName.isEmpty()
                    ? "Teacher: " + summaryTeacherName : "Teacher: Unknown");
        }

        repo.queryAllScans(null, null, null, null, "",
                allRows -> runOnUiThread(() -> {
                    if (!SCREEN_SCANS.equals(currentScreen)) return;

                    List<ScanListRow> allRowsList = (allRows != null) ? allRows : new ArrayList<>();

                    classRenderer.buildGroupBySwitcher(scansGroupSwitcher, new String[][]{
                            {"Sheet Type", "SHEET"},
                            {"Class", "CLASS"},
                            {"Assessment", "ASSESSMENT"},
                            {"Correction", "CORRECTION"},
                    }, scansGroupBy, key -> {
                        scansGroupBy = key;
                        renderScansScreen();
                    });
                    scansSheetFilterBlock.setVisibility("SHEET".equals(scansGroupBy) ? View.VISIBLE : View.GONE);
                    scansClassFilterBlock.setVisibility("CLASS".equals(scansGroupBy) ? View.VISIBLE : View.GONE);
                    scansAssessmentFilterBlock.setVisibility("ASSESSMENT".equals(scansGroupBy) ? View.VISIBLE : View.GONE);
                    scansNeedsCorrectionFilterBlock.setVisibility("CORRECTION".equals(scansGroupBy) ? View.VISIBLE : View.GONE);

                    if ("SHEET".equals(scansGroupBy)) {
                        classRenderer.buildScansSheetTabs(scansSheetTabs, allRowsList, selectedScansSheetFilter,
                                filterVal -> {
                                    selectedScansSheetFilter = filterVal;
                                    renderScansScreen();
                                });
                    } else if ("CLASS".equals(scansGroupBy)) {
                        classRenderer.buildScansClassTabs(scansClassTabs, allRowsList, selectedScansClassFilter,
                                filterVal -> {
                                    selectedScansClassFilter = filterVal;
                                    renderScansScreen();
                                });
                    } else if ("ASSESSMENT".equals(scansGroupBy)) {
                        classRenderer.buildScansAssessmentTabs(scansAssessmentTabs, allRowsList, selectedScansAssessmentFilter,
                                filterVal -> {
                                    selectedScansAssessmentFilter = filterVal;
                                    renderScansScreen();
                                });
                    } else {
                        classRenderer.buildScansNeedsCorrectionTabs(scansNeedsCorrectionTabs, allRowsList, selectedScansNeedsCorrectionFilter,
                                filterVal -> {
                                    selectedScansNeedsCorrectionFilter = filterVal;
                                    renderScansScreen();
                                });
                    }

                    classRenderer.updateAssessmentFilterToggleAppearance(scansFilterToggle,
                            scansFilterPanelVisible,
                            !ClassScreenRenderer.ASSESSMENT_SORT_NEWEST.equals(selectedScansSort)
                                    || selectedScansSheetFilter != null
                                    || selectedScansClassFilter != null
                                    || selectedScansAssessmentFilter != null
                                    || selectedScansNeedsCorrectionFilter != null);

                    String query = (scansSearchQuery != null) ? scansSearchQuery.toLowerCase(Locale.ROOT) : "";
                    String activeScansSheetFilter = "SHEET".equals(scansGroupBy) ? selectedScansSheetFilter : null;
                    String activeScansClassFilter = "CLASS".equals(scansGroupBy) ? selectedScansClassFilter : null;
                    String activeScansAssessmentFilter = "ASSESSMENT".equals(scansGroupBy) ? selectedScansAssessmentFilter : null;
                    String activeScansNeedsCorrectionFilter = "CORRECTION".equals(scansGroupBy) ? selectedScansNeedsCorrectionFilter : null;
                    List<ScanListRow> rows = new ArrayList<>();
                    for (ScanListRow row : allRowsList) {
                        if (activeScansSheetFilter != null && !activeScansSheetFilter.equals(row.sheetType)) continue;
                        if (activeScansClassFilter != null && !activeScansClassFilter.equals(row.classId)) continue;
                        if (activeScansAssessmentFilter != null && !activeScansAssessmentFilter.equals(row.assessmentId)) continue;
                        if ("YES".equals(activeScansNeedsCorrectionFilter) && !row.needsCorrection) continue;
                        if ("NO".equals(activeScansNeedsCorrectionFilter) && row.needsCorrection) continue;
                        if (!query.isEmpty()) {
                            boolean matches = (row.studentLrn != null && row.studentLrn.toLowerCase(Locale.ROOT).contains(query))
                                    || (row.assessmentName != null && row.assessmentName.toLowerCase(Locale.ROOT).contains(query))
                                    || (row.className != null && row.className.toLowerCase(Locale.ROOT).contains(query));
                            if (!matches) continue;
                        }
                        rows.add(row);
                    }

                    if (ClassScreenRenderer.ASSESSMENT_SORT_OLDEST.equals(selectedScansSort)) {
                        Collections.sort(rows, (a, b) -> Long.compare(a.timestamp, b.timestamp));
                    } else if (ClassScreenRenderer.ASSESSMENT_SORT_NAME_ASC.equals(selectedScansSort)) {
                        Collections.sort(rows, (a, b) -> (a.studentLrn != null ? a.studentLrn : "").compareToIgnoreCase(b.studentLrn != null ? b.studentLrn : ""));
                    } else if (ClassScreenRenderer.ASSESSMENT_SORT_NAME_DESC.equals(selectedScansSort)) {
                        Collections.sort(rows, (a, b) -> (b.studentLrn != null ? b.studentLrn : "").compareToIgnoreCase(a.studentLrn != null ? a.studentLrn : ""));
                    } else {
                        Collections.sort(rows, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                    }

                    int rowCount = rows.size();
                    if (scansAllCount != null) scansAllCount.setText(String.valueOf(rowCount));
                    if (scansAllSummaryCount != null) scansAllSummaryCount.setText(String.valueOf(rowCount));

                    if (rowCount == 0) {
                        scansAllEmpty.setVisibility(View.VISIBLE);
                        scansAllList.setVisibility(View.GONE);
                        return;
                    }
                    scansAllEmpty.setVisibility(View.GONE);
                    scansAllList.setVisibility(View.VISIBLE);
                    for (ScanListRow row : rows) {
                        scansAllList.addView(scansRenderer.createScanCard(row));
                    }
                }));
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — ASSESSMENTS (all classes)
    // ═══════════════════════════════════════════════════════════════

    private void renderAssessmentsScreen() {
        assessmentsAllList.removeAllViews();

        String summaryTeacherName = (activeUserFirstName != null && !activeUserFirstName.isEmpty())
                ? (activeUserFirstName + (activeUserLastName != null && !activeUserLastName.isEmpty() ? " " + activeUserLastName : ""))
                : globalTeacherName;
        assessmentsSummaryTeacher.setText(summaryTeacherName != null && !summaryTeacherName.isEmpty()
                ? "Teacher: " + summaryTeacherName : "Teacher: Unknown");
        assessmentsSummaryClassCount.setText(String.valueOf(classFolders.size()));

        List<ActivityFolder> allActivitiesAcrossClasses = new ArrayList<>();
        for (ClassFolder cf : classFolders) {
            if (cf.getActivities() != null) allActivitiesAcrossClasses.addAll(cf.getActivities());
        }

        classRenderer.updateAssessmentFilterToggleAppearance(myAssessmentsFilterToggle,
                myAssessmentsFilterPanelVisible,
                !ASSESSMENT_SORT_NEWEST.equals(selectedMyAssessmentsSort)
                        || selectedMyAssessmentsSheetFilter != null
                        || selectedMyAssessmentsTypeFilter != null
                        || selectedMyAssessmentsClassFilter != null);

        classRenderer.buildGroupBySwitcher(myAssessmentsGroupSwitcher, myAssessmentsGroupBy, key -> {
            myAssessmentsGroupBy = key;
            renderAssessmentsScreen();
        });

        if ("TYPE".equals(myAssessmentsGroupBy)) {
            classRenderer.buildAssessmentTypeTabs(myAssessmentsSheetTabs, allActivitiesAcrossClasses,
                    selectedMyAssessmentsTypeFilter, filterVal -> {
                        selectedMyAssessmentsTypeFilter = filterVal;
                        renderAssessmentsScreen();
                    });
        } else if ("CLASS".equals(myAssessmentsGroupBy)) {
            classRenderer.buildClassGroupTabs(myAssessmentsSheetTabs, classFolders,
                    selectedMyAssessmentsClassFilter, filterVal -> {
                        selectedMyAssessmentsClassFilter = filterVal;
                        renderAssessmentsScreen();
                    });
        } else {
            classRenderer.buildClassSheetTabs(myAssessmentsSheetTabs, allActivitiesAcrossClasses,
                    selectedMyAssessmentsSheetFilter, filterVal -> {
                        selectedMyAssessmentsSheetFilter = filterVal;
                        renderAssessmentsScreen();
                    });
        }

        String activeSheetFilter = "SHEET".equals(myAssessmentsGroupBy) ? selectedMyAssessmentsSheetFilter : null;
        String activeTypeFilter = "TYPE".equals(myAssessmentsGroupBy) ? selectedMyAssessmentsTypeFilter : null;
        String activeClassFilter = "CLASS".equals(myAssessmentsGroupBy) ? selectedMyAssessmentsClassFilter : null;

        repo.queryAllAssessments(activeSheetFilter, activeTypeFilter, activeClassFilter,
                myAssessmentsSearchQuery, selectedMyAssessmentsSort, rows -> runOnUiThread(() -> {
                    if (!SCREEN_ASSESSMENTS.equals(currentScreen)) return;

            int rowCount = (rows != null) ? rows.size() : 0;
            if (assessmentsAllCount != null) assessmentsAllCount.setText(String.valueOf(rowCount));
            assessmentsSummaryCount.setText(String.valueOf(rowCount));

            if (rowCount == 0) {
                assessmentsAllEmpty.setVisibility(View.VISIBLE);
                assessmentsAllList.setVisibility(View.GONE);
                return;
            }
            assessmentsAllEmpty.setVisibility(View.GONE);
            assessmentsAllList.setVisibility(View.VISIBLE);
            for (AssessmentListRow row : rows) {
                ClassFolder ownerClass = findClassById(row.classId);
                assessmentsAllList.addView(classRenderer.createActivityCard(
                        row,
                        () -> {
                            ActivityFolder a = findActivityById(ownerClass, row.id);
                            if (ownerClass != null && a != null) dialogs.showEditActivityDialog(a);
                        },
                        () -> {
                            ActivityFolder a = findActivityById(ownerClass, row.id);
                            if (ownerClass != null && a != null) dialogs.showAnswerKeyFolderDialog(a);
                        },
                        () -> {
                            ActivityFolder a = findActivityById(ownerClass, row.id);
                            if (ownerClass != null && a != null) dialogs.showDeleteActivityConfirmation(a);
                        },
                        () -> {
                            ActivityFolder a = findActivityById(ownerClass, row.id);
                            if (ownerClass != null && a != null) dialogs.showUploadAssessmentDialog(a, ownerClass);
                        },
                        () -> {
                            ActivityFolder a = findActivityById(ownerClass, row.id);
                            if (ownerClass == null || a == null) {
                                ui.showErrorDialog("Assessment unavailable",
                                        "The selected assessment could not be loaded. Please try again.");
                                return;
                            }
                            selectedClass = ownerClass;
                            selectedActivity = a;
                            activityOpenedFromAssessmentsTab = true;
                            showScreen(SCREEN_ACTIVITY);
                        }));
            }
        }));
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — ANSWER KEYS (all)
    // ═══════════════════════════════════════════════════════════════

    /** Distinct assessment id -> name across every answer key's linked assessments. */
    private java.util.LinkedHashMap<String, String> buildAnswerKeysLinkedAssessmentOptions() {
        java.util.LinkedHashMap<String, String> options = new java.util.LinkedHashMap<>();
        for (List<AnswerKeyLinkedAssessment> list : answerKeyLinkedAssessments.values()) {
            if (list == null) continue;
            for (AnswerKeyLinkedAssessment a : list) {
                if (a != null && a.id != null) {
                    options.put(a.id, (a.name != null && !a.name.trim().isEmpty()) ? a.name : "(untitled)");
                }
            }
        }
        return options;
    }

    private void renderAnswerKeysScreen() {
        answerKeysAllList.removeAllViews();

        List<AnswerKeyEntity> allKeys = (answerKeys != null) ? answerKeys : new ArrayList<>();

        classRenderer.buildGroupBySwitcher(answerKeysGroupSwitcher, new String[][]{
                {"Sheet Type", "SHEET"},
                {"Status", "STATUS"},
        }, answerKeysGroupBy, key -> {
            answerKeysGroupBy = key;
            renderAnswerKeysScreen();
        });
        answerKeysSheetFilterBlock.setVisibility("SHEET".equals(answerKeysGroupBy) ? View.VISIBLE : View.GONE);
        answerKeysLinkStatusFilterBlock.setVisibility("STATUS".equals(answerKeysGroupBy) ? View.VISIBLE : View.GONE);

        // Sheet-type filter tabs — built from the full unfiltered set so tab counts stay stable.
        if ("SHEET".equals(answerKeysGroupBy)) {
            classRenderer.buildAnswerKeySheetTabs(answerKeysSheetTabs, allKeys, selectedAnswerKeysSheetFilter,
                    filterVal -> {
                        selectedAnswerKeysSheetFilter = filterVal;
                        renderAnswerKeysScreen();
                    });
        } else {
            // Linked/Unlinked status tabs.
            classRenderer.buildAnswerKeyLinkStatusTabs(answerKeysLinkStatusTabs, allKeys, answerKeyLinkInfo,
                    selectedAnswerKeysLinkFilter, filterVal -> {
                        selectedAnswerKeysLinkFilter = filterVal;
                        renderAnswerKeysScreen();
                    });
        }

        // "Linked To" picker label.
        java.util.LinkedHashMap<String, String> linkedAssessmentOptions = buildAnswerKeysLinkedAssessmentOptions();
        if (answerKeysAssessmentFilterPicker != null) {
            String pickerLabel = "All";
            if (selectedAnswerKeysAssessmentFilter != null) {
                String name = linkedAssessmentOptions.get(selectedAnswerKeysAssessmentFilter);
                pickerLabel = (name != null) ? name : "All";
            }
            answerKeysAssessmentFilterPicker.setText(pickerLabel + " \u25be");
        }

        classRenderer.updateAssessmentFilterToggleAppearance(answerKeysFilterToggle,
                answerKeysFilterPanelVisible,
                !ASSESSMENT_SORT_NEWEST.equals(selectedAnswerKeysSort)
                        || selectedAnswerKeysSheetFilter != null
                        || selectedAnswerKeysLinkFilter != null
                        || selectedAnswerKeysAssessmentFilter != null);

        // Apply sheet-type, link-status, linked-assessment, and search filters.
        String query = (answerKeysSearchQuery != null) ? answerKeysSearchQuery.toLowerCase(Locale.ROOT) : "";
        String activeAnswerKeysSheetFilter = "SHEET".equals(answerKeysGroupBy) ? selectedAnswerKeysSheetFilter : null;
        String activeAnswerKeysLinkFilter = "STATUS".equals(answerKeysGroupBy) ? selectedAnswerKeysLinkFilter : null;
        List<AnswerKeyEntity> keys = new ArrayList<>();
        for (AnswerKeyEntity k : allKeys) {
            if (activeAnswerKeysSheetFilter != null && !activeAnswerKeysSheetFilter.equals(k.sheetType)) continue;

            AnswerKeyLinkInfo linkInfo = answerKeyLinkInfo.get(k.id);
            boolean isLinked = (linkInfo != null && linkInfo.linkedCount > 0);
            if ("LINKED".equals(activeAnswerKeysLinkFilter) && !isLinked) continue;
            if ("UNLINKED".equals(activeAnswerKeysLinkFilter) && isLinked) continue;

            if (selectedAnswerKeysAssessmentFilter != null) {
                List<AnswerKeyLinkedAssessment> linked = answerKeyLinkedAssessments.get(k.id);
                boolean matchesAssessment = false;
                if (linked != null) {
                    for (AnswerKeyLinkedAssessment a : linked) {
                        if (a != null && selectedAnswerKeysAssessmentFilter.equals(a.id)) {
                            matchesAssessment = true;
                            break;
                        }
                    }
                }
                if (!matchesAssessment) continue;
            }

            if (!query.isEmpty()) {
                String name = (k.name != null) ? k.name.toLowerCase(Locale.ROOT) : "";
                String sheet = (k.sheetType != null) ? k.sheetType.toLowerCase(Locale.ROOT) : "";
                String year = (k.schoolYear != null) ? k.schoolYear.toLowerCase(Locale.ROOT) : "";
                if (!name.contains(query) && !sheet.contains(query) && !year.contains(query)) continue;
            }
            keys.add(k);
        }

        // Apply sort.
        java.util.Collections.sort(keys, (a, b) -> {
            if (ClassScreenRenderer.ASSESSMENT_SORT_OLDEST.equals(selectedAnswerKeysSort))
                return Long.compare(a.createdAt, b.createdAt);
            if (ClassScreenRenderer.ASSESSMENT_SORT_NAME_ASC.equals(selectedAnswerKeysSort))
                return (a.name != null ? a.name : "").compareToIgnoreCase(b.name != null ? b.name : "");
            if (ClassScreenRenderer.ASSESSMENT_SORT_NAME_DESC.equals(selectedAnswerKeysSort))
                return (b.name != null ? b.name : "").compareToIgnoreCase(a.name != null ? a.name : "");
            // NEWEST (default) — exam-date sort options don't apply to answer keys, fall back to newest.
            return Long.compare(b.createdAt, a.createdAt);
        });

        java.util.Set<String> sheetTypes = new java.util.HashSet<>();
        for (AnswerKeyEntity k : allKeys) {
            if (k.sheetType != null && !k.sheetType.trim().isEmpty()) sheetTypes.add(k.sheetType);
        }
        answerKeysSummaryCount.setText(String.valueOf(keys.size()));
        answerKeysSummarySheetTypes.setText(String.valueOf(sheetTypes.size()));
        if (answerKeysAllCount != null) answerKeysAllCount.setText(String.valueOf(keys.size()));

        String answerKeysTeacherName = (activeUserFirstName != null && !activeUserFirstName.isEmpty())
                ? (activeUserFirstName + (activeUserLastName != null && !activeUserLastName.isEmpty() ? " " + activeUserLastName : ""))
                : globalTeacherName;
        if (answerKeysSummaryTeacher != null) {
            answerKeysSummaryTeacher.setText(answerKeysTeacherName != null && !answerKeysTeacherName.isEmpty()
                    ? "Teacher: " + answerKeysTeacherName : "Teacher: Unknown");
        }

        if (keys.isEmpty()) {
            answerKeysAllEmpty.setVisibility(View.VISIBLE);
            answerKeysAllList.setVisibility(View.GONE);
            return;
        }
        answerKeysAllEmpty.setVisibility(View.GONE);
        answerKeysAllList.setVisibility(View.VISIBLE);
        for (AnswerKeyEntity key : keys) {
            answerKeysAllList.addView(classRenderer.createAnswerKeyCard(
                    key,
                    answerKeyLinkInfo.get(key.id),
                    answerKeyLinkedAssessments.get(key.id),
                    () -> dialogs.showViewAnswerKeyDialog(key),
                    () -> dialogs.showEditAnswerKeyDialog(key),
                    () -> dialogs.showDeleteAnswerKeyConfirmation(key)));
        }
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
        if (myAssessmentsSortPicker != null)
            myAssessmentsSortPicker.setText(classRenderer.getAssessmentSortLabel(selectedMyAssessmentsSort) + " \u25be");
        if (answerKeysSortPicker != null)
            answerKeysSortPicker.setText(classRenderer.getAssessmentSortLabel(selectedAnswerKeysSort) + " \u25be");
        if (scansSortPicker != null)
            scansSortPicker.setText(classRenderer.getAssessmentSortLabel(selectedScansSort) + " \u25be");
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
                "Fixed Mount — Use this for elevated phone mounts where sheets slide underneath automatically.",
                "Handheld — Auto-detects the sheet's corners in any orientation — no need to line up guide squares or tilt the phone.",
                "Flat Scan — Sheet lies flat on a table, hold the phone flat in portrait, looking straight down."
        };

        android.content.SharedPreferences prefs =
                getSharedPreferences(CAMERA_MODE_PREFS, MODE_PRIVATE);
        int defaultSelection = prefs.getBoolean(PREF_TILT_AGNOSTIC_MODE, false) ? 1 : 0;
        final int[] selectedMode = {defaultSelection};

        CharSequence[] cameraModeItems = new CharSequence[cameraModes.length];
        for (int i = 0; i < cameraModes.length; i++) {
            android.text.SpannableString s = new android.text.SpannableString(cameraModes[i]);
            s.setSpan(new android.text.style.ForegroundColorSpan(Color.BLACK), 0, s.length(), 0);
            cameraModeItems[i] = s;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_OMRScanner_Dialog)
                .setTitle("Choose Camera Mode")
                .setSingleChoiceItems(cameraModeItems, defaultSelection, (dialog, which) -> selectedMode[0] = which)
                .setPositiveButton("Open Camera", (dialog, which) -> {
                    // Index 2 (Flat Scan) is a fully separate Activity with its
                    // own launch path -- it does not touch EXTRA_FIXED_MOUNT_MODE
                    // / EXTRA_TILT_AGNOSTIC_MODE routing used by the first two
                    // options below, so this branch can't affect their behavior.
                    if (selectedMode[0] == 2) {
                        prefs.edit()
                                .putBoolean(PREF_BASIC_MODE, false)
                                .apply();
                        launchFlatScanCamera();
                        return;
                    }
                    boolean tiltAgnosticMode = selectedMode[0] == 1;
                    prefs.edit()
                            .putBoolean(PREF_TILT_AGNOSTIC_MODE, tiltAgnosticMode)
                            .putBoolean(PREF_BASIC_MODE, false)
                            .apply();
                    launchCamera(false, tiltAgnosticMode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchFlatScanCamera() {
        Intent intent = new Intent(this, com.example.omrscanner.camera.FlatScanCameraActivity.class);
        if (selectedSheetType != null) intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
        if (selectedClass != null) intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
        if (selectedActivity != null) intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
        startActivity(intent);
    }

    private void launchBasicCamera() {
        Intent intent = new Intent(this, com.example.omrscanner.camera.BasicCameraActivity.class);
        if (selectedSheetType != null) intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
        if (selectedClass != null) intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
        if (selectedActivity != null) intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
        startActivity(intent);
    }

    private void launchCamera(boolean fixedMountMode) {
        launchCamera(fixedMountMode, false);
    }

    private void launchCamera(boolean fixedMountMode, boolean tiltAgnosticMode) {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra(CameraActivity.EXTRA_FIXED_MOUNT_MODE, fixedMountMode);
        intent.putExtra(CameraActivity.EXTRA_TILT_AGNOSTIC_MODE, tiltAgnosticMode);
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
            tvTeacherName.setText("Welcome, " + displayName + " \uD83D\uDC4B");
            tvTeacherName.setTextColor(Color.parseColor("#FFFFFF"));
            tvTeacherName.setTypeface(null, Typeface.BOLD);
        } else {
            tvTeacherName.setText("Scan your QR code\nto set your name");
            tvTeacherName.setTextColor(Color.parseColor("#BFDBFE"));
            tvTeacherName.setTypeface(null, Typeface.NORMAL);
        }

        String fullTeacherName = (globalTeacherName != null && !globalTeacherName.isEmpty())
                ? globalTeacherName
                : displayName;
        if (homeTeacherLabel != null) {
            homeTeacherLabel.setText(fullTeacherName != null && !fullTeacherName.isEmpty()
                    ? "Teacher: " + fullTeacherName : "Teacher: Unknown");
        }

        updateLastSyncedLabel();
    }

    /** Renders "Last synced X ago" (or "Not synced yet") wherever it's shown — header + User tab. */
    private void updateLastSyncedLabel() {
        long lastSyncMillis = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE)
                .getLong(PREF_LAST_GLOBAL_SYNC, 0L);

        String label;
        if (lastSyncMillis > 0L) {
            CharSequence relative = android.text.format.DateUtils.getRelativeTimeSpanString(
                    lastSyncMillis, System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS);
            label = "Last synced " + relative;
        } else {
            label = "Not synced yet";
        }

        if (tvLastSynced != null) tvLastSynced.setText(label);
        if (userLastSynced != null) userLastSynced.setText(label);
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
                        r.insertAnswersFromMap(existing.id, scanEntry.getAnswers(), ignored ->
                                ClassExporter.autoSaveClassData(context, classId, activityId));
                    });
        } else {
            r.insertScan(entity, newId -> {
                if (newId != null && newId > 0) {
                    r.insertAnswersFromMap(newId.intValue(), scanEntry.getAnswers(), ignored ->
                            ClassExporter.autoSaveClassData(context, classId, activityId));
                } else {
                    ClassExporter.autoSaveClassData(context, classId, activityId);
                }
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
                mainHandler.post(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_OMRScanner_Dialog)
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
        tvTeacherName.setText("Welcome, " + n);
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
        repo.getAllAnswerKeys(keys -> runOnUiThread(() -> {
            answerKeys = (keys != null) ? keys : new ArrayList<>();
            repo.getAnswerKeyLinkInfo(links -> runOnUiThread(() -> {
                answerKeyLinkInfo.clear();
                if (links != null) {
                    for (AnswerKeyLinkInfo l : links) answerKeyLinkInfo.put(l.id, l);
                }
                repo.getAnswerKeyLinkedAssessments(rows -> runOnUiThread(() -> {
                    answerKeyLinkedAssessments.clear();
                    if (rows != null) {
                        for (AnswerKeyLinkedAssessment r : rows) {
                            answerKeyLinkedAssessments
                                    .computeIfAbsent(r.answerKeyId, k -> new ArrayList<>())
                                    .add(r);
                        }
                    }
                    if (SCREEN_ANSWERKEYS.equals(currentScreen)) renderAnswerKeysScreen();
                }));
            }));
        }));
    }
}