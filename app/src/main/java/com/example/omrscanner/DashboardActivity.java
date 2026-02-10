package com.example.omrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
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

    private MaterialButton tabScan, tabCSV, tabResults;
    private TextView teacherInfo;
    private CardView cardUpload, cardScan;
    private View scanContent, csvContent, resultsContent;

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
        cardUpload.setOnClickListener(v -> openGallery());
        cardScan.setOnClickListener(v -> openCamera());

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
                    // Launch PreviewActivity with the saved image path
                    runOnUiThread(() -> {
                        Intent intent = new Intent(DashboardActivity.this,
                                com.example.omrscanner.ui.PreviewActivity.class);
                        intent.putExtra(com.example.omrscanner.ui.PreviewActivity.IMAGE_PATH, savedPath);
                        intent.putExtra(com.example.omrscanner.ui.PreviewActivity.IMAGE_SOURCE,
                                com.example.omrscanner.ui.PreviewActivity.SOURCE_GALLERY);
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

    private void openGallery() {
        // Launch the gallery picker with image MIME type
        galleryLauncher.launch("image/*");
    }

    private void openCamera() {
        Intent intent = new Intent(DashboardActivity.this, CameraActivity.class);
        startActivity(intent);
    }
}