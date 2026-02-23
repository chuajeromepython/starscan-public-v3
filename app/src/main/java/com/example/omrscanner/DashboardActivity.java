package com.example.omrscanner;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final String PREFS_NAME = "omr_dashboard";
    private static final String KEY_CLASSES = "class_folders";

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

    // Views
    private ImageButton btnBack;
    private TextView topBarTitle, topBarBadge;
    private ScrollView screenHome, screenClass, screenActivity;
    private LinearLayout homeEmpty, homeClassList;
    private TextView classTeacherLabel;
    private LinearLayout classEmpty, classActivityList;

    // ── FIX: These two were LinearLayout but are now different view types in the new XML ──
    private MaterialCardView masterCsvBar;   // was LinearLayout — now MaterialCardView in XML
    private CardView scanCtaCard;            // was LinearLayout — now CardView in XML

    private LinearLayout scansHeader, activityScanList, activityScansEmpty;
    private TextView masterCsvLabel, scansTotalCount, scanCtaSub;
    private com.google.android.material.button.MaterialButton btnExportMaster;
    private FloatingActionButton fab;

    // Breadcrumb bar
    private LinearLayout breadcrumbBar;
    private View breadcrumbDivider;
    private TextView breadcrumbRoot, breadcrumbSep1, breadcrumbClass,
            breadcrumbSep2, breadcrumbActivity;

    // Gallery launcher
    private ActivityResultLauncher<String> galleryLauncher;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Update status bar color to match new light design
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#0038A8"));

        initViews();
        initGalleryLauncher();
        loadData();
        showScreen(SCREEN_HOME);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        topBarTitle = findViewById(R.id.topBarTitle);
        topBarBadge = findViewById(R.id.topBarBadge);

        screenHome = findViewById(R.id.screenHome);
        screenClass = findViewById(R.id.screenClass);
        screenActivity = findViewById(R.id.screenActivity);

        homeEmpty = findViewById(R.id.homeEmpty);
        homeClassList = findViewById(R.id.homeClassList);

        classTeacherLabel = findViewById(R.id.classTeacherLabel);
        classEmpty = findViewById(R.id.classEmpty);
        classActivityList = findViewById(R.id.classActivityList);

        // ── FIX: findViewById now resolves to correct types ──
        masterCsvBar = findViewById(R.id.masterCsvBar);
        masterCsvLabel = findViewById(R.id.masterCsvLabel);
        btnExportMaster = findViewById(R.id.btnExportMaster);
        scanCtaCard = findViewById(R.id.scanCtaCard);
        scanCtaSub = findViewById(R.id.scanCtaSub);
        scansHeader = findViewById(R.id.scansHeader);
        scansTotalCount = findViewById(R.id.scansTotalCount);
        activityScanList = findViewById(R.id.activityScanList);
        activityScansEmpty = findViewById(R.id.activityScansEmpty);

        fab = findViewById(R.id.fab);

        // Breadcrumb bar views
        breadcrumbBar      = findViewById(R.id.breadcrumbBar);
        breadcrumbDivider  = findViewById(R.id.breadcrumbDivider);
        breadcrumbRoot     = findViewById(R.id.breadcrumbRoot);
        breadcrumbSep1     = findViewById(R.id.breadcrumbSep1);
        breadcrumbClass    = findViewById(R.id.breadcrumbClass);
        breadcrumbSep2     = findViewById(R.id.breadcrumbSep2);
        breadcrumbActivity = findViewById(R.id.breadcrumbActivity);

        // Click listeners
        btnBack.setOnClickListener(v -> navigateBack());
        fab.setOnClickListener(v -> onFabClicked());

        // "Classes" root crumb — always jumps straight back to home
        breadcrumbRoot.setOnClickListener(v -> {
            selectedClass = null;
            selectedActivity = null;
            showScreen(SCREEN_HOME);
        });

        // Class crumb — on activity screen, tapping goes back to the class
        breadcrumbClass.setOnClickListener(v -> {
            if (SCREEN_ACTIVITY.equals(currentScreen)) {
                selectedActivity = null;
                showScreen(SCREEN_CLASS);
            }
        });

        scanCtaCard.setOnClickListener(v -> showScanMethodDialog());
        btnExportMaster.setOnClickListener(v -> exportMasterCSV());
    }

    private void initGalleryLauncher() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleSelectedImage(uri);
                    } else {
                        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                    }
                }
        );
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
                topBarBadge.setText(classFolders.size() + " class" + (classFolders.size() != 1 ? "es" : ""));
                fab.setVisibility(View.VISIBLE);
                // Hide breadcrumb entirely on home
                breadcrumbBar.setVisibility(View.GONE);
                breadcrumbDivider.setVisibility(View.GONE);
                renderHomeScreen();
                break;

            case SCREEN_CLASS:
                screenClass.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                topBarTitle.setText(selectedClass.getDisplayName());
                topBarBadge.setText("📁 " + selectedClass.getActivityCount());
                fab.setVisibility(View.VISIBLE);
                // Breadcrumb:  ‹  Classes  ›  Grade 10 — Sec A
                breadcrumbBar.setVisibility(View.VISIBLE);
                breadcrumbDivider.setVisibility(View.VISIBLE);
                breadcrumbSep1.setVisibility(View.VISIBLE);
                breadcrumbClass.setVisibility(View.VISIBLE);
                breadcrumbClass.setText(selectedClass.getDisplayName());
                breadcrumbClass.setTextColor(android.graphics.Color.parseColor("#1E293B")); // current level — not tappable-looking
                breadcrumbSep2.setVisibility(View.GONE);
                breadcrumbActivity.setVisibility(View.GONE);
                renderClassScreen();
                break;

            case SCREEN_ACTIVITY:
                screenActivity.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                topBarTitle.setText(selectedActivity.getName());
                topBarBadge.setText(selectedActivity.getSheetType());
                fab.setVisibility(View.GONE);
                // Breadcrumb:  ‹  Classes  ›  Grade 10 — Sec A  ›  Math Quiz
                breadcrumbBar.setVisibility(View.VISIBLE);
                breadcrumbDivider.setVisibility(View.VISIBLE);
                breadcrumbSep1.setVisibility(View.VISIBLE);
                breadcrumbClass.setVisibility(View.VISIBLE);
                breadcrumbClass.setText(selectedClass.getDisplayName());
                breadcrumbClass.setTextColor(android.graphics.Color.parseColor("#0038A8")); // tappable
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
                selectedClass = findClassById(selectedClass.getId());
                selectedActivity = null;
                showScreen(SCREEN_CLASS);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (!SCREEN_HOME.equals(currentScreen)) {
            navigateBack();
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FAB ACTIONS
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
    // RENDER: HOME SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void renderHomeScreen() {
        homeClassList.removeAllViews();

        if (classFolders.isEmpty()) {
            homeEmpty.setVisibility(View.VISIBLE);
            homeClassList.setVisibility(View.GONE);
        } else {
            homeEmpty.setVisibility(View.GONE);
            homeClassList.setVisibility(View.VISIBLE);

            for (ClassFolder cls : classFolders) {
                homeClassList.addView(createClassCard(cls));
            }
        }
    }

    private View createClassCard(ClassFolder cls) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Light design card background
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);

        // Header row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftCol.setLayoutParams(leftParams);

        TextView title = new TextView(this);
        title.setText(cls.getDisplayName());
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        leftCol.addView(title);

        TextView teacher = new TextView(this);
        teacher.setText("👤 " + cls.getTeacher());
        teacher.setTextColor(Color.parseColor("#64748B"));
        teacher.setTextSize(12);
        LinearLayout.LayoutParams teacherParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        teacherParams.topMargin = dp(4);
        teacher.setLayoutParams(teacherParams);
        leftCol.addView(teacher);

        header.addView(leftCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(18);
        header.addView(arrow);

        card.addView(header);

        TextView meta = new TextView(this);
        meta.setText("📂 " + cls.getActivityCount() + " activit" +
                (cls.getActivityCount() != 1 ? "ies" : "y") + " · " + cls.getFormattedDate());
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(8);
        meta.setLayoutParams(metaParams);
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
    // RENDER: CLASS SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void renderClassScreen() {
        classActivityList.removeAllViews();
        classTeacherLabel.setText("Teacher: " + selectedClass.getTeacher());

        List<ActivityFolder> activities = selectedClass.getActivities();

        if (activities == null || activities.isEmpty()) {
            classEmpty.setVisibility(View.VISIBLE);
            classActivityList.setVisibility(View.GONE);
        } else {
            classEmpty.setVisibility(View.GONE);
            classActivityList.setVisibility(View.VISIBLE);

            for (ActivityFolder act : activities) {
                classActivityList.addView(createActivityCard(act));
            }
        }
    }

    private View createActivityCard(ActivityFolder act) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftCol.setLayoutParams(leftParams);

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
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        sub.setLayoutParams(subParams);
        leftCol.addView(sub);

        header.addView(leftCol);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(18);
        header.addView(arrow);

        card.addView(header);

        TextView meta = new TextView(this);
        meta.setText("🗂 " + act.getScanCount() + " scan" +
                (act.getScanCount() != 1 ? "s" : "") + " · " + act.getFormattedDate());
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(8);
        meta.setLayoutParams(metaParams);
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
    // RENDER: ACTIVITY SCREEN
    // ═══════════════════════════════════════════════════════════════

    private void renderActivityScreen() {
        activityScanList.removeAllViews();

        scanCtaSub.setText(selectedActivity.getSheetType() + " · " + selectedActivity.getNumItems() + " items");

        List<ScanEntry> scans = selectedActivity.getScans();
        boolean hasScans = scans != null && !scans.isEmpty();

        if (hasScans) {
            masterCsvBar.setVisibility(View.VISIBLE);
            masterCsvLabel.setText("📊 _Master.csv · " + scans.size() + " entries");
        } else {
            masterCsvBar.setVisibility(View.GONE);
        }

        if (hasScans) {
            scansHeader.setVisibility(View.VISIBLE);
            scansTotalCount.setText(scans.size() + " total");
            activityScanList.setVisibility(View.VISIBLE);
            activityScansEmpty.setVisibility(View.GONE);

            for (int i = 0; i < scans.size(); i++) {
                activityScanList.addView(createScanCard(scans.get(i), i));
            }
        } else {
            scansHeader.setVisibility(View.GONE);
            activityScanList.setVisibility(View.GONE);
            activityScansEmpty.setVisibility(View.VISIBLE);
        }
    }

    private View createScanCard(ScanEntry scan, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);

        // Thumbnail
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(48), dp(60));
        thumbParams.rightMargin = dp(14);
        thumb.setLayoutParams(thumbParams);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.parseColor("#F0F7FF"));
        thumbBg.setCornerRadius(dp(10));
        thumbBg.setStroke(dp(1), Color.parseColor("#0038A8"));
        thumb.setBackground(thumbBg);

        TextView thumbIcon = new TextView(this);
        thumbIcon.setText("📄");
        thumbIcon.setTextSize(22);
        thumbIcon.setGravity(Gravity.CENTER);
        thumb.addView(thumbIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(thumb);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(infoParams);

        TextView lrn = new TextView(this);
        lrn.setText("LRN: " + (scan.getLrn() != null ? scan.getLrn() : "Unknown"));
        lrn.setTextColor(Color.parseColor("#1E293B"));
        lrn.setTextSize(13);
        lrn.setTypeface(null, Typeface.BOLD);
        info.addView(lrn);

        TextView detail = new TextView(this);
        detail.setText("Student #" + (index + 1) + " · " + selectedActivity.getSheetType());
        detail.setTextColor(Color.parseColor("#64748B"));
        detail.setTextSize(12);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(2);
        detail.setLayoutParams(detailParams);
        info.addView(detail);

        card.addView(info);

        // Score badge
        TextView scoreBadge = createScoreBadge(scan);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.rightMargin = dp(8);
        scoreBadge.setLayoutParams(badgeParams);
        card.addView(scoreBadge);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(16);
        card.addView(arrow);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.omrscanner.ui.ScanDetailActivity.class);
            intent.putExtra(com.example.omrscanner.ui.ScanDetailActivity.EXTRA_CLASS_ID, selectedClass.getId());
            intent.putExtra(com.example.omrscanner.ui.ScanDetailActivity.EXTRA_ACTIVITY_ID, selectedActivity.getId());
            intent.putExtra(com.example.omrscanner.ui.ScanDetailActivity.EXTRA_SCAN_INDEX, index);
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

        String level = scan.getScoreLevel();
        switch (level) {
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
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════

    private void showClassOptionsDialog(ClassFolder cls) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText(cls.getDisplayName());
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(24);
        title.setLayoutParams(titleParams);
        root.addView(title);

        root.addView(createMenuOption("✏️   Edit Class Details", () -> {
            dialog.dismiss();
            showEditClassDialog(cls);
        }, false));

        root.addView(createMenuOption("⬇️   Download Summary", () -> {
            dialog.dismiss();
            downloadClassData(cls);
        }, false));

        root.addView(createMenuOption("🗑️   Delete Class", () -> {
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
        btn.setTextColor(isDestructive ? Color.parseColor("#CE1126") : Color.parseColor("#1E293B"));
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isDestructive ? Color.parseColor("#FEF2F2") : Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), isDestructive ? Color.parseColor("#FECACA") : Color.parseColor("#E2E8F0"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private void showEditClassDialog(ClassFolder cls) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText("✏️ Edit Class Folder");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0038A8"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(20);
        title.setLayoutParams(titleParams);
        root.addView(title);

        root.addView(createFieldLabel("TEACHER NAME"));
        EditText teacherInput = createLightInput(cls.getTeacher());
        teacherInput.setText(cls.getTeacher());
        root.addView(teacherInput);

        root.addView(createFieldLabel("GRADE *"));
        EditText gradeInput = createLightInput(cls.getGrade());
        gradeInput.setText(cls.getGrade());
        root.addView(gradeInput);

        root.addView(createFieldLabel("SECTION *"));
        EditText sectionInput = createLightInput(cls.getSection());
        sectionInput.setText(cls.getSection());
        root.addView(sectionInput);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(20);
        actions.setLayoutParams(actionsParams);

        TextView btnCancel = createDialogButton("Cancel", false);
        actions.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnSave = createDialogButton("Save", true);
        actions.addView(btnSave);

        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String grade = gradeInput.getText().toString().trim();
            String section = sectionInput.getText().toString().trim();
            String teacher = teacherInput.getText().toString().trim();

            if (grade.isEmpty() || section.isEmpty()) {
                showErrorDialog("Missing Fields", "Grade and Section are required to save this class.");
                return;
            }

            for (ClassFolder existing : classFolders) {
                if (!existing.getId().equals(cls.getId())
                        && existing.getGrade().equalsIgnoreCase(grade)
                        && existing.getSection().equalsIgnoreCase(section)) {
                    showErrorDialog("Duplicate Class", "A class with \"" + grade + " — " + section + "\" already exists.\nPlease use a different Grade or Section.");
                    return;
                }
            }

            cls.setTeacher(teacher.isEmpty() ? "Unknown Teacher" : teacher);
            cls.setGrade(grade);
            cls.setSection(section);

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        TextView title = new TextView(this);
        title.setText("Delete Class?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Are you sure you want to delete \"" + cls.getDisplayName() + "\"?\n\nThis will permanently delete all activities and scans inside this folder.");
        msg.setTextColor(Color.parseColor("#64748B"));
        msg.setTextSize(14);
        msg.setPadding(0, dp(12), 0, dp(24));
        root.addView(msg);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnCancel = createDialogButton("Cancel", false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnDelete = createDialogButton("Delete", true);
        GradientDrawable deleteBg = new GradientDrawable();
        deleteBg.setColor(Color.parseColor("#CE1126"));
        deleteBg.setCornerRadius(dp(12));
        btnDelete.setBackground(deleteBg);
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
    // ACTIVITY OPTIONS DIALOG
    // ═══════════════════════════════════════════════════════════════

    private void showActivityOptionsDialog(ActivityFolder act) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText(act.getName());
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(24);
        title.setLayoutParams(titleParams);
        root.addView(title);

        root.addView(createMenuOption("✏️   Edit Activity Details", () -> {
            dialog.dismiss();
            showEditActivityDialog(act);
        }, false));

        root.addView(createMenuOption("🗑️   Delete Activity", () -> {
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText("✏️ Edit Activity");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0038A8"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(20);
        title.setLayoutParams(titleParams);
        root.addView(title);

        root.addView(createFieldLabel("ACTIVITY NAME *"));
        EditText nameInput = createLightInput(act.getName());
        nameInput.setText(act.getName());
        root.addView(nameInput);

        root.addView(createFieldLabel("OMR SHEET TYPE"));
        TextView sheetInfo = new TextView(this);
        sheetInfo.setText(act.getSheetType() + " — " + act.getNumItems() + " Items");
        sheetInfo.setTextSize(14);
        sheetInfo.setTypeface(null, Typeface.BOLD);
        sheetInfo.setTextColor(Color.parseColor("#64748B"));
        sheetInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable sheetInfoBg = new GradientDrawable();
        sheetInfoBg.setColor(Color.parseColor("#F8FAFC"));
        sheetInfoBg.setCornerRadius(dp(10));
        sheetInfoBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        sheetInfo.setBackground(sheetInfoBg);
        LinearLayout.LayoutParams sheetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheetParams.bottomMargin = dp(16);
        sheetInfo.setLayoutParams(sheetParams);
        root.addView(sheetInfo);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(20);
        actions.setLayoutParams(actionsParams);

        TextView btnCancel = createDialogButton("Cancel", false);
        actions.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnSave = createDialogButton("Save", true);
        actions.addView(btnSave);

        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();

            if (name.isEmpty()) {
                showErrorDialog("Missing Name", "Activity name is required to save.");
                return;
            }

            if (selectedClass != null && selectedClass.getActivities() != null) {
                for (ActivityFolder existing : selectedClass.getActivities()) {
                    if (!existing.getId().equals(act.getId())
                            && existing.getName().equalsIgnoreCase(name)) {
                        showErrorDialog("Duplicate Activity", "An activity named \"" + name + "\" already exists in this class.\nPlease use a different name.");
                        return;
                    }
                }
            }

            act.setName(name);
            saveData();
            dialog.dismiss();
            showToast("Activity updated ✓");
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        TextView title = new TextView(this);
        title.setText("Delete Activity?");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#CE1126"));
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Are you sure you want to delete \"" + act.getName() + "\"?\n\nThis will permanently delete all " + act.getScanCount() + " scan(s) inside this activity.");
        msg.setTextColor(Color.parseColor("#64748B"));
        msg.setTextSize(14);
        msg.setPadding(0, dp(12), 0, dp(24));
        root.addView(msg);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnCancel = createDialogButton("Cancel", false);
        actions.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnDelete = createDialogButton("Delete", true);
        GradientDrawable deleteBg = new GradientDrawable();
        deleteBg.setColor(Color.parseColor("#CE1126"));
        deleteBg.setCornerRadius(dp(12));
        btnDelete.setBackground(deleteBg);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setOnClickListener(v -> {
            if (selectedClass != null && selectedClass.getActivities() != null) {
                selectedClass.getActivities().remove(act);
                saveData();
                renderClassScreen();
                topBarBadge.setText("📁 " + selectedClass.getActivityCount());
                dialog.dismiss();
                showToast("Activity deleted");
            }
        });
        actions.addView(btnDelete);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        root.addView(actions);
        dialog.setContentView(root);
        configureBottomDialog(dialog);
        dialog.show();
    }

    private void downloadClassData(ClassFolder cls) {
        try {
            if (cls.getActivities() == null || cls.getActivities().isEmpty()) {
                showToast("No data to download");
                return;
            }

            String classFolderName = cls.getGrade().replaceAll("\\s+", "") + "-" +
                    cls.getSection().replaceAll("\\s+", "");
            java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File omrDir = new java.io.File(downloadsDir, "OMRScanner");
            java.io.File classDir = new java.io.File(omrDir, classFolderName);

            int totalImages = 0;
            int totalCsvs = 0;

            for (ActivityFolder act : cls.getActivities()) {
                List<ScanEntry> scans = act.getScans();
                if (scans == null || scans.isEmpty()) continue;

                String actDirName = act.getName().replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
                java.io.File actDir = new java.io.File(classDir, actDirName);
                java.io.File imagesDir = new java.io.File(actDir, "images");
                if (!imagesDir.exists()) imagesDir.mkdirs();

                java.io.File resultsDir = new java.io.File(actDir, "result");
                if (!resultsDir.exists()) resultsDir.mkdirs();

                int scanNum = 0;
                for (ScanEntry scan : scans) {
                    scanNum++;

                    String srcPath = scan.getOverlayImagePath();
                    if (srcPath == null || !(new java.io.File(srcPath).exists())) {
                        srcPath = scan.getImagePath();
                    }
                    if (srcPath != null) {
                        java.io.File srcFile = new java.io.File(srcPath);
                        if (srcFile.exists()) {
                            String ext = srcPath.endsWith(".png") ? ".png" : ".jpg";
                            String lrnPart = scan.getLrn() != null && !scan.getLrn().isEmpty()
                                    ? scan.getLrn().replaceAll("[^a-zA-Z0-9]", "")
                                    : "scan_" + scanNum;
                            java.io.File destFile = new java.io.File(imagesDir, lrnPart + ext);
                            copyFile(srcFile, destFile);
                            scanMediaFile(destFile);
                            totalImages++;
                        }
                    }

                    try {
                        String lrnOnly = scan.getLrn() != null && !scan.getLrn().isEmpty()
                                ? scan.getLrn() : "scan_" + scanNum;
                        String indCsvName = cls.getGrade() + "-" + cls.getSection() + "_" +
                                act.getName().replaceAll("\\s+", "") + "_" +
                                lrnOnly + ".csv";
                        indCsvName = indCsvName.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");

                        java.io.File indCsvFile = new java.io.File(resultsDir, indCsvName);

                        StringBuilder sb = new StringBuilder();
                        sb.append("LRN,Score");
                        for (int k = 1; k <= act.getNumItems(); k++) sb.append(",Q").append(k);
                        sb.append("\n");

                        sb.append(scan.getLrn() != null ? scan.getLrn() : "");
                        sb.append(",").append(scan.getScore()).append("/").append(scan.getNumItems());
                        for (int k = 1; k <= act.getNumItems(); k++) {
                            sb.append(",");
                            String ans = scan.getAnswers() != null ? scan.getAnswers().get(k) : null;
                            sb.append(ans != null ? ans : "");
                        }
                        sb.append("\n");

                        java.io.FileWriter indWriter = new java.io.FileWriter(indCsvFile);
                        indWriter.write(sb.toString());
                        indWriter.close();
                        scanMediaFile(indCsvFile);

                    } catch (Exception ex) {
                        Log.e(TAG, "Error saving individual CSV", ex);
                    }
                }

                int numItems = act.getNumItems();
                StringBuilder actCsv = new StringBuilder();

                actCsv.append("LRN,Score");
                for (int i = 1; i <= numItems; i++) {
                    actCsv.append(",Q").append(i);
                }
                actCsv.append("\n");

                for (ScanEntry scan : scans) {
                    actCsv.append(scan.getLrn() != null ? scan.getLrn() : "");
                    actCsv.append(",").append(scan.getScore()).append("/").append(scan.getNumItems());
                    for (int i = 1; i <= numItems; i++) {
                        actCsv.append(",");
                        String answer = scan.getAnswers() != null ? scan.getAnswers().get(i) : null;
                        actCsv.append(answer != null ? answer : "");
                    }
                    actCsv.append("\n");
                }

                String csvFileName = actDirName.replaceAll("\\s+", "_") + ".csv";
                java.io.File csvFile = new java.io.File(actDir, csvFileName);
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
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private void scanMediaFile(java.io.File file) {
        android.media.MediaScannerConnection.scanFile(
                this, new String[]{file.getAbsolutePath()}, null, null);
    }

    private void showDownloadSuccessDialog(ClassFolder cls, java.io.File classDir, int totalImages, int totalCsvs) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView iconView = new TextView(this);
        iconView.setText("✅");
        iconView.setTextSize(40);
        iconView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.bottomMargin = dp(12);
        iconView.setLayoutParams(iconParams);
        root.addView(iconView);

        TextView title = new TextView(this);
        title.setText("Download Complete!");
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#16A34A"));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(12);
        title.setLayoutParams(titleParams);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("📁 " + cls.getDisplayName() + "\n" +
                "🖼️ " + totalImages + " image" + (totalImages != 1 ? "s" : "") +
                "  •  📄 " + totalCsvs + " CSV" + (totalCsvs != 1 ? "s" : ""));
        info.setTextSize(13);
        info.setTextColor(Color.parseColor("#64748B"));
        info.setGravity(Gravity.CENTER);
        info.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.bottomMargin = dp(8);
        info.setLayoutParams(infoParams);
        root.addView(info);

        TextView pathLabel = new TextView(this);
        String relativePath = "Downloads/OMRScanner/" + classDir.getName() + "/";
        pathLabel.setText("📂 " + relativePath);
        pathLabel.setTextSize(11);
        pathLabel.setTextColor(Color.parseColor("#0038A8"));
        pathLabel.setGravity(Gravity.CENTER);
        pathLabel.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable pathBg = new GradientDrawable();
        pathBg.setColor(Color.parseColor("#F0F7FF"));
        pathBg.setCornerRadius(dp(8));
        pathBg.setStroke(dp(1), Color.parseColor("#BFDBFE"));
        pathLabel.setBackground(pathBg);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathParams.bottomMargin = dp(20);
        pathLabel.setLayoutParams(pathParams);
        root.addView(pathLabel);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnOpen = createDialogButton("Open Folder", true);
        actions.addView(btnOpen);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnDone = createDialogButton("Done", false);
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
            android.net.Uri folderUri = android.net.Uri.parse(folder.getAbsolutePath());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "resource/folder");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Intent fallback = new Intent(android.content.Intent.ACTION_VIEW);
                fallback.setDataAndType(
                        android.net.Uri.parse(android.os.Environment
                                .getExternalStoragePublicDirectory(
                                        android.os.Environment.DIRECTORY_DOWNLOADS)
                                .getAbsolutePath()),
                        "resource/folder"
                );

                if (fallback.resolveActivity(getPackageManager()) != null) {
                    startActivity(fallback);
                } else {
                    showToast("Files saved to: Downloads/OMRScanner/" + folder.getName());
                }
            }
        } catch (Exception e) {
            showToast("Files saved to: Downloads/OMRScanner/" + folder.getName());
        }
    }

    private void showNewClassDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText("⊕ New Class Folder");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0038A8"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(20);
        title.setLayoutParams(titleParams);
        root.addView(title);

        root.addView(createFieldLabel("TEACHER NAME"));
        EditText teacherInput = createLightInput("e.g. Mr. Cruz");
        root.addView(teacherInput);

        root.addView(createFieldLabel("GRADE *"));
        EditText gradeInput = createLightInput("e.g. Grade 10");
        root.addView(gradeInput);

        root.addView(createFieldLabel("SECTION *"));
        EditText sectionInput = createLightInput("e.g. Section A");
        root.addView(sectionInput);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(20);
        actions.setLayoutParams(actionsParams);

        TextView btnCancel = createDialogButton("Cancel", false);
        actions.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnDone = createDialogButton("Done", true);
        actions.addView(btnDone);

        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String grade = gradeInput.getText().toString().trim();
            String section = sectionInput.getText().toString().trim();
            String teacher = teacherInput.getText().toString().trim();

            if (grade.isEmpty() || section.isEmpty()) {
                showErrorDialog("Missing Fields", "Grade and Section are required to create a class.");
                return;
            }

            for (ClassFolder existing : classFolders) {
                if (existing.getGrade().equalsIgnoreCase(grade)
                        && existing.getSection().equalsIgnoreCase(section)) {
                    showErrorDialog("Duplicate Class", "A class with \"" + grade + " — " + section + "\" already exists.\nPlease use a different Grade or Section.");
                    return;
                }
            }

            ClassFolder cls = new ClassFolder(
                    teacher.isEmpty() ? "Unknown Teacher" : teacher,
                    grade, section
            );
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText("⊕ New Activity");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0038A8"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(20);
        title.setLayoutParams(titleParams);
        root.addView(title);

        root.addView(createFieldLabel("ACTIVITY NAME *"));
        EditText nameInput = createLightInput("e.g. Math Pop Quiz 1");
        root.addView(nameInput);

        root.addView(createFieldLabel("OMR SHEET TYPE"));

        String[][] sheetTypes = {
                {"ZPH30", "30 Items"},
                {"ZPH50", "50 Items"},
                {"ZPH60", "60 Items"}
        };

        final String[] selectedType = {"ZPH30"};
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams typeRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typeRowParams.bottomMargin = dp(16);
        typeRow.setLayoutParams(typeRowParams);

        final TextView[] typeButtons = new TextView[3];

        for (int i = 0; i < sheetTypes.length; i++) {
            final int idx = i;
            TextView typeBtn = new TextView(this);
            typeBtn.setText(sheetTypes[i][0] + "\n" + sheetTypes[i][1]);
            typeBtn.setTextSize(12);
            typeBtn.setTypeface(null, Typeface.BOLD);
            typeBtn.setGravity(Gravity.CENTER);
            typeBtn.setPadding(dp(10), dp(10), dp(10), dp(10));

            LinearLayout.LayoutParams typeBtnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) typeBtnParams.rightMargin = dp(8);
            typeBtn.setLayoutParams(typeBtnParams);
            typeBtn.setClickable(true);
            typeBtn.setFocusable(true);

            typeButtons[i] = typeBtn;

            typeBtn.setOnClickListener(v -> {
                selectedType[0] = sheetTypes[idx][0];
                updateSheetTypeSelection(typeButtons, idx);
            });

            typeRow.addView(typeBtn);
        }

        root.addView(typeRow);
        updateSheetTypeSelection(typeButtons, 0);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(4);
        actions.setLayoutParams(actionsParams);

        TextView btnCancel = createDialogButton("Cancel", false);
        actions.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
        actions.addView(spacer);

        TextView btnDone = createDialogButton("Done", true);
        actions.addView(btnDone);

        root.addView(actions);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                showErrorDialog("Missing Name", "Activity name is required to create an activity.");
                return;
            }

            if (selectedClass.getActivities() != null) {
                for (ActivityFolder existing : selectedClass.getActivities()) {
                    if (existing.getName().equalsIgnoreCase(name)) {
                        showErrorDialog("Duplicate Activity", "An activity named \"" + name + "\" already exists in this class.\nPlease use a different name.");
                        return;
                    }
                }
            }

            ActivityFolder act = new ActivityFolder(name, selectedType[0]);
            selectedClass.addActivity(act);
            saveData();
            dialog.dismiss();
            showScreen(SCREEN_CLASS);
            showToast("Activity folder created ✓");
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(sheetBg);

        root.addView(createDialogHandle());

        TextView title = new TextView(this);
        title.setText("📷 Start Scanning");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0038A8"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(4);
        title.setLayoutParams(titleParams);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(selectedActivity.getSheetType() + " · " + selectedActivity.getNumItems() + " items");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(20);
        subtitle.setLayoutParams(subParams);
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

    private View createScanOptionCard(Dialog dialog, String emoji, String label, String desc, String action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F8FAFC"));
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);

        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextSize(28);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.rightMargin = dp(14);
        icon.setLayoutParams(iconParams);
        card.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textColParams);

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
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(2);
        descView.setLayoutParams(descParams);
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

            if ("camera".equals(action)) {
                openCamera();
            } else {
                openGallery();
            }
        });

        return card;
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOG HELPERS
    // ═══════════════════════════════════════════════════════════════

    private View createDialogHandle() {
        View handle = new View(this);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(20);
        handle.setLayoutParams(handleParams);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.parseColor("#E2E8F0"));
        handleBg.setCornerRadius(dp(2));
        handle.setBackground(handleBg);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        label.setLayoutParams(params);
        return label;
    }

    private EditText createLightInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#CBD5E1"));
        input.setTextColor(Color.parseColor("#1E293B"));
        input.setTextSize(14);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#F8FAFC"));
        inputBg.setCornerRadius(dp(10));
        inputBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        input.setBackground(inputBg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setSingleLine(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        input.setLayoutParams(params);
        return input;
    }

    // Keep old name as alias so nothing breaks
    private EditText createDarkInput(String hint) {
        return createLightInput(hint);
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

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btn.setLayoutParams(params);

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
            dialog.getWindow().getAttributes().windowAnimations =
                    com.google.android.material.R.style.Animation_Design_BottomSheetDialog;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CAMERA / GALLERY
    // ═══════════════════════════════════════════════════════════════

    private void openCamera() {
        Log.d(TAG, "Opening camera...");
        try {
            Intent intent = new Intent(this, CameraActivity.class);
            if (selectedSheetType != null) intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
            if (selectedClass != null) intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
            if (selectedActivity != null) intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openGallery() {
        Log.d(TAG, "Opening gallery...");
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
                        if (selectedSheetType != null) intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
                        if (selectedClass != null) intent.putExtra(EXTRA_CLASS_ID, selectedClass.getId());
                        if (selectedActivity != null) intent.putExtra(EXTRA_ACTIVITY_ID, selectedActivity.getId());
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String saveImageToFile(android.net.Uri imageUri) {
        try {
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(), imageUri);
            if (bitmap == null) return null;

            java.io.File outputDir = getCacheDir();
            java.io.File outputFile = java.io.File.createTempFile("omr_upload_", ".jpg", outputDir);

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
    // MASTER CSV EXPORT
    // ═══════════════════════════════════════════════════════════════

    private void exportMasterCSV() {
        if (selectedActivity == null || selectedActivity.getScans() == null || selectedActivity.getScans().isEmpty()) {
            Toast.makeText(this, "No scans to export", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            List<ScanEntry> scans = selectedActivity.getScans();
            int numItems = selectedActivity.getNumItems();

            StringBuilder csv = new StringBuilder();
            csv.append("LRN,Score");
            for (int i = 1; i <= numItems; i++) csv.append(",Q").append(i);
            csv.append("\n");

            for (ScanEntry scan : scans) {
                csv.append(scan.getLrn() != null ? scan.getLrn() : "");
                csv.append(",").append(scan.getScore()).append("/").append(scan.getNumItems());
                for (int i = 1; i <= numItems; i++) {
                    csv.append(",");
                    String answer = scan.getAnswers() != null ? scan.getAnswers().get(i) : null;
                    csv.append(answer != null ? answer : "");
                }
                csv.append("\n");
            }

            String filename = selectedClass.getGrade() + "-" + selectedClass.getSection() + "_" +
                    selectedActivity.getName().replaceAll("\\s+", "") + "_Master.csv";
            java.io.File csvFile = new java.io.File(getCacheDir(), filename);
            java.io.FileWriter writer = new java.io.FileWriter(csvFile);
            writer.write(csv.toString());
            writer.close();

            Intent intent = new Intent(this, com.example.omrscanner.ui.CSVFileActivity.class);
            intent.putExtra(com.example.omrscanner.ui.CSVFileActivity.EXTRA_CSV_FILEPATH,
                    csvFile.getAbsolutePath());
            startActivity(intent);

            showToast("Master CSV exported ✓");

        } catch (Exception e) {
            Log.e(TAG, "Error exporting master CSV", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    private void saveData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = gson.toJson(classFolders);
        prefs.edit().putString(KEY_CLASSES, json).apply();
        Log.d(TAG, "Data saved: " + classFolders.size() + " classes");
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_CLASSES, "[]");
        Type type = new TypeToken<List<ClassFolder>>(){}.getType();
        classFolders = gson.fromJson(json, type);
        if (classFolders == null) {
            classFolders = new ArrayList<>();
        }
        Log.d(TAG, "Data loaded: " + classFolders.size() + " classes");
    }

    public static void saveScanResult(android.content.Context context,
                                      String classId, String activityId,
                                      ScanEntry scanEntry) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString(KEY_CLASSES, "[]");
        Type type = new TypeToken<List<ClassFolder>>(){}.getType();
        List<ClassFolder> classes = gson.fromJson(json, type);

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
            String updatedJson = gson.toJson(classes);
            prefs.edit().putString(KEY_CLASSES, updatedJson).apply();
        }
    }

    private ClassFolder findClassById(String classId) {
        if (classId == null) return null;
        for (ClassFolder cls : classFolders) {
            if (cls.getId().equals(classId)) return cls;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // ERROR DIALOG
    // ═══════════════════════════════════════════════════════════════

    private Dialog activeErrorDialog = null;

    private void showErrorDialog(String title, String message) {
        if (activeErrorDialog != null && activeErrorDialog.isShowing()) {
            activeErrorDialog.dismiss();
        }

        Dialog errorDialog = new Dialog(this);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setCancelable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(Color.WHITE);
        dialogBg.setCornerRadius(dp(24));
        root.setBackground(dialogBg);

        TextView iconView = new TextView(this);
        iconView.setText("⚠️");
        iconView.setTextSize(32);
        iconView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(60), dp(60));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.bottomMargin = dp(16);
        iconView.setLayoutParams(iconParams);
        root.addView(iconView);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#CE1126"));
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(8);
        titleView.setLayoutParams(titleParams);
        root.addView(titleView);

        TextView msgView = new TextView(this);
        msgView.setText(message);
        msgView.setTextSize(13);
        msgView.setTextColor(Color.parseColor("#64748B"));
        msgView.setGravity(Gravity.CENTER);
        msgView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgParams.bottomMargin = dp(24);
        msgView.setLayoutParams(msgParams);
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
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnDismiss.setLayoutParams(btnParams);
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
            if (activeErrorDialog == errorDialog) activeErrorDialog = null;
        });
        errorDialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════════════════════════════

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();

        if (selectedClass != null) {
            selectedClass = findClassById(selectedClass.getId());
            if (selectedClass != null && selectedActivity != null) {
                for (ActivityFolder act : selectedClass.getActivities()) {
                    if (act.getId().equals(selectedActivity.getId())) {
                        selectedActivity = act;
                        break;
                    }
                }
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
}