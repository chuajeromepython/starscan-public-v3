package com.example.omrscanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class Login extends AppCompatActivity {

    private EditText gradeInput;
    private EditText sectionInput;
    private EditText teacherNameInput;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Set status bar color to blue
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

        // Initialize views
        gradeInput = findViewById(R.id.gradeInput);
        sectionInput = findViewById(R.id.sectionInput);
        teacherNameInput = findViewById(R.id.teacherNameInput);
        loginButton = findViewById(R.id.loginButton);

        // Set login button click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();
            }
        });
    }

    private void performLogin() {
        // Get input values
        String grade = gradeInput.getText().toString().trim();
        String section = sectionInput.getText().toString().trim();
        String teacherName = teacherNameInput.getText().toString().trim();

        // Validate inputs
        if (grade.isEmpty()) {
            gradeInput.setError("Please enter grade level");
            gradeInput.requestFocus();
            return;
        }

        if (section.isEmpty()) {
            sectionInput.setError("Please enter section");
            sectionInput.requestFocus();
            return;
        }

        if (teacherName.isEmpty()) {
            teacherNameInput.setError("Please enter teacher name");
            teacherNameInput.requestFocus();
            return;
        }

        // All validations passed - proceed with login
        handleSuccessfulLogin(grade, section, teacherName);
    }

    private void handleSuccessfulLogin(String grade, String section, String teacherName) {
        // Save the login data
        saveLoginData(grade, section, teacherName);

        // Show success message
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();

        // Navigate to Dashboard (CHANGED from CameraActivity)
        Intent intent = new Intent(Login.this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void saveLoginData(String grade, String section, String teacherName) {
        // Save to SharedPreferences
        android.content.SharedPreferences prefs =
                getSharedPreferences("OMRScannerPrefs", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("grade", grade);
        editor.putString("section", section);
        editor.putString("teacherName", teacherName);
        editor.apply();
    }
}