package com.example.omrscanner;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Placeholder landing screen for Student-role accounts, opened by
 * QrScannerActivity.routeToDashboard() when the scanned QR's role is "Student".
 * Currently just a stub — no student-specific features are built yet.
 */
public class StudentDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);
    }
}