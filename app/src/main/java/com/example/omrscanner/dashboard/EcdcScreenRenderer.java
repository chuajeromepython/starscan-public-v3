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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import java.util.function.BiConsumer;

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

    /** The domain's main theme color (same one used for its pill and card accent). */
    public static int domainThemeColor(String serverName) {
        return Color.parseColor(domainColors(serverName)[0]);
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
     * One competency: its number + text on top, three radio buttons below
     * (Present / Not present / Not tested). Choosing Present adds a "Type" row inside the
     * card that behaves like a Help & FAQ item: tap it to expand or collapse the P / O / R
     * choices, and the chevron rotates.
     *
     * @param domainName  the server's domain name; picks the card's accent color
     * @param status      one of EcdcResponseEntity.STATUS_*, or null if not marked yet
     * @param presentType "P", "O", "R" or null (only meaningful when status is PRESENT)
     * @param onChanged   called with the new (status, presentType) after every user edit.
     *                    presentType is always null unless status is PRESENT. Never fires
     *                    for the initial preselection.
     */
    public View createCompetencyRow(int number, String competency, String domainName,
                                    String status, String presentType,
                                    BiConsumer<String, String> onChanged) {

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

        // Radio buttons (original layout)
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

        final boolean isPresent = EcdcResponseEntity.STATUS_PRESENT.equals(status);
        // The type currently held for this mark; always null unless the mark is Present.
        final String[] currentType = {isPresent ? presentType : null};

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

        // ── "Type" accordion: header row (title + chevron) and the P / O / R choices ──
        final String[] typeValues = {
                EcdcResponseEntity.PRESENT_TYPE_P,
                EcdcResponseEntity.PRESENT_TYPE_O,
                EcdcResponseEntity.PRESENT_TYPE_R
        };
        final int[] typeIds = new int[typeValues.length];
        // Open when there's still a letter to choose; collapsed once one is saved.
        final boolean[] expanded = {isPresent && currentType[0] == null};

        final LinearLayout typeSection = new LinearLayout(activity);
        typeSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams typeSectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typeSectionLp.topMargin = ui.dp(4);
        typeSection.setLayoutParams(typeSectionLp);

        // thin divider between the radio buttons and the accordion
        View divider = new View(activity);
        divider.setBackgroundColor(mixWithWhite(accent, 0.25f));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
        dividerLp.rightMargin = ui.dp(4);
        divider.setLayoutParams(dividerLp);
        typeSection.addView(divider);

        // Header row: "Type  •  P" on the left, chevron on the right (same as the FAQ cards).
        final LinearLayout typeHeader = new LinearLayout(activity);
        typeHeader.setOrientation(LinearLayout.HORIZONTAL);
        typeHeader.setGravity(Gravity.CENTER_VERTICAL);
        typeHeader.setPadding(0, ui.dp(6), ui.dp(4), ui.dp(4));
        typeHeader.setClickable(true);
        typeHeader.setFocusable(true);

        final TextView typeTitle = new TextView(activity);
        typeTitle.setTextSize(12);
        typeTitle.setTypeface(null, Typeface.BOLD);
        typeTitle.setTextColor(Color.parseColor("#1E293B"));
        typeTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        typeHeader.addView(typeTitle);

        final android.widget.ImageView chevron = new android.widget.ImageView(activity);
        chevron.setImageResource(com.example.omrscanner.R.drawable.ic_chevron_right);
        chevron.setColorFilter(Color.parseColor("#94A3B8"));
        chevron.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        typeHeader.addView(chevron);
        typeSection.addView(typeHeader);

        // The P / O / R choices. A separate RadioGroup (not nested in the main one) so the
        // two selections stay independent. Plain RadioButtons: tapping the chosen letter
        // keeps it selected.
        final RadioGroup typeGroup = new RadioGroup(activity);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        typeGroup.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int j = 0; j < typeValues.length; j++) {
            // Tapping the chosen letter again un-selects it, like the main buttons.
            RadioButton tb = new RadioButton(activity) {
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
            typeIds[j] = View.generateViewId();
            tb.setId(typeIds[j]);
            tb.setText(typeValues[j]);
            tb.setTextSize(12);
            tb.setMinHeight(ui.dp(36));      // between "as tall as the default" and "squeezed"
            tb.setMinimumHeight(ui.dp(36));
            tb.setPadding(ui.dp(4), 0, 0, 0);
            tb.setTypeface(null, Typeface.BOLD);
            tb.setTextColor(Color.parseColor("#334155"));
            tb.setGravity(Gravity.CENTER_VERTICAL);
            tb.setButtonTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{Color.parseColor(COLOR_CHECKED), Color.parseColor(COLOR_UNCHECKED)}));
            tb.setLayoutParams(new RadioGroup.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            typeGroup.addView(tb);
        }
        typeSection.addView(typeGroup);

        // Applies the open/closed state: choices shown or hidden, chevron rotated, title updated.
        final Runnable refreshType = () -> {
            typeGroup.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            chevron.setRotation(expanded[0] ? 90f : 0f);
            typeTitle.setText(currentType[0] != null
                    ? "Type  \u2022  " + currentType[0] : "Type");
        };

        // Tap the header to expand / collapse, like a FAQ item.
        typeHeader.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            refreshType.run();
        });

        // Preselect BEFORE attaching the listeners so restoring saved state isn't
        // reported as a user edit.
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equals(status)) {
                group.check(ids[i]);
                break;
            }
        }
        int savedTypeIndex = typeIndex(typeValues, currentType[0]);
        if (savedTypeIndex >= 0) typeGroup.check(typeIds[savedTypeIndex]);
        typeSection.setVisibility(isPresent ? View.VISIBLE : View.GONE);
        refreshType.run();

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String newStatus = null; // stays null when the mark was un-selected
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    newStatus = statuses[i];
                    break;
                }
            }

            if (EcdcResponseEntity.STATUS_PRESENT.equals(newStatus)) {
                // No default: the teacher picks P / O / R. A type chosen earlier is kept.
                int idx = typeIndex(typeValues, currentType[0]);
                if (idx >= 0) typeGroup.check(typeIds[idx]);
                typeSection.setVisibility(View.VISIBLE);
                expanded[0] = true; // choosing Present opens it, like tapping a FAQ item
            } else {
                currentType[0] = null; // never keep a stale type on a non-Present mark
                typeGroup.clearCheck();
                typeSection.setVisibility(View.GONE);
                expanded[0] = false;
            }
            refreshType.run();
            onChanged.accept(newStatus, currentType[0]);
        });

        typeGroup.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == View.NO_ID) {
                // Cleared in code (the type is already null): nothing to do.
                if (currentType[0] == null) return;
                // The teacher tapped the selected letter again: un-select it.
                currentType[0] = null;
                refreshType.run(); // title goes back to just "Type"
                onChanged.accept(EcdcResponseEntity.STATUS_PRESENT, null);
                return;
            }
            for (int j = 0; j < typeIds.length; j++) {
                if (typeIds[j] == checkedId) {
                    // The listener above sets currentType first, so its own check() is a no-op here.
                    if (typeValues[j].equals(currentType[0])) return;
                    currentType[0] = typeValues[j];
                    refreshType.run(); // updates the "Type  •  O" title
                    onChanged.accept(EcdcResponseEntity.STATUS_PRESENT, currentType[0]);
                    return;
                }
            }
        });

        card.addView(group);
        card.addView(typeSection);
        return card;
    }

    /** Index of {@code value} in {@code values}, or -1 if it's null or not found. */
    private static int typeIndex(String[] values, String value) {
        if (value == null) return -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return -1;
    }


    private View createStatBox(String label, int count, String hex) {
        int c = Color.parseColor(hex);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.dp(12));
        bg.setColor(mixWithWhite(c, 0.12f));
        box.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = ui.dp(6);
        box.setLayoutParams(lp);

        TextView number = new TextView(activity);
        number.setText(String.valueOf(count));
        number.setTextSize(18);
        number.setTypeface(null, Typeface.BOLD);
        number.setTextColor(c);
        number.setGravity(Gravity.CENTER);
        box.addView(number);

        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextSize(9);
        text.setTextColor(Color.parseColor("#475569"));
        text.setGravity(Gravity.CENTER);
        box.addView(text);

        return box;
    }

    /** Summary card for the eye button. statuses: competency id -> STATUS_*; missing id = unmarked. */
    public View createSummaryCard(String studentName, String subtitle,
                                  List<EcdcDomainEntity> domains,
                                  List<com.example.omrscanner.database.entities.EcdcCompetencyEntity> competencies,
                                  java.util.Map<Integer, String> statuses) {

        // {present, notPresent, notTested, unmarked, total}
        int[] overall = new int[5];
        java.util.Map<Integer, int[]> perDomain = new java.util.LinkedHashMap<>();
        for (EcdcDomainEntity d : domains) perDomain.put(d.id, new int[5]);

        for (com.example.omrscanner.database.entities.EcdcCompetencyEntity c : competencies) {
            String s = statuses.get(c.id);
            int idx;
            if (EcdcResponseEntity.STATUS_PRESENT.equals(s)) idx = 0;
            else if (EcdcResponseEntity.STATUS_NOT_PRESENT.equals(s)) idx = 1;
            else if (EcdcResponseEntity.STATUS_NOT_TESTED.equals(s)) idx = 2;
            else idx = 3;
            overall[idx]++;
            overall[4]++;
            int[] row = perDomain.get(c.domainId);
            if (row != null) {
                row[idx]++;
                row[4]++;
            }
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(18), ui.dp(18), ui.dp(12), ui.dp(8));

        TextView name = new TextView(activity);
        name.setText(studentName != null ? studentName : "");
        name.setTextSize(16);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(Color.parseColor("#1E293B"));
        root.addView(name);

        TextView sub = new TextView(activity);
        sub.setText(subtitle);
        sub.setTextSize(11);
        sub.setTextColor(Color.parseColor("#64748B"));
        root.addView(sub);

        int marked = overall[4] - overall[3];
        TextView progress = new TextView(activity);
        progress.setText(marked + " of " + overall[4] + " marked");
        progress.setTextSize(12);
        progress.setTypeface(null, Typeface.BOLD);
        progress.setTextColor(Color.parseColor(COLOR_CHECKED));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pLp.topMargin = ui.dp(10);
        progress.setLayoutParams(pLp);
        root.addView(progress);

        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = ui.dp(8);
        stats.setLayoutParams(sLp);
        stats.addView(createStatBox("Present", overall[0], "#16A34A"));
        stats.addView(createStatBox("Not present", overall[1], "#DC2626"));
        stats.addView(createStatBox("Not tested", overall[2], "#EA580C"));
        stats.addView(createStatBox("Unmarked", overall[3], "#64748B"));
        root.addView(stats);

        TextView byDomain = new TextView(activity);
        byDomain.setText("By domain");
        byDomain.setTextSize(10);
        byDomain.setTypeface(null, Typeface.BOLD);
        byDomain.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams bdLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bdLp.topMargin = ui.dp(14);
        bdLp.bottomMargin = ui.dp(6);
        byDomain.setLayoutParams(bdLp);
        root.addView(byDomain);

        for (EcdcDomainEntity d : domains) {
            int[] r = perDomain.get(d.id);
            if (r == null || r[4] == 0) continue;
            int accent = Color.parseColor(domainColors(d.domain)[0]);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(ui.dp(12), ui.dp(8), ui.dp(10), ui.dp(8));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(ui.dp(12));
            rowBg.setColor(mixWithWhite(accent, 0.08f));
            rowBg.setStroke(ui.dp(1), accent);
            row.setBackground(rowBg);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = ui.dp(6);
            rowLp.rightMargin = ui.dp(6);
            row.setLayoutParams(rowLp);

            TextView title = new TextView(activity);
            title.setText(shortDomainName(d.domain) + "  \u2022  " + (r[4] - r[3]) + " of " + r[4] + " marked");
            title.setTextSize(12);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(Color.parseColor(domainColors(d.domain)[1]));
            row.addView(title);

            TextView counts = new TextView(activity);
            counts.setText("Present " + r[0] + "   Not present " + r[1]
                    + "   Not tested " + r[2] + "   Unmarked " + r[3]);
            counts.setTextSize(10);
            counts.setTextColor(Color.parseColor("#334155"));
            row.addView(counts);

            root.addView(row);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(root);
        return scroll;
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