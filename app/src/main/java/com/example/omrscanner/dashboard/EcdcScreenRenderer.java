package com.example.omrscanner.dashboard;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.database.entities.EcdcResponseEntity;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Renders the pieces of the ECDC student checklist screen: one card per
 * competency with Present / Not present / Not tested radio buttons.
 */
public class EcdcScreenRenderer {

    private static final String COLOR_PRESENT = "#16A34A";
    private static final String COLOR_NOT_PRESENT = "#DC2626";
    private static final String COLOR_NOT_TESTED = "#64748B";
    private static final String COLOR_UNCHECKED = "#94A3B8";

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;

    public EcdcScreenRenderer(AppCompatActivity activity, DashboardUiHelper ui) {
        this.activity = activity;
        this.ui = ui;
    }

    /**
     * "GROSS MOTOR DOMAIN" -> "Gross Motor", "SOCIO-EMOTIONAL DOMAIN" -> "Socio-Emotional".
     * Display only — the server's original name is what's stored and shown as the list heading.
     */
    public static String shortDomainName(String serverName) {
        if (serverName == null) return "";
        String name = serverName.trim();
        if (name.toUpperCase(Locale.ROOT).endsWith(" DOMAIN")) {
            name = name.substring(0, name.length() - " DOMAIN".length()).trim();
        }
        StringBuilder out = new StringBuilder();
        boolean startOfWord = true;
        for (char c : name.toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            startOfWord = (c == ' ' || c == '-');
        }
        return out.toString();
    }

    /** Full wording of a period key, for the student header. */
    public static String periodLabel(String periodKey) {
        if ("BOSY".equals(periodKey)) return "Beginning of School Year";
        if ("MOSY".equals(periodKey)) return "Middle of School Year";
        if ("EOSY".equals(periodKey)) return "End of School Year";
        return periodKey != null ? periodKey : "";
    }

    /**
     * One competency: its number + text on top, three radio buttons below.
     *
     * @param status          one of EcdcResponseEntity.STATUS_*, or null if not marked yet
     * @param onStatusChanged called with the newly chosen STATUS_* (never fires for the
     *                        initial preselection)
     */
    public View createCompetencyRow(int number, String competency, String status,
                                    Consumer<String> onStatusChanged) {

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(10), ui.dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(ui.dp(16));
        bg.setStroke(ui.dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);
        card.setElevation(ui.dp(2));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = ui.dp(10);
        card.setLayoutParams(cardLp);

        // Number + competency text
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView numberView = new TextView(activity);
        numberView.setText(number + ".");
        numberView.setTextColor(Color.parseColor("#0038A8"));
        numberView.setTextSize(13);
        numberView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams numberLp = new LinearLayout.LayoutParams(
                ui.dp(28), ViewGroup.LayoutParams.WRAP_CONTENT);
        numberView.setLayoutParams(numberLp);
        header.addView(numberView);

        TextView textView = new TextView(activity);
        textView.setText(competency != null ? competency : "");
        textView.setTextColor(Color.parseColor("#1E293B"));
        textView.setTextSize(13);
        textView.setLineSpacing(ui.dp(2), 1f);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(textView);
        card.addView(header);

        // Radio buttons
        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.HORIZONTAL);
        LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupLp.topMargin = ui.dp(6);
        groupLp.leftMargin = 0;
        group.setLayoutParams(groupLp);

        final String[] statuses = {
                EcdcResponseEntity.STATUS_PRESENT,
                EcdcResponseEntity.STATUS_NOT_PRESENT,
                EcdcResponseEntity.STATUS_NOT_TESTED
        };
        final String[] labels = {"Present", "Not present", "Not tested"};
        final String[] colors = {COLOR_PRESENT, COLOR_NOT_PRESENT, COLOR_NOT_TESTED};
        final int[] ids = new int[statuses.length];

        for (int i = 0; i < statuses.length; i++) {
            RadioButton rb = new RadioButton(activity);
            ids[i] = View.generateViewId();
            rb.setId(ids[i]);
            rb.setText(labels[i]);
            rb.setTextSize(11);
            rb.setMaxLines(1);
            rb.setTextColor(Color.parseColor("#334155"));
            rb.setGravity(Gravity.CENTER_VERTICAL);
            rb.setButtonTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{Color.parseColor(colors[i]), Color.parseColor(COLOR_UNCHECKED)}));
            rb.setLayoutParams(new RadioGroup.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            group.addView(rb);
        }

        // Preselect BEFORE attaching the listener so restoring saved state isn't
        // reported as a user edit.
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equals(status)) {
                group.check(ids[i]);
                break;
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    onStatusChanged.accept(statuses[i]);
                    return;
                }
            }
        });

        card.addView(group);
        return card;
    }

    /** Filled blue when there are unsaved changes, muted grey when there's nothing to save. */
    public void styleSaveButton(TextView button, boolean hasChanges) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.dp(14));
        bg.setColor(Color.parseColor(hasChanges ? "#0038A8" : "#CBD5E1"));
        button.setBackground(bg);
        button.setTextColor(Color.WHITE);
        button.setEnabled(hasChanges);
        button.setAlpha(hasChanges ? 1f : 0.85f);
    }
}