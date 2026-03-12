package com.example.omrscanner.dashboard;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Handles the full class-data export/download flow: password dialog → ZIP creation → success dialog.
 */
public class ClassExporter {

    private static final String TAG = "ClassExporter";
    private static final long EXPORT_IMAGE_TARGET_BYTES = 100L * 1024L;
    private static final int  EXPORT_IMAGE_MIN_EDGE_PX  = 900;

    private final AppCompatActivity activity;
    private final DashboardUiHelper ui;

    public ClassExporter(AppCompatActivity activity, DashboardUiHelper ui) {
        this.activity = activity;
        this.ui = ui;
    }

    // ─────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────

    public void downloadClassData(ClassFolder cls) {
        if (cls.getActivities() == null || cls.getActivities().isEmpty()) {
            ui.showToast("No data to download");
            return;
        }
        showDownloadPasswordDialog(cls);
    }

    // ─────────────────────────────────────────────────────────────
    // Password dialog
    // ─────────────────────────────────────────────────────────────

    private void showDownloadPasswordDialog(ClassFolder cls) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);

        TextInputLayout passwordLayout = new TextInputLayout(activity);
        passwordLayout.setHintEnabled(false);
        passwordLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        passwordLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText passwordInput = new TextInputEditText(passwordLayout.getContext());
        passwordInput.setHint("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setBackground(createInputBg());
        passwordInput.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        passwordLayout.addView(passwordInput);

        TextInputLayout confirmLayout = new TextInputLayout(activity);
        confirmLayout.setHintEnabled(false);
        confirmLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        confirmLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText confirmInput = new TextInputEditText(confirmLayout.getContext());
        confirmInput.setHint("Confirm password");
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmInput.setBackground(createInputBg());
        confirmInput.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        confirmLayout.addView(confirmInput);

        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldLp.bottomMargin = ui.dp(10);
        passwordLayout.setLayoutParams(fieldLp);
        confirmLayout.setLayoutParams(fieldLp);

        content.addView(passwordLayout);
        content.addView(confirmLayout);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Protect Download")
                .setMessage("Set a password for this ZIP file.")
                .setView(content)
                .setPositiveButton("Download", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText() != null ? passwordInput.getText().toString().trim() : "";
            String confirm  = confirmInput.getText()  != null ? confirmInput.getText().toString().trim()  : "";

            if (password.isEmpty()) {
                passwordInput.setError("Password is required");
                return;
            }
            if (!password.equals(confirm)) {
                confirmInput.setError("Passwords do not match");
                return;
            }
            dialog.dismiss();
            ui.showToast("Preparing protected download...");
            new Thread(() -> runProtectedClassDownload(cls, password)).start();
        }));

        dialog.show();
    }

    private GradientDrawable createInputBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(ui.dp(8));
        bg.setStroke(ui.dp(1), Color.parseColor("#CBD5E1"));
        return bg;
    }

    // ─────────────────────────────────────────────────────────────
    // Export pipeline
    // ─────────────────────────────────────────────────────────────

    private void runProtectedClassDownload(ClassFolder cls, String password) {
        try {
            DownloadExportResult result = exportClassDataToProtectedZip(cls, password);
            activity.runOnUiThread(() -> showDownloadSuccessDialog(cls, result.zipFile,
                    result.totalImages, result.totalCsvs));
        } catch (Exception e) {
            Log.e(TAG, "Error downloading class data", e);
            activity.runOnUiThread(() -> ui.showToast("Error exporting: " + e.getMessage()));
        }
    }

    private DownloadExportResult exportClassDataToProtectedZip(ClassFolder cls, String password) throws Exception {
        File downloadsDir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File omrDir = new File(downloadsDir, "OMRScanner");
        if (!omrDir.exists() && !omrDir.mkdirs()) {
            throw new IllegalStateException("Unable to create Downloads/OMRScanner");
        }

        String folderName = buildClassFolderName(cls);
        String timestamp  = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File stagingRoot  = new File(activity.getCacheDir(), "omr_export_" + System.currentTimeMillis());
        File classDir     = new File(stagingRoot, folderName);
        if (!classDir.mkdirs()) throw new IllegalStateException("Unable to create export folder");

        int totalImages = 0, totalCsvs = 0;

        try {
            for (ActivityFolder act : cls.getActivities()) {
                List<ScanEntry> scans = act.getScans();
                if (scans == null || scans.isEmpty()) continue;

                String sectionStr  = cls.getSection() != null ? cls.getSection().replaceAll("\\s+", "") : "Section";
                String actDirName  = sanitizeFilePart(sectionStr + "_" + act.getName() + "_" + timestamp);
                File actDir        = new File(classDir, actDirName);
                File imagesDir     = new File(actDir, "images");
                File resultsDir    = new File(actDir, "result");
                if (!imagesDir.exists() && !imagesDir.mkdirs())  throw new IllegalStateException("Unable to create " + imagesDir.getName());
                if (!resultsDir.exists() && !resultsDir.mkdirs()) throw new IllegalStateException("Unable to create " + resultsDir.getName());

                int scanNum = 0;
                for (ScanEntry scan : scans) {
                    scanNum++;
                    String srcPath = scan.getOverlayImagePath();
                    if (srcPath == null || !new File(srcPath).exists()) srcPath = scan.getImagePath();
                    if (srcPath != null) {
                        File srcFile = new File(srcPath);
                        if (srcFile.exists()) {
                            String lrnPart = (scan.getLrn() != null && !scan.getLrn().isEmpty())
                                    ? scan.getLrn().replaceAll("[^a-zA-Z0-9]", "") : "scan_" + scanNum;
                            File dest = new File(imagesDir, lrnPart + ".jpg");
                            if (!compressImageForExport(srcFile, dest)) {
                                Log.w(TAG, "Export image still above target size: " + dest.getName());
                            }
                            if (dest.exists()) totalImages++;
                        }
                    }

                    String lrnOnly  = (scan.getLrn() != null && !scan.getLrn().isEmpty()) ? scan.getLrn() : "scan_" + scanNum;
                    String indName  = (lrnOnly + "_" + cls.getGrade() + "-" + cls.getSection() + "_"
                            + act.getName().replaceAll("\\s+", "") + ".csv")
                            .replaceAll("[^a-zA-Z0-9_\\-.]]", "_");
                    StringBuilder sb = new StringBuilder();
                    String lrnVal = scan.getLrn() != null ? scan.getLrn() : "";
                    for (int c = 0; c < lrnVal.length(); c++) sb.append(lrnVal.charAt(c)).append(";");
                    for (int k = 1; k <= act.getNumItems(); k++) {
                        String ans = scan.getAnswers() != null ? scan.getAnswers().get(k) : null;
                        sb.append(ans != null ? ans : "");
                        if (k < act.getNumItems()) sb.append(";");
                    }
                    sb.append("\n");
                    writeTextFile(new File(resultsDir, indName), sb.toString());
                    totalCsvs++;
                }

                StringBuilder actCsv = new StringBuilder();
                for (ScanEntry scan : scans) {
                    String lrnVal = scan.getLrn() != null ? scan.getLrn() : "";
                    for (int c = 0; c < lrnVal.length(); c++) actCsv.append(lrnVal.charAt(c)).append(";");
                    for (int i = 1; i <= act.getNumItems(); i++) {
                        String ans = scan.getAnswers() != null ? scan.getAnswers().get(i) : null;
                        actCsv.append(ans != null ? ans : "");
                        if (i < act.getNumItems()) actCsv.append(";");
                    }
                    actCsv.append("\n");
                }
                File csvFile = new File(actDir, folderName + "_" + sanitizeFilePart(act.getName()) + ".csv");
                writeTextFile(csvFile, actCsv.toString());
                totalCsvs++;
            }

            File targetClassDir = new File(omrDir, folderName);
            if (!targetClassDir.exists() && !targetClassDir.mkdirs()) {
                throw new IllegalStateException("Unable to create class directory inside Downloads/OMRScanner");
            }
            File zipFile = new File(targetClassDir, folderName + "_" + timestamp + ".zip");
            createEncryptedZip(classDir, zipFile, password);
            scanMediaFile(zipFile);
            return new DownloadExportResult(zipFile, totalImages, totalCsvs);
        } finally {
            deleteRecursively(stagingRoot);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // File / image utilities
    // ─────────────────────────────────────────────────────────────

    private String buildClassFolderName(ClassFolder cls) {
        String grade   = cls.getGrade()   != null ? cls.getGrade().replaceAll("\\s+", "")   : "Class";
        String section = cls.getSection() != null ? cls.getSection().replaceAll("\\s+", "") : "Section";
        return sanitizeFilePart(grade + "-" + section);
    }

    private String sanitizeFilePart(String value) {
        String s = value != null ? value.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim() : "";
        s = s.replaceAll("\\s+", "_");
        return s.isEmpty() ? "item" : s;
    }

    private void writeTextFile(File target, String content) throws Exception {
        try (FileWriter writer = new FileWriter(target)) {
            writer.write(content);
        }
    }

    private boolean compressImageForExport(File srcFile, File destFile) throws Exception {
        Bitmap original = BitmapFactory.decodeFile(srcFile.getAbsolutePath());
        if (original == null) return false;

        Bitmap working = original;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bestBytes = null;
        boolean hitTarget = false;

        try {
            while (true) {
                for (int quality = 92; quality >= 35; quality -= 7) {
                    baos.reset();
                    working.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                    byte[] current = baos.toByteArray();
                    if (bestBytes == null || current.length < bestBytes.length) bestBytes = current;
                    if (current.length <= EXPORT_IMAGE_TARGET_BYTES) {
                        bestBytes = current;
                        hitTarget = true;
                        break;
                    }
                }
                if (hitTarget) break;

                int minEdge = Math.min(working.getWidth(), working.getHeight());
                if (minEdge <= EXPORT_IMAGE_MIN_EDGE_PX) break;

                int nextW = Math.max(EXPORT_IMAGE_MIN_EDGE_PX, Math.round(working.getWidth() * 0.85f));
                int nextH = Math.max(EXPORT_IMAGE_MIN_EDGE_PX, Math.round(working.getHeight() * 0.85f));
                if (nextW >= working.getWidth() || nextH >= working.getHeight()) break;

                Bitmap scaled = Bitmap.createScaledBitmap(working, nextW, nextH, true);
                if (working != original) working.recycle();
                working = scaled;
            }

            if (bestBytes == null) return false;
            try (FileOutputStream fos = new FileOutputStream(destFile)) { fos.write(bestBytes); }
            return bestBytes.length <= EXPORT_IMAGE_TARGET_BYTES;
        } finally {
            if (working != original) working.recycle();
            original.recycle();
        }
    }

    private void createEncryptedZip(File sourceDir, File zipFile, String password) throws Exception {
        ZipParameters zipParams = new ZipParameters();
        zipParams.setEncryptFiles(true);
        zipParams.setEncryptionMethod(EncryptionMethod.AES);
        zipParams.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        ZipFile protectedZip = new ZipFile(zipFile, password.toCharArray());
        File[] children = sourceDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) protectedZip.addFolder(child, zipParams);
                else                     protectedZip.addFile(child, zipParams);
            }
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) Log.w(TAG, "Unable to delete temp path: " + file.getAbsolutePath());
    }

    private void scanMediaFile(File file) {
        android.media.MediaScannerConnection.scanFile(
                activity, new String[]{file.getAbsolutePath()}, null, null);
    }

    // ─────────────────────────────────────────────────────────────
    // Success dialog
    // ─────────────────────────────────────────────────────────────

    private void showDownloadSuccessDialog(ClassFolder cls, File zipFile, int totalImages, int totalCsvs) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = ui.buildSheet();
        root.addView(ui.createDialogHandle());

        TextView iconView = new TextView(activity);
        iconView.setText("✅");
        iconView.setTextSize(40);
        iconView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = ui.dp(12);
        iconView.setLayoutParams(ilp);
        root.addView(iconView);

        root.addView(ui.buildSheetTitle("Download Complete!", "#16A34A", Gravity.CENTER, 12));

        TextView info = new TextView(activity);
        info.setText("🔒 " + cls.getDisplayName() + "\n🖼️ " + totalImages
                + " image" + (totalImages != 1 ? "s" : "")
                + "  •  📄 " + totalCsvs + " CSV" + (totalCsvs != 1 ? "s" : ""));
        info.setTextSize(13);
        info.setTextColor(Color.parseColor("#64748B"));
        info.setGravity(Gravity.CENTER);
        info.setLineSpacing(ui.dp(4), 1f);
        LinearLayout.LayoutParams inlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inlp.bottomMargin = ui.dp(8);
        info.setLayoutParams(inlp);
        root.addView(info);

        TextView pathLabel = new TextView(activity);
        pathLabel.setText("📦 Downloads/OMRScanner/" + zipFile.getParentFile().getName() + "/" + zipFile.getName());
        pathLabel.setTextSize(11);
        pathLabel.setTextColor(Color.parseColor("#0038A8"));
        pathLabel.setGravity(Gravity.CENTER);
        pathLabel.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        GradientDrawable pathBg = new GradientDrawable();
        pathBg.setColor(Color.parseColor("#F0F7FF"));
        pathBg.setCornerRadius(ui.dp(8));
        pathBg.setStroke(ui.dp(1), Color.parseColor("#BFDBFE"));
        pathLabel.setBackground(pathBg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.bottomMargin = ui.dp(20);
        pathLabel.setLayoutParams(plp);
        root.addView(pathLabel);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnOpen = ui.createDialogButton("Open Downloads", true);
        TextView btnDone = ui.createDialogButton("Done", false);
        actions.addView(btnOpen);
        actions.addView(ui.spacer(ui.dp(10)));
        actions.addView(btnDone);
        root.addView(actions);

        btnOpen.setOnClickListener(v -> { dialog.dismiss(); openFolderInFileManager(zipFile.getParentFile()); });
        btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        ui.configureBottomDialog(dialog);
        dialog.show();
    }

    private void openFolderInFileManager(File folder) {
        if (folder == null) { ui.showToast("File saved to: Downloads/OMRScanner/.../"); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(folder.getAbsolutePath()), "resource/folder");
            if (intent.resolveActivity(activity.getPackageManager()) != null) activity.startActivity(intent);
            else ui.showToast("File saved to: Downloads/OMRScanner/.../");
        } catch (Exception e) {
            ui.showToast("File saved to: Downloads/OMRScanner/.../");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Result holder
    // ─────────────────────────────────────────────────────────────

    private static class DownloadExportResult {
        final File zipFile;
        final int  totalImages;
        final int  totalCsvs;
        DownloadExportResult(File zipFile, int totalImages, int totalCsvs) {
            this.zipFile     = zipFile;
            this.totalImages = totalImages;
            this.totalCsvs   = totalCsvs;
        }
    }
}
