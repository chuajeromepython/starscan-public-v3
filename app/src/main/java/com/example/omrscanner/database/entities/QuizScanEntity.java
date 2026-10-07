package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the quiz_scans table.
 * Represents one scanned student paper within a quiz.
 *
 * Deliberately separate from {@link ScanEntity} (and its "scans" table),
 * which is foreign-keyed to {@link AssessmentEntity}. Quizzes are local-only
 * and must never share storage — or a foreign key — with assessments, so a
 * quiz scan lives here instead, foreign-keyed to {@link QuizEntity}.
 *
 * detectedBubbles — raw count of filled bubbles detected by the OMR engine.
 * score — nullable; set by grading against the quiz's linked answer key.
 */
@Entity(tableName = "quiz_scans", foreignKeys = @ForeignKey(entity = QuizEntity.class, parentColumns = "id", childColumns = "quiz_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index("quiz_id") })
public class QuizScanEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public int id;

    @NonNull
    @ColumnInfo(name = "quiz_id")
    public String quizId = "";

    @ColumnInfo(name = "student_lrn")
    public String studentLrn; // Learner Reference Number

    @ColumnInfo(name = "detected_bubbles", defaultValue = "0")
    public int detectedBubbles; // What the OMR system counted

    @Nullable
    @ColumnInfo(name = "score")
    public Integer score; // Null until graded against the quiz's answer key

    @ColumnInfo(name = "num_items")
    public int numItems; // 30, 50, or 60

    @ColumnInfo(name = "image_path")
    public String imagePath; // Path to raw scanned image on device

    @ColumnInfo(name = "overlay_image_path")
    public String overlayImagePath; // Path to highlighted-bubble overlay image

    @Nullable
    @ColumnInfo(name = "key_reference_image_path")
    public String keyReferenceImagePath; // Path to the answer-key reference image (in-app toggle only)

    @ColumnInfo(name = "timestamp")
    public long timestamp; // When the scan was taken

    @ColumnInfo(name = "updated_at")
    public long updatedAt; // Updated when LRN is corrected or score received

    public QuizScanEntity() {
    }

    public QuizScanEntity(@NonNull String quizId, String studentLrn,
                          int detectedBubbles, int numItems) {
        this.quizId = quizId;
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