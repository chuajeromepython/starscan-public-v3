package com.example.omrscanner.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class CSVExporter {

    private static final String TAG = "CSVExporter";

    /**
     * Export OMR results to CSV file
     * @param context Application context
     * @param answers Map of question number to answer letter (e.g., 1->A, 2->B, etc.)
     * @param totalQuestions Total number of questions detected
     * @return File path of generated CSV, or null if failed
     */
    public static String exportToCSV(Context context, Map<Integer, Character> answers, int totalQuestions) {
        try {
            // Create timestamp for filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = "OMR_Results_" + timestamp + ".csv";

            // Use cache directory for temporary file
            File cacheDir = context.getCacheDir();
            File csvFile = new File(cacheDir, fileName);

            // Write CSV content
            FileWriter writer = new FileWriter(csvFile);

            // Write header
            writer.append("Question Number,Answer\n");

            // Sort answers by question number using TreeMap
            TreeMap<Integer, Character> sortedAnswers = new TreeMap<>(answers);

            // Write all questions (1 to totalQuestions)
            for (int i = 1; i <= totalQuestions; i++) {
                writer.append(String.valueOf(i));
                writer.append(",");

                // Write answer if detected, otherwise leave blank
                if (sortedAnswers.containsKey(i)) {
                    writer.append(String.valueOf(sortedAnswers.get(i)));
                } else {
                    writer.append(""); // No answer detected
                }

                writer.append("\n");
            }

            writer.flush();
            writer.close();

            Log.d(TAG, "CSV file created: " + csvFile.getAbsolutePath());
            Log.d(TAG, "Total questions: " + totalQuestions);
            Log.d(TAG, "Answered questions: " + answers.size());

            return csvFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error creating CSV file", e);
            return null;
        }
    }

    /**
     * Export OMR results to CSV with additional statistics
     * @param context Application context
     * @param answers Map of question number to answer letter
     * @param totalQuestions Total number of questions detected
     * @param includeStats Whether to include statistics at the end
     * @return File path of generated CSV, or null if failed
     */
    public static String exportToCSVWithStats(Context context, Map<Integer, Character> answers,
                                              int totalQuestions, boolean includeStats) {
        try {
            // Create timestamp for filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = "OMR_Results_" + timestamp + ".csv";

            // Use cache directory for temporary file
            File cacheDir = context.getCacheDir();
            File csvFile = new File(cacheDir, fileName);

            // Write CSV content
            FileWriter writer = new FileWriter(csvFile);

            // Write header with timestamp
            writer.append("OMR Scan Results - Generated: ").append(timestamp).append("\n");
            writer.append("\n");
            writer.append("Question Number,Answer\n");

            // Sort answers by question number
            TreeMap<Integer, Character> sortedAnswers = new TreeMap<>(answers);

            // Write all questions
            for (int i = 1; i <= totalQuestions; i++) {
                writer.append(String.valueOf(i));
                writer.append(",");

                if (sortedAnswers.containsKey(i)) {
                    writer.append(String.valueOf(sortedAnswers.get(i)));
                }

                writer.append("\n");
            }

            // Add statistics if requested
            if (includeStats) {
                writer.append("\n");
                writer.append("Statistics\n");
                writer.append("Total Questions,").append(String.valueOf(totalQuestions)).append("\n");
                writer.append("Answered Questions,").append(String.valueOf(answers.size())).append("\n");
                writer.append("Unanswered Questions,")
                        .append(String.valueOf(totalQuestions - answers.size())).append("\n");

                // Count answers by letter
                int countA = 0, countB = 0, countC = 0, countD = 0;
                for (Character answer : answers.values()) {
                    switch (answer) {
                        case 'A': countA++; break;
                        case 'B': countB++; break;
                        case 'C': countC++; break;
                        case 'D': countD++; break;
                    }
                }

                writer.append("\n");
                writer.append("Answer Distribution\n");
                writer.append("A,").append(String.valueOf(countA)).append("\n");
                writer.append("B,").append(String.valueOf(countB)).append("\n");
                writer.append("C,").append(String.valueOf(countC)).append("\n");
                writer.append("D,").append(String.valueOf(countD)).append("\n");
            }

            writer.flush();
            writer.close();

            Log.d(TAG, "CSV file with stats created: " + csvFile.getAbsolutePath());

            return csvFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error creating CSV file with stats", e);
            return null;
        }
    }

    /**
     * Get a formatted summary of the results
     */
    public static String getResultSummary(Map<Integer, Character> answers, int totalQuestions) {
        int answered = answers.size();
        int unanswered = totalQuestions - answered;

        return String.format(Locale.getDefault(),
                "Total Questions: %d\nAnswered: %d\nUnanswered: %d",
                totalQuestions, answered, unanswered);
    }
}