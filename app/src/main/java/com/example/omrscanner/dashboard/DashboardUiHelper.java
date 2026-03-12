package com.example.omrscanner.dashboard;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Stateless UI builder helpers shared across all Dashboard renderers and dialogs.
 * Every method mirrors what was previously a private method in DashboardActivity.
 */
public class DashboardUiHelper {

    private final AppCompatActivity activity;

    public DashboardUiHelper(AppCompatActivity activity) {
        this.activity = activity;
    }

    // ─────────────────────────────────────────────────────────────
    // dp / toast / error
    // ─────────────────────────────────────────────────────────────

    public int dp(int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }

    public void showToast(String msg) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────
    // Sheet / dialog building
    // ─────────────────────────────────────────────────────────────

    /** Standard bottom-sheet root layout. */
    public LinearLayout buildSheet() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(bg);
        return root;
    }

    public TextView buildSheetTitle(String text, String hexColor, int gravity, int bottomMarginDp) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor(hexColor));
        tv.setGravity(gravity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(bottomMarginDp);
        tv.setLayoutParams(lp);
        return tv;
    }

    public LinearLayout buildActionsRow(int topMarginPx) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMarginPx;
        row.setLayoutParams(lp);
        return row;
    }

    public View spacer(int widthPx) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 0));
        return v;
    }

    public View createDialogHandle() {
        View handle = new View(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(20);
        handle.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E2E8F0"));
        bg.setCornerRadius(dp(2));
        handle.setBackground(bg);
        return handle;
    }

    public TextView createFieldLabel(String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(Color.parseColor("#64748B"));
        label.setTypeface(null, Typeface.BOLD);
        label.setAllCaps(true);
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        label.setLayoutParams(lp);
        return label;
    }

    public EditText createLightInput(String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setHintTextColor(Color.parseColor("#CBD5E1"));
        input.setTextColor(Color.parseColor("#1E293B"));
        input.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        input.setBackground(bg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        input.setLayoutParams(lp);
        return input;
    }

    public TextView createDialogButton(String text, boolean isPrimary) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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

    public void configureBottomDialog(Dialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow()
                    .getAttributes().windowAnimations =
                    com.google.android.material.R.style.Animation_Design_BottomSheetDialog;
        }
    }

    public View createMenuOption(String text, Runnable onClick, boolean isDestructive) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTextColor(isDestructive
                ? Color.parseColor("#CE1126")
                : Color.parseColor("#1E293B"));
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isDestructive
                ? Color.parseColor("#FEF2F2")
                : Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), isDestructive
                ? Color.parseColor("#FECACA")
                : Color.parseColor("#E2E8F0"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    public View createFilterChip(String label, boolean isActive, Runnable action) {
        TextView chip = new TextView(activity);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        chip.setClickable(true);
        chip.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (isActive) {
            bg.setColor(Color.parseColor("#0038A8"));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#F8FAFC"));
            bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
            chip.setTextColor(Color.parseColor("#64748B"));
        }
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    /** Card used in selection dialogs (upload class/assessment picker). */
    public View createSelectionCard(Dialog dialog, String emoji, String label,
            String desc, Runnable onClick) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView iconView = new TextView(activity);
        iconView.setText(emoji);
        iconView.setTextSize(28);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = dp(14);
        iconView.setLayoutParams(ilp);
        card.addView(iconView);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(activity);
        nameView.setText(label);
        nameView.setTextSize(15);
        nameView.setTextColor(Color.parseColor("#1E293B"));
        nameView.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameView);

        TextView descView = new TextView(activity);
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(2);
        descView.setLayoutParams(dlp);
        textCol.addView(descView);
        card.addView(textCol);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        card.addView(arrow);

        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    public TextView createDropdownField(String initialText) {
        TextView tv = new TextView(activity);
        tv.setText(initialText + "  ▾");
        tv.setTag(initialText);
        tv.setHint("Select…");
        tv.setHintTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(14);
        tv.setClickable(true);
        tv.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        tv.setBackground(bg);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        tv.setLayoutParams(lp);
        return tv;
    }

    public String[] buildSchoolYearOptions() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int currentYear = cal.get(java.util.Calendar.YEAR);
        int startYear = currentYear - 5;
        String[] options = new String[10];
        for (int i = 0; i < 10; i++) {
            int y = startYear + i;
            options[i] = y + "-" + (y + 1);
        }
        return options;
    }

    // ─────────────────────────────────────────────────────────────
    // Error dialog
    // ─────────────────────────────────────────────────────────────

    private Dialog activeErrorDialog = null;

    public void showErrorDialog(String title, String message) {
        if (activeErrorDialog != null && activeErrorDialog.isShowing())
            activeErrorDialog.dismiss();

        Dialog errorDialog = new Dialog(activity);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setCancelable(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable dBg = new GradientDrawable();
        dBg.setColor(Color.WHITE);
        dBg.setCornerRadius(dp(24));
        root.setBackground(dBg);

        TextView iconView = new TextView(activity);
        iconView.setText("⚠️");
        iconView.setTextSize(32);
        iconView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(60), dp(60));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        ilp.bottomMargin = dp(16);
        iconView.setLayoutParams(ilp);
        root.addView(iconView);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#CE1126"));
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(8);
        titleView.setLayoutParams(tlp);
        root.addView(titleView);

        TextView msgView = new TextView(activity);
        msgView.setText(message);
        msgView.setTextSize(13);
        msgView.setTextColor(Color.parseColor("#64748B"));
        msgView.setGravity(Gravity.CENTER);
        msgView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.bottomMargin = dp(24);
        msgView.setLayoutParams(mlp);
        root.addView(msgView);

        TextView btnDismiss = new TextView(activity);
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
        btnDismiss.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnDismiss.setOnClickListener(v -> errorDialog.dismiss());
        root.addView(btnDismiss);

        errorDialog.setContentView(root);
        if (errorDialog.getWindow() != null) {
            errorDialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            errorDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            errorDialog.getWindow().setGravity(Gravity.CENTER);
            errorDialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        activeErrorDialog = errorDialog;
        errorDialog.setOnDismissListener(d -> {
            if (activeErrorDialog == errorDialog)
                activeErrorDialog = null;
        });
        errorDialog.show();
    }
}
