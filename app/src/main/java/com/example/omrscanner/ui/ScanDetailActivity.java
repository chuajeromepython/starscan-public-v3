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
import android.widget.GridLayout;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.omrscanner.R;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScanDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID = "class_id";
    public static final String EXTRA_ACTIVITY_ID = "activity_id";
    public static final String EXTRA_SCAN_INDEX = "scan_index";

    private static final String PREFS_NAME = "omr_dashboard";
    private static final String KEY_CLASSES = "class_folders";

    private String classId;
    private String activityId;
    private int scanIndex;

    private List<ClassFolder> classFolders;
    private ClassFolder currentClass;
    private ActivityFolder currentActivity;
    private ScanEntry currentScan;

    private ImageView scanImage;
    private TextView imgPlaceholder;
    private EditText etLrn;
    private TextView tvScore, tvDate;
    private LinearLayout answersContainer;
    private TextView btnEditToggle, topBarTitle;
    private FloatingActionButton fabSave;
    private ImageButton btnBack;

    private boolean isEditing = false;
    
    // For edit mode: store the newly selected answers here
    // Key: Question Number, Value: Answer (A, B, C, D, or "")
    private Map<Integer, String> editedAnswers = new LinkedHashMap<>();

    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_detail);

        // Dark status bar
        getWindow().setStatusBarColor(Color.parseColor("#0d1117"));

        initViews();
        
        if (getIntent() != null) {
            classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
            activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
            scanIndex = getIntent().getIntExtra(EXTRA_SCAN_INDEX, -1);
        }

        loadData();
    }

    private void initViews() {
        scanImage = findViewById(R.id.scanImage);
        imgPlaceholder = findViewById(R.id.imgPlaceholder);
        etLrn = findViewById(R.id.etLrn);
        tvScore = findViewById(R.id.tvScore);
        tvDate = findViewById(R.id.tvDate);
        answersContainer = findViewById(R.id.answersContainer);
        btnEditToggle = findViewById(R.id.btnEditToggle);
        topBarTitle = findViewById(R.id.topBarTitle);
        fabSave = findViewById(R.id.fabSave);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnEditToggle.setOnClickListener(v -> toggleEditMode());
        fabSave.setOnClickListener(v -> saveChanges());
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_CLASSES, "[]");
        Type type = new TypeToken<List<ClassFolder>>(){}.getType();
        classFolders = gson.fromJson(json, type);

        if (classFolders == null) {
            showError("Failed to load data");
            return;
        }

        // Find the specific scan entry
        currentClass = null;
        for (ClassFolder cls : classFolders) {
            if (cls.getId().equals(classId)) {
                currentClass = cls;
                break;
            }
        }

        if (currentClass == null) {
            showError("Class not found");
            return;
        }

        currentActivity = null;
        for (ActivityFolder act : currentClass.getActivities()) {
            if (act.getId().equals(activityId)) {
                currentActivity = act;
                break;
            }
        }

        if (currentActivity == null) {
            showError("Activity not found");
            return;
        }

        if (scanIndex < 0 || scanIndex >= currentActivity.getScans().size()) {
            showError("Scan not found");
            return;
        }

        currentScan = currentActivity.getScans().get(scanIndex);
        
        // Populate edit buffer
        editedAnswers.clear();
        if (currentScan.getAnswers() != null) {
            editedAnswers.putAll(currentScan.getAnswers());
        }

        populateUI();
    }

    private void populateUI() {
        topBarTitle.setText("Scan #" + (scanIndex + 1));
        
        // Image UI — prefer overlay image (with bubble highlights), fall back to raw
        String imgPath = currentScan.getOverlayImagePath();
        if (imgPath == null || !(new File(imgPath).exists())) {
            imgPath = currentScan.getImagePath();
        }
        if (imgPath != null) {
            File imgFile = new File(imgPath);
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                scanImage.setImageBitmap(myBitmap);
                imgPlaceholder.setVisibility(View.GONE);
            } else {
                imgPlaceholder.setVisibility(View.VISIBLE);
                imgPlaceholder.setText("Image not found on device");
            }
        } else {
            imgPlaceholder.setVisibility(View.VISIBLE);
        }

        etLrn.setText(currentScan.getLrn());
        tvScore.setText(currentScan.getScore() + "/" + currentScan.getNumItems());
        tvDate.setText(currentScan.getFormattedDate());

        renderAnswers();
    }

    private void renderAnswers() {
        answersContainer.removeAllViews();
        
        int numItems = currentScan.getNumItems();

        if (isEditing) {
            // Render EDIT LIST (Vertical)
            renderEditList(numItems);
        } else {
            // Render VIEW GRID (GridLayout)
            renderViewGrid(numItems);
        }
    }

    // =========================================================
    // VIEW MODE: GRID LAYOUT
    // =========================================================
    private void renderViewGrid(int numItems) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        
        // Ensure grid fills width
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        grid.setLayoutParams(params);

        Map<Integer, String> answers = currentScan.getAnswers();

        // Calculate item width based on screen width (roughly)
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int padding = dp(32); // parent padding
        int itemSize = (screenWidth - padding) / 5 - dp(8); // minus margins

        for (int i = 1; i <= numItems; i++) {
            String val = answers.get(i);
            if (val == null) val = "";
            
            View card = createViewGridItem(i, val, itemSize);
            grid.addView(card);
        }

        answersContainer.addView(grid);
    }

    private View createViewGridItem(int qNum, String ans, int size) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_grid_item));
        wrapper.setGravity(Gravity.CENTER);
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = size;
        params.height = (int) (size * 1.2); // slightly taller
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        wrapper.setLayoutParams(params);

        // Q#
        TextView tvQ = new TextView(this);
        tvQ.setText("Q" + qNum);
        tvQ.setTextColor(Color.parseColor("#8b949e"));
        tvQ.setTextSize(10);
        tvQ.setPadding(0, dp(8), 0, 0);
        wrapper.addView(tvQ);

        // Answer
        TextView tvAns = new TextView(this);
        tvAns.setText(ans.isEmpty() ? "-" : ans);
        tvAns.setTextSize(16);
        tvAns.setTypeface(Typeface.DEFAULT_BOLD);
        
        if (!ans.isEmpty()) {
            tvAns.setTextColor(Color.parseColor("#f0a500"));
        } else {
            tvAns.setTextColor(Color.parseColor("#30363d"));
        }
        
        // Push to center
        LinearLayout.LayoutParams ansParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ansParams.topMargin = dp(4);
        tvAns.setLayoutParams(ansParams);
        
        wrapper.addView(tvAns);

        return wrapper;
    }

    // =========================================================
    // EDIT MODE: LIST LAYOUT
    // =========================================================
    private void renderEditList(int numItems) {
        for (int i = 1; i <= numItems; i++) {
            answersContainer.addView(createEditRow(i));
        }
    }

    private View createEditRow(int qNum) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        
        // Q# Label
        TextView tvQ = new TextView(this);
        tvQ.setText("Q" + qNum);
        tvQ.setTextColor(Color.parseColor("#8b949e"));
        tvQ.setTextSize(14);
        tvQ.setWidth(dp(50));
        row.addView(tvQ);

        // Buttons Container
        LinearLayout btnGroup = new LinearLayout(this);
        btnGroup.setOrientation(LinearLayout.HORIZONTAL);
        btnGroup.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnGroup.setGravity(Gravity.END);

        String currentVal = editedAnswers.get(qNum);
        if (currentVal == null) currentVal = "";

        // Options: A, B, C, D, Blank
        String[] options = {"A", "B", "C", "D", ""};
        String[] labels =  {"A", "B", "C", "D", "Blank"};

        for (int j = 0; j < options.length; j++) {
            String optVal = options[j];
            String label = labels[j];
            boolean isSelected = optVal.equals(currentVal);

            View btn = createOptionButton(label, isSelected, v -> {
                editedAnswers.put(qNum, optVal);
                // Re-render this row effectively by updating styling? 
                // Or simplistic approach: re-render whole list (heavy but safe)
                // Better: Refresh just the UI, but for now let's just re-render to be safe.
                renderAnswers(); 
            });
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)); // Fixed height
            if (j > 0) params.leftMargin = dp(8);
            
            // "Blank" button should be wider or distinct?
            // User image depicts circular buttons for A-D and capsule for Blank?
            // Actually image 3 shows "Blank" is a capsule. A-D are circles.
            
            if (label.equals("Blank")) {
                params.width = dp(60); 
            } else {
                params.width = dp(36); 
            }
            
            btn.setLayoutParams(params);
            btnGroup.addView(btn);
        }

        row.addView(btnGroup);
        
        // Add divider automatically via parent? No, let's add view line
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#21262d"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        wrapper.addView(divider);

        return wrapper;
    }

    private View createOptionButton(String label, boolean isSelected, View.OnClickListener onClick) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        
        if (isSelected) {
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_option_selected));
            btn.setTextColor(Color.BLACK);
        } else {
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_option_unselected));
            btn.setTextColor(Color.parseColor("#8b949e"));
        }
        
        btn.setOnClickListener(onClick);
        return btn;
    }

    // =========================================================
    // LOGIC
    // =========================================================

    private void toggleEditMode() {
        isEditing = !isEditing;
        
        btnEditToggle.setText(isEditing ? "CANCEL" : "EDIT");
        btnEditToggle.setTextColor(isEditing ? Color.parseColor("#f85149") : Color.parseColor("#58a6ff"));
        
        etLrn.setEnabled(isEditing);
        etLrn.setBackgroundColor(isEditing ? Color.parseColor("#0d1117") : Color.TRANSPARENT);
        if(isEditing) etLrn.requestFocus();
        
        fabSave.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        
        // Reset edited buffers if entering edit mode
        if (isEditing) {
            editedAnswers.clear();
            if (currentScan.getAnswers() != null) {
                editedAnswers.putAll(currentScan.getAnswers());
            }
        }
        
        renderAnswers();
    }

    private void saveChanges() {
        // 1. Update LRN
        String newLrn = etLrn.getText().toString().trim();
        currentScan.setLrn(newLrn);

        // 2. Commit edited answers
        currentScan.setAnswers(new LinkedHashMap<>(editedAnswers));

        // 3. Recalculate Score
        currentScan.setScore(currentScan.getAnsweredCount());
        
        // 4. Update the list in memory
        currentActivity.getScans().set(scanIndex, currentScan);
        
        // 5. Save everything to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String updatedJson = gson.toJson(classFolders);
        prefs.edit().putString(KEY_CLASSES, updatedJson).apply();

        Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
        
        // Disable edit mode
        isEditing = false;
        btnEditToggle.setText("EDIT");
        btnEditToggle.setTextColor(Color.parseColor("#58a6ff"));
        etLrn.setEnabled(false);
        etLrn.setBackgroundColor(Color.TRANSPARENT);
        fabSave.setVisibility(View.GONE);
        
        // Update UI
        tvScore.setText(currentScan.getScore() + "/" + currentScan.getNumItems());
        renderAnswers();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
