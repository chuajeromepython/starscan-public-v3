package com.example.omrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.omrscanner.camera.CameraActivity;
import com.google.android.material.button.MaterialButton;

public class DashboardActivity extends AppCompatActivity {

    private MaterialButton tabScan, tabCSV, tabResults;
    private TextView teacherInfo;
    private CardView cardUpload, cardScan;
    private View scanContent, csvContent, resultsContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

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
        // TODO: Implement gallery picker
        android.widget.Toast.makeText(this, "Opening gallery...", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openCamera() {
        Intent intent = new Intent(DashboardActivity.this, CameraActivity.class);
        startActivity(intent);
    }
}