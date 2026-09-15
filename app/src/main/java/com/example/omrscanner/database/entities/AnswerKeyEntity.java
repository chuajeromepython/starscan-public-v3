package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the answer_keys table.
 * Represents a reusable answer key that can be assigned to any assessment.
 *
 * Answer keys are scoped to a single teacher (teacher_id, cascade-deleted with
 * the owning teacher, matching classes/assessments/scans/quizzes) — a key can
 * still be shared across many assessments/quizzes in different classes, as
 * long as those classes belong to the same teacher. There is no hard
 * foreign-key back to assessments; that link is soft (assessments hold a
 * nullable answer_key_id).
 *
 * answers — comma-separated correct answers, e.g. "A,B,C,D,A,..."
 *           length matches the numItems implied by sheet_type.
 */
@Entity(
        tableName = "answer_keys",
        foreignKeys = @ForeignKey(entity = TeacherEntity.class, parentColumns = "id",
                childColumns = "teacher_id", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index("sheet_type"),
                @Index("created_at"),
                @Index("teacher_id")
        }
)
public class AnswerKeyEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = ""; // 7-char short UUID, same style as AssessmentEntity

    @Nullable
    @ColumnInfo(name = "teacher_id")
    public Integer teacherId; // owning teacher — nullable only to keep the no-arg/legacy constructors valid pre-insert

    @Nullable
    @ColumnInfo(name = "name")
    public String name; // e.g. "Midterm Science Q1"

    @Nullable
    @ColumnInfo(name = "school_year")
    public String schoolYear; // e.g. "2025-2026"

    @Nullable
    @ColumnInfo(name = "sheet_type")
    public String sheetType; // "ZPH30" / "ZPH40" / "ZPH50" / "ZPH60"

    @Nullable
    @ColumnInfo(name = "answers")
    public String answers; // Comma-separated: "A,B,C,D,A,..." length = numItems

    @ColumnInfo(name = "created_at", defaultValue = "0")
    public long createdAt;

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    public long updatedAt;

    public AnswerKeyEntity() {
    }

    public AnswerKeyEntity(@NonNull String id, @Nullable Integer teacherId, @Nullable String name,
                           @Nullable String schoolYear, @Nullable String sheetType,
                           @Nullable String answers) {
        this.id = id;
        this.teacherId = teacherId;
        this.name = name;
        this.schoolYear = schoolYear;
        this.sheetType = sheetType;
        this.answers = answers;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Number of items implied by sheet_type. Mirrors AssessmentEntity logic.
     */
    public int getNumItems() {
        return com.example.omrscanner.models.ActivityFolder.parseItemCountFromSheetType(sheetType);
    }
}
