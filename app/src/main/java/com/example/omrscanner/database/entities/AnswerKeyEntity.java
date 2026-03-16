package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the answer_keys table.
 * Represents a reusable answer key that can be assigned to any assessment.
 *
 * Answer keys are globally scoped — one key can be shared across many
 * assessments in different classes.  There is no hard foreign-key back to
 * assessments; the link is soft (assessments hold a nullable answer_key_id).
 *
 * answers — comma-separated correct answers, e.g. "A,B,C,D,A,..."
 *           length matches the numItems implied by sheet_type.
 */
@Entity(
    tableName = "answer_keys",
    indices = {
        @Index("sheet_type"),
        @Index("created_at")
    }
)
public class AnswerKeyEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = ""; // 7-char short UUID, same style as AssessmentEntity

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

    public AnswerKeyEntity(@NonNull String id, @Nullable String name,
            @Nullable String schoolYear, @Nullable String sheetType,
            @Nullable String answers) {
        this.id = id;
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
        if (sheetType == null) return 30;
        switch (sheetType) {
            case "ZPH60": return 60;
            case "ZPH50": return 50;
            case "ZPH40": return 40;
            case "ZPH30":
            default:      return 30;
        }
    }
}
