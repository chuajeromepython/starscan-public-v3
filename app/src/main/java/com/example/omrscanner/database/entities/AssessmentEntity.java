package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "assessments", foreignKeys = @ForeignKey(entity = ClassEntity.class, parentColumns = "id", childColumns = "class_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("class_id"),
        @Index("sheet_type"),
        @Index("created_at"),
        @Index("exam_date_epoch")
})
public class AssessmentEntity {

  @PrimaryKey
  @NonNull
  @ColumnInfo(name = "id")
  public String id = "";

  @NonNull
  @ColumnInfo(name = "class_id")
  public String classId = "";

  @ColumnInfo(name = "name")
  public String name;

  @ColumnInfo(name = "sheet_type")
  public String sheetType; // "ZPH30", "ZPH40", "ZPH50", "ZPH60"

  @ColumnInfo(name = "exam_date")
  public String examDate;

  @ColumnInfo(name = "exam_date_epoch")
  public long examDateEpoch;

  @ColumnInfo(name = "created_at")
  public long createdAt;

  @ColumnInfo(name = "updated_at")
  public long updatedAt;

  @Nullable
  @ColumnInfo(name = "answer_key_id")
  public String answerKeyId;

  // ── NEW FIELD ──────────────────────────────────────────────────────
  /** Assessment type: "Diagnostic", "Summative", or "ECD" */
  @Nullable
  @ColumnInfo(name = "assessment_type")
  public String assessmentType;
  // ──────────────────────────────────────────────────────────────────

  @ColumnInfo(name = "hot_sync")
  public int hotSync = 0;

  public AssessmentEntity() {
  }

  public AssessmentEntity(@NonNull String id, @NonNull String classId,
                          String name, String sheetType, String examDate) {
    this.id = id;
    this.classId = classId;
    this.name = name;
    this.sheetType = sheetType;
    this.examDate = examDate;
    this.examDateEpoch = 0L;
    this.createdAt = System.currentTimeMillis();
    this.updatedAt = System.currentTimeMillis();
  }

  public int getNumItems() {
    if (sheetType == null)
      return 30;
    switch (sheetType) {
      case "ZPH60": return 60;
      case "ZPH50": return 50;
      case "ZPH40": return 40;
      case "ZPH30":
      default:      return 30;
    }
  }
}