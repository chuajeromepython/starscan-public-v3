package com.example.omrscanner;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.omrscanner.camera.CameraActivity;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    private MaterialButton tabScan, tabCSV, tabResults;
    private TextView teacherInfo;
    private CardView cardUpload, cardScan;
    private View scanContent, csvContent, resultsContent;

    // Selected sheet type
    public static final String EXTRA_SHEET_TYPE = "sheet_type";
    private String selectedSheetType = null;

    // Activity Result Launcher for picking images from gallery
    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Set status bar color to blue
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

        // Initialize views
        teacherInfo = findViewById(R.id.teacherInfo);
        tabScan = findViewById(R.id.tabScan);
        tabCSV = findViewById(R.id.tabCSV);
        tabResults = findViewById(R.id.tabResults);

        // Initialize scan content views
        cardUpload = findViewById(R.id.cardUpload);
        cardScan = findViewById(R.id.cardScan);
        scanContent = findViewById(R.id.scanContent);
        csvContent = findViewById(R.id.csvContent);
        resultsContent = findViewById(R.id.resultsContent);

        // Initialize gallery launcher
        initializeGalleryLauncher();

        // Load user info
        loadUserInfo();

        // Set tab click listeners
        tabScan.setOnClickListener(v -> setActiveTab("scan"));
        tabCSV.setOnClickListener(v -> setActiveTab("csv"));
        tabResults.setOnClickListener(v -> setActiveTab("results"));

        // Set scan action listeners
        cardUpload.setOnClickListener(v -> showSheetTypeDialog("gallery"));
        cardScan.setOnClickListener(v -> showSheetTypeDialog("camera"));

        // Show scan tab by default
        setActiveTab("scan");
    }

    private void initializeGalleryLauncher() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleSelectedImage(uri);
                    } else {
                        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void handleSelectedImage(Uri imageUri) {
        Toast.makeText(this, "Processing selected image...", Toast.LENGTH_SHORT).show();

        // Process image in background thread
        new Thread(() -> {
            try {
                // Save the selected image to a temporary file
                String savedPath = saveImageToFile(imageUri);

                if (savedPath != null) {
                    // Launch PreviewActivity with the saved image path + selected sheet type
                    runOnUiThread(() -> {
                        Intent intent = new Intent(DashboardActivity.this,
                                com.example.omrscanner.ui.PreviewActivity.class);
                        intent.putExtra(com.example.omrscanner.ui.PreviewActivity.IMAGE_PATH, savedPath);
                        intent.putExtra(com.example.omrscanner.ui.PreviewActivity.IMAGE_SOURCE,
                                com.example.omrscanner.ui.PreviewActivity.SOURCE_GALLERY);
                        if (selectedSheetType != null) {
                            intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
                        }
                        startActivity(intent);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String saveImageToFile(Uri imageUri) {
        try {
            // Read the image from URI
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(), imageUri);

            if (bitmap == null) {
                return null;
            }

            // Create a file in the app's cache directory
            java.io.File outputDir = getCacheDir();
            java.io.File outputFile = java.io.File.createTempFile(
                    "omr_upload_",
                    ".jpg",
                    outputDir
            );

            // Save bitmap to file
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            // Clean up bitmap
            bitmap.recycle();

            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadUserInfo() {
        SharedPreferences prefs = getSharedPreferences("OMRScannerPrefs", MODE_PRIVATE);
        String teacherName = prefs.getString("teacherName", "Teacher");
        String grade = prefs.getString("grade", "");
        String section = prefs.getString("section", "");

        String info = teacherName + " • Grade " + grade + " - " + section;
        teacherInfo.setText(info);
    }

    private void setActiveTab(String tab) {

        // Inactive colors
        int inactiveText = getResources().getColor(android.R.color.darker_gray);
        int transparentBg = android.R.color.transparent;

        // Active colors
        int activeText = getResources().getColor(R.color.red_accent);

        // Reset all tabs
        tabScan.setTextColor(inactiveText);
        tabScan.setBackgroundTintList(
                androidx.core.content.ContextCompat.getColorStateList(this, transparentBg)
        );

        tabCSV.setTextColor(inactiveText);
        tabCSV.setBackgroundTintList(
                androidx.core.content.ContextCompat.getColorStateList(this, transparentBg)
        );

        tabResults.setTextColor(inactiveText);
        tabResults.setBackgroundTintList(
                androidx.core.content.ContextCompat.getColorStateList(this, transparentBg)
        );

        // Hide all content
        scanContent.setVisibility(View.GONE);
        csvContent.setVisibility(View.GONE);
        resultsContent.setVisibility(View.GONE);

        // Activate selected tab
        switch (tab) {
            case "scan":
                tabScan.setTextColor(activeText);
                tabScan.setBackgroundTintList(
                        androidx.core.content.ContextCompat.getColorStateList(this, R.color.red_light_bg)
                );
                scanContent.setVisibility(View.VISIBLE);
                break;

            case "csv":
                tabCSV.setTextColor(activeText);
                tabCSV.setBackgroundTintList(
                        androidx.core.content.ContextCompat.getColorStateList(this, R.color.red_light_bg)
                );
                csvContent.setVisibility(View.VISIBLE);
                break;

            case "results":
                tabResults.setTextColor(activeText);
                tabResults.setBackgroundTintList(
                        androidx.core.content.ContextCompat.getColorStateList(this, R.color.red_light_bg)
                );
                resultsContent.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void showSheetTypeDialog(String action) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        // ── Root container ──────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(20));

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.WHITE);
        rootBg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        root.setBackground(rootBg);

        // ── Drag handle ─────────────────────────────────────────────────
        View handle = new View(this);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(20);
        handle.setLayoutParams(handleParams);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.parseColor("#CBD5E1"));
        handleBg.setCornerRadius(dp(2));
        handle.setBackground(handleBg);
        root.addView(handle);

        // ── Title ────────────────────────────────────────────────────────
        TextView title = new TextView(this);
        title.setText("Select Sheet Type");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#1E293B"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(6);
        title.setLayoutParams(titleParams);
        root.addView(title);

        // ── Subtitle ────────────────────────────────────────────────────
        TextView subtitle = new TextView(this);
        subtitle.setText("Choose the answer sheet type before scanning");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(24);
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        // ── Sheet type options ───────────────────────────────────────────
        String[][] sheetTypes = {
                {"ZPH30", "30 Items", "For short quizzes and exams"},
                {"ZPH50", "50 Items", "For standard exams"},
                {"ZPH60", "60 Items", "For long exams"}
        };

        for (String[] sheet : sheetTypes) {
            root.addView(createSheetOption(dialog, sheet[0], sheet[1], sheet[2], action));
        }

        // ── Cancel button ────────────────────────────────────────────────
        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextSize(15);
        cancel.setTextColor(Color.parseColor("#64748B"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(16), 0, dp(8));
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);

        dialog.setContentView(root);

        // ── Dialog window styling (bottom sheet style) ──────────────────
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getAttributes().windowAnimations =
                    com.google.android.material.R.style.Animation_Design_BottomSheetDialog;
        }

        dialog.show();
    }

    private View createSheetOption(Dialog dialog, String typeId, String itemCount,
                                    String description, String action) {
        // ── Card container ──────────────────────────────────────────────
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F8FAFC"));
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(2), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);
        card.setClickable(true);
        card.setFocusable(true);

        // ── Icon circle ─────────────────────────────────────────────────
        LinearLayout iconCircle = new LinearLayout(this);
        iconCircle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        circleParams.rightMargin = dp(14);
        iconCircle.setLayoutParams(circleParams);

        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.parseColor("#0038A8"));
        iconCircle.setBackground(circleBg);

        TextView iconText = new TextView(this);
        iconText.setText(typeId.replace("ZPH", ""));
        iconText.setTextSize(16);
        iconText.setTextColor(Color.WHITE);
        iconText.setTypeface(null, android.graphics.Typeface.BOLD);
        iconCircle.addView(iconText);
        card.addView(iconCircle);

        // ── Text column ─────────────────────────────────────────────────
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textColParams);

        TextView nameView = new TextView(this);
        nameView.setText(typeId + " — " + itemCount);
        nameView.setTextSize(15);
        nameView.setTextColor(Color.parseColor("#1E293B"));
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(nameView);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextSize(12);
        descView.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(2);
        descView.setLayoutParams(descParams);
        textCol.addView(descView);

        card.addView(textCol);

        // ── Arrow indicator ─────────────────────────────────────────────
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        card.addView(arrow);

        // ── Click behaviour: highlight + proceed ────────────────────────
        card.setOnClickListener(v -> {
            // Highlight selected
            GradientDrawable selectedBg = new GradientDrawable();
            selectedBg.setColor(Color.parseColor("#EFF6FF"));
            selectedBg.setCornerRadius(dp(14));
            selectedBg.setStroke(dp(2), Color.parseColor("#0038A8"));
            card.setBackground(selectedBg);

            selectedSheetType = typeId;
            Log.d(TAG, "Selected sheet type: " + typeId);

            // Brief delay for visual feedback, then proceed
            card.postDelayed(() -> {
                dialog.dismiss();
                if ("camera".equals(action)) {
                    openCamera();
                } else {
                    openGallery();
                }
            }, 200);
        });

        return card;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void openGallery() {
        Log.d(TAG, "Opening gallery...");
        try {
            // Launch the gallery picker with image MIME type
            galleryLauncher.launch("image/*");
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery", e);
            Toast.makeText(this, "Error opening gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        Log.d(TAG, "Opening camera...");
        try {
            Intent intent = new Intent(DashboardActivity.this, CameraActivity.class);
            if (selectedSheetType != null) {
                intent.putExtra(EXTRA_SHEET_TYPE, selectedSheetType);
            }
            Log.d(TAG, "Starting CameraActivity with sheet type: " + selectedSheetType);
            startActivity(intent);
            Log.d(TAG, "CameraActivity started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "DashboardActivity resumed");
    }
}