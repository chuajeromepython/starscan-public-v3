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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.camera.CameraActivity;
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

    // ── Intent extras used by CameraActivity / PreviewActivity ──
    public static final String EXTRA_SHEET_TYPE    = "sheet_type";
    public static final String EXTRA_CLASS_ID      = "class_id";
    public static final String EXTRA_ACTIVITY_ID   = "activity_id";
    public static final String EXTRA_ANSWER_KEY_ID = "answer_key_id";

    // ── Screen names ──
    private static final String SCREEN_HOME     = "home";
    private static final String SCREEN_CLASS    = "class";
    private static final String SCREEN_ACTIVITY = "activity";

    // ── Sort constants (delegated to renderers, kept here for initialisation) ──
    private static final String CLASS_SORT_NEWEST      = HomeScreenRenderer.CLASS_SORT_NEWEST;
    private static final String ASSESSMENT_SORT_NEWEST = ClassScreenRenderer.ASSESSMENT_SORT_NEWEST;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private OMRRepository repo;
    private int currentTeacherId = -1;

    private String currentScreen = SCREEN_HOME;
    private List<ClassFolder> classFolders = new ArrayList<>();
    private ClassFolder  selectedClass    = null;
    private ActivityFolder selectedActivity = null;
    private String selectedSheetType      = null;
    private String selectedSheetFilter    = null;
    private String globalTeacherName      = "";
    /** Cached list of all answer keys — refreshed on load and after CRUD operations. */
    private List<AnswerKeyEntity> answerKeys = new ArrayList<>();

    private String classSearchQuery       = "";
    private String selectedClassGradeFilter      = null;
    private String selectedClassSchoolYearFilter = null;
    private String selectedClassSort      = CLASS_SORT_NEWEST;

    private String assessmentSearchQuery  = "";
    private String selectedAssessmentSort = ASSESSMENT_SORT_NEWEST;

    private int homeQueryGeneration       = 0;
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
    private ClassExporter exporter;

    // ═══════════════════════════════════════════════════════════════
    // VIEWS
    // ═══════════════════════════════════════════════════════════════

    private ImageButton btnBack, btnUpload;
    private TextView topBarTitle, topBarBadge;
    private TextView tvTeacherName;
    private LinearLayout teacherNameRow;

    private View       screenHome;
    private ScrollView screenClass, screenActivity;

    private LinearLayout homeEmpty, homeClassList;
    private EditText     homeClassSearchInput;
    private TextView     homeClassSortPicker;
    private LinearLayout homeGradeFilterChips, homeSchoolYearFilterChips;
    private LinearLayout homeFilterPanel;
    private android.widget.ImageView homeFilterToggle;
    private boolean homeFilterPanelVisible = false;

    private TextView  classTeacherLabel, classNameLabel, classActivityCount;
    private LinearLayout classEmpty, classActivityList, classSheetTabs;
    private TextView  classAssessmentCount;
    private EditText  classAssessmentSearchInput;
    private TextView  classAssessmentSortPicker;

    private CardView  scanCtaCard;
    private LinearLayout scansHeader, activityScanList, activityScansEmpty;
    private TextView  scansTotalCount, scanCtaSub;

    private ExtendedFloatingActionButton fab;
    private ExtendedFloatingActionButton fabAnswerKey;

    private LinearLayout breadcrumbBar;
    private View         breadcrumbDivider;
    private TextView     breadcrumbRoot, breadcrumbSep1, breadcrumbClass,
                         breadcrumbSep2, breadcrumbActivity;


    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        enableFullScreen();

        repo = new OMRRepository(this);

        // Initialise helpers
        ui              = new DashboardUiHelper(this);
        homeRenderer    = new HomeScreenRenderer(this, ui);
        classRenderer   = new ClassScreenRenderer(this, ui);
        activityRenderer = new ActivityScreenRenderer(this, ui);
        dialogs         = new DashboardDialogs(this, ui, repo, this);
        exporter        = new ClassExporter(this, ui);

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
        btnBack    = findViewById(R.id.btnBack);
        btnUpload  = findViewById(R.id.btnUpload);
        topBarTitle = findViewById(R.id.topBarTitle);
        topBarBadge = findViewById(R.id.topBarBadge);
        tvTeacherName  = findViewById(R.id.tvTeacherName);
        teacherNameRow = findViewById(R.id.teacherNameRow);

        screenHome     = findViewById(R.id.screenHome);
        screenClass    = findViewById(R.id.screenClass);
        screenActivity = findViewById(R.id.screenActivity);

        homeEmpty            = findViewById(R.id.homeEmpty);
        homeClassList        = findViewById(R.id.homeClassList);
        homeClassSearchInput = findViewById(R.id.homeClassSearchInput);
        homeClassSortPicker  = findViewById(R.id.homeClassSortPicker);
        homeGradeFilterChips      = findViewById(R.id.homeGradeFilterChips);
        homeSchoolYearFilterChips = findViewById(R.id.homeSchoolYearFilterChips);
        homeFilterPanel  = findViewById(R.id.homeFilterPanel);
        homeFilterToggle = findViewById(R.id.homeFilterToggle);

        classTeacherLabel        = findViewById(R.id.classTeacherLabel);
        classNameLabel           = findViewById(R.id.classNameLabel);
        classActivityCount       = findViewById(R.id.classActivityCount);
        classEmpty               = findViewById(R.id.classEmpty);
        classActivityList        = findViewById(R.id.classActivityList);
        classSheetTabs           = findViewById(R.id.classSheetTabs);
        classAssessmentCount     = findViewById(R.id.classAssessmentCount);
        classAssessmentSearchInput = findViewById(R.id.classAssessmentSearchInput);
        classAssessmentSortPicker  = findViewById(R.id.classAssessmentSortPicker);

        scanCtaCard        = findViewById(R.id.scanCtaCard);
        scanCtaSub         = findViewById(R.id.scanCtaSub);
        scansHeader        = findViewById(R.id.scansHeader);
        scansTotalCount    = findViewById(R.id.scansTotalCount);
        activityScanList   = findViewById(R.id.activityScanList);
        activityScansEmpty = findViewById(R.id.activityScansEmpty);

        fab = findViewById(R.id.fab);
        fabAnswerKey = findViewById(R.id.fabAnswerKey);

        breadcrumbBar      = findViewById(R.id.breadcrumbBar);
        breadcrumbDivider  = findViewById(R.id.breadcrumbDivider);
        breadcrumbRoot     = findViewById(R.id.breadcrumbRoot);
        breadcrumbSep1     = findViewById(R.id.breadcrumbSep1);
        breadcrumbClass    = findViewById(R.id.breadcrumbClass);
        breadcrumbSep2     = findViewById(R.id.breadcrumbSep2);
        breadcrumbActivity = findViewById(R.id.breadcrumbActivity);

        btnBack.setOnClickListener(v -> navigateBack());
        btnUpload.setOnClickListener(v -> dialogs.showGlobalUploadClassDialog());
        fab.setOnClickListener(v -> onFabClicked());
        fabAnswerKey.setOnClickListener(v -> dialogs.showNewAnswerKeyDialog());
        teacherNameRow.setOnClickListener(v -> dialogs.showEditTeacherNameDialog());

        breadcrumbRoot.setOnClickListener(v -> {
            selectedClass    = null;
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
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                classSearchQuery = s != null ? s.toString().trim() : "";
                scheduleHomeSearchRefresh();
            }
        });
        classAssessmentSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
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

    // ═══════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════════

    private void showScreen(String screen) {
        currentScreen = screen;

        screenHome.setVisibility(View.GONE);
        screenClass.setVisibility(View.GONE);
        screenActivity.setVisibility(View.GONE);

        switch (screen) {
            case SCREEN_HOME:
                screenHome.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                topBarTitle.setText("SagotSuri");
                topBarBadge.setVisibility(View.GONE);
                if (!classSearchQuery.equals(homeClassSearchInput.getText().toString())) {
                    homeClassSearchInput.setText(classSearchQuery);
                    homeClassSearchInput.setSelection(homeClassSearchInput.getText().length());
                }
                updateSortPickers();
                if (globalTeacherName != null && !globalTeacherName.isEmpty()) {
                    tvTeacherName.setText(globalTeacherName);
                    tvTeacherName.setTextColor(Color.parseColor("#FFFFFF"));
                    tvTeacherName.setTypeface(null, Typeface.BOLD);
                } else {
                    tvTeacherName.setText("Tap to set teacher name");
                    tvTeacherName.setTextColor(Color.parseColor("#BFDBFE"));
                    tvTeacherName.setTypeface(null, Typeface.NORMAL);
                }
                fab.setText("Class");
                fab.setVisibility(View.VISIBLE);
                fabAnswerKey.setVisibility(View.VISIBLE);
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                renderHomeScreen();
                break;

            case SCREEN_CLASS:
                if (selectedClass == null) { showScreen(SCREEN_HOME); return; }
                screenClass.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                topBarTitle.setText(selectedClass.getDisplayName());
                topBarBadge.setVisibility(View.VISIBLE);
                topBarBadge.setText("📁 " + selectedClass.getActivityCount());
                if (!assessmentSearchQuery.equals(classAssessmentSearchInput.getText().toString())) {
                    classAssessmentSearchInput.setText(assessmentSearchQuery);
                    classAssessmentSearchInput.setSelection(classAssessmentSearchInput.getText().length());
                }
                updateSortPickers();
                fab.setText("Assessment");
                fab.setVisibility(View.VISIBLE);
                fabAnswerKey.setVisibility(View.GONE);
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
                if (selectedClass == null || selectedActivity == null) { showScreen(SCREEN_HOME); return; }
                screenActivity.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                topBarTitle.setText(selectedActivity.getName());
                topBarBadge.setVisibility(View.VISIBLE);
                topBarBadge.setText(selectedActivity.getSheetType());
                fab.setVisibility(View.GONE);
                fabAnswerKey.setVisibility(View.GONE);
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
                    root.addView(ui.createDialogHandle());
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
                    android.widget.TextView btnSet    = ui.createDialogButton("Set Now", true);
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
                dialogs.showNewClassDialog();
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

        TextView statClasses    = findViewById(R.id.statClasses);
        TextView statActivities = findViewById(R.id.statActivities);
        TextView statScans      = findViewById(R.id.statScans);
        TextView homeClassCount = findViewById(R.id.homeClassCount);

        int totalActivities = 0, totalScans = 0;
        for (ClassFolder cls : classFolders) {
            if (cls.getActivities() != null) {
                totalActivities += cls.getActivities().size();
                for (ActivityFolder act : cls.getActivities())
                    totalScans += act.getScanCount();
            }
        }
        if (statClasses    != null) statClasses.setText(String.valueOf(classFolders.size()));
        if (statActivities != null) statActivities.setText(String.valueOf(totalActivities));
        if (statScans      != null) statScans.setText(String.valueOf(totalScans));

        homeRenderer.updateFilterToggleAppearance(homeFilterToggle, homeFilterPanelVisible,
                selectedClassGradeFilter, selectedClassSchoolYearFilter, selectedClassSort);

        final int requestId = ++homeQueryGeneration;
        repo.queryClassList(classSearchQuery, selectedClassGradeFilter,
                selectedClassSchoolYearFilter, selectedClassSort, rows -> runOnUiThread(() -> {
                    if (requestId != homeQueryGeneration || !SCREEN_HOME.equals(currentScreen)) return;

                    List<String> grades = homeRenderer.getDistinctGrades(classFolders);
                    List<String> years  = homeRenderer.getDistinctSchoolYears(classFolders);

                    boolean stale = homeRenderer.buildHomeFilterChips(
                            homeGradeFilterChips, homeSchoolYearFilterChips,
                            grades, years,
                            selectedClassGradeFilter, selectedClassSchoolYearFilter,
                            v -> { selectedClassGradeFilter = v; if (SCREEN_HOME.equals(currentScreen)) renderHomeScreen(); },
                            v -> { selectedClassSchoolYearFilter = v; if (SCREEN_HOME.equals(currentScreen)) renderHomeScreen(); },
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
                                () -> { ClassFolder c = findClassById(row.id); if (c != null) dialogs.showEditClassDialog(c); },
                                () -> { ClassFolder c = findClassById(row.id); if (c != null) exporter.downloadClassData(c); },
                                () -> { ClassFolder c = findClassById(row.id); if (c != null) dialogs.showDeleteClassConfirmation(c); },
                                () -> {
                                    selectedClass = findClassById(row.id);
                                    if (selectedClass == null) {
                                        ui.showErrorDialog("Class unavailable",
                                                "The selected class could not be loaded. Please try again.");
                                        return;
                                    }
                                    selectedSheetFilter    = null;
                                    assessmentSearchQuery  = "";
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
                    if (requestId != assessmentQueryGeneration || !SCREEN_CLASS.equals(currentScreen)) return;

                    int rowCount = (rows != null) ? rows.size() : 0;
                    if (classAssessmentCount != null) classAssessmentCount.setText(rowCount + " total");

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
                                () -> { ActivityFolder a = findActivityById(selectedClass, row.id); if (a != null) dialogs.showEditActivityDialog(a); },
                                () -> { ActivityFolder a = findActivityById(selectedClass, row.id); if (a != null) dialogs.showAnswerKeyFolderDialog(a); },
                                () -> { ActivityFolder a = findActivityById(selectedClass, row.id); if (a != null) dialogs.showDeleteActivityConfirmation(a); },
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
            Intent intent = new Intent(this, CameraActivity.class);
            if (selectedSheetType != null) intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
            if (selectedClass    != null) intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
            if (selectedActivity != null) intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE — Room Database
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void loadDataFromDb() {
        reloadAnswerKeys(); // keep the answer-key cache fresh
        final String prevClassId    = (selectedClass    != null) ? selectedClass.getId()    : null;
        final String prevActivityId = (selectedActivity != null) ? selectedActivity.getId() : null;
        final String prevScreen     = currentScreen;

        repo.getFirstTeacher(teacher -> {
            if (teacher != null) {
                globalTeacherName  = teacher.name != null ? teacher.name : "";
                currentTeacherId   = teacher.id;
                loadClassesFromDb(prevClassId, prevActivityId, prevScreen);
                return;
            }
            repo.upsertTeacher("", ensuredTeacher -> {
                if (ensuredTeacher != null) {
                    globalTeacherName = ensuredTeacher.name != null ? ensuredTeacher.name : "";
                    currentTeacherId  = ensuredTeacher.id;
                } else {
                    globalTeacherName = "";
                    currentTeacherId  = -1;
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
                ClassFolder cf = DataMapper.toClassFolder(ce, globalTeacherName);
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
        if (currentTeacherId > 0) { callback.onResult(currentTeacherId); return; }
        String fallbackName = globalTeacherName != null ? globalTeacherName : "";
        repo.upsertTeacher(fallbackName, teacher -> {
            if (teacher != null) {
                currentTeacherId  = teacher.id;
                globalTeacherName = teacher.name != null ? teacher.name : "";
                callback.onResult(currentTeacherId);
            } else {
                callback.onResult(-1);
            }
        });
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
                    done -> r.insertAnswersFromMap(existing.id, scanEntry.getAnswers(), null));
        } else {
            r.insertScan(entity, newId -> {
                if (newId != null && newId > 0)
                    r.insertAnswersFromMap(newId.intValue(), scanEntry.getAnswers(), null);
            });
        }
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

    // ═══════════════════════════════════════════════════════════════
    // DialogHost INTERFACE IMPL
    // ═══════════════════════════════════════════════════════════════

    @Override public String getGlobalTeacherName()        { return globalTeacherName; }
    @Override public void   setGlobalTeacherName(String n){ globalTeacherName = n; tvTeacherName.setText(n); tvTeacherName.setTextColor(Color.parseColor("#FFFFFF")); tvTeacherName.setTypeface(null, Typeface.BOLD); if (SCREEN_CLASS.equals(currentScreen) && selectedClass != null) classTeacherLabel.setText("Teacher: " + n); }
    @Override public void   setCurrentTeacherId(int id)   { currentTeacherId  = id; }
    @Override public int    getCurrentTeacherId()          { return currentTeacherId; }
    @Override public List<ClassFolder>  getClassFolders() { return classFolders; }
    @Override public ClassFolder        getSelectedClass() { return selectedClass; }
    @Override public void   setSelectedClass(ClassFolder c){ selectedClass = c; }
    @Override public ActivityFolder     getSelectedActivity(){ return selectedActivity; }
    @Override public void   setSelectedActivity(ActivityFolder a){ selectedActivity = a; }
    @Override public void   setSelectedSheetType(String t) { selectedSheetType = t; }
    @Override public List<AnswerKeyEntity> getAnswerKeys()  { return answerKeys; }
    @Override public void reloadAnswerKeys() {
        repo.getAllAnswerKeys(keys -> runOnUiThread(() ->
            answerKeys = (keys != null) ? keys : new ArrayList<>()));
    }
}
