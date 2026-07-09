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

import com.example.omrscanner.database.projections.AssessmentListRow;
import com.example.omrscanner.models.ActivityFolder;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Renders the Class screen: assessment list, sheet-type tabs, sort dialog.
 */
public class ClassScreenRenderer {

    public static final String ASSESSMENT_SORT_NEWEST          = "NEWEST";
    public static final String ASSESSMENT_SORT_OLDEST          = "OLDEST";
    public static final String ASSESSMENT_SORT_NAME_ASC        = "NAME_ASC";
    public static final String ASSESSMENT_SORT_NAME_DESC       = "NAME_DESC";
    public static final String ASSESSMENT_SORT_EXAM_DATE_NEWEST = "EXAM_DATE_NEWEST";
    public static final String ASSESSMENT_SORT_EXAM_DATE_OLDEST = "EXAM_DATE_OLDEST";

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;

    public ClassScreenRenderer(AppCompatActivity activity, DashboardUiHelper ui) {
        this.activity = activity;
        this.ui = ui;
    }

    // ─────────────────────────────────────────────────────────────
    // Sort helpers
    // ─────────────────────────────────────────────────────────────

    public String getAssessmentSortLabel(String key) {
        if (ASSESSMENT_SORT_OLDEST.equals(key))          return "Oldest";
        if (ASSESSMENT_SORT_NAME_ASC.equals(key))        return "Name A-Z";
        if (ASSESSMENT_SORT_NAME_DESC.equals(key))       return "Name Z-A";
        if (ASSESSMENT_SORT_EXAM_DATE_NEWEST.equals(key)) return "Exam Date ↓";
        if (ASSESSMENT_SORT_EXAM_DATE_OLDEST.equals(key)) return "Exam Date ↑";
        return "Newest";
    }

    public void showAssessmentSortDialog(String selectedAssessmentSort,
            java.util.function.Consumer<String> onSelected) {
        final String[] labels = {"Newest", "Oldest", "Name A-Z", "Name Z-A",
                "Exam Date (Newest)", "Exam Date (Oldest)"};
        final String[] keys = {ASSESSMENT_SORT_NEWEST, ASSESSMENT_SORT_OLDEST,
                ASSESSMENT_SORT_NAME_ASC, ASSESSMENT_SORT_NAME_DESC,
                ASSESSMENT_SORT_EXAM_DATE_NEWEST, ASSESSMENT_SORT_EXAM_DATE_OLDEST};
        int checked = indexOfKey(keys, selectedAssessmentSort);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Sort Assessments")
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
    // Sheet-type filter tabs
    // ─────────────────────────────────────────────────────────────

    /**
     * Populates the sheet-type tab row above the assessment list.
     * @param onTabSelected called with the selected filterValue (null = All)
     */
    public void buildClassSheetTabs(LinearLayout tabContainer,
            List<ActivityFolder> activities, String selectedSheetFilter,
            java.util.function.Consumer<String> onTabSelected) {

        tabContainer.removeAllViews();

        int countZPH30 = 0, countZPH40 = 0, countZPH50 = 0, countZPH60 = 0;
        if (activities != null) {
            for (ActivityFolder act : activities) {
                switch (act.getSheetType()) {
                    case "ZPH30": countZPH30++; break;
                    case "ZPH40": countZPH40++; break;
                    case "ZPH50": countZPH50++; break;
                    case "ZPH60": countZPH60++; break;
                }
            }
        }
        int totalCount = (activities != null) ? activities.size() : 0;

        Object[][] tabs = {
                {"All",   null,    totalCount},
                {"ZPH30", "ZPH30", countZPH30},
                {"ZPH40", "ZPH40", countZPH40},
                {"ZPH50", "ZPH50", countZPH50},
                {"ZPH60", "ZPH60", countZPH60},
        };

        for (Object[] tab : tabs) {
            final String label     = (String) tab[0];
            final String filterVal = (String) tab[1];
            final int count        = (int) tab[2];

            if (filterVal != null && count == 0) continue;

            boolean isActive = (selectedSheetFilter == null && filterVal == null)
                    || (selectedSheetFilter != null && selectedSheetFilter.equals(filterVal));

            TextView tabView = new TextView(activity);
            tabView.setText(label + " (" + count + ")");
            tabView.setTextSize(12);
            tabView.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            tabView.setGravity(Gravity.CENTER);
            tabView.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(20));
            if (isActive) {
                bg.setColor(Color.parseColor("#0038A8"));
                tabView.setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.parseColor("#F1F5F9"));
                bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
                tabView.setTextColor(Color.parseColor("#64748B"));
            }
            tabView.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = ui.dp(8);
            tabView.setLayoutParams(lp);

            tabView.setOnClickListener(v -> onTabSelected.accept(filterVal));
            tabContainer.addView(tabView);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Activity card
    // ─────────────────────────────────────────────────────────────

    /**
     * @param onEdit   called when Edit is tapped
     * @param onDelete called when Delete is tapped
     * @param onOpen   called when the card body is tapped
     */
    public View createActivityCard(AssessmentListRow row,
            Runnable onEdit, Runnable onSelectAnswerKey, Runnable onDelete, Runnable onUpload, Runnable onOpen) {

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(ui.dp(16));
        bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.dp(12);
        card.setLayoutParams(lp);

        // Header
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(activity);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(activity);
        title.setText(row.name);
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        leftCol.addView(title);

        TextView sub = new TextView(activity);
        sub.setText("Sheet: " + row.sheetType);
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(12);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = ui.dp(4);
        sub.setLayoutParams(slp);
        leftCol.addView(sub);
        header.addView(leftCol);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(18);
        header.addView(arrow);
        card.addView(header);

        // Meta
        TextView meta = new TextView(activity);
        String dateToShow = (row.examDate != null && !row.examDate.isEmpty())
                ? row.examDate
                : new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(new java.util.Date(row.createdAt));
        meta.setText("\uD83D\uDC65 " + row.scanCount + " of " + row.syncedStudentCount + " student"
                + (row.syncedStudentCount != 1 ? "s" : "") + " scanned · " + dateToShow);
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = ui.dp(8);
        meta.setLayoutParams(mlp);
        card.addView(meta);

        // Answer key badge (shown only when a key is assigned)
        if (row.answerKeyName != null && !row.answerKeyName.isEmpty()) {
            TextView keyBadge = new TextView(activity);
            keyBadge.setText("🗝 " + row.answerKeyName);
            keyBadge.setTextColor(Color.parseColor("#059669"));
            keyBadge.setTextSize(11);
            keyBadge.setTypeface(null, Typeface.ITALIC);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#ECFDF5"));
            badgeBg.setCornerRadius(ui.dp(8));
            badgeBg.setStroke(ui.dp(1), Color.parseColor("#A7F3D0"));
            keyBadge.setBackground(badgeBg);
            keyBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
            LinearLayout.LayoutParams kblp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kblp.topMargin = ui.dp(6);
            keyBadge.setLayoutParams(kblp);
            card.addView(keyBadge);
        }

        View divider = new View(activity);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
        divLp.topMargin = ui.dp(14);
        divLp.bottomMargin = ui.dp(4);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(Color.parseColor("#F1F5F9"));
        card.addView(divider);

        // Actions row
        LinearLayout actionsRow = new LinearLayout(activity);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionsRow.setWeightSum(4f);

        android.util.TypedValue outValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);

        TextView btnEdit = makeActionBtn("✏️ Edit", "#64748B", outValue.resourceId);
        btnEdit.setOnClickListener(v -> onEdit.run());

        TextView btnSelectAnswerKey = makeActionBtn("🗝️ Answer Key", "#0038A8", outValue.resourceId);
        btnSelectAnswerKey.setOnClickListener(v -> onSelectAnswerKey.run());

        TextView btnDelete = makeActionBtn("🗑️ Delete", "#EF4444", outValue.resourceId);
        btnDelete.setOnClickListener(v -> onDelete.run());

        TextView btnUpload = makeActionBtn("⬆️ Upload", "#059669", outValue.resourceId);
        btnUpload.setOnClickListener(v -> onUpload.run());

        actionsRow.addView(btnEdit);
        actionsRow.addView(btnSelectAnswerKey);
        actionsRow.addView(btnDelete);
        actionsRow.addView(btnUpload);
        card.addView(actionsRow);

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
