package com.example.omrscanner;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.example.omrscanner.ui.ScanDetailActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final String PREFS_NAME = "omr_dashboard";
    private static final String KEY_CLASSES = "class_folders";
    private static final String KEY_TEACHER_NAME = "teacher_name";

    // Extras
    public static final String EXTRA_SHEET_TYPE = "sheet_type";
    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";

    // Screens
    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_CLASS = "class";
    private static final String SCREEN_ACTIVITY = "activity";

    // State
    private String currentScreen = SCREEN_HOME;
    private List<ClassFolder> classFolders = new ArrayList<>();
    private ClassFolder selectedClass = null;
    private ActivityFolder selectedActivity = null;
    private String selectedSheetType = null;
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
    private final Gson gson = new Gson();

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        getWindow().setStatusBarColor(Color.parseColor("#0038A8"));

        initViews();
        initBackHandler(); // ← modern replacement for onBackPressed()
        initGalleryLauncher();
        loadData();
        showScreen(SCREEN_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();

        if (selectedClass != null) {
            selectedClass = findClassById(selectedClass.getId());
            if (selectedClass != null && selectedActivity != null) {
                ActivityFolder refreshed = null;
                for (ActivityFolder act : selectedClass.getActivities()) {
                    if (act.getId().equals(selectedActivity.getId())) {
                        refreshed = act;
                        break;
                    }
                }
                selectedActivity = refreshed;
                showScreen(currentScreen);
            } else if (selectedClass != null) {
                showScreen(SCREEN_CLASS);
            } else {
                showScreen(SCREEN_HOME);
            }
        } else {
            showScreen(SCREEN_HOME);
        }
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
                topBarBadge.setText(classFolders.size() + " class"
                        + (classFolders.size() != 1 ? "es" : ""));
                // Refresh teacher name display in header
                if (globalTeacherName != null && !globalTeacherName.isEmpty()) {
                    tvTeacherName.setText("\uD83D\uDC64 " + globalTeacherName);
                } else {
                    tvTeacherName.setText("\uD83D\uDC64 Tap to set teacher name");
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
        if (activities == null || activities.isEmpty()) {
            classEmpty.setVisibility(View.VISIBLE);
            classActivityList.setVisibility(View.GONE);
        } else {
            classEmpty.setVisibility(View.GONE);
            classActivityList.setVisibility(View.VISIBLE);
            for (ActivityFolder act : activities)
                classActivityList.addView(createActivityCard(act));
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

    private TextView createScoreBadge(ScanEntry scan) {
        TextView badge = new TextView(this);
        badge.setText(scan.getScore() + "/" + scan.getNumItems());
        badge.setTextSize(12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(6));
        switch (scan.getScoreLevel()) {
            case "high":
                badgeBg.setColor(Color.parseColor("#DCFCE7"));
                badge.setTextColor(Color.parseColor("#16A34A"));
                break;
            case "mid":
                badgeBg.setColor(Color.parseColor("#FEF9C3"));
                badge.setTextColor(Color.parseColor("#CA8A04"));
                break;
            default:
                badgeBg.setColor(Color.parseColor("#FEE2E2"));
                badge.setTextColor(Color.parseColor("#CE1126"));
                break;
        }
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
        root.addView(createMenuOption("⬇️   Download Summary",
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
        EditText schoolYearInput = createLightInput("e.g. 2023-2024");
        schoolYearInput.setText(cls.getSchoolYear() != null ? cls.getSchoolYear() : "");
        root.addView(schoolYearInput);

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
            cls.setSchoolYear(schoolYearInput.getText().toString().trim());
            saveData();
            dialog.dismiss();
            showToast("Class updated ✓");
            renderHomeScreen();
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
            classFolders.remove(cls);
            saveData();
            renderHomeScreen();
            dialog.dismiss();
            showToast("Class deleted");
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
                        globalTeacherName = newName;
                        // Update teacher on all existing classes so data stays consistent
                        for (ClassFolder cls : classFolders) {
                            cls.setTeacher(globalTeacherName);
                        }
                        saveData();
                        // Refresh the teacher name label in the header
                        tvTeacherName.setText("\uD83D\uDC64 " + globalTeacherName);
                        // If currently on class screen, refresh teacher label there too
                        if (SCREEN_CLASS.equals(currentScreen) && selectedClass != null) {
                            classTeacherLabel.setText("Teacher: " + globalTeacherName);
                        }
                        renderHomeScreen();
                        dialog.dismiss();
                        showToast("Teacher name updated ✓");
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
            saveData();
            dialog.dismiss();
            showToast("Assessment updated ✓");
            renderClassScreen();
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
                selectedClass.getActivities().remove(act);
                saveData();
                renderClassScreen();
                topBarBadge.setText("📁 " + selectedClass.getActivityCount());
                dialog.dismiss();
                showToast("Assessment deleted");
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
        EditText schoolYearInput = createLightInput("e.g. 2023-2024");
        root.addView(schoolYearInput);

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
            if (grade.isEmpty() || section.isEmpty()) {
                showErrorDialog("Missing Fields", "Grade and Section are required to create a class.");
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
            String schoolYear = schoolYearInput.getText().toString().trim();
            // Use the global teacher name when creating the class
            String teacher = (globalTeacherName != null && !globalTeacherName.isEmpty())
                    ? globalTeacherName
                    : "Unknown Teacher";
            ClassFolder cls = new ClassFolder(teacher, grade, section, schoolYear);
            classFolders.add(cls);
            saveData();
            dialog.dismiss();
            showScreen(SCREEN_HOME);
            showToast("Class folder created ✓");
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
            selectedClass.addActivity(act);
            saveData();
            dialog.dismiss();
            showScreen(SCREEN_CLASS);
            showToast("Assessment folder created ✓");
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
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    private void saveData() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_CLASSES, gson.toJson(classFolders))
                .putString(KEY_TEACHER_NAME, globalTeacherName != null ? globalTeacherName : "")
                .apply();
        Log.d(TAG, "Saved " + classFolders.size() + " classes");
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_CLASSES, "[]");
        globalTeacherName = prefs.getString(KEY_TEACHER_NAME, "");
        Type type = new TypeToken<List<ClassFolder>>() {
        }.getType();
        classFolders = gson.fromJson(json, type);
        if (classFolders == null)
            classFolders = new ArrayList<>();
        Log.d(TAG, "Loaded " + classFolders.size() + " classes");
    }

    public static void saveScanResult(android.content.Context context,
            String classId, String activityId,
            ScanEntry scanEntry) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson g = new Gson();
        String json = prefs.getString(KEY_CLASSES, "[]");
        Type type = new TypeToken<List<ClassFolder>>() {
        }.getType();
        List<ClassFolder> classes = g.fromJson(json, type);
        if (classes != null) {
            for (ClassFolder cls : classes) {
                if (cls.getId().equals(classId)) {
                    for (ActivityFolder act : cls.getActivities()) {
                        if (act.getId().equals(activityId)) {
                            act.addScan(scanEntry);
                            break;
                        }
                    }
                    break;
                }
            }
            prefs.edit().putString(KEY_CLASSES, g.toJson(classes)).apply();
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
    // UTILS
    // ═══════════════════════════════════════════════════════════════

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
