package com.example.omrscanner.ui;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.omrscanner.R;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScanDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID    = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";
    public static final String EXTRA_SCAN_INDEX  = "scan_index";

    private static final String PREFS_NAME  = "omr_dashboard";
    private static final String KEY_CLASSES = "class_folders";

    // ── Design tokens (match XML palette) ─────────────────────
    private static final String COLOR_BLUE       = "#0038A8"; // primary accent
    private static final String COLOR_BLUE_LIGHT = "#EFF6FF"; // stripe / pill bg
    private static final String COLOR_BLUE_MID   = "#2563EB"; // badge text
    private static final String COLOR_DARK       = "#1E293B"; // answer letter
    private static final String COLOR_MUTED      = "#64748B"; // labels
    private static final String COLOR_SUBTLE     = "#94A3B8"; // col headers
    private static final String COLOR_DIVIDER    = "#E2E8F0"; // center divider
    private static final String COLOR_CHUNK_DIV  = "#EFF6FF"; // between chunks
    private static final String COLOR_STRIPE_ODD = "#F8FAFC"; // zebra odd row
    private static final String COLOR_WHITE      = "#FFFFFF"; // zebra even row
    private static final String COLOR_RED        = "#F43F5E"; // cancel / error

    // ── State ──────────────────────────────────────────────────
    private String classId, activityId;
    private int scanIndex;

    private List<ClassFolder> classFolders;
    private ClassFolder    currentClass;
    private ActivityFolder currentActivity;
    private ScanEntry      currentScan;

    // ── Views ──────────────────────────────────────────────────
    private ImageView         scanImage;
    private TextView          imgPlaceholder;
    private EditText          etLrn;
    private TextView          tvScore, tvDate, tvAnswerCount;
    private LinearLayout      answersContainer;
    private TextView          btnEditToggle, topBarTitle;
    private MaterialButton      btnSaveChanges;
    private ImageButton       btnBack;

    private boolean isEditing = false;
    private Map<Integer, String> editedAnswers = new LinkedHashMap<>();
    private Gson gson = new Gson();

    // ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_detail);

        // Match the gradient header status bar
        getWindow().setStatusBarColor(Color.parseColor("#0038A8"));

        initViews();

        if (getIntent() != null) {
            classId    = getIntent().getStringExtra(EXTRA_CLASS_ID);
            activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
            scanIndex  = getIntent().getIntExtra(EXTRA_SCAN_INDEX, -1);
        }

        loadData();
    }

    private void initViews() {
        scanImage        = findViewById(R.id.scanImage);
        imgPlaceholder   = findViewById(R.id.imgPlaceholder);
        etLrn            = findViewById(R.id.etLrn);
        tvScore          = findViewById(R.id.tvScore);
        tvDate           = findViewById(R.id.tvDate);
        tvAnswerCount    = findViewById(R.id.tvAnswerCount);
        answersContainer = findViewById(R.id.answersContainer);
        btnEditToggle    = findViewById(R.id.btnEditToggle);
        topBarTitle      = findViewById(R.id.topBarTitle);
        btnSaveChanges   = findViewById(R.id.btnSaveChanges);
        btnBack          = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnEditToggle.setOnClickListener(v -> toggleEditMode());
        btnSaveChanges.setOnClickListener(v -> saveChanges());
    }

    // ──────────────────────────────────────────────────────────
    // DATA
    // ──────────────────────────────────────────────────────────
    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_CLASSES, "[]");
        Type type   = new TypeToken<List<ClassFolder>>(){}.getType();
        classFolders = gson.fromJson(json, type);

        if (classFolders == null) { showError("Failed to load data"); return; }

        for (ClassFolder cls : classFolders) {
            if (cls.getId().equals(classId)) { currentClass = cls; break; }
        }
        if (currentClass == null) { showError("Class not found"); return; }

        for (ActivityFolder act : currentClass.getActivities()) {
            if (act.getId().equals(activityId)) { currentActivity = act; break; }
        }
        if (currentActivity == null) { showError("Activity not found"); return; }

        if (scanIndex < 0 || scanIndex >= currentActivity.getScans().size()) {
            showError("Scan not found"); return;
        }

        currentScan = currentActivity.getScans().get(scanIndex);

        editedAnswers.clear();
        if (currentScan.getAnswers() != null) editedAnswers.putAll(currentScan.getAnswers());

        populateUI();
    }

    private void populateUI() {
        topBarTitle.setText("Scan #" + (scanIndex + 1));

        // Image — prefer overlay (highlighted bubbles), fall back to raw
        String imgPath = currentScan.getOverlayImagePath();
        if (imgPath == null || !(new File(imgPath).exists())) imgPath = currentScan.getImagePath();
        if (imgPath != null && new File(imgPath).exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(imgPath);
            scanImage.setImageBitmap(bmp);
            imgPlaceholder.setVisibility(View.GONE);
        } else {
            imgPlaceholder.setVisibility(View.VISIBLE);
            imgPlaceholder.setText(imgPath != null ? "Image not found on device" : "No Image Available");
        }

        etLrn.setText(currentScan.getLrn());
        // Detected answers count (not a score — there is no answer key)
        tvScore.setText(currentScan.getScore() + "/" + currentScan.getNumItems());
        tvDate.setText(currentScan.getFormattedDate());

        renderAnswers();
    }

    // ──────────────────────────────────────────────────────────
    // RENDER DISPATCHER
    // ──────────────────────────────────────────────────────────
    private void renderAnswers() {
        answersContainer.removeAllViews();
        int numItems = currentScan.getNumItems();

        // Update item-count badge in the card header
        if (tvAnswerCount != null) {
            tvAnswerCount.setText(numItems + " items");
        }

        if (isEditing) {
            renderEditList(numItems);
        } else {
            renderViewGrid(numItems);
        }
    }

    // ══════════════════════════════════════════════════════════
    // VIEW MODE — 2-column grid, 10 items per column
    // Matches XML: left col (items N to N+9) | 1dp divider |
    //              right col (items N+10 to N+19), chunks of 20
    // ══════════════════════════════════════════════════════════
    private void renderViewGrid(int numItems) {
        Map<Integer, String> answers = currentScan.getAnswers();
        if (answers == null) answers = new LinkedHashMap<>();

        int chunkSize  = 20;   // items per horizontal chunk (10 left + 10 right)
        int colSize    = 10;   // rows per column
        int totalChunks = (int) Math.ceil((double) numItems / chunkSize);

        for (int chunk = 0; chunk < totalChunks; chunk++) {
            int startItem = chunk * chunkSize + 1;           // e.g. 1, 21, 41…
            int midItem   = startItem + colSize;              // e.g. 11, 31, 51…
            int endItem   = Math.min(startItem + chunkSize - 1, numItems); // e.g. 20, 40…

            // ── Horizontal chunk wrapper ───────────────────────
            LinearLayout chunkRow = new LinearLayout(this);
            chunkRow.setOrientation(LinearLayout.HORIZONTAL);
            chunkRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // ── Left column (items startItem → midItem-1) ──────
            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            leftCol.setLayoutParams(colParams);

            for (int i = startItem; i < midItem && i <= numItems; i++) {
                String ans = answers.getOrDefault(i, "");
                leftCol.addView(createViewRow(i, ans, false));
            }
            chunkRow.addView(leftCol);

            // ── Center 1dp divider ─────────────────────────────
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor(COLOR_DIVIDER));
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
            chunkRow.addView(divider);

            // ── Right column (items midItem → endItem) ─────────
            LinearLayout rightCol = new LinearLayout(this);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            for (int i = midItem; i <= endItem; i++) {
                String ans = answers.getOrDefault(i, "");
                rightCol.addView(createViewRow(i, ans, true));
            }
            chunkRow.addView(rightCol);

            answersContainer.addView(chunkRow);

            // ── Chunk divider (between chunks, not after last) ──
            if (chunk < totalChunks - 1) {
                View chunkDivider = new View(this);
                chunkDivider.setBackgroundColor(Color.parseColor(COLOR_CHUNK_DIV));
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dp.topMargin    = dp(4);
                dp.bottomMargin = dp(4);
                chunkDivider.setLayoutParams(dp);
                answersContainer.addView(chunkDivider);
            }
        }
    }

    /**
     * Creates one answer row for view mode.
     *
     * Layout: [number 36dp] [answer letter]
     * Zebra:  odd rows = #F8FAFC, even = #FFFFFF (based on item number)
     * isRightCol: adds paddingStart=8dp to align with right column header
     */
    private View createViewRow(int itemNum, String answer, boolean isRightCol) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                isRightCol ? dp(8) : dp(4),  // start
                0, dp(4), 0);                // top, end, bottom

        // Zebra stripe based on item number (1-indexed, odd=stripe)
        boolean isOdd = (itemNum % 2 != 0);
        row.setBackgroundColor(Color.parseColor(isOdd ? COLOR_STRIPE_ODD : COLOR_WHITE));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        row.setLayoutParams(rowParams);

        // Item number — 36dp wide, #0038A8 bold 11sp
        TextView tvNum = new TextView(this);
        tvNum.setText(String.valueOf(itemNum));
        tvNum.setTextColor(Color.parseColor(COLOR_BLUE));
        tvNum.setTextSize(11);
        tvNum.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_medium), Typeface.BOLD);
        LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(dp(36),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tvNum.setLayoutParams(numParams);
        row.addView(tvNum);

        // Answer letter — #1E293B bold 14sp (dash if empty)
        TextView tvAns = new TextView(this);
        tvAns.setText(answer.isEmpty() ? "—" : answer);
        tvAns.setTextColor(Color.parseColor(answer.isEmpty() ? COLOR_MUTED : COLOR_DARK));
        tvAns.setTextSize(14);
        tvAns.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_black), Typeface.BOLD);
        row.addView(tvAns);

        return row;
    }

    // ══════════════════════════════════════════════════════════
    // EDIT MODE — vertical list, one row per item
    // Each row: Q label | A B C D Blank buttons (right-aligned)
    // Matches the new blue/white palette
    // ══════════════════════════════════════════════════════════
    private void renderEditList(int numItems) {
        for (int i = 1; i <= numItems; i++) {
            answersContainer.addView(createEditRow(i));
        }
    }

    private View createEditRow(int qNum) {
        // Outer wrapper (row + divider)
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Zebra stripe
        boolean isOdd = (qNum % 2 != 0);
        wrapper.setBackgroundColor(Color.parseColor(isOdd ? COLOR_STRIPE_ODD : COLOR_WHITE));

        // Row content
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));

        // Q# label — #0038A8 bold 12sp, 40dp wide
        TextView tvQ = new TextView(this);
        tvQ.setText(String.format("#%d", qNum));
        tvQ.setTextColor(Color.parseColor(COLOR_BLUE));
        tvQ.setTextSize(12);
        tvQ.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_medium), Typeface.BOLD);
        LinearLayout.LayoutParams qParams = new LinearLayout.LayoutParams(dp(40),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tvQ.setLayoutParams(qParams);
        row.addView(tvQ);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(spacer);

        // Option buttons: A B C D Blank
        String currentVal = editedAnswers.getOrDefault(qNum, "");
        String[] options = {"A", "B", "C", "D", ""};
        String[] labels  = {"A", "B", "C", "D", "—"};

        for (int j = 0; j < options.length; j++) {
            final String optVal = options[j];
            boolean selected = optVal.equals(currentVal);

            TextView btn = new TextView(this);
            btn.setText(labels[j]);
            btn.setGravity(Gravity.CENTER);
            btn.setTextSize(12);
            btn.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_bold));

            int btnW = labels[j].equals("—") ? dp(44) : dp(36);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(btnW, dp(36));
            if (j > 0) btnParams.leftMargin = dp(6);
            btn.setLayoutParams(btnParams);

            if (selected) {
                // Selected: blue bg, white text, rounded
                btn.setBackgroundColor(Color.parseColor(COLOR_BLUE));
                btn.setTextColor(Color.WHITE);
            } else {
                // Unselected: light blue bg, blue text
                btn.setBackgroundColor(Color.parseColor(COLOR_BLUE_LIGHT));
                btn.setTextColor(Color.parseColor(COLOR_BLUE_MID));
            }

            // Round corners programmatically via GradientDrawable
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(8));
            bg.setColor(Color.parseColor(selected ? COLOR_BLUE : COLOR_BLUE_LIGHT));
            btn.setBackground(bg);
            btn.setTextColor(Color.parseColor(selected ? "#FFFFFF" : COLOR_BLUE_MID));

            btn.setOnClickListener(v -> {
                editedAnswers.put(qNum, optVal);
                renderAnswers();
            });

            row.addView(btn);
        }

        wrapper.addView(row);

        // Bottom hairline divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor(COLOR_DIVIDER));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        wrapper.addView(divider);

        return wrapper;
    }

    // ──────────────────────────────────────────────────────────
    // EDIT / SAVE LOGIC
    // ──────────────────────────────────────────────────────────
    private void toggleEditMode() {
        isEditing = !isEditing;

        if (isEditing) {
            // Entering edit mode — reset buffer from saved state
            editedAnswers.clear();
            if (currentScan.getAnswers() != null) editedAnswers.putAll(currentScan.getAnswers());

            // EDIT button becomes CANCEL (red text, white bg — matches XML pill style)
            btnEditToggle.setText("CANCEL");
            btnEditToggle.setTextColor(Color.parseColor(COLOR_RED));
        } else {
            // Cancelling edit mode
            btnEditToggle.setText("EDIT");
            btnEditToggle.setTextColor(Color.parseColor(COLOR_BLUE));
        }

        etLrn.setEnabled(isEditing);
        // Subtle underline when editing, transparent when viewing
        etLrn.setBackgroundColor(isEditing
                ? Color.parseColor(COLOR_BLUE_LIGHT)
                : Color.TRANSPARENT);
        if (isEditing) etLrn.requestFocus();

        btnSaveChanges.setVisibility(isEditing ? View.VISIBLE : View.GONE);

        renderAnswers();
    }

    private void saveChanges() {
        // 1. Update LRN
        currentScan.setLrn(etLrn.getText().toString().trim());

        // 2. Commit edited answers
        currentScan.setAnswers(new LinkedHashMap<>(editedAnswers));

        // 3. Recalculate detected answer count
        currentScan.setScore(currentScan.getAnsweredCount());

        // 4. Update list in memory
        currentActivity.getScans().set(scanIndex, currentScan);

        // 5. Persist
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_CLASSES, gson.toJson(classFolders)).apply();

        Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();

        // Exit edit mode
        isEditing = false;
        btnEditToggle.setText("EDIT");
        btnEditToggle.setTextColor(Color.parseColor(COLOR_BLUE));
        etLrn.setEnabled(false);
        etLrn.setBackgroundColor(Color.TRANSPARENT);
        btnSaveChanges.setVisibility(View.GONE);

        // Refresh detected count display + grid
        tvScore.setText(currentScan.getScore() + "/" + currentScan.getNumItems());
        renderAnswers();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}