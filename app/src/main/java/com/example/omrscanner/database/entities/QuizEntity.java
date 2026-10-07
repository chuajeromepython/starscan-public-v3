package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Quizzes are local-only: unlike assessments, they are never synced to or
 * from the STARS backend. No hot_sync / server_id fields on purpose.
 */
@Entity(tableName = "quizzes", foreignKeys = @ForeignKey(entity = ClassEntity.class,
        parentColumns = "id", childColumns = "class_id", onDelete = ForeignKey.CASCADE),
        indices = { @Index("class_id"), @Index("created_at"), @Index("exam_date_epoch") })
public class QuizEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @NonNull
    @ColumnInfo(name = "class_id")
    public String classId = "";

    @ColumnInfo(name = "name")
    public String name;

    /** "1st Term", "2nd Term", or "3rd Term" */
    @ColumnInfo(name = "term")
    public String term;

    /** Only "ZPH40" is supported for quizzes. */
    @ColumnInfo(name = "sheet_type")
    public String sheetType = "ZPH40";

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

    public QuizEntity() {
    }

    public QuizEntity(@NonNull String id, @NonNull String classId, String name,
                      String term, String examDate) {
        this.id = id;
        this.classId = classId;
        this.name = name;
        this.term = term;
        this.examDate = examDate;
        this.sheetType = "ZPH40";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}