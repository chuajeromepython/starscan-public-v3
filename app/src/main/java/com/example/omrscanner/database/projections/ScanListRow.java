package com.example.omrscanner.database.projections;

import androidx.room.ColumnInfo;

/**
 * Read model for the cross-class "all scans" list (Scans tab).
 * View-only projection — never written back to the database.
 */
public class ScanListRow {
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "assessmentId")
    public String assessmentId;

    @ColumnInfo(name = "classId")
    public String classId;

    /** Display name of the owning class (e.g. "9 — Gumamela"). */
    @ColumnInfo(name = "className")
    public String className;

    @ColumnInfo(name = "assessmentName")
    public String assessmentName;

    @ColumnInfo(name = "sheetType")
    public String sheetType;

    @ColumnInfo(name = "studentLrn")
    public String studentLrn;

    @ColumnInfo(name = "studentName")
    public String studentName;

    @ColumnInfo(name = "score")
    public Integer score;

    @ColumnInfo(name = "detectedBubbles")
    public int detectedBubbles;

    @ColumnInfo(name = "numItems")
    public int numItems;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    /** True when the owning assessment has an answer key assigned (score is a real grade). */
    @ColumnInfo(name = "isGraded")
    public boolean isGraded;

    /** True if any answer on this scan has more than one letter (e.g. "AC") and still needs a teacher to resolve it. */
    @ColumnInfo(name = "needsCorrection")
    public boolean needsCorrection;
}