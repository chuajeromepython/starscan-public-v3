package com.example.omrscanner.dashboard;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.projections.ClassListRow;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Home screen: class list, filter chips, sort dialog.
 */
public class HomeScreenRenderer {

    // Sort constants (mirrors those in DashboardActivity)
    public static final String CLASS_SORT_NEWEST   = "NEWEST";
    public static final String CLASS_SORT_OLDEST   = "OLDEST";
    public static final String CLASS_SORT_GRADE_ASC   = "GRADE_ASC";
    public static final String CLASS_SORT_SECTION_ASC = "SECTION_ASC";

    private static final String[] CLASS_CARD_ACCENTS = {
            "#2563EB", "#059669", "#7C3AED", "#D97706", "#DC2626", "#0891B2"
    };

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;

    public HomeScreenRenderer(AppCompatActivity activity, DashboardUiHelper ui) {
        this.activity = activity;
        this.ui = ui;
    }

    // ─────────────────────────────────────────────────────────────
    // Sort helpers
    // ─────────────────────────────────────────────────────────────

    public String getClassSortLabel(String key) {
        if (CLASS_SORT_OLDEST.equals(key))   return "Oldest";
        if (CLASS_SORT_GRADE_ASC.equals(key))   return "Grade A-Z";
        if (CLASS_SORT_SECTION_ASC.equals(key)) return "Section A-Z";
        return "Newest";
    }

    public void showClassSortDialog(String selectedClassSort, java.util.function.Consumer<String> onSelected) {
        final String[] labels = {"Newest", "Oldest", "Grade A-Z", "Section A-Z"};
        final String[] keys   = {CLASS_SORT_NEWEST, CLASS_SORT_OLDEST, CLASS_SORT_GRADE_ASC, CLASS_SORT_SECTION_ASC};
        int checked = indexOfKey(keys, selectedClassSort);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Sort Classes")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    onSelected.accept(keys[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private int indexOfKey(String[] keys, String selected) {
        if (selected == null) return 0;
        for (int i = 0; i < keys.length; i++) {
            if (selected.equals(keys[i])) return i;
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────
    // Filter chips
    // ─────────────────────────────────────────────────────────────

    public interface ChipSelectionHandler {
        void onSelected(String value);
    }

    public List<String> getDistinctGrades(List<ClassFolder> classFolders) {
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

    public List<String> getDistinctSchoolYears(List<ClassFolder> classFolders) {
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

    /**
     * Builds grade and school-year filter chip rows.
     * Returns true if a stale filter selection was auto-cleared (caller should re-render).
     */
    public boolean buildHomeFilterChips(
            LinearLayout gradeChips, LinearLayout schoolYearChips,
            List<String> grades, List<String> years,
            String selectedGrade, String selectedYear,
            ChipSelectionHandler onGrade, ChipSelectionHandler onYear,
            java.util.function.BooleanSupplier isHomeScreen) {

        boolean selectionChanged = false;
        if (selectedGrade != null && (grades == null || !grades.contains(selectedGrade))) {
            onGrade.onSelected(null);
            selectionChanged = true;
        }
        if (selectedYear != null && (years == null || !years.contains(selectedYear))) {
            onYear.onSelected(null);
            selectionChanged = true;
        }
        if (selectionChanged && isHomeScreen.getAsBoolean()) {
            return true;
        }

        buildFilterChipRow(gradeChips, grades, selectedGrade, onGrade);
        buildFilterChipRow(schoolYearChips, years, selectedYear, onYear);
        return false;
    }

    public void buildFilterChipRow(LinearLayout container, List<String> values,
            String selectedValue, ChipSelectionHandler handler) {
        container.removeAllViews();
        container.addView(ui.createFilterChip("All", selectedValue == null, () -> handler.onSelected(null)));
        if (values == null) return;
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            boolean isActive = value.equals(selectedValue);
            container.addView(ui.createFilterChip(value, isActive, () -> handler.onSelected(value)));
        }
    }

    public void updateFilterToggleAppearance(
            android.widget.ImageView homeFilterToggle,
            boolean homeFilterPanelVisible,
            String selectedClassGradeFilter,
            String selectedClassSchoolYearFilter,
            String selectedClassSort) {

        boolean hasActiveFilter = selectedClassGradeFilter != null
                || selectedClassSchoolYearFilter != null
                || !CLASS_SORT_NEWEST.equals(selectedClassSort);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.dp(8));

        if (homeFilterPanelVisible) {
            bg.setColor(Color.parseColor("#0038A8"));
            bg.setStroke(ui.dp(1), Color.parseColor("#0038A8"));
            homeFilterToggle.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (hasActiveFilter) {
            bg.setColor(Color.parseColor("#EFF6FF"));
            bg.setStroke(ui.dp(1), Color.parseColor("#2563EB"));
            homeFilterToggle.setColorFilter(Color.parseColor("#2563EB"), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            bg.setColor(Color.parseColor("#F1F5F9"));
            bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
            homeFilterToggle.setColorFilter(Color.parseColor("#64748B"), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        homeFilterToggle.setBackground(bg);
        homeFilterToggle.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
    }

    // ─────────────────────────────────────────────────────────────
    // Class card
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a class card view for the home list.
     * @param onEdit     called when Edit is tapped
     * @param onDownload called when Download is tapped
     * @param onDelete   called when Delete is tapped
     * @param onOpen     called when the card body is tapped
     */
    public View createClassCard(ClassListRow row,
            String globalTeacherName,
            Runnable onEdit, Runnable onDownload,
            Runnable onDelete, Runnable onOpen) {

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(ui.dp(16));
        bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);
        card.setElevation(ui.dp(2));
        card.setClipToOutline(true);

        android.content.res.TypedArray ta = activity.obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground});
        card.setForeground(ta.getDrawable(0));
        ta.recycle();

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.dp(10);
        card.setLayoutParams(lp);

        // Accent bar
        int accentIndex = Math.abs((row.getDisplayName() != null
                ? row.getDisplayName().hashCode() : 0)) % CLASS_CARD_ACCENTS.length;
        View accentBar = new View(activity);
        accentBar.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(5), ViewGroup.LayoutParams.MATCH_PARENT));
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(Color.parseColor(CLASS_CARD_ACCENTS[accentIndex]));
        accentBar.setBackground(accentBg);
        card.addView(accentBar);

        // Content
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Title row
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(row.getDisplayName());
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#CBD5E1"));
        arrow.setTextSize(20);
        arrow.setPadding(ui.dp(8), 0, 0, 0);
        header.addView(arrow);
        content.addView(header);

        // Teacher row
        TextView teacher = new TextView(activity);
        String displayTeacher = (globalTeacherName != null && !globalTeacherName.isEmpty())
                ? globalTeacherName : "Unknown Teacher";
        teacher.setText("👤  " + displayTeacher);
        teacher.setTextColor(Color.parseColor("#64748B"));
        teacher.setTextSize(12);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = ui.dp(4);
        teacher.setLayoutParams(tlp);
        content.addView(teacher);

        // Meta row
        TextView meta = new TextView(activity);
        String schoolYearText = (row.schoolYear != null && !row.schoolYear.isEmpty())
                ? " · S.Y. " + row.schoolYear : "";
        meta.setText("📂 " + row.assessmentCount + " Assessment"
                + (row.assessmentCount != 1 ? "s" : "") + schoolYearText);
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = ui.dp(6);
        meta.setLayoutParams(mlp);
        content.addView(meta);

        // Divider
        View divider = new View(activity);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
        divLp.topMargin = ui.dp(14);
        divLp.bottomMargin = ui.dp(4);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(Color.parseColor("#F1F5F9"));
        content.addView(divider);

        // Action row
        LinearLayout actionsRow = new LinearLayout(activity);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionsRow.setWeightSum(3f);

        android.util.TypedValue outValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);

        TextView btnEdit = makeActionBtn("✏️ Edit", "#64748B", outValue.resourceId);
        btnEdit.setOnClickListener(v -> onEdit.run());

        TextView btnDownload = makeActionBtn("⬇️ Download", "#64748B", outValue.resourceId);
        btnDownload.setOnClickListener(v -> onDownload.run());

        TextView btnDelete = makeActionBtn("🗑️ Delete", "#EF4444", outValue.resourceId);
        btnDelete.setOnClickListener(v -> onDelete.run());

        actionsRow.addView(btnEdit);
        actionsRow.addView(btnDownload);
        actionsRow.addView(btnDelete);
        content.addView(actionsRow);

        card.addView(content);
        card.setOnClickListener(v -> onOpen.run());
        return card;
    }

    private TextView makeActionBtn(String text, String colorHex, int bg) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(colorHex));
        btn.setTextSize(12);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, ui.dp(10), 0, ui.dp(10));
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btn.setBackgroundResource(bg);
        return btn;
    }
}
