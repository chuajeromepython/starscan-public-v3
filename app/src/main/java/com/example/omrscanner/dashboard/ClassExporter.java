package com.example.omrscanner.dashboard;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.omrscanner.database.DataMapper;
import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles automatic class-data saving: every time a scan is saved, the whole
 * assessment's images + CSVs are re-written to Downloads/OMRScanner/... in
 * place (no dialog, no password, no zip). File names are LRN-based, so
 * re-saving the same student just overwrites their old file.
 */
public class ClassExporter {

    private static final String TAG = "ClassExporter";
    private static final long EXPORT_IMAGE_TARGET_BYTES = 100L * 1024L;
    private static final int  EXPORT_IMAGE_MIN_EDGE_PX  = 900;

    private ClassExporter() { /* static utility class — no instances */ }

    // ─────────────────────────────────────────────────────────────
    // Entry point — called from DashboardActivity.saveScanResult
    // ─────────────────────────────────────────────────────────────

    public static void autoSaveClassData(android.content.Context context, String classId, String activityId) {
        new Thread(() -> {
            try {
                OMRRepository r = new OMRRepository(context);
                ClassEntity ce = r.getClassByIdSync(classId);
                AssessmentEntity ae = r.getAssessmentByIdSync(activityId);
                if (ce == null || ae == null) return;

                ActivityFolder af = DataMapper.toActivityFolder(ae);
                List<ScanEntry> scans = new ArrayList<>();
                for (ScanEntity se : r.getScansByAssessmentSync(activityId)) {
                    List<AnswerEntity> answers = r.getAnswersByScanSync(se.id);
                    scans.add(DataMapper.toScanEntry(se, DataMapper.toAnswerMap(answers)));
                }
                af.setScans(scans);

                ClassFolder cf = DataMapper.toClassFolder(ce, "");
                cf.setActivities(Collections.singletonList(af));

                writeClassFolderPlain(cf);
            } catch (Exception e) {
                Log.e(TAG, "Auto-save failed", e);
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // Save pipeline
    // ─────────────────────────────────────────────────────────────

    private static void writeClassFolderPlain(ClassFolder cls) throws Exception {
        File downloadsDir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File omrDir = new File(downloadsDir, "OMRScanner");
        if (!omrDir.exists() && !omrDir.mkdirs()) {
            throw new IllegalStateException("Unable to create Downloads/OMRScanner");
        }

        String folderName = buildClassFolderName(cls);
        File classDir = new File(omrDir, folderName);
        if (!classDir.exists() && !classDir.mkdirs()) throw new IllegalStateException("Unable to create export folder");

        for (ActivityFolder act : cls.getActivities()) {
            List<ScanEntry> scans = act.getScans();
            if (scans == null || scans.isEmpty()) continue;

            String sectionStr  = cls.getSection() != null ? cls.getSection().replaceAll("\\s+", "") : "Section";
            String actDirName  = sanitizeFilePart(sectionStr + "_" + act.getName());
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
            }

            StringBuilder actCsv = new StringBuilder();
            for (ScanEntry scan : scans) {
                // Never let a scan with unresolved multi-letter answers (e.g. "AC")
                // reach the CSV that gets uploaded to STARS — it must be corrected
                // to a single letter (or blank) in the app first.
                if (scan.needsAnswerCorrection()) continue;

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
        }
    }

    /**
     * Returns the path to the per-assessment CSV that autoSaveClassData writes
     * on every scan (semicolon-delimited: LRN split into 12 single-digit
     * columns, then one column per item's answer) — this is the exact file
     * format the STARS /api/upload/assessment endpoint expects.
     * Does NOT write/refresh the file — call autoSaveClassData for that.
     */
    public static File getAssessmentCsvFile(ClassFolder cls, ActivityFolder act) {
        File downloadsDir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File omrDir = new File(downloadsDir, "OMRScanner");
        String folderName = buildClassFolderName(cls);
        File classDir = new File(omrDir, folderName);

        String sectionStr = cls.getSection() != null ? cls.getSection().replaceAll("\\s+", "") : "Section";
        String actDirName = sanitizeFilePart(sectionStr + "_" + act.getName());
        File actDir = new File(classDir, actDirName);

        return new File(actDir, folderName + "_" + sanitizeFilePart(act.getName()) + ".csv");
    }

    // ─────────────────────────────────────────────────────────────
    // File / image utilities
    // ─────────────────────────────────────────────────────────────

    private static String buildClassFolderName(ClassFolder cls) {
        String grade   = cls.getGrade()   != null ? cls.getGrade().replaceAll("\\s+", "")   : "Class";
        String section = cls.getSection() != null ? cls.getSection().replaceAll("\\s+", "") : "Section";
        return sanitizeFilePart(grade + "-" + section);
    }

    private static String sanitizeFilePart(String value) {
        String s = value != null ? value.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim() : "";
        s = s.replaceAll("\\s+", "_");
        return s.isEmpty() ? "item" : s;
    }

    private static void writeTextFile(File target, String content) throws Exception {
        try (FileWriter writer = new FileWriter(target)) {
            writer.write(content);
        }
    }

    private static boolean compressImageForExport(File srcFile, File destFile) throws Exception {
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
}