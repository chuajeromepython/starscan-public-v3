package com.example.omrscanner.utils;

import android.content.Context;
import android.util.Log;

import com.example.omrscanner.omr.ScanResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Exports {@link ScanResult} data to CSV files.
 *
 * <h3>CSV format</h3>
 * <pre>
 * Timestamp,TemplateID,LNR,Q1,Q2,Q3,...,Qn
 * 2026-02-12 14:30:05,ZPH30,123456789012,A,B,,C,AC,...
 * </pre>
 *
 * <ul>
 *   <li>Timestamp — ISO-style date/time of the scan</li>
 *   <li>TemplateID — detected sheet type (ZPH30 / ZPH50 / ZPH60)</li>
 *   <li>LNR — 12-digit student ID</li>
 *   <li>Q1..Qn — detected answer letters per question (blank if none detected,
 *       multi-letter like "AC" if multiple bubbles filled)</li>
 * </ul>
 */
public class CsvHelper {

    private static final String TAG = "CsvHelper";

    private static final SimpleDateFormat FILE_DATE_FMT =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    private static final SimpleDateFormat ROW_DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    /**
     * Export a single scan result to a new CSV file in the app's cache directory.
     *
     * @param context    Android context
     * @param scanResult the completed scan result
     * @return absolute path of the written CSV file, or null on failure
     */
    public static String exportToCSV(Context context, ScanResult scanResult) {
        if (scanResult == null) {
            Log.e(TAG, "exportToCSV: scanResult is null");
            return null;
        }

        Date now = new Date();
        String lrnStr = safe(scanResult.lnr);
        if (lrnStr.isEmpty()) {
            lrnStr = "UnknownLRN";
        }
        String fileName = lrnStr + "_OMR_" + FILE_DATE_FMT.format(now) + ".csv";

        File cacheDir = context.getCacheDir();
        File csvFile = new File(cacheDir, fileName);

        try (FileWriter writer = new FileWriter(csvFile)) {
            int qCount = scanResult.getQuestionCount();

            // ── Data row ────────────────────────────────────────────────
            StringBuilder row = new StringBuilder();
            String lrnVal = safe(scanResult.lnr);
            for (int c = 0; c < lrnVal.length(); c++) {
                row.append(lrnVal.charAt(c)).append(';');
            }

            for (int q = 1; q <= qCount; q++) {
                String answer = scanResult.answers.get(q);
                row.append(safe(answer));
                if (q < qCount) {
                    row.append(';');
                }
            }
            writer.append(row).append('\n');

            writer.flush();

            Log.d(TAG, "CSV written: " + csvFile.getAbsolutePath());
            Log.d(TAG, String.format("Template=%s, LNR=%s, Questions=%d, Answered=%d",
                    scanResult.templateId, scanResult.lnr,
                    qCount, scanResult.getAnsweredCount()));

            return csvFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV", e);
            return null;
        }
    }

    /**
     * Append a scan result as a new row to an existing CSV file.
     * If the file does not exist it is created with a header row.
     *
     * @param csvFile    target file
     * @param scanResult the completed scan result
     * @return true on success
     */
    public static boolean appendToCSV(File csvFile, ScanResult scanResult) {
        if (scanResult == null) {
            Log.e(TAG, "appendToCSV: scanResult is null");
            return false;
        }

        boolean needsHeader = !csvFile.exists() || csvFile.length() == 0;

        try (FileWriter writer = new FileWriter(csvFile, true)) {
            int qCount = scanResult.getQuestionCount();

            StringBuilder row = new StringBuilder();
            String lrnVal = safe(scanResult.lnr);
            for (int c = 0; c < lrnVal.length(); c++) {
                row.append(lrnVal.charAt(c)).append(';');
            }

            for (int q = 1; q <= qCount; q++) {
                String answer = scanResult.answers.get(q);
                row.append(safe(answer));
                if (q < qCount) {
                    row.append(';');
                }
            }
            writer.append(row).append('\n');

            writer.flush();

            Log.d(TAG, "Row appended to: " + csvFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error appending to CSV", e);
            return false;
        }
    }

    /**
     * Build a human-readable summary string for display in the UI.
     */
    public static String buildSummary(ScanResult scanResult) {
        if (scanResult == null) return "No result";

        StringBuilder sb = new StringBuilder();
        sb.append("Template: ").append(safe(scanResult.templateId)).append('\n');
        sb.append("Student ID (LNR): ").append(safe(scanResult.lnr)).append('\n');
        sb.append("Questions: ").append(scanResult.getQuestionCount()).append('\n');
        sb.append("Answered: ").append(scanResult.getAnsweredCount()).append('\n');

        sb.append('\n');
        for (Map.Entry<Integer, String> entry : scanResult.answers.entrySet()) {
            String ans = entry.getValue();
            sb.append("Q").append(entry.getKey()).append(": ");
            sb.append(ans == null || ans.isEmpty() ? "-" : ans);
            sb.append('\n');
        }

        return sb.toString();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Return the string or empty if null. */
    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
