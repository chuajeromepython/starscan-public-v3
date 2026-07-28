package com.example.omrscanner.database.projections;

import androidx.room.ColumnInfo;

/**
 * One row per assessment currently linked to an answer key.
 * Used to populate the "Linked to" dropdown on the answer key card
 * (grouped client-side by answerKeyId — one key can have many rows).
 */
public class AnswerKeyLinkedAssessment {
    @ColumnInfo(name = "answerKeyId")
    public String answerKeyId;

    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "sheetType")
    public String sheetType;
}