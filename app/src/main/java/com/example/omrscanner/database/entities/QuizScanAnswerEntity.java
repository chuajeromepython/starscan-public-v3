package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the quiz_scan_answers table.
 * Represents one answer bubble per question item within a quiz scan.
 * Supports multi-bubble answers (e.g. "ABC") as a TEXT string.
 *
 * Mirrors {@link AnswerEntity}, but foreign-keyed to {@link QuizScanEntity}
 * instead of {@link ScanEntity} — the "answers" table's scan_id FK only
 * resolves against "scans", so quiz scan answers need their own table to
 * stay isolated from assessments.
 *
 * Unique constraint: (quiz_scan_id, item_number) — one answer per question per scan.
 */
@Entity(tableName = "quiz_scan_answers", foreignKeys = @ForeignKey(entity = QuizScanEntity.class, parentColumns = "id", childColumns = "quiz_scan_id", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = { "quiz_scan_id", "item_number" }, unique = true) })
public class QuizScanAnswerEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "quiz_scan_id")
    public int quizScanId;

    @ColumnInfo(name = "item_number")
    public int itemNumber; // Question number: 1–60

    @NonNull
    @ColumnInfo(name = "answer", defaultValue = "")
    public String answer = ""; // "A", "B", "C", "D", "ABC", or "" for blank

    public QuizScanAnswerEntity() {
    }

    public QuizScanAnswerEntity(int quizScanId, int itemNumber, @NonNull String answer) {
        this.quizScanId = quizScanId;
        this.itemNumber = itemNumber;
        this.answer = answer;
    }
}