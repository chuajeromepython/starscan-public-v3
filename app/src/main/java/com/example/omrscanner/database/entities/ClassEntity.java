package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the classes table.
 * Represents one class (e.g. "Grade 9 — Gumamela, S.Y. 2024-2025").
 * Named ClassEntity to avoid conflict with Java's built-in Class keyword.
 */
@Entity(tableName = "classes", foreignKeys = @ForeignKey(entity = TeacherEntity.class, parentColumns = "id", childColumns = "teacher_id", onDelete = ForeignKey.CASCADE), indices = {
    @Index("teacher_id") })
public class ClassEntity {

  @PrimaryKey
  @NonNull
  @ColumnInfo(name = "id")
  public String id = ""; // 7-char UUID kept from existing ClassFolder model

  @ColumnInfo(name = "teacher_id")
  public int teacherId;

  @ColumnInfo(name = "grade")
  public String grade; // e.g. "9", "10"

  @ColumnInfo(name = "section")
  public String section; // e.g. "Gumamela"

  @ColumnInfo(name = "school_year")
  public String schoolYear; // e.g. "2024-2025"

  @ColumnInfo(name = "created_at")
  public long createdAt;

  @ColumnInfo(name = "updated_at")
  public long updatedAt;

  public ClassEntity() {
  }

  public ClassEntity(@NonNull String id, int teacherId, String grade,
      String section, String schoolYear) {
    this.id = id;
    this.teacherId = teacherId;
    this.grade = grade;
    this.section = section;
    this.schoolYear = schoolYear;
    this.createdAt = System.currentTimeMillis();
    this.updatedAt = System.currentTimeMillis();
  }

  /** Returns display name matching existing app UI: "9 — Gumamela" */
  public String getDisplayName() {
    return grade + " \u2014 " + section;
  }
}
