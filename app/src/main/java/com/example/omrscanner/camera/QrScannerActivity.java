// This Java file is responsible for managing QR Code Scanning
// Right now does the basic QR Scanning

package com.example.omrscanner.camera;

import android.os.Bundle;
import android.util.Base64;
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
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.example.omrscanner.R;
import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.entities.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.net.HttpURLConnection;
import java.net.URL;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

public class QrScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    // ── Global passkey this device expects a scanned QR to match ─────────
    private static final String passkey = "stars";

    private static final String SERVER_URL = "http://192.168.1.131";

    private PreviewView previewView;
    private TextView tvQrResult;
    private TextView tvFloatingHint;
    private ExecutorService cameraExecutor;
    private boolean scanned = false;
    private OMRRepository repository;
    private final Gson gson = new Gson();

    /** Shape of the decrypted QR payload JSON. */
    private static class QrPayload {
        String username;
        Integer userId;
        String passKey;
        String host;
        String firstName;
        String middleName;
        String lastName;
        String suffix;
        String schoolName;
    }

    private interface PingCallback {
        void onResult(boolean success, String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        previewView = findViewById(R.id.qrPreviewView);
        tvQrResult = findViewById(R.id.tvQrResult);
        tvFloatingHint = findViewById(R.id.tvFloatingHint);
        tvFloatingHint.postDelayed(() -> tvFloatingHint.setVisibility(android.view.View.GONE), 2000);
        repository = new OMRRepository(this);

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

    private void showQrResultDialog(String rawQrValue) {
        String decryptedJson;
        try {
            decryptedJson = decryptPayload(rawQrValue);
        } catch (Exception e) {
            android.util.Log.e("QR_SCANNER", "Decryption failed", e);
            showMessageDialog("Error", "Could not decrypt QR code: " + e.getMessage());
            return;
        }

        QrPayload payload;
        try {
            payload = gson.fromJson(decryptedJson, QrPayload.class);
        } catch (JsonSyntaxException e) {
            showMessageDialog("Error", "Decrypted data is not valid JSON.");
            return;
        }

        if (payload == null) {
            showMessageDialog("Error", "QR data is missing required fields.");
            return;
        }

        UserEntity user = new UserEntity();
        user.username = payload.username;
        user.userId = payload.userId;
        user.passkey = payload.passKey;
        user.serverIp = payload.host;
        user.firstName = payload.firstName;
        user.middleName = payload.middleName;
        user.lastName = payload.lastName;
        user.suffix = payload.suffix;
        user.school = payload.schoolName;

        pingServer((success, message) -> {
            if (!success) {
                showMessageDialog("Failed", "Could not save — server unreachable.\n" + message);
                return;
            }
            repository.insertUserAsActive(user, id -> runOnUiThread(() ->
                    showMessageDialog("Success", "User provisioned successfully.")));
        });
    }

    /**
     * Decrypts the raw string scanned from the QR code into the plaintext JSON payload.
     *
     * Matches the reference encryption tool: PBKDF2WithHmacSHA256 (100,000 iterations,
     * 256-bit key) derives the AES key from {@link #passkey} and a salt, then
     * AES/GCM/NoPadding decrypts using a 12-byte IV.
     *
     * Byte layout of the base64-decoded QR payload: salt(16) + iv(12) + ciphertext(+tag).
     */
    private String decryptPayload(String rawQrValue) throws Exception {
        byte[] combined = Base64.decode(rawQrValue, Base64.DEFAULT);

        final int SALT_LEN = 16;
        final int IV_LEN = 12;
        if (combined.length < SALT_LEN + IV_LEN) {
            throw new IllegalArgumentException("QR payload too short to contain salt/IV");
        }

        byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(combined, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] cipherBytes = Arrays.copyOfRange(combined, SALT_LEN + IV_LEN, combined.length);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passkey.toCharArray(), salt, 100_000, 256);
        SecretKey derived = factory.generateSecret(spec);
        SecretKeySpec keySpec = new SecretKeySpec(derived.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }


    private void showMessageDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    scanned = false;
                })
                .setCancelable(false)
                .show();
    }
    private void pingServer(PingCallback callback) {
        cameraExecutor.execute(() -> {
            HttpURLConnection conn = null;
            boolean success;
            String message;
            try {
                URL url = new URL(SERVER_URL);
                android.util.Log.d("PING_TEST", "Connecting to: " + SERVER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                success = true;
                message = "Server reachable — HTTP " + code;
            } catch (java.net.SocketTimeoutException e) {
                success = false;
                message = "Ping failed — timeout: " + e.getMessage();
            } catch (java.net.ConnectException e) {
                success = false;
                message = "Ping failed — connection refused: " + e.getMessage();
            } catch (java.net.UnknownHostException e) {
                success = false;
                message = "Ping failed — unknown host: " + e.getMessage();
            } catch (Exception e) {
                success = false;
                message = "Ping failed — " + e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }
            android.util.Log.d("PING_TEST", message);
            boolean finalSuccess = success;
            String finalMessage = message;
            runOnUiThread(() -> callback.onResult(finalSuccess, finalMessage));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}