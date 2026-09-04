package com.example.omrscanner.database.projections;

import androidx.room.ColumnInfo;

/**
 * One row per quiz currently linked to an answer key.
 * Used to populate the "Linked to Quiz" dropdown on the answer key card
 * (grouped client-side by answerKeyId — one key can have many rows).
 */
public class AnswerKeyLinkedQuiz {
    @ColumnInfo(name = "answerKeyId")
    public String answerKeyId;

    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "sheetType")
    public String sheetType;
}