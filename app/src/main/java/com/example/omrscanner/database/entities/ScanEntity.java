package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the scans table.
 * Represents one scanned student paper within an assessment.
 *
 * detectedBubbles — raw count of filled bubbles detected by the OMR engine.
 * score — nullable; set by an external grading/scoring system later.
 */
@Entity(tableName = "scans", foreignKeys = @ForeignKey(entity = AssessmentEntity.class, parentColumns = "id", childColumns = "assessment_id", onDelete = ForeignKey.CASCADE), indices = {
    @Index("assessment_id") })
public class ScanEntity {

  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  public int id;

  @NonNull
  @ColumnInfo(name = "assessment_id")
  public String assessmentId = "";

  @ColumnInfo(name = "student_lrn")
  public String studentLrn; // Learner Reference Number

  @ColumnInfo(name = "detected_bubbles", defaultValue = "0")
  public int detectedBubbles; // What the OMR system counted

  @Nullable
  @ColumnInfo(name = "score")
  public Integer score; // Null until external scoring system provides it

  @ColumnInfo(name = "num_items")
  public int numItems; // 30, 50, or 60

  @ColumnInfo(name = "image_path")
  public String imagePath; // Path to raw scanned image on device

  @ColumnInfo(name = "overlay_image_path")
  public String overlayImagePath; // Path to highlighted-bubble overlay image

  @ColumnInfo(name = "timestamp")
  public long timestamp; // When the scan was taken

  @ColumnInfo(name = "updated_at")
  public long updatedAt; // Updated when LRN is corrected or score received

  public ScanEntity() {
  }

  public ScanEntity(@NonNull String assessmentId, String studentLrn,
      int detectedBubbles, int numItems) {
    this.assessmentId = assessmentId;
    this.studentLrn = studentLrn;
    this.detectedBubbles = detectedBubbles;
    this.numItems = numItems;
    this.score = null;
    this.timestamp = System.currentTimeMillis();
    this.updatedAt = System.currentTimeMillis();
  }

  /** Returns score percentage based on detected bubbles vs total items. */
  public float getDetectionPercentage() {
    if (numItems == 0)
      return 0f;
    return (float) detectedBubbles / numItems * 100f;
  }

  public String getFormattedTimestamp() {
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm",
        java.util.Locale.getDefault());
    return sdf.format(new java.util.Date(timestamp));
  }
}
