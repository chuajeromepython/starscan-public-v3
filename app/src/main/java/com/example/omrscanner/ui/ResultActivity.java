package com.example.omrscanner.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

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

        // Set status bar color to blue
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

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

        // Get data from intent
        originalImagePath = getIntent().getStringExtra(PreviewActivity.IMAGE_PATH);
        double[] anchorData = getIntent().getDoubleArrayExtra(PreviewActivity.ANCHOR_POINTS);
        selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
        classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
        activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);
        imageSource = getIntent().getStringExtra(PreviewActivity.IMAGE_SOURCE);

        Log.d(TAG, "Received sheet type: " + selectedSheetType + ", classId: " + classId + ", activityId: " + activityId);

        if (originalImagePath == null || anchorData == null) {
            Toast.makeText(this, "Missing image data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Convert anchor data back to Point[]
        Point[] anchors = new Point[4];
        for (int i = 0; i < 4; i++) {
            anchors[i] = new Point(
                    anchorData[i * 2],
                    anchorData[i * 2 + 1]
            );
        }

        // Process image
        processImage(originalImagePath, anchors);

        // Button listeners
        btnRetry.setOnClickListener(v -> retakePhoto());
        btnExport.setOnClickListener(v -> exportResults());
    }

    private void retakePhoto() {
        if (PreviewActivity.SOURCE_GALLERY.equals(imageSource)) {
            // Redirect to dashboard for gallery retake
            finish();
        } else {
            // Go back to camera for retake
            Intent intent = new Intent(this, CameraActivity.class);
            if (selectedSheetType != null) {
                intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
            }
            if (classId != null) {
                intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
            }
            if (activityId != null) {
                intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
            }
            startActivity(intent);
            finish();
        }
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

                // Validate anchors
                if (!PerspectiveAligner.validateAnchors(anchors)) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                this,
                                "Invalid anchor points detected",
                                Toast.LENGTH_LONG
                        ).show();
                        finish();
                    });
                    return;
                }

                // STEP 1: Apply perspective alignment (now maintains correct aspect ratio!)
                alignedBitmap = PerspectiveAligner.alignPerspective(original, anchors);

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
                        String summary = String.format(
                                "✓ %s | LNR: %s | %d / %d answers",
                                scanResult.templateId,
                                scanResult.lnr,
                                scanResult.getAnsweredCount(),
                                scanResult.getQuestionCount()
                        );

                        Toast.makeText(this, summary, Toast.LENGTH_LONG).show();

                        // Enable export button
                        btnExport.setEnabled(true);

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

    private void exportResults() {
        if (scanResult == null || scanResult.answers.isEmpty()) {
            Toast.makeText(this, "No answers to export", Toast.LENGTH_SHORT).show();
            return;
        }

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
                    saveScanToFolder(csvFilePath);
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
    private void saveScanToFolder(String csvFilePath) {
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
            entry.setScore(scanResult.getAnsweredCount());

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

            DashboardActivity.saveScanResult(this, classId, activityId, entry);
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