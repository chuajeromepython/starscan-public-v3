package com.example.omrscanner.dashboard;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.database.projections.ScanListRow;
import com.example.omrscanner.ui.ScanDetailActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renders the Scans tab: a flat, read-only list of every scan across all
 * classes and assessments. Tapping a card opens ScanDetailActivity in
 * view-only mode (EXTRA_READ_ONLY) — teachers can inspect a scan from here
 * but cannot edit it. Editing only remains possible from a scan's own
 * class → assessment screen.
 */
public class ScansScreenRenderer {

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault());

    public ScansScreenRenderer(AppCompatActivity activity, DashboardUiHelper ui) {
        this.activity = activity;
        this.ui = ui;
    }

    public View createScanCard(ScanListRow row) {
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
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.parseColor("#F0F7FF"));
        thumbBg.setCornerRadius(ui.dp(10));
        thumbBg.setStroke(ui.dp(1), Color.parseColor("#0038A8"));
        thumb.setBackground(thumbBg);
        TextView thumbIcon = new TextView(activity);
        thumbIcon.setText("📄");
        thumbIcon.setTextSize(22);
        thumbIcon.setGravity(Gravity.CENTER);
        thumb.addView(thumbIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(thumb);

        // Info column
        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView lrn = new TextView(activity);
        lrn.setText("LRN: " + (row.studentLrn != null && !row.studentLrn.isEmpty()
                ? row.studentLrn : "Unknown"));
        lrn.setTextColor(Color.parseColor("#1E293B"));
        lrn.setTextSize(13);
        lrn.setTypeface(null, Typeface.BOLD);
        info.addView(lrn);

        TextView detail = new TextView(activity);
        String className = row.className != null ? row.className : "Unknown class";
        String assessmentName = row.assessmentName != null ? row.assessmentName : "Unknown assessment";
        detail.setText(className + " · " + assessmentName);
        detail.setTextColor(Color.parseColor("#64748B"));
        detail.setTextSize(12);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = ui.dp(2);
        detail.setLayoutParams(dlp);
        info.addView(detail);

        TextView dateLabel = new TextView(activity);
        dateLabel.setText(dateFormat.format(new Date(row.timestamp)));
        dateLabel.setTextColor(Color.parseColor("#94A3B8"));
        dateLabel.setTextSize(11);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = ui.dp(2);
        dateLabel.setLayoutParams(dateLp);
        info.addView(dateLabel);

        card.addView(info);

        // Score badge
        TextView scoreBadge = createScoreBadge(row);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.rightMargin = ui.dp(8);
        scoreBadge.setLayoutParams(badgeLp);
        card.addView(scoreBadge);

        // Lock glyph instead of the usual ">" arrow — signals up-front that
        // opening this card is a view-only action, not navigation into an editor.
        TextView lockIcon = new TextView(activity);
        lockIcon.setText("🔒");
        lockIcon.setTextSize(13);
        card.addView(lockIcon);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(activity, ScanDetailActivity.class);
            intent.putExtra(ScanDetailActivity.EXTRA_SCAN_ID, row.id);
            intent.putExtra(ScanDetailActivity.EXTRA_READ_ONLY, true);
            activity.startActivity(intent);
        });

        return card;
    }

    private TextView createScoreBadge(ScanListRow row) {
        TextView badge = new TextView(activity);
        int score = row.score != null ? row.score : row.detectedBubbles;
        badge.setText(score + "/" + row.numItems);
        badge.setTextSize(12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(ui.dp(8));
        boolean scored = row.isGraded && row.score != null;
        if (scored) {
            badgeBg.setColor(Color.parseColor("#DCFCE7"));
            badge.setTextColor(Color.parseColor("#059669"));
        } else {
            badgeBg.setColor(Color.parseColor("#FEF9C3"));
            badge.setTextColor(Color.parseColor("#854D0E"));
        }
        badge.setBackground(badgeBg);
        return badge;
    }
}