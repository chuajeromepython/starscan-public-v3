package com.example.omrscanner;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.app.DatePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.database.DataMapper;
import com.example.omrscanner.database.OMRRepository;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final long EXPORT_IMAGE_TARGET_BYTES = 100L * 1024L;
    private static final int EXPORT_IMAGE_MIN_EDGE_PX = 900;

    // Extras
    public static final String EXTRA_SHEET_TYPE = "sheet_type";
    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";

    // Screens
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_CLASS = "class";
    private static final String SCREEN_ACTIVITY = "activity";
    private static final String CLASS_SORT_NEWEST = "NEWEST";
    private static final String CLASS_SORT_OLDEST = "OLDEST";
    private static final String CLASS_SORT_GRADE_ASC = "GRADE_ASC";
    private static final String CLASS_SORT_SECTION_ASC = "SECTION_ASC";
    private static final String ASSESSMENT_SORT_NEWEST = "NEWEST";
    private static final String ASSESSMENT_SORT_OLDEST = "OLDEST";
    private static final String ASSESSMENT_SORT_NAME_ASC = "NAME_ASC";
    private static final String ASSESSMENT_SORT_NAME_DESC = "NAME_DESC";
    private static final String ASSESSMENT_SORT_EXAM_DATE_NEWEST = "EXAM_DATE_NEWEST";
    private static final String ASSESSMENT_SORT_EXAM_DATE_OLDEST = "EXAM_DATE_OLDEST";

    // ── Room database repository ─────────────────────────────────────────
    private OMRRepository repo;
    /** Room primary key of the single teacher row (-1 until loaded). */
    private int currentTeacherId = -1;

    // State
    private String currentScreen = SCREEN_HOME;
    private List<ClassFolder> classFolders = new ArrayList<>();
    private ClassFolder selectedClass = null;
    private ActivityFolder selectedActivity = null;
    private String selectedSheetType = null;
    private String selectedSheetFilter = null; // null = "All"
    private String globalTeacherName = "";
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

    // Views
    private ImageButton btnBack;
    private ImageButton btnUpload;
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
    private TextView classTeacherLabel;
    private TextView classNameLabel;
    private TextView classStudentCount;
    private TextView classActivityCount;
    private LinearLayout classEmpty, classActivityList;
    private LinearLayout classSheetTabs;
    private TextView classAssessmentCount;
    private EditText classAssessmentSearchInput;
    private TextView classAssessmentSortPicker;

    private CardView scanCtaCard;

    private LinearLayout scansHeader, activityScanList, activityScansEmpty;
    private TextView scansTotalCount, scanCtaSub;
    private ExtendedFloatingActionButton fab;

    // Breadcrumb
    private LinearLayout breadcrumbBar;
    private View breadcrumbDivider;
    private TextView breadcrumbRoot, breadcrumbSep1, breadcrumbClass,
            breadcrumbSep2, breadcrumbActivity;

    private ActivityResultLauncher<String> galleryLauncher;

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Full screen — hide status bar and navigation bar
        enableFullScreen();

        // Initialise Room repository
        repo = new OMRRepository(this);

        initViews();
        initBackHandler(); // ← modern replacement for onBackPressed()
        initGalleryLauncher();
        loadDataFromDb(); // loads from Room, then showScreen(SCREEN_HOME)
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableFullScreen(); // Re-apply after returning from other activities

        // Reload from Room, then re-navigate to the current screen
        loadDataFromDb();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingHomeSearchRunnable != null) {
            searchDebounceHandler.removeCallbacks(pendingHomeSearchRunnable);
        }
        if (pendingAssessmentSearchRunnable != null) {
            searchDebounceHandler.removeCallbacks(pendingAssessmentSearchRunnable);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FULL SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void enableFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    // ═══════════════════════════════════════════════════════════════
    // BACK HANDLER — replaces deprecated onBackPressed() override
    // ═══════════════════════════════════════════════════════════════

    /**
     * Use OnBackPressedDispatcher (AndroidX) instead of the deprecated
     * onBackPressed() override. The callback is always enabled.
     * When we are already on the home screen we disable the callback first,
     * then call onBackPressed() on the dispatcher so the system can finish
     * the activity normally.
     */
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
                    // Home screen — let the system finish the activity
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
        classStudentCount = findViewById(R.id.classStudentCount);
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

        fab = findViewById(R.id.fab);

        breadcrumbBar = findViewById(R.id.breadcrumbBar);
        breadcrumbDivider = findViewById(R.id.breadcrumbDivider);
        breadcrumbRoot = findViewById(R.id.breadcrumbRoot);
        breadcrumbSep1 = findViewById(R.id.breadcrumbSep1);
        breadcrumbClass = findViewById(R.id.breadcrumbClass);
        breadcrumbSep2 = findViewById(R.id.breadcrumbSep2);
        breadcrumbActivity = findViewById(R.id.breadcrumbActivity);

        btnBack.setOnClickListener(v -> navigateBack());
        btnUpload.setOnClickListener(v -> showGlobalUploadClassDialog());
        fab.setOnClickListener(v -> onFabClicked());

        // Teacher name row click → edit dialog
        teacherNameRow.setOnClickListener(v -> showEditTeacherNameDialog());

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

        scanCtaCard.setOnClickListener(v -> showScanMethodDialog());

        homeClassSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                classSearchQuery = s != null ? s.toString().trim() : "";
                scheduleHomeSearchRefresh();
            }
        });

        classAssessmentSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                assessmentSearchQuery = s != null ? s.toString().trim() : "";
                scheduleAssessmentSearchRefresh();
            }
        });

        homeClassSortPicker.setOnClickListener(v -> showClassSortDialog());
        classAssessmentSortPicker.setOnClickListener(v -> showAssessmentSortDialog());
        updateSortPickers();

        // Filter panel toggle
        homeFilterToggle.setOnClickListener(v -> {
            homeFilterPanelVisible = !homeFilterPanelVisible;
            homeFilterPanel.setVisibility(homeFilterPanelVisible ? View.VISIBLE : View.GONE);
            updateFilterToggleAppearance();
        });
    }

    private void scheduleHomeSearchRefresh() {
        if (pendingHomeSearchRunnable != null) {
            searchDebounceHandler.removeCallbacks(pendingHomeSearchRunnable);
        }
        pendingHomeSearchRunnable = () -> {
            if (SCREEN_HOME.equals(currentScreen)) {
                renderHomeScreen();
            }
        };
        searchDebounceHandler.postDelayed(pendingHomeSearchRunnable, 220);
    }

    private void scheduleAssessmentSearchRefresh() {
        if (pendingAssessmentSearchRunnable != null) {
            searchDebounceHandler.removeCallbacks(pendingAssessmentSearchRunnable);
        }
        pendingAssessmentSearchRunnable = () -> {
            if (SCREEN_CLASS.equals(currentScreen)) {
                renderClassScreen();
            }
        };
        searchDebounceHandler.postDelayed(pendingAssessmentSearchRunnable, 220);
    }

    private void initGalleryLauncher() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null)
                        handleSelectedImage(uri);
                    else
                        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                });
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
                // Refresh teacher name display in header
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
                topBarTitle.setText(selectedActivity.getName());
                topBarBadge.setVisibility(View.VISIBLE);
                topBarBadge.setText(selectedActivity.getSheetType());
                fab.setVisibility(View.GONE);
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
                // Block class creation if teacher name is not set yet
                if (globalTeacherName == null || globalTeacherName.trim().isEmpty()) {
                    Dialog noNameDialog = new Dialog(this);
                    noNameDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    noNameDialog.setCancelable(true);

                    LinearLayout root = buildSheet();
                    root.addView(createDialogHandle());
                    root.addView(buildSheetTitle("⚠ Teacher Name Required", "#D97706", Gravity.START, 20));

                    TextView msg = new TextView(this);
                    msg.setText("Please set your teacher name first before creating a class.");
                    msg.setTextColor(Color.parseColor("#475569"));
                    msg.setTextSize(14);
                    msg.setPadding(dp(24), dp(4), dp(24), dp(16));
                    root.addView(msg);

                    LinearLayout actions = buildActionsRow(dp(20));
                    TextView btnCancel = createDialogButton("Cancel", false);
                    TextView btnSet = createDialogButton("Set Now", true);
                    actions.addView(btnCancel);
                    actions.addView(spacer(dp(10)));
                    actions.addView(btnSet);
                    root.addView(actions);

                    btnCancel.setOnClickListener(v -> noNameDialog.dismiss());
                    btnSet.setOnClickListener(v -> {
                        noNameDialog.dismiss();
                        showEditTeacherNameDialog();
                    });

                    noNameDialog.setContentView(root);
                    configureBottomDialog(noNameDialog);
                    noNameDialog.show();
                    return;
                }
                showNewClassDialog();
                break;
            case SCREEN_CLASS:
                showNewActivityDialog();
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
        if (statClasses != null)
            statClasses.setText(String.valueOf(classFolders.size()));
        if (statActivities != null)
            statActivities.setText(String.valueOf(totalActivities));
        if (statScans != null)
            statScans.setText(String.valueOf(totalScans));

        updateFilterToggleAppearance();

        final int requestId = ++homeQueryGeneration;
        repo.queryClassList(classSearchQuery, selectedClassGradeFilter, selectedClassSchoolYearFilter,
                selectedClassSort, rows -> runOnUiThread(() -> {
                    if (requestId != homeQueryGeneration || !SCREEN_HOME.equals(currentScreen)) {
                        return;
                    }

                    if (buildHomeFilterChips(getDistinctGradesFromCache(), getDistinctSchoolYearsFromCache())) {
                        return;
                    }

                    int rowCount = (rows != null) ? rows.size() : 0;
                    if (homeClassCount != null) {
                        homeClassCount.setText(rowCount + " total");
                    }

                    if (rowCount == 0) {
                        homeEmpty.setVisibility(View.VISIBLE);
                        homeClassList.setVisibility(View.GONE);
                        return;
                    }

                    homeEmpty.setVisibility(View.GONE);
                    homeClassList.setVisibility(View.VISIBLE);
                    for (ClassListRow row : rows) {
                        homeClassList.addView(createClassCard(row));
                    }
                }));
    }

    /** Accent colours that cycle for class cards to give visual variety. */
    private static final String[] CLASS_CARD_ACCENTS = {
            "#2563EB", "#059669", "#7C3AED", "#D97706", "#DC2626", "#0891B2"
    };

    private View createClassCard(ClassListRow row) {
        // Outer card: horizontal layout = accent bar + content
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);
        card.setElevation(dp(2));
        card.setClipToOutline(true);

        // Ripple feedback
        android.content.res.TypedArray ta = obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground});
        card.setForeground(ta.getDrawable(0));
        ta.recycle();

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        // ── Colored accent bar on left ──
        int accentIndex = Math.abs((row.getDisplayName() != null
                ? row.getDisplayName().hashCode() : 0)) % CLASS_CARD_ACCENTS.length;
        String accentColor = CLASS_CARD_ACCENTS[accentIndex];

        View accentBar = new View(this);
        accentBar.setLayoutParams(new LinearLayout.LayoutParams(dp(5),
                ViewGroup.LayoutParams.MATCH_PARENT));
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(Color.parseColor(accentColor));
        accentBar.setBackground(accentBg);
        card.addView(accentBar);

        // ── Content column ──
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Row 1: title + arrow
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(row.getDisplayName());
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#CBD5E1"));
        arrow.setTextSize(20);
        arrow.setPadding(dp(8), 0, 0, 0);
        header.addView(arrow);
        content.addView(header);

        // Row 2: teacher name
        TextView teacher = new TextView(this);
        String displayTeacher = (globalTeacherName != null && !globalTeacherName.isEmpty())
                ? globalTeacherName
                : "Unknown Teacher";
        teacher.setText("👤  " + displayTeacher);
        teacher.setTextColor(Color.parseColor("#64748B"));
        teacher.setTextSize(12);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        teacher.setLayoutParams(tlp);
        content.addView(teacher);

        // Row 3: assessments + school year
        TextView meta = new TextView(this);
        String schoolYearText = (row.schoolYear != null && !row.schoolYear.isEmpty())
                ? " · S.Y. " + row.schoolYear
                : "";
        meta.setText("📂 " + row.assessmentCount + " Assessment"
                + (row.assessmentCount != 1 ? "s" : "")
                + schoolYearText);
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(6);
        meta.setLayoutParams(mlp);
        content.addView(meta);

        card.addView(content);

        card.setOnClickListener(v -> {
            selectedClass = findClassById(row.id);
            if (selectedClass == null) {
                showErrorDialog("Class unavailable", "The selected class could not be loaded. Please try again.");
                return;
            }
            selectedSheetFilter = null;
            assessmentSearchQuery = "";
            selectedAssessmentSort = ASSESSMENT_SORT_NEWEST;
            showScreen(SCREEN_CLASS);
        });
        card.setOnLongClickListener(v -> {
            ClassFolder cls = findClassById(row.id);
            if (cls != null) {
                showClassOptionsDialog(cls);
            }
            return true;
        });
        return card;
    }

    /** Update the filter toggle button appearance based on active filters. */
    private void updateFilterToggleAppearance() {
        boolean hasActiveFilter = selectedClassGradeFilter != null
                || selectedClassSchoolYearFilter != null
                || !CLASS_SORT_NEWEST.equals(selectedClassSort);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));

        if (homeFilterPanelVisible) {
            // Panel is open — filled blue background
            bg.setColor(Color.parseColor("#0038A8"));
            bg.setStroke(dp(1), Color.parseColor("#0038A8"));
            homeFilterToggle.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (hasActiveFilter) {
            // Panel closed but filters are active — light blue tinted background
            bg.setColor(Color.parseColor("#EFF6FF"));
            bg.setStroke(dp(1), Color.parseColor("#2563EB"));
            homeFilterToggle.setColorFilter(Color.parseColor("#2563EB"), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            // Default — subtle grey background
            bg.setColor(Color.parseColor("#F1F5F9"));
            bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
            homeFilterToggle.setColorFilter(Color.parseColor("#64748B"), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        homeFilterToggle.setBackground(bg);
        homeFilterToggle.setPadding(dp(6), dp(6), dp(6), dp(6));
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — CLASS
    // ═══════════════════════════════════════════════════════════════

    private void renderClassScreen() {
        classActivityList.removeAllViews();
        // Use global teacher name; fall back to class-level if not set
        String displayTeacher = (selectedClass.getTeacher() != null && !selectedClass.getTeacher().trim().isEmpty())
                ? selectedClass.getTeacher()
                : ((globalTeacherName != null && !globalTeacherName.isEmpty()) ? globalTeacherName : "Unknown Teacher");

        classTeacherLabel.setText("Teacher: " + displayTeacher);
        classNameLabel.setText(selectedClass.getDisplayName());

        int maxScans = 0;
        int activityCount = selectedClass.getActivityCount();
        if (selectedClass.getActivities() != null) {
            for (ActivityFolder act : selectedClass.getActivities()) {
                if (act.getScanCount() > maxScans) {
                    maxScans = act.getScanCount();
                }
            }
        }
        
        // Use max scans as an approximation for the number of students since it's the closest metric
        classStudentCount.setText(maxScans + " student" + (maxScans == 1 ? "" : "s"));
        classActivityCount.setText(activityCount + " assessment" + (activityCount == 1 ? "" : "s"));

        List<ActivityFolder> activities = selectedClass.getActivities();

        // ── Build sheet-type filter tabs ──
        buildClassSheetTabs(activities);

        final int requestId = ++assessmentQueryGeneration;
        repo.queryAssessmentList(
                selectedClass.getId(),
                selectedSheetFilter,
                assessmentSearchQuery,
                selectedAssessmentSort,
                rows -> runOnUiThread(() -> {
                    if (requestId != assessmentQueryGeneration || !SCREEN_CLASS.equals(currentScreen)) {
                        return;
                    }

                    int rowCount = (rows != null) ? rows.size() : 0;
                    if (classAssessmentCount != null) {
                        classAssessmentCount.setText(rowCount + " total");
                    }

                    if (rowCount == 0) {
                        classEmpty.setVisibility(View.VISIBLE);
                        classActivityList.setVisibility(View.GONE);
                        return;
                    }

                    classEmpty.setVisibility(View.GONE);
                    classActivityList.setVisibility(View.VISIBLE);
                    for (AssessmentListRow row : rows) {
                        classActivityList.addView(createActivityCard(row));
                    }
                }));
    }

    /**
     * Build the "All / ZPH30 / ZPH40 / ZPH50 / ZPH60" filter tabs above the
     * assessment list.
     */
    private void buildClassSheetTabs(List<ActivityFolder> activities) {
        classSheetTabs.removeAllViews();

        // Count how many assessments per sheet type
        int countZPH30 = 0, countZPH40 = 0, countZPH50 = 0, countZPH60 = 0;
        if (activities != null) {
            for (ActivityFolder act : activities) {
                switch (act.getSheetType()) {
                    case "ZPH30":
                        countZPH30++;
                        break;
                    case "ZPH40":
                        countZPH40++;
                        break;
                    case "ZPH50":
                        countZPH50++;
                        break;
                    case "ZPH60":
                        countZPH60++;
                        break;
                }
            }
        }
        int totalCount = (activities != null) ? activities.size() : 0;

        // Tab data: label, filter value (null = all), count
        Object[][] tabs = {
                { "All", null, totalCount },
                { "ZPH30", "ZPH30", countZPH30 },
                { "ZPH40", "ZPH40", countZPH40 },
                { "ZPH50", "ZPH50", countZPH50 },
                { "ZPH60", "ZPH60", countZPH60 },
        };

        for (int i = 0; i < tabs.length; i++) {
            final String label = (String) tabs[i][0];
            final String filterVal = (String) tabs[i][1];
            final int count = (int) tabs[i][2];

            // Only show a tab if it has assessments (always show "All")
            if (filterVal != null && count == 0)
                continue;

            boolean isActive = (selectedSheetFilter == null && filterVal == null)
                    || (selectedSheetFilter != null && selectedSheetFilter.equals(filterVal));

            TextView tab = new TextView(this);
            tab.setText(label + " (" + count + ")");
            tab.setTextSize(12);
            tab.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(14), dp(8), dp(14), dp(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            if (isActive) {
                bg.setColor(Color.parseColor("#0038A8"));
                tab.setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.parseColor("#F1F5F9"));
                bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
                tab.setTextColor(Color.parseColor("#64748B"));
            }
            tab.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            tab.setLayoutParams(lp);

            tab.setOnClickListener(v -> {
                selectedSheetFilter = filterVal;
                renderClassScreen();
            });
            classSheetTabs.addView(tab);
        }
    }

    private View createActivityCard(AssessmentListRow row) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(row.name);
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        leftCol.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sheet: " + row.sheetType);
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(12);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        sub.setLayoutParams(slp);
        leftCol.addView(sub);
        header.addView(leftCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(18);
        header.addView(arrow);
        card.addView(header);

        TextView meta = new TextView(this);
        String dateToShow = (row.examDate != null && !row.examDate.isEmpty())
                ? row.examDate
                : new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new java.util.Date(row.createdAt));
        meta.setText("\uD83D\uDCC4 " + row.scanCount + " scan"
                + (row.scanCount != 1 ? "s" : "")
                + " · " + dateToShow);
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        meta.setLayoutParams(mlp);
        card.addView(meta);

        card.setOnClickListener(v -> {
            ActivityFolder act = findActivityById(selectedClass, row.id);
            if (act == null) {
                showErrorDialog("Assessment unavailable", "The selected assessment could not be loaded. Please try again.");
                return;
            }
            selectedActivity = act;
            showScreen(SCREEN_ACTIVITY);
        });
        card.setOnLongClickListener(v -> {
            ActivityFolder act = findActivityById(selectedClass, row.id);
            if (act != null) {
                showActivityOptionsDialog(act);
            }
            return true;
        });
        return card;
    }

    private interface ChipSelectionHandler {
        void onSelected(String value);
    }

    private List<String> getDistinctGradesFromCache() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (ClassFolder cls : classFolders) {
            if (cls.getGrade() != null && !cls.getGrade().trim().isEmpty()) {
                set.add(cls.getGrade().trim());
            }
        }
        List<String> list = new ArrayList<>(set);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    private List<String> getDistinctSchoolYearsFromCache() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (ClassFolder cls : classFolders) {
            if (cls.getSchoolYear() != null && !cls.getSchoolYear().trim().isEmpty()) {
                set.add(cls.getSchoolYear().trim());
            }
        }
        List<String> list = new ArrayList<>(set);
        list.sort((a, b) -> b.compareToIgnoreCase(a));
        return list;
    }

    private boolean buildHomeFilterChips(List<String> grades, List<String> years) {
        boolean selectionChanged = false;
        if (selectedClassGradeFilter != null && (grades == null || !grades.contains(selectedClassGradeFilter))) {
            selectedClassGradeFilter = null;
            selectionChanged = true;
        }
        if (selectedClassSchoolYearFilter != null
                && (years == null || !years.contains(selectedClassSchoolYearFilter))) {
            selectedClassSchoolYearFilter = null;
            selectionChanged = true;
        }

        if (selectionChanged && SCREEN_HOME.equals(currentScreen)) {
            renderHomeScreen();
            return true;
        }

        buildFilterChipRow(homeGradeFilterChips, grades, selectedClassGradeFilter, value -> {
            selectedClassGradeFilter = value;
            if (SCREEN_HOME.equals(currentScreen)) {
                renderHomeScreen();
            }
        });
        buildFilterChipRow(homeSchoolYearFilterChips, years, selectedClassSchoolYearFilter, value -> {
            selectedClassSchoolYearFilter = value;
            if (SCREEN_HOME.equals(currentScreen)) {
                renderHomeScreen();
            }
        });
        return false;
    }

    private void buildFilterChipRow(LinearLayout container, List<String> values, String selectedValue,
            ChipSelectionHandler handler) {
        container.removeAllViews();
        container.addView(createFilterChip("All", selectedValue == null, () -> handler.onSelected(null)));
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            boolean isActive = value.equals(selectedValue);
            container.addView(createFilterChip(value, isActive, () -> handler.onSelected(value)));
        }
    }

    private View createFilterChip(String label, boolean isActive, Runnable action) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        chip.setClickable(true);
        chip.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (isActive) {
            bg.setColor(Color.parseColor("#0038A8"));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#F8FAFC"));
            bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
            chip.setTextColor(Color.parseColor("#64748B"));
        }
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    private void showClassSortDialog() {
        final String[] labels = {
                "Newest",
                "Oldest",
                "Grade A-Z",
                "Section A-Z"
        };
        final String[] keys = {
                CLASS_SORT_NEWEST,
                CLASS_SORT_OLDEST,
                CLASS_SORT_GRADE_ASC,
                CLASS_SORT_SECTION_ASC
        };
        int checked = indexOfSortKey(keys, selectedClassSort);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Sort Classes")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectedClassSort = keys[which];
                    updateSortPickers();
                    dialog.dismiss();
                    if (SCREEN_HOME.equals(currentScreen)) {
                        renderHomeScreen();
                    }
                })
                .show();
    }

    private void showAssessmentSortDialog() {
        final String[] labels = {
                "Newest",
                "Oldest",
                "Name A-Z",
                "Name Z-A",
                "Exam Date (Newest)",
                "Exam Date (Oldest)"
        };
        final String[] keys = {
                ASSESSMENT_SORT_NEWEST,
                ASSESSMENT_SORT_OLDEST,
                ASSESSMENT_SORT_NAME_ASC,
                ASSESSMENT_SORT_NAME_DESC,
                ASSESSMENT_SORT_EXAM_DATE_NEWEST,
                ASSESSMENT_SORT_EXAM_DATE_OLDEST
        };
        int checked = indexOfSortKey(keys, selectedAssessmentSort);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Sort Assessments")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectedAssessmentSort = keys[which];
                    updateSortPickers();
                    dialog.dismiss();
                    if (SCREEN_CLASS.equals(currentScreen)) {
                        renderClassScreen();
                    }
                })
                .show();
    }

    private int indexOfSortKey(String[] keys, String selected) {
        if (selected == null) {
            return 0;
        }
        for (int i = 0; i < keys.length; i++) {
            if (selected.equals(keys[i])) {
                return i;
            }
        }
        return 0;
    }

    private void updateSortPickers() {
        if (homeClassSortPicker != null) {
            homeClassSortPicker.setText(getClassSortLabel(selectedClassSort) + " \u25be");
        }
        if (classAssessmentSortPicker != null) {
            classAssessmentSortPicker.setText(getAssessmentSortLabel(selectedAssessmentSort) + " \u25be");
        }
    }

    private String getClassSortLabel(String key) {
        if (CLASS_SORT_OLDEST.equals(key)) {
            return "Oldest";
        }
        if (CLASS_SORT_GRADE_ASC.equals(key)) {
            return "Grade A-Z";
        }
        if (CLASS_SORT_SECTION_ASC.equals(key)) {
            return "Section A-Z";
        }
        return "Newest";
    }

    private String getAssessmentSortLabel(String key) {
        if (ASSESSMENT_SORT_OLDEST.equals(key)) {
            return "Oldest";
        }
        if (ASSESSMENT_SORT_NAME_ASC.equals(key)) {
            return "Name A-Z";
        }
        if (ASSESSMENT_SORT_NAME_DESC.equals(key)) {
            return "Name Z-A";
        }
        if (ASSESSMENT_SORT_EXAM_DATE_NEWEST.equals(key)) {
            return "Exam Date ↓";
        }
        if (ASSESSMENT_SORT_EXAM_DATE_OLDEST.equals(key)) {
            return "Exam Date ↑";
        }
        return "Newest";
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — ACTIVITY
    // ═══════════════════════════════════════════════════════════════

    private void renderActivityScreen() {
        activityScanList.removeAllViews();
        scanCtaSub.setText(selectedActivity.getSheetType()
                + " · " + selectedActivity.getNumItems() + " items");

        List<ScanEntry> scans = selectedActivity.getScans();
        boolean hasScans = (scans != null && !scans.isEmpty());

        if (hasScans) {
            scansHeader.setVisibility(View.VISIBLE);
            scansTotalCount.setText(scans.size() + " total");
            activityScanList.setVisibility(View.VISIBLE);
            activityScansEmpty.setVisibility(View.GONE);

            // ── FIX: Capture IDs as final local Strings so the lambda never
            // holds a reference to the mutable selectedClass /
            // selectedActivity fields (which can become null on resume).
            final String classId = selectedClass.getId();
            final String activityId = selectedActivity.getId();

            for (int i = 0; i < scans.size(); i++) {
                activityScanList.addView(
                        createScanCard(scans.get(i), i, classId, activityId));
            }
        } else {
            scansHeader.setVisibility(View.GONE);
            activityScanList.setVisibility(View.GONE);
            activityScansEmpty.setVisibility(View.VISIBLE);
        }
    }

    /**
     * classId and activityId are explicit parameters so the click listener
     * captures stable strings instead of mutable activity-level fields.
     */
    private View createScanCard(ScanEntry scan, int index,
            final String classId, final String activityId) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        // Prevent child views from intercepting touch events
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        // Thumbnail
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(48), dp(60));
        thumbLp.rightMargin = dp(14);
        thumb.setLayoutParams(thumbLp);
        thumb.setClickable(false);
        thumb.setFocusable(false);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.parseColor("#F0F7FF"));
        thumbBg.setCornerRadius(dp(10));
        thumbBg.setStroke(dp(1), Color.parseColor("#0038A8"));
        thumb.setBackground(thumbBg);
        TextView thumbIcon = new TextView(this);
        thumbIcon.setText("📄");
        thumbIcon.setTextSize(22);
        thumbIcon.setGravity(Gravity.CENTER);
        thumbIcon.setClickable(false);
        thumbIcon.setFocusable(false);
        thumb.addView(thumbIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(thumb);

        // Info column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClickable(false);
        info.setFocusable(false);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView lrn = new TextView(this);
        lrn.setText("LRN: " + (scan.getLrn() != null && !scan.getLrn().isEmpty()
                ? scan.getLrn()
                : "Unknown"));
        lrn.setTextColor(Color.parseColor("#1E293B"));
        lrn.setTextSize(13);
        lrn.setTypeface(null, Typeface.BOLD);
        lrn.setClickable(false);
        info.addView(lrn);

        TextView detail = new TextView(this);
        detail.setText("Student #" + (index + 1) + " · " + selectedActivity.getSheetType());
        detail.setTextColor(Color.parseColor("#64748B"));
        detail.setTextSize(12);
        detail.setClickable(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(2);
        detail.setLayoutParams(dlp);
        info.addView(detail);
        card.addView(info);

        // Score badge
        TextView scoreBadge = createScoreBadge(scan);
        scoreBadge.setClickable(false);
        scoreBadge.setFocusable(false);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.rightMargin = dp(8);
        scoreBadge.setLayoutParams(badgeLp);
        card.addView(scoreBadge);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(16);
        arrow.setClickable(false);
        arrow.setFocusable(false);
        card.addView(arrow);

        // ── THE KEY FIX: use final captured Strings, not mutable fields ──
        card.setOnClickListener(v -> {
            Log.d(TAG, "Opening ScanDetail — index=" + index
                    + "  classId=" + classId + "  activityId=" + activityId);
            Intent intent = new Intent(DashboardActivity.this, ScanDetailActivity.class);
            intent.putExtra(ScanDetailActivity.EXTRA_CLASS_ID, classId);
            intent.putExtra(ScanDetailActivity.EXTRA_ACTIVITY_ID, activityId);
            intent.putExtra(ScanDetailActivity.EXTRA_SCAN_INDEX, index);
            startActivity(intent);
        });

        return card;
    }

    /** Badge showing how many items had a detected answer (not a score). */
    private TextView createScoreBadge(ScanEntry scan) {
        TextView badge = new TextView(this);
        badge.setText(scan.getScore() + "/" + scan.getNumItems());
        badge.setTextSize(12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(6));
        // Neutral green-tinted badge — represents detected count, not correctness
        badgeBg.setColor(Color.parseColor("#DCFCE7"));
        badge.setTextColor(Color.parseColor("#059669"));
        badge.setBackground(badgeBg);
        return badge;
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS — CLASS
    // ═══════════════════════════════════════════════════════════════

    private void showClassOptionsDialog(ClassFolder cls) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle(cls.getDisplayName(), "#1E293B", Gravity.CENTER, 24));
        root.addView(createMenuOption("✏️   Edit Class Details",
                () -> {
                    dialog.dismiss();
                    showEditClassDialog(cls);
                }, false));
        root.addView(createMenuOption("⬇️   Download Folder",
                () -> {
                    dialog.dismiss();
                    downloadClassData(cls);
                }, false));
        root.addView(createMenuOption("🗑️   Delete Class",
                () -> {
                    dialog.dismiss();
                    showDeleteClassConfirmation(cls);
                }, true));

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private View createMenuOption(String text, Runnable onClick, boolean isDestructive) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTextColor(isDestructive
                ? Color.parseColor("#CE1126")
                : Color.parseColor("#1E293B"));
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isDestructive
                ? Color.parseColor("#FEF2F2")
                : Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), isDestructive
                ? Color.parseColor("#FECACA")
                : Color.parseColor("#E2E8F0"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private void showEditClassDialog(ClassFolder cls) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("✏️ Edit Class Folder", "#0038A8", Gravity.START, 20));

        // Teacher name field removed — teacher name is managed globally in the header

        root.addView(createFieldLabel("GRADE *"));
        EditText gradeInput = createLightInput("e.g. Grade 10");
        gradeInput.setText(cls.getGrade());
        root.addView(gradeInput);

        root.addView(createFieldLabel("SECTION *"));
        EditText sectionInput = createLightInput("e.g. Section A");
        sectionInput.setText(cls.getSection());
        root.addView(sectionInput);

        root.addView(createFieldLabel("SCHOOL YEAR"));
        // School Year dropdown
        final String[] schoolYearOptions = buildSchoolYearOptions();
        int editCurrentYr = Calendar.getInstance().get(Calendar.YEAR);
        String editDefaultSY = editCurrentYr + "-" + (editCurrentYr + 1);
        String currentSY = (cls.getSchoolYear() != null && !cls.getSchoolYear().isEmpty())
                ? cls.getSchoolYear()
                : editDefaultSY;
        final String[] selectedSchoolYear = { currentSY };
        TextView schoolYearPicker = createDropdownField(currentSY);
        schoolYearPicker.setTextColor(Color.parseColor("#1E293B"));
        schoolYearPicker.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Select School Year")
                    .setItems(schoolYearOptions, (dlg, which) -> {
                        selectedSchoolYear[0] = schoolYearOptions[which];
                        schoolYearPicker.setText(schoolYearOptions[which] + "  ▾");
                        schoolYearPicker.setTextColor(Color.parseColor("#1E293B"));
                    })
                    .show();
        });
        root.addView(schoolYearPicker);

        LinearLayout actions = buildActionsRow(dp(20));
        TextView btnCancel = createDialogButton("Cancel", false);
        TextView btnSave = createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String grade = gradeInput.getText().toString().trim();
            String section = sectionInput.getText().toString().trim();
            if (grade.isEmpty() || section.isEmpty()) {
                showErrorDialog("Missing Fields", "Grade and Section are required to save this class.");
                return;
            }
            for (ClassFolder existing : classFolders) {
                if (!existing.getId().equals(cls.getId())
                        && existing.getGrade().equalsIgnoreCase(grade)
                        && existing.getSection().equalsIgnoreCase(section)) {
                    showErrorDialog("Duplicate Class",
                            "A class with \"" + grade + " — " + section
                                    + "\" already exists.\nPlease use a different Grade or Section.");
                    return;
                }
            }
            // Keep global teacher name on the class model for consistency
            cls.setTeacher(globalTeacherName != null && !globalTeacherName.isEmpty()
                    ? globalTeacherName
                    : cls.getTeacher());
            cls.setGrade(grade);
            cls.setSection(section);
            cls.setSchoolYear(selectedSchoolYear[0]);
            ensureTeacherId(teacherId -> {
                if (teacherId <= 0) {
                    runOnUiThread(() -> showErrorDialog("Save Failed",
                            "Teacher profile is not ready yet. Please try again."));
                    return;
                }

                ClassEntity updatedEntity = DataMapper.toClassEntity(cls, teacherId);
                repo.updateClass(updatedEntity, ignored -> runOnUiThread(() -> {
                    dialog.dismiss();
                    showToast("Class updated ✓");
                    loadDataFromDb();
                }));
            });
        });

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void showDeleteClassConfirmation(ClassFolder cls) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();

        TextView title = new TextView(this);
        title.setText("Delete Class?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Are you sure you want to delete \"" + cls.getDisplayName()
                + "\"?\n\nThis will permanently delete all activities and scans inside this folder.");
        msg.setTextColor(Color.parseColor("#64748B"));
        msg.setTextSize(14);
        msg.setPadding(0, dp(12), 0, dp(24));
        root.addView(msg);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnCancel = createDialogButton("Cancel", false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));

        TextView btnDelete = createDialogButton("Delete", true);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.parseColor("#CE1126"));
        delBg.setCornerRadius(dp(12));
        btnDelete.setBackground(delBg);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setOnClickListener(v -> {
            repo.getClassById(cls.getId(), classEntity -> {
                if (classEntity == null) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        loadDataFromDb();
                    });
                    return;
                }

                repo.deleteClass(classEntity, ignored -> runOnUiThread(() -> {
                    dialog.dismiss();
                    showToast("Class deleted");
                    loadDataFromDb();
                }));
            });
        });
        actions.addView(btnDelete);
        root.addView(actions);

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS — TEACHER NAME
    // ═══════════════════════════════════════════════════════════════

    private void showEditTeacherNameDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("✏️ Teacher Name", "#0038A8", Gravity.START, 16));

        // Info note
        TextView note = new TextView(this);
        note.setText("This name will appear on all class folders.");
        note.setTextColor(Color.parseColor("#64748B"));
        note.setTextSize(13);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.bottomMargin = dp(16);
        note.setLayoutParams(noteLp);
        root.addView(note);

        root.addView(createFieldLabel("TEACHER NAME"));
        EditText nameInput = createLightInput("e.g. Mr. Cruz");
        if (globalTeacherName != null && !globalTeacherName.isEmpty()) {
            nameInput.setText(globalTeacherName);
            nameInput.setSelection(globalTeacherName.length());
        }
        root.addView(nameInput);

        LinearLayout actions = buildActionsRow(dp(20));
        TextView btnCancel = createDialogButton("Cancel", false);
        TextView btnSave = createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newName = nameInput.getText().toString().trim();
            if (newName.isEmpty()) {
                showErrorDialog("Name Required", "Please enter a teacher name.");
                return;
            }
            if (newName.equals(globalTeacherName)) {
                dialog.dismiss();
                return;
            }

            // Runnable that actually persists the name
            Runnable saveAction = () -> {
                repo.upsertTeacher(newName, savedTeacher -> runOnUiThread(() -> {
                    globalTeacherName = savedTeacher != null && savedTeacher.name != null
                            ? savedTeacher.name
                            : newName;
                    if (savedTeacher != null) {
                        currentTeacherId = savedTeacher.id;
                    }

                    for (ClassFolder cls : classFolders) {
                        cls.setTeacher(globalTeacherName);
                    }

                    tvTeacherName.setText(globalTeacherName);
                    tvTeacherName.setTextColor(Color.parseColor("#FFFFFF"));
                    tvTeacherName.setTypeface(null, Typeface.BOLD);
                    if (SCREEN_CLASS.equals(currentScreen) && selectedClass != null) {
                        classTeacherLabel.setText("Teacher: " + globalTeacherName);
                    }

                    dialog.dismiss();
                    showToast("Teacher name updated ✓");
                    loadDataFromDb();
                }));
            };

            // First-time setup → save directly, no confirmation needed
            if (globalTeacherName == null || globalTeacherName.trim().isEmpty()) {
                saveAction.run();
            } else {
                // Changing existing name → ask for confirmation
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Change Teacher Name?")
                        .setMessage("Are you sure you want to change the teacher name to \""
                                + newName + "\"?\n\nThis will apply to all class folders.")
                        .setPositiveButton("Confirm", (alertDialog, which) -> saveAction.run())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS — ACTIVITY
    // ═══════════════════════════════════════════════════════════════

    private void showActivityOptionsDialog(ActivityFolder act) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle(act.getName(), "#1E293B", Gravity.CENTER, 24));
        root.addView(createMenuOption("✏️   Edit Assessment Details",
                () -> {
                    dialog.dismiss();
                    showEditActivityDialog(act);
                }, false));
        root.addView(createMenuOption("🗑️   Delete Assessment",
                () -> {
                    dialog.dismiss();
                    showDeleteActivityConfirmation(act);
                }, true));

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void showEditActivityDialog(ActivityFolder act) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("✏️ Edit Assessment", "#0038A8", Gravity.START, 20));

        root.addView(createFieldLabel("ASSESSMENT NAME *"));
        EditText nameInput = createLightInput(act.getName());
        nameInput.setText(act.getName());
        root.addView(nameInput);

        root.addView(createFieldLabel("EXAM DATE"));
        EditText dateInput = createLightInput("Select date");
        dateInput.setFocusable(false);
        dateInput.setClickable(true);
        String initialDate = (act.getExamDate() != null && !act.getExamDate().isEmpty()) ? act.getExamDate()
                : act.getFormattedDate();
        dateInput.setText(initialDate);
        dateInput.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth);
                dateInput.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(chosen.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        root.addView(dateInput);

        root.addView(createFieldLabel("OMR SHEET TYPE"));
        TextView sheetInfo = new TextView(this);
        sheetInfo.setText(act.getSheetType() + " — " + act.getNumItems() + " Items");
        sheetInfo.setTextSize(14);
        sheetInfo.setTypeface(null, Typeface.BOLD);
        sheetInfo.setTextColor(Color.parseColor("#64748B"));
        sheetInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable siBg = new GradientDrawable();
        siBg.setColor(Color.parseColor("#F8FAFC"));
        siBg.setCornerRadius(dp(10));
        siBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        sheetInfo.setBackground(siBg);
        LinearLayout.LayoutParams silp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        silp.bottomMargin = dp(16);
        sheetInfo.setLayoutParams(silp);
        root.addView(sheetInfo);

        LinearLayout actions = buildActionsRow(dp(20));
        TextView btnCancel = createDialogButton("Cancel", false);
        TextView btnSave = createDialogButton("Save", true);
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));
        actions.addView(btnSave);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                showErrorDialog("Missing Name", "Assessment name is required to save.");
                return;
            }
            if (selectedClass != null && selectedClass.getActivities() != null) {
                for (ActivityFolder existing : selectedClass.getActivities()) {
                    if (!existing.getId().equals(act.getId())
                            && existing.getName().equalsIgnoreCase(name)) {
                        showErrorDialog("Duplicate Assessment",
                                "An assessment named \"" + name
                                        + "\" already exists in this class.\nPlease use a different name.");
                        return;
                    }
                }
            }
            act.setName(name);
            String examDate = dateInput.getText().toString().trim();
            act.setExamDate(examDate);
            act.setExamDateEpoch(parseExamDateToEpoch(examDate, act.getCreatedAt()));
            if (selectedClass == null) {
                showErrorDialog("Save Failed", "No class selected.");
                return;
            }

            AssessmentEntity updatedEntity = DataMapper.toAssessmentEntity(act, selectedClass.getId());
            repo.updateAssessment(updatedEntity, ignored -> runOnUiThread(() -> {
                dialog.dismiss();
                showToast("Assessment updated ✓");
                loadDataFromDb();
            }));
        });

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void showDeleteActivityConfirmation(ActivityFolder act) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();

        TextView title = new TextView(this);
        title.setText("Delete Assessment?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Are you sure you want to delete \"" + act.getName()
                + "\"?\n\nThis will permanently delete all "
                + act.getScanCount() + " scan(s) inside this assessment.");
        msg.setTextColor(Color.parseColor("#64748B"));
        msg.setTextSize(14);
        msg.setPadding(0, dp(12), 0, dp(24));
        root.addView(msg);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnCancel = createDialogButton("Cancel", false);
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));

        TextView btnDelete = createDialogButton("Delete", true);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.parseColor("#CE1126"));
        delBg.setCornerRadius(dp(12));
        btnDelete.setBackground(delBg);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setOnClickListener(v -> {
            if (selectedClass != null && selectedClass.getActivities() != null) {
                repo.getAssessmentById(act.getId(), assessmentEntity -> {
                    if (assessmentEntity == null) {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            loadDataFromDb();
                        });
                        return;
                    }

                    repo.deleteAssessment(assessmentEntity, ignored -> runOnUiThread(() -> {
                        dialog.dismiss();
                        showToast("Assessment deleted");
                        loadDataFromDb();
                    }));
                });
            }
        });
        actions.addView(btnDelete);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(actions);

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS — NEW CLASS / ACTIVITY
    // ═══════════════════════════════════════════════════════════════

    private void showNewClassDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("⊕ New Class Folder", "#0038A8", Gravity.START, 20));

        // Teacher name field removed — teacher name is set globally in the header

        root.addView(createFieldLabel("GRADE *"));
        EditText gradeInput = createLightInput("e.g. Grade 10");
        root.addView(gradeInput);

        root.addView(createFieldLabel("SECTION *"));
        EditText sectionInput = createLightInput("e.g. Section A");
        root.addView(sectionInput);

        root.addView(createFieldLabel("SCHOOL YEAR"));
        // School Year dropdown — default to current school year
        final String[] schoolYearOptions = buildSchoolYearOptions();
        int currentYr = Calendar.getInstance().get(Calendar.YEAR);
        String defaultSY = currentYr + "-" + (currentYr + 1);
        // Find position of the current SY in the array (fallback to first)
        int defaultIdx = 0;
        for (int i = 0; i < schoolYearOptions.length; i++) {
            if (schoolYearOptions[i].equals(defaultSY)) {
                defaultIdx = i;
                break;
            }
        }
        final String[] selectedSchoolYear = { schoolYearOptions[defaultIdx] };
        TextView schoolYearPicker = createDropdownField(schoolYearOptions[defaultIdx]);
        schoolYearPicker.setTextColor(Color.parseColor("#1E293B"));
        schoolYearPicker.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Select School Year")
                    .setItems(schoolYearOptions, (dlg, which) -> {
                        selectedSchoolYear[0] = schoolYearOptions[which];
                        schoolYearPicker.setText(schoolYearOptions[which] + "  ▾");
                        schoolYearPicker.setTextColor(Color.parseColor("#1E293B"));
                    })
                    .show();
        });
        root.addView(schoolYearPicker);

        LinearLayout actions = buildActionsRow(dp(20));
        TextView btnCancel = createDialogButton("Cancel", false);
        TextView btnDone = createDialogButton("Done", true);
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String grade = gradeInput.getText().toString().trim();
            String section = sectionInput.getText().toString().trim();
            if (grade.isEmpty()) {
                showErrorDialog("Missing Grade", "Please enter the grade level (e.g. Grade 10).");
                return;
            }
            if (section.isEmpty()) {
                showErrorDialog("Missing Section", "Please enter the section name (e.g. Section A).");
                return;
            }
            for (ClassFolder existing : classFolders) {
                if (existing.getGrade().equalsIgnoreCase(grade)
                        && existing.getSection().equalsIgnoreCase(section)) {
                    showErrorDialog("Duplicate Class",
                            "A class with \"" + grade + " — " + section
                                    + "\" already exists.\nPlease use a different Grade or Section.");
                    return;
                }
            }
            String schoolYear = selectedSchoolYear[0];
            // Use the global teacher name when creating the class
            String teacher = (globalTeacherName != null && !globalTeacherName.isEmpty())
                    ? globalTeacherName
                    : "Unknown Teacher";
            ClassFolder cls = new ClassFolder(teacher, grade, section, schoolYear);
            ensureTeacherId(teacherId -> {
                if (teacherId <= 0) {
                    runOnUiThread(() -> showErrorDialog("Create Failed",
                            "Teacher profile is not ready yet. Please try again."));
                    return;
                }

                ClassEntity entity = DataMapper.toClassEntity(cls, teacherId);
                repo.insertClass(entity, ignored -> runOnUiThread(() -> {
                    dialog.dismiss();
                    showScreen(SCREEN_HOME);
                    showToast("Class folder created ✓");
                    loadDataFromDb();
                }));
            });
        });

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void showNewActivityDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("⊕ New Assessment", "#0038A8", Gravity.START, 20));

        root.addView(createFieldLabel("ASSESSMENT NAME *"));
        EditText nameInput = createLightInput("e.g. Math Pop Quiz 1");
        root.addView(nameInput);

        root.addView(createFieldLabel("EXAM DATE"));
        EditText dateInput = createLightInput("Select date");
        dateInput.setFocusable(false);
        dateInput.setClickable(true);
        dateInput.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new java.util.Date()));
        dateInput.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth);
                dateInput.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(chosen.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        root.addView(dateInput);

        root.addView(createFieldLabel("OMR SHEET TYPE"));

        String[][] sheetTypes = {
                { "ZPH30", "30 Items" },
                { "ZPH40", "40 Items" },
                { "ZPH50", "50 Items" },
                { "ZPH60", "60 Items" }
        };
        final String[] selectedType = { "ZPH30" };

        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = dp(16);
        typeRow.setLayoutParams(trLp);

        final TextView[] typeButtons = new TextView[sheetTypes.length];
        for (int i = 0; i < sheetTypes.length; i++) {
            final int idx = i;
            TextView btn = new TextView(this);
            btn.setText(sheetTypes[i][0] + "\n" + sheetTypes[i][1]);
            btn.setTextSize(12);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(10), dp(10), dp(10), dp(10));
            btn.setClickable(true);
            btn.setFocusable(true);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < sheetTypes.length - 1)
                blp.rightMargin = dp(8);
            btn.setLayoutParams(blp);
            typeButtons[i] = btn;
            btn.setOnClickListener(v -> {
                selectedType[0] = sheetTypes[idx][0];
                updateSheetTypeSelection(typeButtons, idx);
            });
            typeRow.addView(btn);
        }
        root.addView(typeRow);
        updateSheetTypeSelection(typeButtons, 0);

        LinearLayout actions = buildActionsRow(dp(4));
        TextView btnCancel = createDialogButton("Cancel", false);
        TextView btnDone = createDialogButton("Done", true);
        actions.addView(btnCancel);
        actions.addView(spacer(dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                showErrorDialog("Missing Name", "Assessment name is required to create an assessment.");
                return;
            }
            if (selectedClass.getActivities() != null) {
                for (ActivityFolder existing : selectedClass.getActivities()) {
                    if (existing.getName().equalsIgnoreCase(name)) {
                        showErrorDialog("Duplicate Assessment",
                                "An assessment named \"" + name
                                        + "\" already exists in this class.\nPlease use a different name.");
                        return;
                    }
                }
            }
            ActivityFolder act = new ActivityFolder(name, selectedType[0]);
            String examDate = dateInput.getText().toString().trim();
            act.setExamDate(examDate);
            act.setExamDateEpoch(parseExamDateToEpoch(examDate, System.currentTimeMillis()));
            AssessmentEntity entity = DataMapper.toAssessmentEntity(act, selectedClass.getId());
            repo.insertAssessment(entity, ignored -> runOnUiThread(() -> {
                dialog.dismiss();
                showScreen(SCREEN_CLASS);
                showToast("Assessment folder created ✓");
                loadDataFromDb();
            }));
        });

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void updateSheetTypeSelection(TextView[] buttons, int selectedIdx) {
        for (int i = 0; i < buttons.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            if (i == selectedIdx) {
                bg.setColor(Color.parseColor("#0038A8"));
                bg.setStroke(dp(1), Color.parseColor("#0038A8"));
                buttons[i].setBackground(bg);
                buttons[i].setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.parseColor("#F8FAFC"));
                bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
                buttons[i].setBackground(bg);
                buttons[i].setTextColor(Color.parseColor("#64748B"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCAN METHOD DIALOG
    // ═══════════════════════════════════════════════════════════════

    private void showScanMethodDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("📷 Start Scanning", "#0038A8", Gravity.START, 4));

        TextView subtitle = new TextView(this);
        subtitle.setText(selectedActivity.getSheetType()
                + " · " + selectedActivity.getNumItems() + " items");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(20);
        subtitle.setLayoutParams(slp);
        root.addView(subtitle);

        root.addView(createScanOptionCard(dialog, "📸", "Open Camera",
                "Take a photo of the answer sheet", "camera"));
        root.addView(createScanOptionCard(dialog, "🖼", "Upload Image",
                "Choose from gallery", "gallery"));

        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextSize(14);
        cancel.setTextColor(Color.parseColor("#94A3B8"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(16), 0, dp(8));
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private View createScanOptionCard(Dialog dialog, String emoji, String label,
            String desc, String action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView iconView = new TextView(this);
        iconView.setText(emoji);
        iconView.setTextSize(28);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = dp(14);
        iconView.setLayoutParams(ilp);
        card.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(this);
        nameView.setText(label);
        nameView.setTextSize(15);
        nameView.setTextColor(Color.parseColor("#1E293B"));
        nameView.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(2);
        descView.setLayoutParams(dlp);
        textCol.addView(descView);
        card.addView(textCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        card.addView(arrow);

        card.setOnClickListener(v -> {
            selectedSheetType = selectedActivity.getSheetType();
            dialog.dismiss();
            if ("camera".equals(action))
                openCamera();
            else
                openGallery();
        });
        return card;
    }

    private View createSelectionCard(Dialog dialog, String emoji, String label,
            String desc, Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView iconView = new TextView(this);
        iconView.setText(emoji);
        iconView.setTextSize(28);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = dp(14);
        iconView.setLayoutParams(ilp);
        card.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(this);
        nameView.setText(label);
        nameView.setTextSize(15);
        nameView.setTextColor(Color.parseColor("#1E293B"));
        nameView.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(2);
        descView.setLayoutParams(dlp);
        textCol.addView(descView);
        card.addView(textCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        card.addView(arrow);

        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    private void showGlobalUploadClassDialog() {
        if (classFolders == null || classFolders.isEmpty()) {
            showToast("No classes available. Please create a class first.");
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("🎓 Select Class to Upload To", "#0038A8", Gravity.START, 4));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(400)));
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        for (ClassFolder cls : classFolders) {
            View card = createSelectionCard(dialog, "📁", cls.getDisplayName(), cls.getActivityCount() + " Assessments", () -> {
                dialog.dismiss();
                showGlobalUploadAssessmentDialog(cls);
            });
            listContainer.addView(card);
        }

        root.addView(scrollView);

        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextSize(14);
        cancel.setTextColor(Color.parseColor("#94A3B8"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(16), 0, dp(8));
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void showGlobalUploadAssessmentDialog(ClassFolder cls) {
        if (cls.getActivities() == null || cls.getActivities().isEmpty()) {
            showToast("No assessments available in this class. Please create one first.");
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());
        root.addView(buildSheetTitle("📋 Select Assessment", "#0038A8", Gravity.START, 4));

        TextView subtitle = new TextView(this);
        subtitle.setText(cls.getDisplayName());
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(20);
        subtitle.setLayoutParams(slp);
        root.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(400)));
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        for (ActivityFolder act : cls.getActivities()) {
            View card = createSelectionCard(dialog, "📝", act.getName(), act.getSheetType() + " · " + act.getNumItems() + " Items", () -> {
                dialog.dismiss();
                selectedClass = cls;
                selectedActivity = act;
                selectedSheetType = act.getSheetType();
                openGallery();
            });
            listContainer.addView(card);
        }

        root.addView(scrollView);

        TextView back = new TextView(this);
        back.setText("Back to Classes");
        back.setTextSize(14);
        back.setTextColor(Color.parseColor("#94A3B8"));
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(16), 0, dp(8));
        back.setOnClickListener(v -> {
            dialog.dismiss();
            showGlobalUploadClassDialog();
        });
        root.addView(back);

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // DOWNLOAD CLASS DATA
    // ═══════════════════════════════════════════════════════════════

    private void downloadClassData(ClassFolder cls) {
        if (cls.getActivities() == null || cls.getActivities().isEmpty()) {
            showToast("No data to download");
            return;
        }
        showDownloadPasswordDialog(cls);
    }

    private void showDownloadPasswordDialog(ClassFolder cls) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);

        // ==== Password Field ====
        TextInputLayout passwordLayout = new TextInputLayout(this);
        passwordLayout.setHintEnabled(false);
        passwordLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        passwordLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText passwordInput = new TextInputEditText(passwordLayout.getContext());
        passwordInput.setHint("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setBackground(createInputBg());
        passwordInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        passwordLayout.addView(passwordInput);

        // ==== Confirm Password Field ====
        TextInputLayout confirmLayout = new TextInputLayout(this);
        confirmLayout.setHintEnabled(false);
        confirmLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        confirmLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText confirmInput = new TextInputEditText(confirmLayout.getContext());
        confirmInput.setHint("Confirm password");
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmInput.setBackground(createInputBg());
        confirmInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        confirmLayout.addView(confirmInput);

        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldLp.bottomMargin = dp(10);
        passwordLayout.setLayoutParams(fieldLp);
        confirmLayout.setLayoutParams(fieldLp);

        content.addView(passwordLayout);
        content.addView(confirmLayout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Protect Download")
                .setMessage("Set a password for this ZIP file.")
                .setView(content)
                .setPositiveButton("Download", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText() != null ? passwordInput.getText().toString().trim() : "";
            String confirm = confirmInput.getText() != null ? confirmInput.getText().toString().trim() : "";

            if (password.isEmpty()) {
                passwordInput.setError("Password is required");
                return;
            }
            if (!password.equals(confirm)) {
                confirmInput.setError("Passwords do not match");
                return;
            }

            dialog.dismiss();
            showToast("Preparing protected download...");
            new Thread(() -> runProtectedClassDownload(cls, password)).start();
        }));

        dialog.show();
    }

    private GradientDrawable createInputBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.parseColor("#CBD5E1"));
        return bg;
    }

    private void runProtectedClassDownload(ClassFolder cls, String password) {
        try {
            DownloadExportResult result = exportClassDataToProtectedZip(cls, password);
            runOnUiThread(() -> showDownloadSuccessDialog(cls, result.zipFile, result.totalImages, result.totalCsvs));
        } catch (Exception e) {
            Log.e(TAG, "Error downloading class data", e);
            runOnUiThread(() -> showToast("Error exporting: " + e.getMessage()));
        }
    }

    private DownloadExportResult exportClassDataToProtectedZip(ClassFolder cls, String password) throws Exception {
        File downloadsDir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File omrDir = new File(downloadsDir, "OMRScanner");
        if (!omrDir.exists() && !omrDir.mkdirs()) {
            throw new IllegalStateException("Unable to create Downloads/OMRScanner");
        }

        String folderName = buildClassFolderName(cls);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File stagingRoot = new File(getCacheDir(), "omr_export_" + System.currentTimeMillis());
        File classDir = new File(stagingRoot, folderName);
        if (!classDir.mkdirs()) {
            throw new IllegalStateException("Unable to create export folder");
        }

        int totalImages = 0;
        int totalCsvs = 0;

        try {
            for (ActivityFolder act : cls.getActivities()) {
                List<ScanEntry> scans = act.getScans();
                if (scans == null || scans.isEmpty()) {
                    continue;
                }

                String actDirName = sanitizeFilePart(act.getName());
                File actDir = new File(classDir, actDirName);
                File imagesDir = new File(actDir, "images");
                File resultsDir = new File(actDir, "result");
                if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                    throw new IllegalStateException("Unable to create " + imagesDir.getName());
                }
                if (!resultsDir.exists() && !resultsDir.mkdirs()) {
                    throw new IllegalStateException("Unable to create " + resultsDir.getName());
                }

                int scanNum = 0;
                for (ScanEntry scan : scans) {
                    scanNum++;
                    String srcPath = scan.getOverlayImagePath();
                    if (srcPath == null || !(new File(srcPath).exists())) {
                        srcPath = scan.getImagePath();
                    }
                    if (srcPath != null) {
                        File srcFile = new File(srcPath);
                        if (srcFile.exists()) {
                            String lrnPart = (scan.getLrn() != null && !scan.getLrn().isEmpty())
                                    ? scan.getLrn().replaceAll("[^a-zA-Z0-9]", "")
                                    : "scan_" + scanNum;
                            File dest = new File(imagesDir, lrnPart + ".jpg");
                            boolean reachedTarget = compressImageForExport(srcFile, dest);
                            if (!reachedTarget) {
                                Log.w(TAG, "Export image still above target size: " + dest.getName());
                            }
                            if (dest.exists()) {
                                totalImages++;
                            }
                        }
                    }

                    String lrnOnly = (scan.getLrn() != null && !scan.getLrn().isEmpty())
                            ? scan.getLrn()
                            : "scan_" + scanNum;
                    String indName = (lrnOnly + "_" + cls.getGrade() + "-" + cls.getSection() + "_"
                            + act.getName().replaceAll("\\s+", "") + ".csv")
                            .replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
                    StringBuilder sb = new StringBuilder();
                    String lrnVal = scan.getLrn() != null ? scan.getLrn() : "";
                    for (int c = 0; c < lrnVal.length(); c++) {
                        sb.append(lrnVal.charAt(c)).append(";");
                    }
                    for (int k = 1; k <= act.getNumItems(); k++) {
                        String ans = scan.getAnswers() != null ? scan.getAnswers().get(k) : null;
                        sb.append(ans != null ? ans : "");
                        if (k < act.getNumItems()) {
                            sb.append(";");
                        }
                    }
                    sb.append("\n");
                    writeTextFile(new File(resultsDir, indName), sb.toString());
                    totalCsvs++;
                }

                StringBuilder actCsv = new StringBuilder();
                for (ScanEntry scan : scans) {
                    String lrnVal = scan.getLrn() != null ? scan.getLrn() : "";
                    for (int c = 0; c < lrnVal.length(); c++) {
                        actCsv.append(lrnVal.charAt(c)).append(";");
                    }
                    for (int i = 1; i <= act.getNumItems(); i++) {
                        String ans = scan.getAnswers() != null ? scan.getAnswers().get(i) : null;
                        actCsv.append(ans != null ? ans : "");
                        if (i < act.getNumItems()) {
                            actCsv.append(";");
                        }
                    }
                    actCsv.append("\n");
                }
                File csvFile = new File(actDir, actDirName + ".csv");
                writeTextFile(csvFile, actCsv.toString());
                totalCsvs++;
            }

            File zipFile = new File(omrDir, folderName + "_" + timestamp + ".zip");
            createEncryptedZip(classDir, zipFile, password);
            scanMediaFile(zipFile);
            return new DownloadExportResult(zipFile, totalImages, totalCsvs);
        } finally {
            deleteRecursively(stagingRoot);
        }
    }

    private String buildClassFolderName(ClassFolder cls) {
        String grade = cls.getGrade() != null ? cls.getGrade().replaceAll("\\s+", "") : "Class";
        String section = cls.getSection() != null ? cls.getSection().replaceAll("\\s+", "") : "Section";
        return sanitizeFilePart(grade + "-" + section);
    }

    private String sanitizeFilePart(String value) {
        String sanitized = value != null ? value.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim() : "";
        sanitized = sanitized.replaceAll("\\s+", "_");
        return sanitized.isEmpty() ? "item" : sanitized;
    }

    private void writeTextFile(File target, String content) throws Exception {
        try (FileWriter writer = new FileWriter(target)) {
            writer.write(content);
        }
    }

    private boolean compressImageForExport(File srcFile, File destFile) throws Exception {
        Bitmap original = BitmapFactory.decodeFile(srcFile.getAbsolutePath());
        if (original == null) {
            return false;
        }

        Bitmap working = original;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bestBytes = null;
        boolean hitTarget = false;

        try {
            while (true) {
                for (int quality = 92; quality >= 35; quality -= 7) {
                    baos.reset();
                    working.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                    byte[] current = baos.toByteArray();
                    if (bestBytes == null || current.length < bestBytes.length) {
                        bestBytes = current;
                    }
                    if (current.length <= EXPORT_IMAGE_TARGET_BYTES) {
                        bestBytes = current;
                        hitTarget = true;
                        break;
                    }
                }

                if (hitTarget) {
                    break;
                }

                int minEdge = Math.min(working.getWidth(), working.getHeight());
                if (minEdge <= EXPORT_IMAGE_MIN_EDGE_PX) {
                    break;
                }

                int nextW = Math.max(EXPORT_IMAGE_MIN_EDGE_PX, Math.round(working.getWidth() * 0.85f));
                int nextH = Math.max(EXPORT_IMAGE_MIN_EDGE_PX, Math.round(working.getHeight() * 0.85f));
                if (nextW >= working.getWidth() || nextH >= working.getHeight()) {
                    break;
                }

                Bitmap scaled = Bitmap.createScaledBitmap(working, nextW, nextH, true);
                if (working != original) {
                    working.recycle();
                }
                working = scaled;
            }

            if (bestBytes == null) {
                return false;
            }

            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(bestBytes);
            }
            return bestBytes.length <= EXPORT_IMAGE_TARGET_BYTES;
        } finally {
            if (working != original) {
                working.recycle();
            }
            original.recycle();
        }
    }

    private void createEncryptedZip(File sourceDir, File zipFile, String password) throws Exception {
        ZipParameters zipParams = new ZipParameters();
        zipParams.setEncryptFiles(true);
        zipParams.setEncryptionMethod(EncryptionMethod.AES);
        zipParams.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        ZipFile protectedZip = new ZipFile(zipFile, password.toCharArray());
        protectedZip.addFolder(sourceDir, zipParams);
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Unable to delete temp path: " + file.getAbsolutePath());
        }
    }

    private void scanMediaFile(File file) {
        android.media.MediaScannerConnection.scanFile(
                this, new String[] { file.getAbsolutePath() }, null, null);
    }

    private void showDownloadSuccessDialog(ClassFolder cls, File zipFile,
            int totalImages, int totalCsvs) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = buildSheet();
        root.addView(createDialogHandle());

        TextView iconView = new TextView(this);
        iconView.setText("✅");
        iconView.setTextSize(40);
        iconView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = dp(12);
        iconView.setLayoutParams(ilp);
        root.addView(iconView);

        root.addView(buildSheetTitle("Download Complete!", "#16A34A", Gravity.CENTER, 12));

        TextView info = new TextView(this);
        info.setText("🔒 " + cls.getDisplayName() + "\n🖼️ " + totalImages
                + " image" + (totalImages != 1 ? "s" : "")
                + "  •  📄 " + totalCsvs + " CSV" + (totalCsvs != 1 ? "s" : ""));
        info.setTextSize(13);
        info.setTextColor(Color.parseColor("#64748B"));
        info.setGravity(Gravity.CENTER);
        info.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams inlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inlp.bottomMargin = dp(8);
        info.setLayoutParams(inlp);
        root.addView(info);

        TextView pathLabel = new TextView(this);
        pathLabel.setText("📦 Downloads/OMRScanner/" + zipFile.getName());
        pathLabel.setTextSize(11);
        pathLabel.setTextColor(Color.parseColor("#0038A8"));
        pathLabel.setGravity(Gravity.CENTER);
        pathLabel.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable pathBg = new GradientDrawable();
        pathBg.setColor(Color.parseColor("#F0F7FF"));
        pathBg.setCornerRadius(dp(8));
        pathBg.setStroke(dp(1), Color.parseColor("#BFDBFE"));
        pathLabel.setBackground(pathBg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.bottomMargin = dp(20);
        pathLabel.setLayoutParams(plp);
        root.addView(pathLabel);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnOpen = createDialogButton("Open Downloads", true);
        TextView btnDone = createDialogButton("Done", false);
        actions.addView(btnOpen);
        actions.addView(spacer(dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnOpen.setOnClickListener(v -> {
            dialog.dismiss();
            openFolderInFileManager(zipFile.getParentFile());
        });
        btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void openFolderInFileManager(File folder) {
        if (folder == null) {
            showToast("File saved to: Downloads/OMRScanner/");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(folder.getAbsolutePath()), "resource/folder");
            if (intent.resolveActivity(getPackageManager()) != null)
                startActivity(intent);
            else
                showToast("File saved to: Downloads/OMRScanner/");
        } catch (Exception e) {
            showToast("File saved to: Downloads/OMRScanner/");
        }
    }

    private static class DownloadExportResult {
        final File zipFile;
        final int totalImages;
        final int totalCsvs;

        DownloadExportResult(File zipFile, int totalImages, int totalCsvs) {
            this.zipFile = zipFile;
            this.totalImages = totalImages;
            this.totalCsvs = totalCsvs;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CAMERA / GALLERY
    // ═══════════════════════════════════════════════════════════════

    private void openCamera() {
        try {
            Intent intent = new Intent(this, CameraActivity.class);
            if (selectedSheetType != null)
                intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
            if (selectedClass != null)
                intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
            if (selectedActivity != null)
                intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openGallery() {
        try {
            galleryLauncher.launch("image/*");
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery", e);
            Toast.makeText(this, "Error opening gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedImage(android.net.Uri imageUri) {
        Toast.makeText(this, "Processing selected image...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String savedPath = saveImageToFile(imageUri);
                if (savedPath != null) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(DashboardActivity.this,
                                com.example.omrscanner.ui.PreviewActivity.class);
                        intent.putExtra(com.example.omrscanner.ui.PreviewActivity.IMAGE_PATH, savedPath);
                        intent.putExtra(com.example.omrscanner.ui.PreviewActivity.IMAGE_SOURCE,
                                com.example.omrscanner.ui.PreviewActivity.SOURCE_GALLERY);
                        if (selectedSheetType != null)
                            intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
                        if (selectedClass != null)
                            intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
                        if (selectedActivity != null)
                            intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String saveImageToFile(android.net.Uri imageUri) {
        try {
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media
                    .getBitmap(getContentResolver(), imageUri);
            if (bitmap == null)
                return null;
            java.io.File outputFile = java.io.File.createTempFile("omr_upload_", ".jpg", getCacheDir());
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            bitmap.recycle();
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE — Room Database
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load all data from Room in one chained background call.
     * 1. Teacher → set globalTeacherName + currentTeacherId.
     * 2. Classes → for each class, load its Assessments.
     * 3. Assessments → for each assessment, load its Scans.
     * 4. Scans → for each scan, load its Answers.
     * When the chain is complete we jump back to the UI thread and
     * re-navigate to the same screen the user was on before.
     *
     * NOTE: We do the entire load on the repo's single background thread
     * by nesting callbacks. No concurrency conflicts because the repo's
     * ExecutorService is single-threaded.
     */
    private void loadDataFromDb() {
        // Snapshot the selected IDs so the lambdas don't hold mutable refs
        final String prevClassId = (selectedClass != null) ? selectedClass.getId() : null;
        final String prevActivityId = (selectedActivity != null) ? selectedActivity.getId() : null;
        final String prevScreen = currentScreen;

        repo.getFirstTeacher(teacher -> {
            if (teacher != null) {
                globalTeacherName = teacher.name != null ? teacher.name : "";
                currentTeacherId = teacher.id;
                loadClassesFromDb(prevClassId, prevActivityId, prevScreen);
                return;
            }

            // Ensure a teacher row exists so class inserts always have a valid FK.
            repo.upsertTeacher("", ensuredTeacher -> {
                if (ensuredTeacher != null) {
                    globalTeacherName = ensuredTeacher.name != null ? ensuredTeacher.name : "";
                    currentTeacherId = ensuredTeacher.id;
                } else {
                    globalTeacherName = "";
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

            // We need to load assessments for every class, then scans for
            // every assessment. Use an AtomicInteger to track completion.
            AtomicInteger classCountdown = new AtomicInteger(classEntities.size());

            for (ClassEntity ce : classEntities) {
                ClassFolder cf = DataMapper.toClassFolder(ce, globalTeacherName);

                // ── Step 3: load assessments for this class ─────────────
                repo.getAssessmentsByClass(ce.id, assessmentEntities -> {
                    List<ActivityFolder> activities = new ArrayList<>();

                    if (assessmentEntities == null || assessmentEntities.isEmpty()) {
                        cf.setActivities(activities);
                        loadedClasses.add(cf);
                        if (classCountdown.decrementAndGet() == 0) {
                            publishResult(loadedClasses, prevClassId, prevActivityId, prevScreen);
                        }
                        return;
                    }

                    AtomicInteger assessmentCountdown = new AtomicInteger(assessmentEntities.size());

                    for (AssessmentEntity ae : assessmentEntities) {
                        ActivityFolder af = DataMapper.toActivityFolder(ae);

                        // ── Step 4: load scans for this assessment ──────
                        repo.getScansByAssessment(ae.id, scanEntities -> {
                            List<ScanEntry> scanEntries = new ArrayList<>();

                            if (scanEntities == null || scanEntities.isEmpty()) {
                                af.setScans(scanEntries);
                                activities.add(af);
                                if (assessmentCountdown.decrementAndGet() == 0) {
                                    cf.setActivities(activities);
                                    loadedClasses.add(cf);
                                    if (classCountdown.decrementAndGet() == 0) {
                                        publishResult(loadedClasses, prevClassId,
                                                prevActivityId, prevScreen);
                                    }
                                }
                                return;
                            }

                            AtomicInteger scanCountdown = new AtomicInteger(scanEntities.size());

                            for (ScanEntity se : scanEntities) {
                                // ── Step 5: load answers for this scan ──
                                repo.getAnswersByScan(se.id, answerEntities -> {
                                    Map<Integer, String> answers = DataMapper.toAnswerMap(answerEntities);
                                    scanEntries.add(DataMapper.toScanEntry(se, answers));

                                    if (scanCountdown.decrementAndGet() == 0) {
                                        af.setScans(scanEntries);
                                        activities.add(af);
                                        if (assessmentCountdown.decrementAndGet() == 0) {
                                            cf.setActivities(activities);
                                            loadedClasses.add(cf);
                                            if (classCountdown.decrementAndGet() == 0) {
                                                publishResult(loadedClasses, prevClassId,
                                                        prevActivityId, prevScreen);
                                            }
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

    private void ensureTeacherId(OMRRepository.Callback<Integer> callback) {
        if (callback == null) {
            return;
        }

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

    /**
     * Called on the repo background thread once all data has been loaded.
     * Switches back to the main thread, sets the in-memory list, and
     * restores the previously active screen.
     */
    private void publishResult(List<ClassFolder> loaded,
            String prevClassId, String prevActivityId, String prevScreen) {
        runOnUiThread(() -> {
            classFolders = loaded;
            Log.d(TAG, "Loaded " + classFolders.size() + " classes from Room");

            // Re-resolve the selected class / activity by ID so that after
            // a reload the correct objects are referenced.
            if (prevClassId != null) {
                selectedClass = findClassById(prevClassId);
                if (selectedClass != null && prevActivityId != null) {
                    selectedActivity = findActivityById(selectedClass, prevActivityId);
                } else {
                    selectedActivity = null;
                }
            }
            showScreen(prevScreen != null ? prevScreen : SCREEN_HOME);
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Static helpers for other Activities (CameraActivity / PreviewActivity)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Check whether an LRN already has a scan in a given assessment.
     * <p>
     * Must be called from a <em>background</em> thread — Room cannot run on
     * the main thread and this method blocks.
     */
    public static boolean isLrnExists(android.content.Context context,
            String classId, String activityId, String lrn) {
        if (activityId == null || lrn == null)
            return false;
        OMRRepository r = new OMRRepository(context);
        return r.isLrnExistsSync(activityId, lrn);
    }

    /**
     * Persist a scan result to Room.
     * <p>
     * Must be called from a <em>background</em> thread. If {@code replace}
     * is true and a scan with the same LRN already exists in the assessment,
     * the existing row is updated instead of inserting a new one.
     */
    public static void saveScanResult(android.content.Context context,
            String classId, String activityId,
            ScanEntry scanEntry, boolean replace) {
        if (activityId == null || scanEntry == null)
            return;
        OMRRepository r = new OMRRepository(context);
        ScanEntity existing = (replace && scanEntry.getLrn() != null)
                ? r.getScanByAssessmentAndLrnSync(activityId, scanEntry.getLrn())
                : null;

        ScanEntity entity = DataMapper.toScanEntity(scanEntry, activityId);
        if (existing != null) {
            entity.id = existing.id; // keep the original PK so Room does an UPDATE
            r.updateScan(entity, null);
            // Re-save answers: delete old ones first, then insert new
            r.deleteAnswersByScan(existing.id,
                    done -> r.insertAnswersFromMap(existing.id, scanEntry.getAnswers(), null));
        } else {
            // insertScan returns the new row ID; use it to store answers
            r.insertScan(entity, newId -> {
                if (newId != null && newId > 0) {
                    r.insertAnswersFromMap(newId.intValue(), scanEntry.getAnswers(), null);
                }
            });
        }
    }

    private ClassFolder findClassById(String classId) {
        if (classId == null)
            return null;
        for (ClassFolder cls : classFolders)
            if (cls.getId().equals(classId))
                return cls;
        return null;
    }

    private ActivityFolder findActivityById(ClassFolder cls, String activityId) {
        if (cls == null || activityId == null || cls.getActivities() == null)
            return null;
        for (ActivityFolder act : cls.getActivities())
            if (act.getId().equals(activityId))
                return act;
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // ERROR DIALOG
    // ═══════════════════════════════════════════════════════════════

    private Dialog activeErrorDialog = null;

    private void showErrorDialog(String title, String message) {
        if (activeErrorDialog != null && activeErrorDialog.isShowing())
            activeErrorDialog.dismiss();

        Dialog errorDialog = new Dialog(this);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable dBg = new GradientDrawable();
        dBg.setColor(Color.WHITE);
        dBg.setCornerRadius(dp(24));
        root.setBackground(dBg);

        TextView iconView = new TextView(this);
        iconView.setText("⚠️");
        iconView.setTextSize(32);
        iconView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(60), dp(60));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        ilp.bottomMargin = dp(16);
        iconView.setLayoutParams(ilp);
        root.addView(iconView);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#CE1126"));
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp2.bottomMargin = dp(8);
        titleView.setLayoutParams(tlp2);
        root.addView(titleView);

        TextView msgView = new TextView(this);
        msgView.setText(message);
        msgView.setTextSize(13);
        msgView.setTextColor(Color.parseColor("#64748B"));
        msgView.setGravity(Gravity.CENTER);
        msgView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.bottomMargin = dp(24);
        msgView.setLayoutParams(mlp);
        root.addView(msgView);

        TextView btnDismiss = new TextView(this);
        btnDismiss.setText("Got it");
        btnDismiss.setTextSize(14);
        btnDismiss.setTypeface(null, Typeface.BOLD);
        btnDismiss.setGravity(Gravity.CENTER);
        btnDismiss.setTextColor(Color.WHITE);
        GradientDrawable dismissBg = new GradientDrawable();
        dismissBg.setColor(Color.parseColor("#CE1126"));
        dismissBg.setCornerRadius(dp(12));
        btnDismiss.setBackground(dismissBg);
        btnDismiss.setPadding(dp(20), dp(12), dp(20), dp(12));
        btnDismiss.setClickable(true);
        btnDismiss.setFocusable(true);
        btnDismiss.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnDismiss.setOnClickListener(v -> errorDialog.dismiss());
        root.addView(btnDismiss);

        errorDialog.setContentView(root);
        if (errorDialog.getWindow() != null) {
            errorDialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.82),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            errorDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            errorDialog.getWindow().setGravity(Gravity.CENTER);
            errorDialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        activeErrorDialog = errorDialog;
        errorDialog.setOnDismissListener(d -> {
            if (activeErrorDialog == errorDialog)
                activeErrorDialog = null;
        });
        errorDialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER HELPERS
    // ═══════════════════════════════════════════════════════════════

    /** Standard bottom-sheet root */
    private LinearLayout buildSheet() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadii(new float[] { dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0 });
        root.setBackground(bg);
        return root;
    }

    private TextView buildSheetTitle(String text, String hexColor, int gravity, int bottomMarginDp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor(hexColor));
        tv.setGravity(gravity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(bottomMarginDp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout buildActionsRow(int topMarginPx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMarginPx;
        row.setLayoutParams(lp);
        return row;
    }

    private View spacer(int widthPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 0));
        return v;
    }

    private View createDialogHandle() {
        View handle = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(20);
        handle.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E2E8F0"));
        bg.setCornerRadius(dp(2));
        handle.setBackground(bg);
        return handle;
    }

    private TextView createFieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(Color.parseColor("#64748B"));
        label.setTypeface(null, Typeface.BOLD);
        label.setAllCaps(true);
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        label.setLayoutParams(lp);
        return label;
    }

    private EditText createLightInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#CBD5E1"));
        input.setTextColor(Color.parseColor("#1E293B"));
        input.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        input.setBackground(bg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        input.setLayoutParams(lp);
        return input;
    }

    private TextView createDialogButton(String text, boolean isPrimary) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (isPrimary) {
            bg.setColor(Color.parseColor("#0038A8"));
            btn.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#F8FAFC"));
            bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
            btn.setTextColor(Color.parseColor("#64748B"));
        }
        btn.setBackground(bg);
        return btn;
    }

    private void configureBottomDialog(Dialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow()
                    .getAttributes().windowAnimations = com.google.android.material.R.style.Animation_Design_BottomSheetDialog;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCHOOL YEAR HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build an array of school-year strings centred on the current calendar year.
     */
    private String[] buildSchoolYearOptions() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        // Range: 5 years before … 4 years after (10 options total)
        int startYear = currentYear - 5;
        String[] options = new String[10];
        for (int i = 0; i < 10; i++) {
            int y = startYear + i;
            options[i] = y + "-" + (y + 1);
        }
        return options;
    }

    /**
     * Creates a clickable TextView styled as a dropdown field (matching the
     * {@link #createLightInput} look) with a ▼ indicator.
     */
    private TextView createDropdownField(String initialText) {
        TextView tv = new TextView(this);
        tv.setText(initialText + "  ▾");
        tv.setTag(initialText); // keep raw value in tag
        tv.setHint("Select…");
        tv.setHintTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(14);
        tv.setClickable(true);
        tv.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        tv.setBackground(bg);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        tv.setLayoutParams(lp);
        return tv;
    }

    private long parseExamDateToEpoch(String examDate, long fallback) {
        if (examDate == null || examDate.trim().isEmpty()) {
            return fallback;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            sdf.setLenient(false);
            java.util.Date parsed = sdf.parse(examDate.trim());
            if (parsed != null) {
                return parsed.getTime();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════════════════════════════

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
