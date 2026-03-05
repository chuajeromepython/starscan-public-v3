package com.example.omrscanner.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.omrscanner.R;
import com.example.omrscanner.ui.widgets.ZoomableImageView;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FullImageViewerActivity extends AppCompatActivity {

    private ZoomableImageView fullScreenImage;
    private MaterialButton btnClose;
    private TextView tvFilename;
    private TextView tvDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_image_viewer);

        // Full screen — hide status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Initialize views
        fullScreenImage = findViewById(R.id.fullScreenImage);
        btnClose = findViewById(R.id.btnClose);
        tvFilename = findViewById(R.id.tvFilename);
        tvDate = findViewById(R.id.tvDate);

        // Get data from intent
        String imageUri = getIntent().getStringExtra("image_uri");
        String filename = getIntent().getStringExtra("filename");
        long timestamp = getIntent().getLongExtra("timestamp", System.currentTimeMillis());

        // Load and display image
        if (imageUri != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imageUri);
            if (bitmap != null) {
                fullScreenImage.setImageBitmap(bitmap);
            }
        }

        // Set filename
        if (filename != null) {
            tvFilename.setText(filename);
        }

        // Format and set date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        String formattedDate = dateFormat.format(new Date(timestamp));
        tvDate.setText(formattedDate);

        // Close button
        btnClose.setOnClickListener(v -> finish());
    }
}