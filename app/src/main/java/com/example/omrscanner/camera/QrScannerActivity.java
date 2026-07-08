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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.HttpURLConnection;
import java.net.URL;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

public class QrScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    // ── Must be the EXACT same value as USER_QR_ENCRYPTION_KEY in the
    // Laravel app's .env (including the "base64:" prefix, if present). ─────
    private static final String QR_ENCRYPTION_KEY = "1234";

    //private static final String SERVER_URL = "http://192.168.1.131";
    //private static final String SERVER_URL = "http://192.168.1.130:8000";
    private static final String SERVER_URL = "http://192.168.1.130:8000";

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
        user.middleName = payload.middleName.isEmpty() ? "" : payload.middleName;
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
     * Matches Laravel's built-in Illuminate\Encryption\Encrypter (AES-256-CBC), which is
     * what QrAuthorizationPayloadService::encryptString() uses on the server side:
     *
     *   1. The QR's raw text is base64 of a JSON object: {"iv","value","mac","tag"}.
     *   2. "iv" and "value" are themselves base64-encoded (16-byte IV, ciphertext).
     *   3. The AES key is SHA-256(rawKey) — NOT the rawKey itself, and NOT PBKDF2-derived.
     *   4. The mac is HMAC-SHA256(iv_base64 + value_base64, key), used to verify the
     *      payload hasn't been tampered with before decrypting it.
     */
    private String decryptPayload(String rawQrValue) throws Exception {
        String json = new String(Base64.decode(rawQrValue, Base64.DEFAULT), StandardCharsets.UTF_8);

        // Log the QR contents BEFORE we do anything to it (decode/verify/decrypt),
        // so we can see exactly what was scanned even if a later step fails.
        android.util.Log.d("QR_SCANNER", "Raw QR (base64): " + rawQrValue);
        android.util.Log.d("QR_SCANNER", "Decoded JSON envelope: " + json);

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        String ivB64 = obj.get("iv").getAsString();
        String valueB64 = obj.get("value").getAsString();
        String mac = obj.get("mac").getAsString();

        android.util.Log.d("QR_SCANNER", "iv=" + ivB64 + " value=" + valueB64 + " mac=" + mac);

        byte[] key = deriveKey();

        if (!constantTimeEquals(hmacSha256Hex(ivB64 + valueB64, key), mac)) {
            throw new SecurityException("MAC verification failed — payload may be corrupted or tampered with.");
        }

        byte[] iv = Base64.decode(ivB64, Base64.DEFAULT);
        byte[] cipherBytes = Base64.decode(valueB64, Base64.DEFAULT);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        String plaintext = new String(plainBytes, StandardCharsets.UTF_8);

        android.util.Log.d("QR_SCANNER", "Decrypted plaintext: " + plaintext);

        return plaintext;
    }

    /**
     * Mirrors QrAuthorizationPayloadService::encrypter(): strips an optional "base64:"
     * prefix from the configured key, then SHA-256-hashes the raw key bytes to get the
     * 32-byte AES key.
     */
    private byte[] deriveKey() throws NoSuchAlgorithmException {
        String rawKey = QR_ENCRYPTION_KEY;
        byte[] keyBytes;
        if (rawKey.startsWith("base64:")) {
            keyBytes = Base64.decode(rawKey.substring("base64:".length()), Base64.DEFAULT);
        } else {
            keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        }
        return MessageDigest.getInstance("SHA-256").digest(keyBytes);
    }

    private static String hmacSha256Hex(String data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : result) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Constant-time string comparison to avoid timing attacks on the MAC check. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
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