package com.example.omrscanner.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an activity (quiz/exam) within a class folder.
 * Each activity has a name, sheet type, and a list of scan results.
 */
public class ActivityFolder {
    private String id;
    private String name;
    private String sheetType; // ZPH30, ZPH40, ZPH50, ZPH60
    private List<ScanEntry> scans;
    private long createdAt;
    private String examDate; // Date when the activity was taken
    private long examDateEpoch; // millis at local midnight, used for sorting
    /** Soft-link to the assigned answer key's ID; null if none assigned. */
    private String answerKeyId;

    private String assessmentType; // Assessment type, [diagnostic, summative, ECD]

    public ActivityFolder() {
        // Default constructor for deserialization
        this.scans = new ArrayList<>();
    }

    public ActivityFolder(String name, String sheetType) {
        this.id = UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        this.name = name;
        this.sheetType = sheetType;
        this.scans = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.examDate = getFormattedDate(); // Default to creation date
        this.examDateEpoch = this.createdAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSheetType() {
        return sheetType;
    }

    public void setSheetType(String sheetType) {
        this.sheetType = sheetType;
    }

    public List<ScanEntry> getScans() {
        return scans;
    }

    public void setScans(List<ScanEntry> scans) {
        this.scans = scans;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getExamDate() {
        return examDate;
    }

    public void setExamDate(String examDate) {
        this.examDate = examDate;
    }

    public long getExamDateEpoch() {
        return examDateEpoch;
    }

    public void setExamDateEpoch(long examDateEpoch) {
        this.examDateEpoch = examDateEpoch;
    }

    public String getAnswerKeyId() {
        return answerKeyId;
    }

    public void setAnswerKeyId(String answerKeyId) {
        this.answerKeyId = answerKeyId;
    }

    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }

    public void addScan(ScanEntry scan) {
        if (this.scans == null) {
            this.scans = new ArrayList<>();
        }
        this.scans.add(scan);
    }

    public int getScanCount() {
        return scans != null ? scans.size() : 0;
    }

    public int getNumItems() {
        switch (sheetType) {
            case "ZPH60":
                return 60;
            case "ZPH50":
                return 50;
            case "ZPH40":
                return 40;
            case "ZPH30":
                return 30;
            default:
                return 30;
        }
    }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(createdAt));
    }
}
