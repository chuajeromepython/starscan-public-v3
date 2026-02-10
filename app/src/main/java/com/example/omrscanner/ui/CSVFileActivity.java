package com.example.omrscanner.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class CSVFileActivity extends AppCompatActivity {

    public static final String EXTRA_CSV_FILEPATH = "csv_filepath";
    public static final String EXTRA_IMAGE_FILEPATH = "image_filepath";

    private TextView tvFilePath;
    private TextView tvFileInfo;
    private Button btnShare;
    private Button btnSaveToDownloads;
    private Button btnDone;

    private String csvFilePath;
    private String imageFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_file);

        // Set status bar color
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

        // Initialize views
        tvFilePath = findViewById(R.id.tvFilePath);
        tvFileInfo = findViewById(R.id.tvFileInfo);
        btnShare = findViewById(R.id.btnShare);
        btnSaveToDownloads = findViewById(R.id.btnSaveToDownloads);
        btnDone = findViewById(R.id.btnDone);

        // Get file paths from intent
        csvFilePath = getIntent().getStringExtra(EXTRA_CSV_FILEPATH);
        imageFilePath = getIntent().getStringExtra(EXTRA_IMAGE_FILEPATH);

        if (csvFilePath == null) {
            Toast.makeText(this, "Error: No CSV file provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Display file info
        File csvFile = new File(csvFilePath);
        String fileName = csvFile.getName();
        long fileSize = csvFile.length();

        tvFilePath.setText("File: " + fileName);
        tvFileInfo.setText(String.format("Size: %.2f KB", fileSize / 1024.0));

        // Button listeners
        btnShare.setOnClickListener(v -> shareCSV());
        btnSaveToDownloads.setOnClickListener(v -> saveToDownloads());
        btnDone.setOnClickListener(v -> finish());
    }

    /**
     * Share CSV file using Android's share intent
     */
    private void shareCSV() {
        try {
            File csvFile = new File(csvFilePath);

            // Use FileProvider to get content URI
            Uri csvUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    csvFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, csvUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "OMR Scan Results");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "OMR scan results attached.");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share CSV File"));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error sharing file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Save CSV file to Downloads folder
     */
    private void saveToDownloads() {
        try {
            File csvFile = new File(csvFilePath);
            String fileName = csvFile.getName();

            // Get Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            File destFile = new File(downloadsDir, fileName);

            // Copy file to Downloads
            copyFile(csvFile, destFile);

            Toast.makeText(this,
                    "File saved to Downloads: " + fileName,
                    Toast.LENGTH_LONG).show();

            // Open file manager to show the file
            openFileManager(destFile);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this,
                    "Error saving file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copy file from source to destination
     */
    private void copyFile(File source, File dest) throws IOException {
        FileInputStream inStream = new FileInputStream(source);
        FileOutputStream outStream = new FileOutputStream(dest);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = inStream.read(buffer)) > 0) {
            outStream.write(buffer, 0, length);
        }

        inStream.close();
        outStream.close();
    }

    /**
     * Open file manager to show the saved file
     */
    private void openFileManager(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "text/csv");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(Intent.createChooser(intent, "Open CSV with"));

        } catch (Exception e) {
            // If can't open CSV viewer, just show file manager
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri downloadsUri = Uri.parse(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).getPath()
            );
            intent.setDataAndType(downloadsUri, "resource/folder");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up temporary cache file
        if (csvFilePath != null) {
            File csvFile = new File(csvFilePath);
            if (csvFile.exists() && csvFile.getParent().contains("cache")) {
                // Only delete if it's in cache directory
                csvFile.delete();
            }
        }
    }
}