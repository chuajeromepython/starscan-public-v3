// This Java file is responsible for managing QR Code Scanning
// Right now does the basic QR Scanning

package com.example.omrscanner.camera;

import android.os.Bundle;
import android.widget.ImageButton;
import androidx.appcompat.app.AlertDialog;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.example.omrscanner.R;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

public class QrScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private PreviewView previewView;
    private TextView tvQrResult;
    private ExecutorService cameraExecutor;
    private boolean scanned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        previewView = findViewById(R.id.qrPreviewView);
        tvQrResult = findViewById(R.id.tvQrResult);

        findViewById(R.id.btnCloseQr).setOnClickListener(v -> finish());

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            tvQrResult.setText("Camera permission is required.");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build();
                BarcodeScanner scanner = BarcodeScanning.getClient(options);

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (scanned) {
                        imageProxy.close();
                        return;
                    }
                    @SuppressWarnings("UnsafeOptInUsageError")
                    InputImage image = InputImage.fromMediaImage(
                            imageProxy.getImage(),
                            imageProxy.getImageInfo().getRotationDegrees());

                    scanner.process(image)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty() && !scanned) {
                                    scanned = true;
                                    String value = barcodes.get(0).getRawValue();
                                    android.util.Log.d("QR_SCANNER", "Scanned: " + value); // for debugging purposes
                                    runOnUiThread(() -> showQrResultDialog(value));                                }
                            })
                            .addOnCompleteListener(task -> imageProxy.close());
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showQrResultDialog(String result) {
        new AlertDialog.Builder(this)
                .setTitle("QR Code Scanned")
                .setMessage(result)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    scanned = false;
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}