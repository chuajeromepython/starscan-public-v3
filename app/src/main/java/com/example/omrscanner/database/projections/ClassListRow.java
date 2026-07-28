package com.example.omrscanner.database.projections;

import androidx.room.ColumnInfo;

/**
 * Read model for the class list screen.
 */
public class ClassListRow {
  @ColumnInfo(name = "id")
  public String id;

  @ColumnInfo(name = "grade")
  public String grade;

  @ColumnInfo(name = "section")
  public String section;

  @ColumnInfo(name = "schoolYear")
  public String schoolYear;

  @ColumnInfo(name = "createdAt")
  public long createdAt;

  @ColumnInfo(name = "assessmentCount")
  public int assessmentCount;

  @ColumnInfo(name = "scanCount")
  public int scanCount;

  public String getDisplayName() {
    return (grade != null ? grade : "") + " \u2014 " + (section != null ? section : "");
  }

  /** Active = the class has at least one assessment, and at least one scan among them. */
  public boolean isActive() {
    return assessmentCount > 0 && scanCount > 0;
  }
}
