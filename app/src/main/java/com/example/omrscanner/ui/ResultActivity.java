package com.example.omrscanner.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.R;
import com.example.omrscanner.omr.BubbleDetector;
import com.example.omrscanner.omr.PerspectiveAligner;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

public class ResultActivity extends AppCompatActivity {

    private ImageView imageResult;
    private Button btnExport;
    private Button btnRetry;
    private ProgressBar progressBar;

    private Bitmap alignedBitmap;
    private BubbleDetector.OMRResult omrResult; // Store detection result

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

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
        String imagePath = getIntent().getStringExtra(PreviewActivity.IMAGE_PATH);
        double[] anchorData = getIntent().getDoubleArrayExtra(PreviewActivity.ANCHOR_POINTS);

        if (imagePath == null || anchorData == null) {
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
        processImage(imagePath, anchors);

        // Button listeners
        btnRetry.setOnClickListener(v -> {
            finish(); // Go back to preview
        });

        btnExport.setOnClickListener(v -> {
            exportResults();
        });
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

                // STEP 1: Apply perspective alignment
                alignedBitmap = PerspectiveAligner.alignPerspective(original, anchors);

                if (alignedBitmap == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Alignment failed", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // STEP 2: Detect bubbles and extract answers
                omrResult = BubbleDetector.detectBubbles(alignedBitmap);

                // Update UI
                runOnUiThread(() -> {
                    showLoading(false);

                    if (omrResult != null && omrResult.annotatedImage != null) {
                        // Show image with highlighted bubbles
                        imageResult.setImageBitmap(omrResult.annotatedImage);

                        // Show detection summary
                        String summary = String.format(
                                "✓ Detected %d / %d answers",
                                omrResult.answeredQuestions,
                                omrResult.totalQuestions
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
        if (omrResult == null || omrResult.answers.isEmpty()) {
            Toast.makeText(this, "No answers to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Next step - CSV export
        // For now, just show the results
        showResultsSummary();
    }

    private void showResultsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("OMR Results:\n\n");

        // Sort question numbers
        java.util.List<Integer> questionNumbers = new java.util.ArrayList<>(omrResult.answers.keySet());
        java.util.Collections.sort(questionNumbers);

        for (Integer qNum : questionNumbers) {
            Character answer = omrResult.answers.get(qNum);
            summary.append(String.format("Q%d: %s\n", qNum, answer));
        }

        summary.append("\nTotal: ")
                .append(omrResult.answeredQuestions)
                .append(" / ")
                .append(omrResult.totalQuestions);

        // Show in a simple dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("OMR Detection Results")
                .setMessage(summary.toString())
                .setPositiveButton("OK", null)
                .setNegativeButton("Export CSV", (dialog, which) -> {
                    Toast.makeText(this, "CSV export coming next!", Toast.LENGTH_SHORT).show();
                })
                .show();
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
        if (omrResult != null && omrResult.annotatedImage != null &&
                !omrResult.annotatedImage.isRecycled()) {
            omrResult.annotatedImage.recycle();
        }
    }
}