package com.example.omrscanner.dashboard;

import com.example.omrscanner.R;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.projections.AssessmentListRow;
import com.example.omrscanner.database.projections.AnswerKeyLinkInfo;
import com.example.omrscanner.database.projections.AnswerKeyLinkedAssessment;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_OMRScanner_Dialog)
                .setTitle("Sort Assessments")
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

    /**
     * Updates a search-bar filter-toggle button's appearance — identical styling to
     * HomeScreenRenderer.updateFilterToggleAppearance(), used by MY CLASSES: filled blue
     * while the panel is open, outlined blue when a non-default filter/sort is active,
     * neutral gray otherwise.
     */
    public void updateAssessmentFilterToggleAppearance(
            android.widget.ImageView filterToggle,
            boolean filterPanelVisible,
            boolean hasActiveFilter) {

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.dp(8));

        if (filterPanelVisible) {
            bg.setColor(Color.parseColor("#0038A8"));
            bg.setStroke(ui.dp(1), Color.parseColor("#0038A8"));
            filterToggle.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (hasActiveFilter) {
            bg.setColor(Color.parseColor("#EFF6FF"));
            bg.setStroke(ui.dp(1), Color.parseColor("#2563EB"));
            filterToggle.setColorFilter(Color.parseColor("#2563EB"), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            bg.setColor(Color.parseColor("#F1F5F9"));
            bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
            filterToggle.setColorFilter(Color.parseColor("#64748B"), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        filterToggle.setBackground(bg);
        filterToggle.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
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

        int countZPH40 = 0, countZPH60 = 0;
        if (activities != null) {
            for (ActivityFolder act : activities) {
                switch (ActivityFolder.parseBaseTemplateId(act.getSheetType())) {
                    case "ZPH40": countZPH40++; break;
                    case "ZPH60": countZPH60++; break;
                }
            }
        }
        int totalCount = (activities != null) ? activities.size() : 0;

        Object[][] tabs = {
                {"All",   null,    totalCount},
                {"ZPH40", "ZPH40", countZPH40},
                {"ZPH60", "ZPH60", countZPH60},
        };

        renderPillRow(tabContainer, tabs, selectedSheetFilter, onTabSelected);
    }

    /**
     * Populates a tab row grouping assessments by assessment type (Diagnostic/Summative/ECD).
     * @param onTabSelected called with the selected filterValue (null = All)
     */
    public void buildAssessmentTypeTabs(LinearLayout tabContainer,
                                        List<ActivityFolder> activities, String selectedTypeFilter,
                                        java.util.function.Consumer<String> onTabSelected) {

        int countDiagnostic = 0, countSummative = 0, countEcd = 0;
        if (activities != null) {
            for (ActivityFolder act : activities) {
                String type = act.getAssessmentType();
                if ("Diagnostic".equals(type)) countDiagnostic++;
                else if ("Summative".equals(type)) countSummative++;
                else if ("ECD".equals(type)) countEcd++;
            }
        }
        int totalCount = (activities != null) ? activities.size() : 0;

        Object[][] tabs = {
                {"All",        null,         totalCount},
                {"Diagnostic", "Diagnostic", countDiagnostic},
                {"Summative",  "Summative",  countSummative},
                {"ECD",        "ECD",        countEcd},
        };

        renderPillRow(tabContainer, tabs, selectedTypeFilter, onTabSelected);
    }

    /**
     * Populates a tab row grouping assessments by owning class.
     * @param onTabSelected called with the selected filterValue (classId, null = All)
     */
    public void buildClassGroupTabs(LinearLayout tabContainer,
                                    List<ClassFolder> classes, String selectedClassFilter,
                                    java.util.function.Consumer<String> onTabSelected) {

        int totalCount = 0;
        List<Object[]> tabList = new java.util.ArrayList<>();
        tabList.add(new Object[]{"All", null, 0});

        if (classes != null) {
            for (ClassFolder cf : classes) {
                int count = (cf.getActivities() != null) ? cf.getActivities().size() : 0;
                totalCount += count;
                if (count > 0) {
                    tabList.add(new Object[]{cf.getDisplayName(), cf.getId(), count});
                }
            }
        }
        tabList.set(0, new Object[]{"All", null, totalCount});

        renderPillRow(tabContainer, tabList.toArray(new Object[0][]), selectedClassFilter, onTabSelected);
    }

    /**
     * Populates the sheet-type tab row above the ALL ANSWER KEYS list.
     * Tallies whatever sheet types are actually present (ZPH30/40/50/60), unlike
     * buildClassSheetTabs which only distinguishes ZPH40/ZPH60 for assessments.
     * @param onTabSelected called with the selected filterValue (null = All)
     */
    public void buildAnswerKeySheetTabs(LinearLayout tabContainer,
                                        List<AnswerKeyEntity> keys, String selectedSheetFilter,
                                        java.util.function.Consumer<String> onTabSelected) {

        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (keys != null) {
            for (AnswerKeyEntity k : keys) {
                if (k.sheetType == null || k.sheetType.trim().isEmpty()) continue;
                counts.put(k.sheetType, counts.containsKey(k.sheetType) ? counts.get(k.sheetType) + 1 : 1);
            }
        }
        int totalCount = (keys != null) ? keys.size() : 0;

        List<Object[]> tabList = new ArrayList<>();
        tabList.add(new Object[]{"All", null, totalCount});
        List<String> sortedTypes = new ArrayList<>(counts.keySet());
        java.util.Collections.sort(sortedTypes);
        for (String type : sortedTypes) {
            tabList.add(new Object[]{type, type, counts.get(type)});
        }

        renderPillRow(tabContainer, tabList.toArray(new Object[0][]), selectedSheetFilter, onTabSelected);
    }

    /**
     * Populates the All / Linked / Unlinked pill row above the ALL ANSWER KEYS list.
     * "Linked" means at least one assessment currently references the key.
     */
    public void buildAnswerKeyLinkStatusTabs(LinearLayout tabContainer,
                                             List<AnswerKeyEntity> keys,
                                             java.util.Map<String, AnswerKeyLinkInfo> linkInfoMap,
                                             String selectedLinkFilter,
                                             java.util.function.Consumer<String> onTabSelected) {

        int linkedCount = 0, unlinkedCount = 0;
        if (keys != null) {
            for (AnswerKeyEntity k : keys) {
                AnswerKeyLinkInfo info = (linkInfoMap != null) ? linkInfoMap.get(k.id) : null;
                boolean linked = (info != null && info.linkedCount > 0);
                if (linked) linkedCount++; else unlinkedCount++;
            }
        }
        int totalCount = (keys != null) ? keys.size() : 0;

        Object[][] tabs = {
                {"All",      null,       totalCount},
                {"Linked",   "LINKED",   linkedCount},
                {"Unlinked", "UNLINKED", unlinkedCount},
        };

        renderPillRow(tabContainer, tabs, selectedLinkFilter, onTabSelected);
    }

    /**
     * Generic single-choice filter dialog. labels[i] corresponds to values[i];
     * values.get(0) should be null, representing the "All" option.
     * Used by Answer Keys' "Filter by Linked Assessment" picker.
     */
    public void showChoiceFilterDialog(String title, List<String> labels, List<String> values,
                                       String selectedValue, java.util.function.Consumer<String> onSelected) {
        int checked = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null && values.get(i).equals(selectedValue)) { checked = i; break; }
        }
        final int checkedFinal = checked;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_OMRScanner_Dialog)
                .setTitle(title)
                .setSingleChoiceItems(blackItems(labels.toArray(new String[0])), checkedFinal, (dialog, which) -> {
                    onSelected.accept(values.get(which));
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * Populates a small segmented-control row for choosing what the pill row below groups by.
     * @param onSelected called with "SHEET", "TYPE", or "CLASS"
     */
    public void buildGroupBySwitcher(LinearLayout container, String selectedGroupBy,
                                     java.util.function.Consumer<String> onSelected) {
        buildGroupBySwitcher(container, new String[][]{
                {"Sheet Type", "SHEET"},
                {"Assessment Type", "TYPE"},
                {"Class", "CLASS"},
        }, selectedGroupBy, onSelected);
    }

    /**
     * Overload allowing a custom set of grouping options (e.g. the Class screen omits
     * "Class" since the list is already scoped to one class).
     */
    public void buildGroupBySwitcher(LinearLayout container, String[][] options, String selectedGroupBy,
                                     java.util.function.Consumer<String> onSelected) {

        container.removeAllViews();

        for (String[] opt : options) {
            final String label = opt[0];
            final String key = opt[1];
            boolean isActive = key.equals(selectedGroupBy);

            TextView btn = new TextView(activity);
            btn.setText(label);
            btn.setTextSize(11);
            btn.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(16));
            if (isActive) {
                bg.setColor(Color.parseColor("#0038A8"));
                btn.setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.parseColor("#F1F5F9"));
                bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
                btn.setTextColor(Color.parseColor("#64748B"));
            }
            btn.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = ui.dp(8);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> onSelected.accept(key));
            container.addView(btn);
        }
    }

    /** Shared pill-row renderer used by all group-tab builders above. */
    private void renderPillRow(LinearLayout tabContainer, Object[][] tabs,
                               String selectedFilter, java.util.function.Consumer<String> onTabSelected) {

        tabContainer.removeAllViews();

        for (Object[] tab : tabs) {
            final String label     = (String) tab[0];
            final String filterVal = (String) tab[1];
            final int count        = (int) tab[2];

            if (filterVal != null && count == 0) continue;

            boolean isActive = (selectedFilter == null && filterVal == null)
                    || (selectedFilter != null && selectedFilter.equals(filterVal));

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
            android.graphics.drawable.Drawable keyIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_key).mutate();
            keyIcon.setTint(Color.parseColor("#0038A8"));
            android.graphics.drawable.Drawable uploadIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_upload).mutate();
            uploadIcon.setTint(Color.parseColor("#059669"));
            android.graphics.drawable.Drawable deleteIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_trash_outline).mutate();
            deleteIcon.setTint(Color.parseColor("#EF4444"));

            android.text.SpannableString editTitle = new android.text.SpannableString("Edit");
            editTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, editTitle.length(), 0);
            android.text.SpannableString keyTitle = new android.text.SpannableString("Answer Key");
            keyTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, keyTitle.length(), 0);
            android.text.SpannableString uploadTitle = new android.text.SpannableString("Upload");
            uploadTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, uploadTitle.length(), 0);
            android.text.SpannableString deleteTitle = new android.text.SpannableString("Delete");
            deleteTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, deleteTitle.length(), 0);

            popup.getMenu().add(0, 1, 0, editTitle).setIcon(editIcon);
            popup.getMenu().add(0, 2, 1, keyTitle).setIcon(keyIcon);
            popup.getMenu().add(0, 3, 2, uploadTitle).setIcon(uploadIcon);
            popup.getMenu().add(0, 4, 3, deleteTitle).setIcon(deleteIcon);

            try {
                java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuPopupHelper = field.get(popup);
                Class<?> helperClass = Class.forName(menuPopupHelper.getClass().getName());
                java.lang.reflect.Method setForceIcons = helperClass.getMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuPopupHelper, true);
            } catch (Exception ignored) { }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) { onEdit.run(); return true; }
                if (id == 2) { onSelectAnswerKey.run(); return true; }
                if (id == 3) { onUpload.run(); return true; }
                if (id == 4) { onDelete.run(); return true; }
                return false;
            });
            popup.show();
        });
        header.addView(menuBtn);

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

        // Scan count badge + class badge + answer key badge + needs-correction badge
        // (scan count badge always shown; the rest only when applicable)
        boolean hasClassBadge = row.className != null && !row.className.trim().isEmpty();
        boolean hasKeyBadge = row.answerKeyName != null && !row.answerKeyName.isEmpty();
        boolean hasCorrectionBadge = row.needsCorrectionCount > 0;
        {
            LinearLayout badgeRow = new LinearLayout(activity);
            badgeRow.setOrientation(LinearLayout.HORIZONTAL);
            badgeRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams badgeRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeRowLp.topMargin = ui.dp(6);
            badgeRow.setLayoutParams(badgeRowLp);

            // Scan count badge — always shown, first in the row.
            TextView scanCountBadge = new TextView(activity);
            scanCountBadge.setText("\uD83D\uDCC4 " + row.scanCount + (row.scanCount == 1 ? " scan" : " scans"));
            scanCountBadge.setTextColor(Color.parseColor("#0038A8"));
            scanCountBadge.setTextSize(11);
            scanCountBadge.setTypeface(null, Typeface.ITALIC);
            GradientDrawable scanCountBadgeBg = new GradientDrawable();
            scanCountBadgeBg.setColor(Color.parseColor("#EFF6FF"));
            scanCountBadgeBg.setCornerRadius(ui.dp(8));
            scanCountBadgeBg.setStroke(ui.dp(1), Color.parseColor("#BFDBFE"));
            scanCountBadge.setBackground(scanCountBadgeBg);
            scanCountBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
            badgeRow.addView(scanCountBadge);

            if (hasClassBadge) {
                TextView classBadge = new TextView(activity);
                classBadge.setText("🏫 " + row.className);
                classBadge.setTextColor(Color.parseColor("#3730A3"));
                classBadge.setTextSize(11);
                classBadge.setTypeface(null, Typeface.ITALIC);
                GradientDrawable classBadgeBg = new GradientDrawable();
                classBadgeBg.setColor(Color.parseColor("#EEF2FF"));
                classBadgeBg.setCornerRadius(ui.dp(8));
                classBadgeBg.setStroke(ui.dp(1), Color.parseColor("#C7D2FE"));
                classBadge.setBackground(classBadgeBg);
                classBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
                LinearLayout.LayoutParams classBadgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                classBadgeLp.leftMargin = ui.dp(6);
                classBadge.setLayoutParams(classBadgeLp);
                badgeRow.addView(classBadge);
            }

            if (hasKeyBadge) {
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
                LinearLayout.LayoutParams keyBadgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                keyBadgeLp.leftMargin = ui.dp(6);
                keyBadge.setLayoutParams(keyBadgeLp);
                badgeRow.addView(keyBadge);
            }

            if (hasCorrectionBadge) {
                TextView correctionBadge = new TextView(activity);
                correctionBadge.setText("⚠ " + row.needsCorrectionCount
                        + (row.needsCorrectionCount == 1 ? " student needs correction" : " students need correction"));
                correctionBadge.setTextColor(Color.parseColor("#B45309"));
                correctionBadge.setTextSize(11);
                correctionBadge.setTypeface(null, Typeface.ITALIC);
                GradientDrawable correctionBg = new GradientDrawable();
                correctionBg.setColor(Color.parseColor("#FFFBEB"));
                correctionBg.setCornerRadius(ui.dp(8));
                correctionBg.setStroke(ui.dp(1), Color.parseColor("#FDE68A"));
                correctionBadge.setBackground(correctionBg);
                correctionBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
                LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cblp.leftMargin = ui.dp(6);
                correctionBadge.setLayoutParams(cblp);
                badgeRow.addView(correctionBadge);
            }

            card.addView(badgeRow);
        }

        card.setOnClickListener(v -> onOpen.run());
        return card;
    }

    /**
     * Creates a card for a single answer key, for the "Answer Keys" tab list.
     */
    public View createAnswerKeyCard(AnswerKeyEntity key, AnswerKeyLinkInfo link,
                                    List<AnswerKeyLinkedAssessment> linkedAssessments, Runnable onView, Runnable onEdit, Runnable onDelete) {

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
        title.setText(key.name != null && !key.name.trim().isEmpty() ? key.name : "Untitled answer key");
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        leftCol.addView(title);

        TextView sub = new TextView(activity);
        sub.setText("Sheet: " + (key.sheetType != null ? key.sheetType : "—")
                + " · " + key.getNumItems() + " items");
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(12);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = ui.dp(4);
        sub.setLayoutParams(slp);
        leftCol.addView(sub);
        header.addView(leftCol);

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

            android.graphics.drawable.Drawable viewIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_eye_outline).mutate();
            viewIcon.setTint(Color.parseColor("#64748B"));
            android.graphics.drawable.Drawable editIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_edit_pencil).mutate();
            editIcon.setTint(Color.parseColor("#64748B"));
            android.graphics.drawable.Drawable deleteIcon =
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_trash_outline).mutate();
            deleteIcon.setTint(Color.parseColor("#EF4444"));

            android.text.SpannableString viewTitle = new android.text.SpannableString("View");
            viewTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, viewTitle.length(), 0);
            android.text.SpannableString editTitle = new android.text.SpannableString("Edit");
            editTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, editTitle.length(), 0);
            android.text.SpannableString deleteTitle = new android.text.SpannableString("Delete");
            deleteTitle.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#1E293B")), 0, deleteTitle.length(), 0);

            popup.getMenu().add(0, 1, 0, viewTitle).setIcon(viewIcon);
            popup.getMenu().add(0, 2, 1, editTitle).setIcon(editIcon);
            popup.getMenu().add(0, 3, 2, deleteTitle).setIcon(deleteIcon);

            try {
                java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuPopupHelper = field.get(popup);
                Class<?> helperClass = Class.forName(menuPopupHelper.getClass().getName());
                java.lang.reflect.Method setForceIcons = helperClass.getMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuPopupHelper, true);
            } catch (Exception ignored) { }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) { onView.run(); return true; }
                if (id == 2) { onEdit.run(); return true; }
                if (id == 3) { onDelete.run(); return true; }
                return false;
            });
            popup.show();
        });
        header.addView(menuBtn);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(18);
        header.addView(arrow);
        card.addView(header);

        // Link status: tappable "Linked to" dropdown + sheet-type badge, or a single "not linked" badge
        LinearLayout linkBadgeRow = new LinearLayout(activity);
        linkBadgeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams linkBadgeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linkBadgeRowLp.topMargin = ui.dp(8);
        linkBadgeRow.setLayoutParams(linkBadgeRowLp);

        boolean isLinked = link != null && link.linkedAssessmentName != null;
        if (isLinked) {
            String extra = link.linkedCount > 1 ? " (+" + (link.linkedCount - 1) + " more)" : "";

            TextView nameBadge = new TextView(activity);
            nameBadge.setText("🔗 Linked to " + link.linkedAssessmentName + extra + "  ▾");
            nameBadge.setTextColor(Color.parseColor("#1D4ED8"));
            nameBadge.setTextSize(11);
            nameBadge.setTypeface(null, Typeface.ITALIC);
            GradientDrawable nameBadgeBg = new GradientDrawable();
            nameBadgeBg.setCornerRadius(ui.dp(8));
            nameBadgeBg.setColor(Color.parseColor("#EFF6FF"));
            nameBadgeBg.setStroke(ui.dp(1), Color.parseColor("#BFDBFE"));
            nameBadge.setBackground(nameBadgeBg);
            nameBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
            nameBadge.setClickable(true);
            nameBadge.setFocusable(true);
            nameBadge.setOnClickListener(v -> {
                List<AnswerKeyLinkedAssessment> rows = linkedAssessments != null
                        ? linkedAssessments : new ArrayList<>();
                String[] labels;
                if (rows.isEmpty()) {
                    // Fallback in case the grouped list hasn't loaded yet — show what we already know.
                    labels = new String[]{ link.linkedAssessmentName
                            + (link.linkedSheetType != null ? "  ·  " + link.linkedSheetType : "") };
                } else {
                    labels = new String[rows.size()];
                    for (int i = 0; i < rows.size(); i++) {
                        AnswerKeyLinkedAssessment r = rows.get(i);
                        labels[i] = r.name + (r.sheetType != null ? "  ·  " + r.sheetType : "");
                    }
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        activity, R.style.ThemeOverlay_OMRScanner_Dialog)
                        .setTitle("Linked Assessments")
                        .setItems(labels, null)
                        .show();
            });
            linkBadgeRow.addView(nameBadge);

            if (link.linkedSheetType != null) {
                TextView typeBadge = new TextView(activity);
                typeBadge.setText(link.linkedSheetType + " (" + key.getNumItems() + " items)");
                typeBadge.setTextColor(Color.parseColor("#1D4ED8"));
                typeBadge.setTextSize(11);
                typeBadge.setTypeface(null, Typeface.ITALIC);
                GradientDrawable typeBadgeBg = new GradientDrawable();
                typeBadgeBg.setCornerRadius(ui.dp(8));
                typeBadgeBg.setColor(Color.parseColor("#EFF6FF"));
                typeBadgeBg.setStroke(ui.dp(1), Color.parseColor("#BFDBFE"));
                typeBadge.setBackground(typeBadgeBg);
                typeBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
                LinearLayout.LayoutParams typeBadgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                typeBadgeLp.leftMargin = ui.dp(6);
                typeBadge.setLayoutParams(typeBadgeLp);
                linkBadgeRow.addView(typeBadge);
            }
        } else {
            TextView notLinkedBadge = new TextView(activity);
            notLinkedBadge.setText("◌ Not linked to an assessment");
            notLinkedBadge.setTextColor(Color.parseColor("#64748B"));
            notLinkedBadge.setTextSize(11);
            notLinkedBadge.setTypeface(null, Typeface.ITALIC);
            GradientDrawable notLinkedBg = new GradientDrawable();
            notLinkedBg.setCornerRadius(ui.dp(8));
            notLinkedBg.setColor(Color.parseColor("#F1F5F9"));
            notLinkedBg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
            notLinkedBadge.setBackground(notLinkedBg);
            notLinkedBadge.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
            linkBadgeRow.addView(notLinkedBadge);
        }
        card.addView(linkBadgeRow);

        // Meta
        TextView meta = new TextView(activity);
        String dateToShow = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(new java.util.Date(key.createdAt));
        meta.setText("🗝 Created " + dateToShow
                + (key.schoolYear != null && !key.schoolYear.isEmpty() ? " · S.Y. " + key.schoolYear : ""));
        meta.setTextColor(Color.parseColor("#94A3B8"));
        meta.setTextSize(11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = ui.dp(8);
        meta.setLayoutParams(mlp);
        card.addView(meta);

        card.setOnClickListener(v -> onView.run());
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
