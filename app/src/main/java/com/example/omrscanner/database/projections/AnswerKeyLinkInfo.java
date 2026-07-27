package com.example.omrscanner.database.projections;

import androidx.room.ColumnInfo;

/**
 * Read model for the answer keys screen — tells each card whether it's
 * currently referenced by an assessment, and if so, by which one.
 */
public class AnswerKeyLinkInfo {
    @ColumnInfo(name = "id")
    public String id;

    /** Nullable: name of the most recently created assessment referencing this key. */
    @ColumnInfo(name = "linkedAssessmentName")
    public String linkedAssessmentName;

    /** Nullable: sheet type of that same assessment. */
    @ColumnInfo(name = "linkedSheetType")
    public String linkedSheetType;

    /** Total number of assessments currently referencing this key. */
    @ColumnInfo(name = "linkedCount")
    public int linkedCount;
}