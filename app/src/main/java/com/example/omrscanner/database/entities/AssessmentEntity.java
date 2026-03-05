package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the assessments table.
 * Represents one assessment (quiz/exam) within a class.
 * e.g. name="hs", sheetType="ZPH50"
 */
@Entity(tableName = "assessments", foreignKeys = @ForeignKey(entity = ClassEntity.class, parentColumns = "id", childColumns = "class_id", onDelete = ForeignKey.CASCADE), indices = {
    @Index("class_id") })
public class AssessmentEntity {

  @PrimaryKey
  @NonNull
  @ColumnInfo(name = "id")
  public String id = ""; // 7-char UUID kept from existing ActivityFolder model

  @NonNull
  @ColumnInfo(name = "class_id")
  public String classId = "";

  @ColumnInfo(name = "name")
  public String name; // e.g. "hs", "he"

  @ColumnInfo(name = "sheet_type")
  public String sheetType; // "ZPH30", "ZPH50", "ZPH60"

  @ColumnInfo(name = "exam_date")
  public String examDate; // e.g. "Mar 05, 2026"

  @ColumnInfo(name = "created_at")
  public long createdAt;

  @ColumnInfo(name = "updated_at")
  public long updatedAt;

  public AssessmentEntity() {
  }

  public AssessmentEntity(@NonNull String id, @NonNull String classId,
      String name, String sheetType, String examDate) {
    this.id = id;
    this.classId = classId;
    this.name = name;
    this.sheetType = sheetType;
    this.examDate = examDate;
    this.createdAt = System.currentTimeMillis();
    this.updatedAt = System.currentTimeMillis();
  }

  /**
   * Number of items based on sheet type — mirrors existing ActivityFolder logic
   */
  public int getNumItems() {
    if (sheetType == null)
      return 30;
    switch (sheetType) {
      case "ZPH60":
        return 60;
      case "ZPH50":
        return 50;
      case "ZPH30":
      default:
        return 30;
    }
  }
}
