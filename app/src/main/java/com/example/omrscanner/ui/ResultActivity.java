package com.example.omrscanner.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.R;
import com.example.omrscanner.omr.BubbleScanner;
import com.example.omrscanner.omr.OmrTemplate;
import com.example.omrscanner.omr.PerspectiveAligner;
import com.example.omrscanner.omr.ScanResult;
import com.example.omrscanner.omr.TemplateManager;
import com.example.omrscanner.utils.CsvHelper;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

public class ResultActivity extends AppCompatActivity {

    private ImageView imageResult;
    private Button btnExport;
    private Button btnRetry;
    private ProgressBar progressBar;

    private Bitmap alignedBitmap;
    private ScanResult scanResult;
    private String originalImagePath;

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
        btnRetry.setOnClickListener(v -> finish());
        btnExport.setOnClickListener(v -> exportResults());
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

                // STEP 2: Detect sheet type, then scan bubbles with pixel-density
                TemplateManager tm = new TemplateManager(ResultActivity.this);
                String sheetType = tm.detectSheetType(alignedBitmap);
                OmrTemplate template = tm.getTemplate(sheetType);
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

                if (csvFilePath != null) {
                    runOnUiThread(() -> {
                        showLoading(false);

                        Toast.makeText(
                                this,
                                "Opening file manager to save...",
                                Toast.LENGTH_SHORT
                        ).show();

                        // Navigate to CSVFileActivity to save the files properly
                        Intent intent = new Intent(this, CSVFileActivity.class);
                        intent.putExtra(CSVFileActivity.EXTRA_CSV_FILEPATH, csvFilePath);
                        intent.putExtra(CSVFileActivity.EXTRA_IMAGE_FILEPATH, originalImagePath);
                        startActivity(intent);

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