package com.example.omrscanner.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.camera.CameraActivity;
import com.example.omrscanner.models.ScanEntry;
import com.example.omrscanner.omr.BubbleScanner;
import com.example.omrscanner.omr.OmrTemplate;
import com.example.omrscanner.omr.PerspectiveAligner;
import com.example.omrscanner.omr.ScanResult;
import com.example.omrscanner.omr.TemplateManager;
import com.example.omrscanner.utils.CsvHelper;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResultActivity extends AppCompatActivity {

    private static final String TAG = "ResultActivity";

    private ImageView imageResult;
    private Button btnExport;
    private Button btnRetry;
    private ProgressBar progressBar;
    private ScrollView scrollView;

    // LRN verification views
    private MaterialCardView lrnCard;
    private android.widget.LinearLayout lrnDigitContainer;
    private EditText[] digitBoxes = new EditText[12];
    private TextView tvLrnStatus;
    private TextView tvLrnHelper;
    private TextView tvLrnCardTitle;
    private android.widget.LinearLayout lrnInputContainer;
    private MaterialButton btnConfirmLrn;
    private boolean isLrnConfirmed = false;

    private Bitmap alignedBitmap;
    private ScanResult scanResult;
    private String originalImagePath;
    private String selectedSheetType;
    private String classId;
    private String activityId;
    private String imageSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Full screen — hide status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize views
        imageResult = findViewById(R.id.imageResult);
        btnExport = findViewById(R.id.btnExport);
        btnRetry = findViewById(R.id.btnRetry);
        progressBar = findViewById(R.id.progressBar);
        scrollView  = findViewById(R.id.scrollView);

        // LRN verification views
        lrnCard       = findViewById(R.id.lrnCard);
        lrnDigitContainer = findViewById(R.id.lrnDigitContainer);
        tvLrnStatus   = findViewById(R.id.tvLrnStatus);
        tvLrnHelper   = findViewById(R.id.tvLrnHelper);
        tvLrnCardTitle = findViewById(R.id.tvLrnCardTitle);
        lrnInputContainer = findViewById(R.id.lrnInputContainer);
        btnConfirmLrn = findViewById(R.id.btnConfirmLrn);

        setupLrnBoxes();

        // Get data from intent
        originalImagePath = getIntent().getStringExtra(PreviewActivity.IMAGE_PATH);
        double[] anchorData = getIntent().getDoubleArrayExtra(PreviewActivity.ANCHOR_POINTS);
        selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
        classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
        activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);
        imageSource = getIntent().getStringExtra(PreviewActivity.IMAGE_SOURCE);

        Log.d(TAG, "Received sheet type: " + selectedSheetType + ", classId: " + classId + ", activityId: " + activityId);

        if (originalImagePath == null) {
            Toast.makeText(this, "Missing image data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Point[] anchors = null;
        if (anchorData != null) {
            // Convert anchor data back to Point[]
            anchors = new Point[4];
            for (int i = 0; i < 4; i++) {
                anchors[i] = new Point(
                        anchorData[i * 2],
                        anchorData[i * 2 + 1]
                );
            }
        }

        // Process image
        processImage(originalImagePath, anchors);

        // Button listeners
        btnRetry.setOnClickListener(v -> retakePhoto());
        btnExport.setOnClickListener(v -> exportResults());
        btnConfirmLrn.setOnClickListener(v -> confirmLrn());

    }

    private void setupLrnBoxes() {
        int heightPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 44, getResources().getDisplayMetrics());
        int marginPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());

        for (int i = 0; i < 12; i++) {
            EditText et = new EditText(this);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    0, heightPx, 1.0f);
            params.setMargins(marginPx, 0, marginPx, 0);
            et.setLayoutParams(params);
            et.setBackgroundResource(R.drawable.bg_lrn_digit);
            et.setGravity(android.view.Gravity.CENTER);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setMaxLines(1);
            et.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(1) });
            et.setTextSize(18);
            et.setTextColor(0xFF0F172A);
            et.setTypeface(null, android.graphics.Typeface.BOLD);
            et.setPadding(0, 0, 0, 0);

            final int index = i;

            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isLrnConfirmed) {
                        isLrnConfirmed = false;
                        tvLrnStatus.setText("⚠ Not verified");
                        tvLrnStatus.setTextColor(0xFFF59E0B);
                        tvLrnHelper.setText("LRN was modified. Please re-confirm before saving.");
                        tvLrnHelper.setTextColor(0xFFF59E0B);
                        btnExport.setEnabled(false);
                        btnConfirmLrn.setText("CONFIRM");
                        btnConfirmLrn.setVisibility(View.VISIBLE);
                    }
                    if (s.length() == 1 && index < 11 && digitBoxes[index].hasFocus()) {
                        digitBoxes[index + 1].requestFocus();
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });

            et.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (et.getText().toString().isEmpty() && index > 0) {
                        digitBoxes[index - 1].requestFocus();
                        digitBoxes[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });

            et.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    scrollView.postDelayed(() ->
                        scrollView.smoothScrollTo(0, lrnCard.getTop()), 300);
                }
            });

            digitBoxes[i] = et;
            lrnDigitContainer.addView(et);
        }
    }

    private void retakePhoto() {
        // Always go back to camera
        Intent intent = new Intent(this, CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (selectedSheetType != null) intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
        if (classId    != null) intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
        if (activityId != null) intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
        startActivity(intent);
        finish();
    }

    private void processImage(String imagePath, Point[] anchors) {
        showLoading(true);

        new Thread(() -> {
            try {
                // Load original bitmap
                Bitmap original = BitmapFactory.decodeFile(imagePath);

                if (original == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                Point[] finalAnchors = anchors;
                if (finalAnchors == null) {
                    finalAnchors = com.example.omrscanner.omr.AnchorDetector.detectAnchors(original);
                    if (finalAnchors == null || finalAnchors.length != 4) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    ResultActivity.this,
                                    "⚠ Anchor detection failed. Please retake.",
                                    Toast.LENGTH_LONG
                            ).show();
                            imageResult.setImageBitmap(original);
                            showLoading(false);
                        });
                        return;
                    }
                }

                // Validate anchors
                if (!PerspectiveAligner.validateAnchors(finalAnchors)) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                this,
                                "Invalid anchor points detected",
                                Toast.LENGTH_LONG
                        ).show();
                        imageResult.setImageBitmap(original);
                        showLoading(false);
                    });
                    return;
                }

                // STEP 1: Apply perspective alignment (now maintains correct aspect ratio!)
                alignedBitmap = PerspectiveAligner.alignPerspective(original, finalAnchors);

                if (alignedBitmap == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Alignment failed", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // STEP 2: Detect orientation + sheet type, then scan bubbles
                TemplateManager tm = new TemplateManager(ResultActivity.this);

                Bitmap scanBitmap;
                String sheetType;
                OmrTemplate template;

                if (selectedSheetType != null) {
                    // User pre-selected the sheet type — use it directly
                    // Still need orientation detection
                    Log.d(TAG, "Using user-selected sheet type: " + selectedSheetType);
                    TemplateManager.OrientationResult orient =
                            tm.detectAndOrientWithTemplate(alignedBitmap, selectedSheetType);
                    scanBitmap = orient.orientedBitmap;
                    sheetType = orient.templateId;
                    template = tm.getTemplate(sheetType);
                } else {
                    // No pre-selection — auto-detect sheet type
                    Log.d(TAG, "Auto-detecting sheet type...");
                    TemplateManager.OrientationResult orient = tm.detectAndOrient(alignedBitmap);
                    scanBitmap = orient.orientedBitmap;
                    sheetType = orient.templateId;
                    template = tm.getTemplate(sheetType);
                }

                // If the oriented bitmap is different from the original, update
                // alignedBitmap so the overlay displays correctly
                if (scanBitmap != alignedBitmap) {
                    alignedBitmap.recycle();
                    alignedBitmap = scanBitmap;
                }

                BubbleScanner scanner = new BubbleScanner();
                scanResult = scanner.scan(alignedBitmap, template, tm);

                // Update UI
                runOnUiThread(() -> {
                    showLoading(false);

                    if (scanResult != null && scanResult.overlayBitmap != null) {
                        // Show image with highlighted bubbles
                        imageResult.setImageBitmap(scanResult.overlayBitmap);

                        // Show detection summary
                        String summary;
                        if (scanResult.hasDoubleShadedLrn()) {
                            summary = String.format(
                                    "⚠ %s | LRN: DOUBLE-SHADED (%d column(s)) | %d / %d answers",
                                    scanResult.templateId,
                                    scanResult.doubleShadedLnrPositions.size(),
                                    scanResult.getAnsweredCount(),
                                    scanResult.getQuestionCount()
                            );
                        } else if (scanResult.hasUndetectedLrnDigits()) {
                            summary = String.format(
                                    "⚠ %s | LRN: %s (%d digit(s) not detected!) | %d / %d answers",
                                    scanResult.templateId,
                                    scanResult.lnr,
                                    scanResult.undetectedLnrPositions.size(),
                                    scanResult.getAnsweredCount(),
                                    scanResult.getQuestionCount()
                            );
                        } else {
                            summary = String.format(
                                    "✓ %s | LRN: %s | %d / %d answers",
                                    scanResult.templateId,
                                    scanResult.lnr,
                                    scanResult.getAnsweredCount(),
                                    scanResult.getQuestionCount()
                            );
                        }

                        Toast.makeText(this, summary, Toast.LENGTH_LONG).show();

                        // Show LRN verification card with detected LRN
                        // (auto-confirms if LRN is valid; shows manual edit if error)
                        showLrnVerification(scanResult.lnr);

                    } else {
                        Toast.makeText(
                                this,
                                "Bubble detection failed",
                                Toast.LENGTH_SHORT
                        ).show();

                        // Show aligned image anyway
                        imageResult.setImageBitmap(alignedBitmap);
                    }
                });

                // Cleanup original
                original.recycle();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(
                            this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            }
        }).start();
    }

    // ──────────────────────────────────────────────────────────
    // LRN VERIFICATION
    // ──────────────────────────────────────────────────────────
    private void showLrnVerification(String detectedLrn) {
        isLrnConfirmed = false;

        // Reset all boxes
        for (int i = 0; i < 12; i++) {
            digitBoxes[i].setBackgroundResource(R.drawable.bg_lrn_digit);
            digitBoxes[i].setText("");
            digitBoxes[i].setEnabled(true);
        }

        // Fill detected LRN without triggering verification warning
        String rawLrn = detectedLrn != null ? detectedLrn : "";
        for (int i = 0; i < Math.min(rawLrn.length(), 12); i++) {
            char c = rawLrn.charAt(i);
            if (c != '_' && c != 'X') {
                digitBoxes[i].setText(String.valueOf(c));
            }
        }

        // Apply error borders
        if (scanResult != null && scanResult.hasDoubleShadedLrn()) {
            for (int pos : scanResult.doubleShadedLnrPositions) {
                if (pos >= 0 && pos < 12) {
                    digitBoxes[pos].setBackgroundResource(R.drawable.bg_lrn_digit_error);
                }
            }
        }
        if (scanResult != null && scanResult.hasUndetectedLrnDigits()) {
            for (int pos : scanResult.undetectedLnrPositions) {
                if (pos >= 0 && pos < 12) {
                    digitBoxes[pos].setBackgroundResource(R.drawable.bg_lrn_digit_error);
                }
            }
        }

        // Clean LRN for checking valid length
        String displayLrn = rawLrn.replace("_", "").replace("X", "");

        // ── VALID LRN: show as read-only, no confirm needed ────────
        if (scanResult != null
                && !scanResult.hasDoubleShadedLrn()
                && !scanResult.hasUndetectedLrnDigits()
                && displayLrn.length() == 12) {
            lrnCard.setVisibility(View.VISIBLE);
            lrnInputContainer.setVisibility(View.VISIBLE);
            btnConfirmLrn.setVisibility(View.GONE);
            
            isLrnConfirmed = true;
            for (int i = 0; i < 12; i++) {
                digitBoxes[i].setEnabled(false);
            }
            
            tvLrnCardTitle.setText("STUDENT LRN");
            tvLrnCardTitle.setTextColor(0xFF1E293B);
            tvLrnStatus.setText("✓ Detected");
            tvLrnStatus.setTextColor(0xFF22C55E);
            tvLrnHelper.setText("LRN is correctly detected. You can proceed to save.");
            tvLrnHelper.setTextColor(0xFF64748B);
            tvLrnHelper.setTextSize(11);
            btnExport.setEnabled(true);
            return;
        }

        // ── ERROR CASES: show manual edit card ──────────────────────
        lrnCard.setVisibility(View.VISIBLE);
        lrnInputContainer.setVisibility(View.VISIBLE);
        btnConfirmLrn.setVisibility(View.VISIBLE);
        btnExport.setEnabled(false);
        tvLrnHelper.setTextSize(11); // reset from valid-LRN large display

        if (scanResult != null && scanResult.hasDoubleShadedLrn()) {
            // Build human-readable list of double-shaded positions (1-based)
            StringBuilder positions = new StringBuilder();
            for (int i = 0; i < scanResult.doubleShadedLnrPositions.size(); i++) {
                if (i > 0) positions.append(", ");
                positions.append(scanResult.doubleShadedLnrPositions.get(i) + 1);
            }
            int count = scanResult.doubleShadedLnrPositions.size();

            tvLrnStatus.setText("⚠ " + count + " double-shaded");
            tvLrnStatus.setTextColor(0xFFDC2626);
            tvLrnHelper.setText("⚠ DOUBLE-SHADED LRN DETECTED\n\n"
                    + count + " LRN column(s) have two or more shaded bubbles "
                    + "at position(s): " + positions + ".\n\n"
                    + "Manually enter the correct 12-digit LRN below, "
                    + "then tap CONFIRM to proceed.");
            tvLrnHelper.setTextColor(0xFFDC2626);
            tvLrnCardTitle.setText("⚠ MANUAL LRN ENTRY");
            tvLrnCardTitle.setTextColor(0xFFDC2626);

        } else if (scanResult != null && scanResult.hasUndetectedLrnDigits()) {
            StringBuilder positions = new StringBuilder();
            for (int i = 0; i < scanResult.undetectedLnrPositions.size(); i++) {
                if (i > 0) positions.append(", ");
                positions.append(scanResult.undetectedLnrPositions.get(i) + 1);
            }
            int count = scanResult.undetectedLnrPositions.size();

            tvLrnStatus.setText("⚠ " + count + " digit(s) missing");
            tvLrnStatus.setTextColor(0xFFEF4444);
            tvLrnHelper.setText(count + " LRN digit(s) not detected at position(s): "
                    + positions + ".\n\n"
                    + "Manually enter the correct 12-digit LRN below, "
                    + "then tap CONFIRM to proceed.");
            tvLrnHelper.setTextColor(0xFFEF4444);
            tvLrnCardTitle.setText("⚠ MANUAL LRN ENTRY");
            tvLrnCardTitle.setTextColor(0xFFEF4444);

        } else if (detectedLrn == null || detectedLrn.trim().isEmpty()) {
            tvLrnStatus.setText("⚠ Not detected");
            tvLrnStatus.setTextColor(0xFFEF4444);
            tvLrnHelper.setText("No LRN detected — please enter the student's 12-digit LRN below.");
            tvLrnHelper.setTextColor(0xFFEF4444);
            tvLrnCardTitle.setText("⚠ MANUAL LRN ENTRY");
            tvLrnCardTitle.setTextColor(0xFFEF4444);

        } else {
            // displayLrn.length() != 12
            tvLrnStatus.setText("⚠ " + displayLrn.length() + " digits");
            tvLrnStatus.setTextColor(0xFFF59E0B);
            tvLrnHelper.setText("Detected LRN is " + displayLrn.length() + " digits — expected 12. "
                    + "Please correct the LRN below and tap CONFIRM.");
            tvLrnHelper.setTextColor(0xFFF59E0B);
            tvLrnCardTitle.setText("⚠ VERIFY LRN");
            tvLrnCardTitle.setTextColor(0xFFF59E0B);
        }

    }

    private void confirmLrn() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(digitBoxes[i].getText().toString().trim());
        }
        String lrn = sb.toString();

        if (lrn.isEmpty()) {
            tvLrnStatus.setText("✗ Empty");
            tvLrnStatus.setTextColor(0xFFEF4444);
            tvLrnHelper.setText("LRN cannot be empty. Please enter a valid 12-digit LRN.");
            tvLrnHelper.setTextColor(0xFFEF4444);
            Toast.makeText(this, "Please enter the student LRN", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lrn.length() != 12) {
            tvLrnStatus.setText("✗ Invalid (" + lrn.length() + " digits)");
            tvLrnStatus.setTextColor(0xFFEF4444);
            tvLrnHelper.setText("LRN must be exactly 12 digits. Current: " + lrn.length() + " digits.");
            tvLrnHelper.setTextColor(0xFFEF4444);
            Toast.makeText(this, "LRN must be exactly 12 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        // LRN is valid — confirm it
        isLrnConfirmed = true;
        scanResult.lnr = lrn;

        // Clear double-shaded / undetected errors since teacher manually confirmed
        if (scanResult.doubleShadedLnrPositions != null) {
            scanResult.doubleShadedLnrPositions.clear();
        }
        if (scanResult.undetectedLnrPositions != null) {
            scanResult.undetectedLnrPositions.clear();
        }

        for (int i = 0; i < 12; i++) {
            digitBoxes[i].setBackgroundResource(R.drawable.bg_lrn_digit);
        }

        tvLrnStatus.setText("✓ Verified");
        tvLrnStatus.setTextColor(0xFF22C55E); // green
        tvLrnHelper.setText("LRN confirmed: " + lrn + ". You can now save the result.");
        tvLrnHelper.setTextColor(0xFF22C55E);
        btnConfirmLrn.setText("CONFIRMED ✓");

        // Enable the SAVE button and auto-save
        btnExport.setEnabled(true);

        Toast.makeText(this, "LRN verified ✓ Saving...", Toast.LENGTH_SHORT).show();
        
        // Auto-save immediately after confirmation
        exportResults();
    }

    private void exportResults() {
        if (scanResult == null || scanResult.answers.isEmpty()) {
            Toast.makeText(this, "No answers to export", Toast.LENGTH_SHORT).show();
            return;
        }

        if (classId != null && activityId != null && scanResult.lnr != null) {
            new Thread(() -> {
                boolean exists = DashboardActivity.isLrnExists(this, classId, activityId, scanResult.lnr);
                runOnUiThread(() -> {
                    if (exists) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Duplicate LRN detected")
                                .setMessage("A scan with LRN " + scanResult.lnr + " already exists in this assessment. Do you want to replace it?")
                                .setPositiveButton("Replace", (dialog, which) -> proceedWithExport(true))
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        proceedWithExport(false);
                    }
                });
            }).start();
            return;
        }

        proceedWithExport(false);
    }

    private void proceedWithExport(boolean replace) {
        showLoading(true);

        new Thread(() -> {
            try {
                // Generate CSV file in temp directory
                String csvFilePath = CsvHelper.exportToCSV(
                        this,
                        scanResult
                );

                // Save scan result to the class/activity folder structure
                if (classId != null && activityId != null) {
                    saveScanToFolder(csvFilePath, replace);
                }

                if (csvFilePath != null) {
                    runOnUiThread(() -> {
                        showLoading(false);

                        Toast.makeText(
                                this,
                                "Scanned sheet is being saved...",
                                Toast.LENGTH_SHORT
                        ).show();

                        // Finish this activity so user goes back to dashboard
                        finish();
                    });
                } else {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(
                                this,
                                "Failed to generate CSV",
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(
                            this,
                            "Error exporting CSV: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    /**
     * Save the scan result into the class/activity folder structure
     * so it appears in the Dashboard's activity scan list.
     */
    private void saveScanToFolder(String csvFilePath, boolean replace) {
        try {
            // Convert ScanResult answers to the ScanEntry format
            Map<Integer, String> answersMap = new LinkedHashMap<>();
            if (scanResult.answers != null) {
                answersMap.putAll(scanResult.answers);
            }

            ScanEntry entry = new ScanEntry(
                    scanResult.lnr,
                    answersMap,
                    scanResult.getQuestionCount(),
                    scanResult.templateId
            );
            entry.setImagePath(originalImagePath);
            entry.setCsvPath(csvFilePath);
            // NOTE: do NOT call entry.setScore() here — score must stay null (ungraded)
            //       until an answer key is assigned in DashboardActivity.saveScanResult().

            // Save overlay bitmap to disk
            if (scanResult.overlayBitmap != null && !scanResult.overlayBitmap.isRecycled()) {
                try {
                    java.io.File overlayDir = new java.io.File(getFilesDir(), "images");
                    if (!overlayDir.exists()) overlayDir.mkdirs();

                    String overlayName = "overlay_" + System.currentTimeMillis() + ".png";
                    java.io.File overlayFile = new java.io.File(overlayDir, overlayName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(overlayFile);
                    scanResult.overlayBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();

                    entry.setOverlayImagePath(overlayFile.getAbsolutePath());
                    Log.d(TAG, "Overlay saved: " + overlayFile.getAbsolutePath());
                } catch (Exception oe) {
                    Log.e(TAG, "Error saving overlay image", oe);
                }
            }

            DashboardActivity.saveScanResult(this, classId, activityId, entry, replace);
            Log.d(TAG, "Scan result saved to folder: classId=" + classId + ", activityId=" + activityId);

        } catch (Exception e) {
            Log.e(TAG, "Error saving scan to folder", e);
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnExport.setEnabled(!show);
        btnRetry.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alignedBitmap != null && !alignedBitmap.isRecycled()) {
            alignedBitmap.recycle();
        }
        if (scanResult != null && scanResult.overlayBitmap != null &&
                !scanResult.overlayBitmap.isRecycled()) {
            scanResult.overlayBitmap.recycle();
        }
    }
}
