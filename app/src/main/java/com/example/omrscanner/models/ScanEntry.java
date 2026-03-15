package com.example.omrscanner.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single scanned paper result within an activity.
 * Stores LRN, answers, score, image path, and CSV path.
 */
public class ScanEntry {
    private String lrn;
    private Map<Integer, String> answers; // question number -> answer (A/B/C/D or blank)
    private int score;
    private int numItems;
    private String templateId;
    private String imagePath;        // path to the scanned image
    private String overlayImagePath; // path to the overlay image (with bubble highlights)
    private String csvPath;          // path to the individual CSV
    private long timestamp;
    /** True when score was computed by comparing against an answer key; false = raw detected count. */
    private boolean isScored;

    public ScanEntry() {
        // Default constructor for deserialization
        this.answers = new LinkedHashMap<>();
    }

    public ScanEntry(String lrn, Map<Integer, String> answers, int numItems, String templateId) {
        this.lrn = lrn;
        this.answers = answers != null ? answers : new LinkedHashMap<>();
        this.numItems = numItems;
        this.templateId = templateId;
        this.score = calculateScore();
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getLrn() { return lrn; }
    public void setLrn(String lrn) { this.lrn = lrn; }

    public Map<Integer, String> getAnswers() { return answers; }
    public void setAnswers(Map<Integer, String> answers) { this.answers = answers; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getNumItems() { return numItems; }
    public void setNumItems(int numItems) { this.numItems = numItems; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getOverlayImagePath() { return overlayImagePath; }
    public void setOverlayImagePath(String overlayImagePath) { this.overlayImagePath = overlayImagePath; }

    public String getCsvPath() { return csvPath; }
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isScored() { return isScored; }
    public void setScored(boolean scored) { isScored = scored; }

    /**
     * Count how many questions were answered (non-blank).
     */
    public int getAnsweredCount() {
        int count = 0;
        if (answers != null) {
            for (String v : answers.values()) {
                if (v != null && !v.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Calculate score based on answers.
     * This is a simple calculation — in production you'd compare with an answer key.
     */
    private int calculateScore() {
        return getAnsweredCount(); // placeholder; real scoring needs answer key
    }

    /**
     * Get score percentage (0–100).
     */
    public float getScorePercentage() {
        if (numItems == 0) return 0;
        return (float) score / numItems * 100f;
    }

    /**
     * Score level: "high" (≥80%), "mid" (≥50%), "low" (<50%).
     */
    public String getScoreLevel() {
        float pct = getScorePercentage();
        if (pct >= 80f) return "high";
        if (pct >= 50f) return "mid";
        return "low";
    }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}
