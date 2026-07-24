package com.example.omrscanner.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.DashboardActivity;
import com.example.omrscanner.R;
import com.example.omrscanner.camera.BasicCameraActivity;

public class BasicPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "basic_preview_image_path";

    private String imagePath;
    private String selectedSheetType;
    private String classId;
    private String activityId;
    private Bitmap displayedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_preview);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.hide(WindowInsetsCompat.Type.systemBars());

        ImageView imagePreview = findViewById(R.id.basicImagePreview);
        Button btnRetake = findViewById(R.id.btnBasicRetake);
        Button btnDone = findViewById(R.id.btnBasicDone);

        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        selectedSheetType = getIntent().getStringExtra(DashboardActivity.EXTRA_SHEET_TYPE);
        classId = getIntent().getStringExtra(DashboardActivity.EXTRA_CLASS_ID);
        activityId = getIntent().getStringExtra(DashboardActivity.EXTRA_ACTIVITY_ID);
        int rotationBucket = getIntent().getIntExtra(
                BasicCameraActivity.EXTRA_CAPTURE_ROTATION_BUCKET, 0);

        if (imagePath == null) {
            Toast.makeText(this, "No image to preview", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Bitmap raw = BitmapFactory.decodeFile(imagePath);
        if (raw == null) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        displayedBitmap = rotateToNormalOrientation(raw, rotationBucket);
        imagePreview.setImageBitmap(displayedBitmap);

        btnRetake.setOnClickListener(v -> retakePhoto());
        btnDone.setOnClickListener(v -> finish());
    }

    private Bitmap rotateToNormalOrientation(Bitmap raw, int rotationBucket) {
        float degrees;
        switch (rotationBucket) {
            case -90: degrees = 90f; break;
            case 90:  degrees = -90f; break;
            case 180: degrees = 180f; break;
            default:  return raw;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);
        if (rotated != raw) raw.recycle();
        return rotated;
    }

    private void retakePhoto() {
        Intent intent = new Intent(this, BasicCameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (selectedSheetType != null) intent.putExtra(DashboardActivity.EXTRA_SHEET_TYPE, selectedSheetType);
        if (classId != null) intent.putExtra(DashboardActivity.EXTRA_CLASS_ID, classId);
        if (activityId != null) intent.putExtra(DashboardActivity.EXTRA_ACTIVITY_ID, activityId);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (displayedBitmap != null && !displayedBitmap.isRecycled()) {
            displayedBitmap.recycle();
        }
    }
}