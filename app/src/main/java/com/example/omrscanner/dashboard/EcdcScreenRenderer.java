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

import com.example.omrscanner.database.entities.EcdcDomainEntity;
import com.example.omrscanner.database.entities.EcdcResponseEntity;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Renders the pieces of the ECDC student checklist screen: one card per
 * competency with Present / Not present / Not tested radio buttons.
 */
public class EcdcScreenRenderer {

    // Every selected radio button is blue, whichever option it is.
    private static final String COLOR_CHECKED = "#0038A8";
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

    /**
     * {fill color, dark text color (unselected pill), text color on the fill (selected pill)}
     * for a domain, matched on its name. Unknown domains fall back to the app blue.
     */
    private static String[] domainColors(String serverName) {
        String n = serverName == null ? "" : serverName.toUpperCase(Locale.ROOT);
        if (n.contains("GROSS"))      return new String[]{"#DC2626", "#991B1B", "#FFFFFF"}; // red
        if (n.contains("FINE"))       return new String[]{"#EA580C", "#9A3412", "#FFFFFF"}; // orange
        if (n.contains("SELF"))       return new String[]{"#EAB308", "#854D0E", "#422006"}; // yellow
        if (n.contains("RECEPTIVE"))  return new String[]{"#16A34A", "#166534", "#FFFFFF"}; // green
        if (n.contains("EXPRESSIVE")) return new String[]{"#2563EB", "#1E40AF", "#FFFFFF"}; // blue
        if (n.contains("COGNITIVE"))  return new String[]{"#0891B2", "#155E75", "#FFFFFF"}; // cyan
        if (n.contains("SOCIO"))      return new String[]{"#8B5CF6", "#5B21B6", "#FFFFFF"}; // purple
        return new String[]{"#0038A8", "#0038A8", "#FFFFFF"};
    }

    /**
     * The row of domain pills, each in its own domain color: solid when selected,
     * a light tint with a colored outline when not.
     */
    public void buildDomainPills(LinearLayout container, List<EcdcDomainEntity> domains,
                                 Integer selectedDomainId, Consumer<Integer> onSelected) {
        container.removeAllViews();

        for (EcdcDomainEntity d : domains) {
            final int domainId = d.id;
            boolean isActive = selectedDomainId != null && selectedDomainId == domainId;
            String[] c = domainColors(d.domain);
            int main = Color.parseColor(c[0]);

            TextView btn = new TextView(activity);
            btn.setText(shortDomainName(d.domain));
            btn.setTextSize(11);
            btn.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(16));
            if (isActive) {
                bg.setColor(main);
                btn.setTextColor(Color.parseColor(c[2]));
            } else {
                bg.setColor((main & 0x00FFFFFF) | 0x26000000); // ~15% tint of the domain color
                bg.setStroke(ui.dp(1), main);
                btn.setTextColor(Color.parseColor(c[1]));
            }
            btn.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = ui.dp(8);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> onSelected.accept(domainId));
            container.addView(btn);
        }
    }

    /** {@code color} blended over white; {@code amount} is how much of the color to keep (0..1). */
    private static int mixWithWhite(int color, float amount) {
        int r = Math.round(255 - (255 - Color.red(color)) * amount);
        int g = Math.round(255 - (255 - Color.green(color)) * amount);
        int b = Math.round(255 - (255 - Color.blue(color)) * amount);
        return Color.rgb(r, g, b);
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
     * @param domainName      the server's domain name; picks the card's accent color
     * @param status          one of EcdcResponseEntity.STATUS_*, or null if not marked yet
     * @param onStatusChanged called with the newly chosen STATUS_* (never fires for the
     *                        initial preselection)
     */
    public View createCompetencyRow(int number, String competency, String domainName,
                                    String status, Consumer<String> onStatusChanged) {

        String[] domainColor = domainColors(domainName);
        int accent = Color.parseColor(domainColor[0]);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(10), ui.dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mixWithWhite(accent, 0.08f)); // very light wash of the domain color
        bg.setCornerRadius(ui.dp(16));
        bg.setStroke(ui.dp(1), accent);
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
        numberView.setTextColor(Color.parseColor(domainColor[1]));
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
        final int[] ids = new int[statuses.length];

        for (int i = 0; i < statuses.length; i++) {
            // A RadioGroup normally can't be un-checked by the user; tapping the
            // already-selected button clears the group instead.
            RadioButton rb = new RadioButton(activity) {
                @Override
                public void toggle() {
                    if (isChecked()) {
                        if (getParent() instanceof RadioGroup) {
                            ((RadioGroup) getParent()).clearCheck();
                        }
                    } else {
                        super.toggle();
                    }
                }
            };
            ids[i] = View.generateViewId();
            rb.setId(ids[i]);
            rb.setText(labels[i]);
            rb.setTextSize(10);
            rb.setTextColor(Color.parseColor("#334155"));
            rb.setGravity(Gravity.CENTER_VERTICAL);
            rb.setButtonTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{Color.parseColor(COLOR_CHECKED), Color.parseColor(COLOR_UNCHECKED)}));
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
            if (checkedId == View.NO_ID) {
                onStatusChanged.accept(null); // un-selected
                return;
            }
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