package com.example.omrscanner.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a class folder containing activities.
 * Each class has a teacher, grade, section, and a list of activities.
 */
public class ClassFolder {
    private String id;
    private String teacher;
    private String grade;
    private String section;
    private List<ActivityFolder> activities;
    private long createdAt;

    public ClassFolder() {
        // Default constructor for deserialization
        this.activities = new ArrayList<>();
    }

    public ClassFolder(String teacher, String grade, String section) {
        this.id = UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        this.teacher = teacher;
        this.grade = grade;
        this.section = section;
        this.activities = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public List<ActivityFolder> getActivities() { return activities; }
    public void setActivities(List<ActivityFolder> activities) { this.activities = activities; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void addActivity(ActivityFolder activity) {
        if (this.activities == null) {
            this.activities = new ArrayList<>();
        }
        this.activities.add(activity);
    }

    public int getActivityCount() {
        return activities != null ? activities.size() : 0;
    }

    public int getTotalScans() {
        int total = 0;
        if (activities != null) {
            for (ActivityFolder act : activities) {
                total += act.getScanCount();
            }
        }
        return total;
    }

    public String getDisplayName() {
        return grade + " — " + section;
    }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(createdAt));
    }
}
