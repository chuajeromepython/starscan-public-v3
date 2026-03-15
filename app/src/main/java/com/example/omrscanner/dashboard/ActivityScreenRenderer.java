package com.example.omrscanner.dashboard;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ScanEntry;
import com.example.omrscanner.ui.ScanDetailActivity;

import java.util.List;

/**
 * Renders the Activity (Assessment detail) screen: scan list and scan cards.
 */
public class ActivityScreenRenderer {

    private static final String TAG = "ActivityScreenRenderer";

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;

    public ActivityScreenRenderer(AppCompatActivity activity, DashboardUiHelper ui) {
        this.activity = activity;
        this.ui = ui;
    }

    /**
     * Populates the scan list in the activity screen.
     *
     * @param activityScanList    container to add scan cards into
     * @param activityScansEmpty  empty-state view
     * @param scansHeader         header shown only when scans exist
     * @param scansTotalCount     label showing "X total"
     * @param selectedActivity    the currently displayed assessment
     * @param classId             stable class ID for scan-detail navigation
     * @param activityId          stable activity ID for scan-detail navigation
     */
    public void renderActivityScreen(
            LinearLayout activityScanList,
            LinearLayout activityScansEmpty,
            LinearLayout scansHeader,
            TextView scansTotalCount,
            ActivityFolder selectedActivity,
            String classId,
            String activityId) {

        activityScanList.removeAllViews();

        List<ScanEntry> scans = selectedActivity.getScans();
        boolean hasScans = (scans != null && !scans.isEmpty());

        if (hasScans) {
            scansHeader.setVisibility(View.VISIBLE);
            scansTotalCount.setText(scans.size() + " total");
            activityScanList.setVisibility(View.VISIBLE);
            activityScansEmpty.setVisibility(View.GONE);
            for (int i = 0; i < scans.size(); i++) {
                activityScanList.addView(createScanCard(scans.get(i), i,
                        selectedActivity, classId, activityId));
            }
        } else {
            scansHeader.setVisibility(View.GONE);
            activityScanList.setVisibility(View.GONE);
            activityScansEmpty.setVisibility(View.VISIBLE);
        }
    }

    private View createScanCard(ScanEntry scan, int index,
            ActivityFolder selectedActivity,
            final String classId, final String activityId) {

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(ui.dp(16));
        cardBg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = ui.dp(10);
        card.setLayoutParams(cardLp);

        // Thumbnail
        FrameLayout thumb = new FrameLayout(activity);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(ui.dp(48), ui.dp(60));
        thumbLp.rightMargin = ui.dp(14);
        thumb.setLayoutParams(thumbLp);
        thumb.setClickable(false);
        thumb.setFocusable(false);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.parseColor("#F0F7FF"));
        thumbBg.setCornerRadius(ui.dp(10));
        thumbBg.setStroke(ui.dp(1), Color.parseColor("#0038A8"));
        thumb.setBackground(thumbBg);
        TextView thumbIcon = new TextView(activity);
        thumbIcon.setText("📄");
        thumbIcon.setTextSize(22);
        thumbIcon.setGravity(Gravity.CENTER);
        thumbIcon.setClickable(false);
        thumbIcon.setFocusable(false);
        thumb.addView(thumbIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(thumb);

        // Info column
        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClickable(false);
        info.setFocusable(false);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView lrn = new TextView(activity);
        lrn.setText("LRN: " + (scan.getLrn() != null && !scan.getLrn().isEmpty()
                ? scan.getLrn() : "Unknown"));
        lrn.setTextColor(Color.parseColor("#1E293B"));
        lrn.setTextSize(13);
        lrn.setTypeface(null, Typeface.BOLD);
        lrn.setClickable(false);
        info.addView(lrn);

        TextView detail = new TextView(activity);
        detail.setText("Student #" + (index + 1) + " · " + selectedActivity.getSheetType());
        detail.setTextColor(Color.parseColor("#64748B"));
        detail.setTextSize(12);
        detail.setClickable(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = ui.dp(2);
        detail.setLayoutParams(dlp);
        info.addView(detail);
        card.addView(info);

        // Score badge
        TextView scoreBadge = createScoreBadge(scan);
        scoreBadge.setClickable(false);
        scoreBadge.setFocusable(false);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.rightMargin = ui.dp(8);
        scoreBadge.setLayoutParams(badgeLp);
        card.addView(scoreBadge);

        // Arrow
        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(16);
        arrow.setClickable(false);
        arrow.setFocusable(false);
        card.addView(arrow);

        card.setOnClickListener(v -> {
            Log.d(TAG, "Opening ScanDetail — index=" + index
                    + "  classId=" + classId + "  activityId=" + activityId);
            Intent intent = new Intent(activity, ScanDetailActivity.class);
            intent.putExtra(ScanDetailActivity.EXTRA_CLASS_ID, classId);
            intent.putExtra(ScanDetailActivity.EXTRA_ACTIVITY_ID, activityId);
            intent.putExtra(ScanDetailActivity.EXTRA_SCAN_INDEX, index);
            activity.startActivity(intent);
        });

        return card;
    }

    private TextView createScoreBadge(ScanEntry scan) {
        TextView badge = new TextView(activity);
        badge.setText(scan.getScore() + "/" + scan.getNumItems());
        badge.setTextSize(12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(ui.dp(8));
        if (scan.isScored()) {
            // Real graded score — gold/amber to distinguish from raw count
            badgeBg.setColor(Color.parseColor("#FEF9C3"));
            badge.setTextColor(Color.parseColor("#854D0E"));
        } else {
            // Detected bubbles only — neutral green
            badgeBg.setColor(Color.parseColor("#DCFCE7"));
            badge.setTextColor(Color.parseColor("#059669"));
        }
        badge.setBackground(badgeBg);
        return badge;
    }
}
