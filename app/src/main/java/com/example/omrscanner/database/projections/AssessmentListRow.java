package com.example.omrscanner.database.projections;

import androidx.room.ColumnInfo;

/**
 * Read model for the assessment list screen.
 */
public class AssessmentListRow {
  @ColumnInfo(name = "id")
  public String id;

  @ColumnInfo(name = "classId")
  public String classId;

  @ColumnInfo(name = "name")
  public String name;

  @ColumnInfo(name = "sheetType")
  public String sheetType;

  @ColumnInfo(name = "examDate")
  public String examDate;

  @ColumnInfo(name = "examDateEpoch")
  public long examDateEpoch;

  @ColumnInfo(name = "createdAt")
  public long createdAt;

  @ColumnInfo(name = "scanCount")
  public int scanCount;

  // NEW: how many students were synced from the backend for this assessment's class
  @ColumnInfo(name = "syncedStudentCount")
  public int syncedStudentCount;


  /** Nullable: the ID of the assigned answer key, or null if none. */
  @ColumnInfo(name = "answerKeyId")
  public String answerKeyId;

  /** Nullable: the name of the assigned answer key (from LEFT JOIN), or null if none. */
  @ColumnInfo(name = "answerKeyName")
  public String answerKeyName;

  /** Number of scans in this assessment with at least one ambiguous (multi-letter) answer. */
  @ColumnInfo(name = "needsCorrectionCount")
  public int needsCorrectionCount;
}
