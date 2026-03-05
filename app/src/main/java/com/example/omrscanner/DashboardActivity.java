package com.example.omrscanner;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.app.DatePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import android.widget.ArrayAdapter;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.example.omrscanner.ui.ScanDetailActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    // Extras
    public static final String EXTRA_SHEET_TYPE = "sheet_type";
    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";

    // Screens
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_CLASS = "class";
    private static final String SCREEN_ACTIVITY = "activity";

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

    // Views
    private ImageButton btnBack;
    private TextView topBarTitle, topBarBadge;
    private TextView tvTeacherName;
    private LinearLayout teacherNameRow;
    private View screenHome;
    private ScrollView screenClass, screenActivity;
    private LinearLayout homeEmpty, homeClassList;
    private TextView classTeacherLabel;
    private LinearLayout classEmpty, classActivityList;
    private LinearLayout classSheetTabs;
    private TextView classAssessmentCount;

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
        topBarTitle = findViewById(R.id.topBarTitle);
        topBarBadge = findViewById(R.id.topBarBadge);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        teacherNameRow = findViewById(R.id.teacherNameRow);

        screenHome = findViewById(R.id.screenHome);
        screenClass = findViewById(R.id.screenClass);
        screenActivity = findViewById(R.id.screenActivity);

        homeEmpty = findViewById(R.id.homeEmpty);
        homeClassList = findViewById(R.id.homeClassList);

        classTeacherLabel = findViewById(R.id.classTeacherLabel);
        classEmpty = findViewById(R.id.classEmpty);
        classActivityList = findViewById(R.id.classActivityList);
        classSheetTabs = findViewById(R.id.classSheetTabs);
        classAssessmentCount = findViewById(R.id.classAssessmentCount);

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
                selectedSheetFilter = null; // reset tab filter for new class
                topBarTitle.setText(selectedClass.getDisplayName());
                topBarBadge.setVisibility(View.VISIBLE);
                topBarBadge.setText("📁 " + selectedClass.getActivityCount());
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
        if (homeClassCount != null)
            homeClassCount.setText(classFolders.size() + " total");

        if (classFolders.isEmpty()) {
            homeEmpty.setVisibility(View.VISIBLE);
            homeClassList.setVisibility(View.GONE);
        } else {
            homeEmpty.setVisibility(View.GONE);
            homeClassList.setVisibility(View.VISIBLE);
            for (ClassFolder cls : classFolders)
                homeClassList.addView(createClassCard(cls));
        }
    }

    private View createClassCard(ClassFolder cls) {
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
        title.setText(cls.getDisplayName());
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        leftCol.addView(title);

        TextView teacher = new TextView(this);
        String displayTeacher = (globalTeacherName != null && !globalTeacherName.isEmpty())
                ? globalTeacherName
                : cls.getTeacher();
        teacher.setText("\uD83D\uDC64 " + displayTeacher);
        teacher.setTextColor(Color.parseColor("#64748B"));
        teacher.setTextSize(12);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        teacher.setLayoutParams(tlp);
        leftCol.addView(teacher);
        header.addView(leftCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(18);
        header.addView(arrow);
        card.addView(header);

        TextView meta = new TextView(this);
        String schoolYearText = (cls.getSchoolYear() != null && !cls.getSchoolYear().isEmpty())
                ? " · S.Y. " + cls.getSchoolYear()
                : "";
        meta.setText("📂 " + cls.getActivityCount() + " Assessment"
                + (cls.getActivityCount() != 1 ? "s" : "")
                + schoolYearText);
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        meta.setLayoutParams(mlp);
        card.addView(meta);

        card.setOnClickListener(v -> {
            selectedClass = cls;
            showScreen(SCREEN_CLASS);
        });
        card.setOnLongClickListener(v -> {
            showClassOptionsDialog(cls);
            return true;
        });
        return card;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER — CLASS
    // ═══════════════════════════════════════════════════════════════

    private void renderClassScreen() {
        classActivityList.removeAllViews();
        // Use global teacher name; fall back to class-level if not set
        String displayTeacher = (globalTeacherName != null && !globalTeacherName.isEmpty())
                ? globalTeacherName
                : selectedClass.getTeacher();
        classTeacherLabel.setText("Teacher: " + displayTeacher);

        List<ActivityFolder> activities = selectedClass.getActivities();

        // ── Build sheet-type filter tabs ──
        buildClassSheetTabs(activities);

        // ── Apply filter ──
        List<ActivityFolder> filtered = new ArrayList<>();
        if (activities != null) {
            for (ActivityFolder act : activities) {
                if (selectedSheetFilter == null || act.getSheetType().equals(selectedSheetFilter)) {
                    filtered.add(act);
                }
            }
        }

        // Update count label
        if (classAssessmentCount != null) {
            classAssessmentCount.setText(filtered.size() + " total");
        }

        if (filtered.isEmpty()) {
            classEmpty.setVisibility(View.VISIBLE);
            classActivityList.setVisibility(View.GONE);
        } else {
            classEmpty.setVisibility(View.GONE);
            classActivityList.setVisibility(View.VISIBLE);
            for (ActivityFolder act : filtered)
                classActivityList.addView(createActivityCard(act));
        }
    }

    /**
     * Build the "All / ZPH30 / ZPH50 / ZPH60" filter tabs above the assessment
     * list.
     */
    private void buildClassSheetTabs(List<ActivityFolder> activities) {
        classSheetTabs.removeAllViews();

        // Count how many assessments per sheet type
        int countZPH30 = 0, countZPH50 = 0, countZPH60 = 0;
        if (activities != null) {
            for (ActivityFolder act : activities) {
                switch (act.getSheetType()) {
                    case "ZPH30":
                        countZPH30++;
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

    private View createActivityCard(ActivityFolder act) {
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
        title.setText(act.getName());
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        leftCol.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sheet: " + act.getSheetType());
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
        String dateToShow = act.getExamDate() != null && !act.getExamDate().isEmpty() ? act.getExamDate()
                : act.getFormattedDate();
        meta.setText("�� " + act.getScanCount() + " scan"
                + (act.getScanCount() != 1 ? "s" : "")
                + " · " + dateToShow);
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        meta.setLayoutParams(mlp);
        card.addView(meta);

        card.setOnClickListener(v -> {
            selectedActivity = act;
            showScreen(SCREEN_ACTIVITY);
        });
        card.setOnLongClickListener(v -> {
            showActivityOptionsDialog(act);
            return true;
        });
        return card;
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
            // Confirmation alert dialog
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Change Teacher Name?")
                    .setMessage("Are you sure you want to change the teacher name to \""
                            + newName + "\"?\n\nThis will apply to all class folders.")
                    .setPositiveButton("Confirm", (alertDialog, which) -> {
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
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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
            act.setExamDate(dateInput.getText().toString().trim());
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

        String[][] sheetTypes = { { "ZPH30", "30 Items" }, { "ZPH50", "50 Items" }, { "ZPH60", "60 Items" } };
        final String[] selectedType = { "ZPH30" };

        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = dp(16);
        typeRow.setLayoutParams(trLp);

        final TextView[] typeButtons = new TextView[3];
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
            if (i < 2)
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
            act.setExamDate(dateInput.getText().toString().trim());
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

    // ═══════════════════════════════════════════════════════════════
    // DOWNLOAD CLASS DATA
    // ═══════════════════════════════════════════════════════════════

    private void downloadClassData(ClassFolder cls) {
        try {
            if (cls.getActivities() == null || cls.getActivities().isEmpty()) {
                showToast("No data to download");
                return;
            }
            String folderName = cls.getGrade().replaceAll("\\s+", "")
                    + "-" + cls.getSection().replaceAll("\\s+", "");
            java.io.File downloadsDir = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File classDir = new java.io.File(new java.io.File(downloadsDir, "OMRScanner"), folderName);

            int totalImages = 0, totalCsvs = 0;

            for (ActivityFolder act : cls.getActivities()) {
                List<ScanEntry> scans = act.getScans();
                if (scans == null || scans.isEmpty())
                    continue;

                String actDirName = act.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
                java.io.File actDir = new java.io.File(classDir, actDirName);
                java.io.File imagesDir = new java.io.File(actDir, "images");
                java.io.File resultsDir = new java.io.File(actDir, "result");
                if (!imagesDir.exists())
                    imagesDir.mkdirs();
                if (!resultsDir.exists())
                    resultsDir.mkdirs();

                int scanNum = 0;
                for (ScanEntry scan : scans) {
                    scanNum++;
                    String srcPath = scan.getOverlayImagePath();
                    if (srcPath == null || !(new java.io.File(srcPath).exists()))
                        srcPath = scan.getImagePath();
                    if (srcPath != null) {
                        java.io.File srcFile = new java.io.File(srcPath);
                        if (srcFile.exists()) {
                            String ext = srcPath.endsWith(".png") ? ".png" : ".jpg";
                            String lrnPart = (scan.getLrn() != null && !scan.getLrn().isEmpty())
                                    ? scan.getLrn().replaceAll("[^a-zA-Z0-9]", "")
                                    : "scan_" + scanNum;
                            java.io.File dest = new java.io.File(imagesDir, lrnPart + ext);
                            copyFile(srcFile, dest);
                            scanMediaFile(dest);
                            totalImages++;
                        }
                    }
                    try {
                        String lrnOnly = (scan.getLrn() != null && !scan.getLrn().isEmpty())
                                ? scan.getLrn()
                                : "scan_" + scanNum;
                        String indName = (lrnOnly + "_" + cls.getGrade() + "-" + cls.getSection() + "_"
                                + act.getName().replaceAll("\\s+", "") + ".csv")
                                .replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
                        StringBuilder sb = new StringBuilder("LRN,Score");
                        for (int k = 1; k <= act.getNumItems(); k++)
                            sb.append(",Q").append(k);
                        sb.append("\n").append(scan.getLrn() != null ? scan.getLrn() : "")
                                .append(",").append(scan.getScore()).append("/").append(scan.getNumItems());
                        for (int k = 1; k <= act.getNumItems(); k++) {
                            String ans = scan.getAnswers() != null ? scan.getAnswers().get(k) : null;
                            sb.append(",").append(ans != null ? ans : "");
                        }
                        sb.append("\n");
                        java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(resultsDir, indName));
                        fw.write(sb.toString());
                        fw.close();
                        scanMediaFile(new java.io.File(resultsDir, indName));
                    } catch (Exception ex) {
                        Log.e(TAG, "Ind CSV error", ex);
                    }
                }

                StringBuilder actCsv = new StringBuilder("LRN,Score");
                for (int i = 1; i <= act.getNumItems(); i++)
                    actCsv.append(",Q").append(i);
                actCsv.append("\n");
                for (ScanEntry scan : scans) {
                    actCsv.append(scan.getLrn() != null ? scan.getLrn() : "")
                            .append(",").append(scan.getScore()).append("/").append(scan.getNumItems());
                    for (int i = 1; i <= act.getNumItems(); i++) {
                        String ans = scan.getAnswers() != null ? scan.getAnswers().get(i) : null;
                        actCsv.append(",").append(ans != null ? ans : "");
                    }
                    actCsv.append("\n");
                }
                java.io.File csvFile = new java.io.File(actDir, actDirName.replaceAll("\\s+", "_") + ".csv");
                java.io.FileWriter writer = new java.io.FileWriter(csvFile);
                writer.write(actCsv.toString());
                writer.close();
                scanMediaFile(csvFile);
                totalCsvs++;
            }
            showDownloadSuccessDialog(cls, classDir, totalImages, totalCsvs);
        } catch (Exception e) {
            Log.e(TAG, "Error downloading class data", e);
            showToast("Error exporting: " + e.getMessage());
        }
    }

    private void copyFile(java.io.File src, java.io.File dst) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0)
                out.write(buf, 0, len);
        }
    }

    private void scanMediaFile(java.io.File file) {
        android.media.MediaScannerConnection.scanFile(
                this, new String[] { file.getAbsolutePath() }, null, null);
    }

    private void showDownloadSuccessDialog(ClassFolder cls, java.io.File classDir,
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
        info.setText("📁 " + cls.getDisplayName() + "\n🖼️ " + totalImages
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
        pathLabel.setText("📂 Downloads/OMRScanner/" + classDir.getName() + "/");
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
        TextView btnOpen = createDialogButton("Open Folder", true);
        TextView btnDone = createDialogButton("Done", false);
        actions.addView(btnOpen);
        actions.addView(spacer(dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnOpen.setOnClickListener(v -> {
            dialog.dismiss();
            openFolderInFileManager(classDir);
        });
        btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void openFolderInFileManager(java.io.File folder) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(folder.getAbsolutePath()), "resource/folder");
            if (intent.resolveActivity(getPackageManager()) != null)
                startActivity(intent);
            else
                showToast("Files saved to: Downloads/OMRScanner/" + folder.getName());
        } catch (Exception e) {
            showToast("Files saved to: Downloads/OMRScanner/" + folder.getName());
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
