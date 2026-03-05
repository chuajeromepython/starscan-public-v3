package com.example.omrscanner.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the answers table.
 * Represents one answer bubble per question item within a scan.
 * Supports multi-bubble answers (e.g. "ABC") as a TEXT string.
 *
 * Unique constraint: (scan_id, item_number) — one answer per question per scan.
 */
@Entity(tableName = "answers", foreignKeys = @ForeignKey(entity = ScanEntity.class, parentColumns = "id", childColumns = "scan_id", onDelete = ForeignKey.CASCADE), indices = {
    @Index(value = { "scan_id", "item_number" }, unique = true) })
public class AnswerEntity {

  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  public int id;

  @ColumnInfo(name = "scan_id")
  public int scanId;

  @ColumnInfo(name = "item_number")
  public int itemNumber; // Question number: 1–60

  @NonNull
  @ColumnInfo(name = "answer", defaultValue = "")
  public String answer = ""; // "A", "B", "C", "D", "ABC", or "" for blank

  public AnswerEntity() {
  }

  public AnswerEntity(int scanId, int itemNumber, @NonNull String answer) {
    this.scanId = scanId;
    this.itemNumber = itemNumber;
    this.answer = answer;
  }
}
