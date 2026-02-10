package com.example.omrscanner.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for exporting OMR results to CSV format
 */
public class CSVExporter {

    /**
     * Export OMR results to CSV file
     *
     * @param context Application context
     * @param answers Map of question number to answer (A, B, C, D)
     * @param totalQuestions Total number of questions
     * @return File path of generated CSV, or null if failed
     */
    public static String exportToCSV(Context context, Map<Integer, Character> answers, int totalQuestions) {
        try {
            // Create CSV directory if it doesn't exist
            File csvDir = new File(context.getFilesDir(), "csv");
            if (!csvDir.exists()) {
                csvDir.mkdirs();
            }

            // Generate filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            String filename = "omr_results_" + timestamp + ".csv";
            File csvFile = new File(csvDir, filename);

            // Generate CSV content
            String csvContent = generateCSVContent(answers, totalQuestions);

            // Write to file
            FileWriter writer = new FileWriter(csvFile);
            writer.write(csvContent);
            writer.close();

            return csvFile.getAbsolutePath();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate CSV content in the required format
     * Format:
     * Row 1: Question Number,1,2,3,4,...
     * Row 2: Answer,A,B,C,D,...
     *
     * @param answers Map of question number to answer
     * @param totalQuestions Total number of questions (default 50 if not specified)
     * @return CSV content as string
     */
    private static String generateCSVContent(Map<Integer, Character> answers, int totalQuestions) {
        StringBuilder csv = new StringBuilder();

        // Determine question range
        int maxQuestions = totalQuestions > 0 ? totalQuestions : 50;

        // Sort question numbers
        List<Integer> questionNumbers = new ArrayList<>();
        for (int i = 1; i <= maxQuestions; i++) {
            questionNumbers.add(i);
        }
        Collections.sort(questionNumbers);

        // Build header row (Question Number)
        csv.append("Question Number");
        for (Integer qNum : questionNumbers) {
            csv.append(",").append(qNum);
        }
        csv.append("\n");

        // Build answer row
        csv.append("Answer");
        for (Integer qNum : questionNumbers) {
            csv.append(",");
            if (answers.containsKey(qNum)) {
                csv.append(answers.get(qNum));
            } else {
                // Empty cell for unanswered questions
                csv.append("");
            }
        }
        csv.append("\n");

        return csv.toString();
    }

    /**
     * Generate CSV content with custom format (vertical layout)
     * Format:
     * Question,Answer
     * 1,A
     * 2,B
     * ...
     */
    public static String generateVerticalCSV(Map<Integer, Character> answers) {
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("Question,Answer\n");

        // Sort question numbers
        List<Integer> questionNumbers = new ArrayList<>(answers.keySet());
        Collections.sort(questionNumbers);

        // Data rows
        for (Integer qNum : questionNumbers) {
            csv.append(qNum).append(",").append(answers.get(qNum)).append("\n");
        }

        return csv.toString();
    }

    /**
     * Get CSV file extension
     */
    public static String getCSVExtension() {
        return ".csv";
    }

    /**
     * Validate CSV filename
     */
    public static boolean isValidCSVFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".csv");
    }

    /**
     * Get MIME type for CSV files
     */
    public static String getCSVMimeType() {
        return "text/csv";
    }
}