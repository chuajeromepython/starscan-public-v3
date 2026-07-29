package com.example.omrscanner.dashboard;

import com.example.omrscanner.R;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_OMRScanner_Dialog)
                .setTitle("Sort Classes")
                .setSingleChoiceItems(blackItems(labels), checked, (dialog, which) -> {
                    onSelected.accept(keys[which]);
                    dialog.dismiss();
                })
                .show();
    }

    /** Forces dialog list-item text to render solid black — the Material3 single-choice
     *  dialog list otherwise renders item text in gray regardless of theme overrides. */
    private CharSequence[] blackItems(String[] labels) {
        CharSequence[] out = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) {
            android.text.SpannableString s = new android.text.SpannableString(labels[i]);
            s.setSpan(new android.text.style.ForegroundColorSpan(Color.BLACK), 0, s.length(), 0);
            out[i] = s;
        }
        return out;
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
            Runnable onEdit, Runnable onDelete, Runnable onOpen) {

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

        // Card body: a FrameLayout so the trailing arrow can be pinned to the
        // bottom-right corner independently of the vertical content stack.
        FrameLayout cardBody = new FrameLayout(activity);
        cardBody.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Content
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        content.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

        android.util.TypedValue menuBgValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, menuBgValue, true);

        android.widget.ImageView menuBtn = new android.widget.ImageView(activity);
        menuBtn.setImageResource(R.drawable.ic_more_vert);
        menuBtn.setColorFilter(Color.parseColor("#94A3B8"));
        menuBtn.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6));
        menuBtn.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        menuBtn.setClickable(true);
        menuBtn.setFocusable(true);
        menuBtn.setBackgroundResource(menuBgValue.resourceId);
        menuBtn.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(
                    activity, menuBtn, Gravity.END, 0, R.style.PopupMenu_RoundedCard);

            android.graphics.drawable.Drawable editIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_edit_pencil).mutate();
            editIcon.setTint(Color.parseColor("#64748B"));
            android.graphics.drawable.Drawable deleteIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_trash_outline).mutate();
            deleteIcon.setTint(Color.parseColor("#EF4444"));

            android.text.SpannableString editTitle = new android.text.SpannableString("Edit");
            editTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, editTitle.length(), 0);
            android.text.SpannableString deleteTitle = new android.text.SpannableString("Delete");
            deleteTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, deleteTitle.length(), 0);

            popup.getMenu().add(0, 1, 0, editTitle).setIcon(editIcon);
            popup.getMenu().add(0, 2, 1, deleteTitle).setIcon(deleteIcon);

            try {
                java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuPopupHelper = field.get(popup);
                Class<?> helperClass = Class.forName(menuPopupHelper.getClass().getName());
                java.lang.reflect.Method setForceIcons = helperClass.getMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuPopupHelper, true);
            } catch (Exception ignored) { }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    onEdit.run();
                    return true;
                } else if (item.getItemId() == 2) {
                    onDelete.run();
                    return true;
                }
                return false;
            });
            popup.show();
        });
        header.addView(menuBtn);
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

        // Active / Inactive status badge
        boolean isActive = row.isActive();
        TextView statusBadge = new TextView(activity);
        statusBadge.setText(isActive ? "● Active" : "○ Inactive");
        statusBadge.setTextColor(isActive ? Color.parseColor("#059669") : Color.parseColor("#64748B"));
        statusBadge.setTextSize(11);
        statusBadge.setTypeface(null, Typeface.ITALIC);
        GradientDrawable statusBadgeBg = new GradientDrawable();
        statusBadgeBg.setCornerRadius(ui.dp(8));
        statusBadgeBg.setColor(isActive ? Color.parseColor("#ECFDF5") : Color.parseColor("#F1F5F9"));
        statusBadgeBg.setStroke(ui.dp(1), isActive ? Color.parseColor("#A7F3D0") : Color.parseColor("#E2E8F0"));
        statusBadge.setBackground(statusBadgeBg);
        statusBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
        LinearLayout.LayoutParams statusBadgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusBadgeLp.topMargin = ui.dp(6);
        statusBadge.setLayoutParams(statusBadgeLp);
        content.addView(statusBadge);

        cardBody.addView(content);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#CBD5E1"));
        arrow.setTextSize(20);
        arrow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(
                ui.dp(32), ViewGroup.LayoutParams.WRAP_CONTENT);
        arrowLp.gravity = Gravity.BOTTOM | Gravity.END;
        arrowLp.bottomMargin = ui.dp(10);
        arrowLp.rightMargin = ui.dp(14);
        arrow.setLayoutParams(arrowLp);
        cardBody.addView(arrow);

        card.addView(cardBody);
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
