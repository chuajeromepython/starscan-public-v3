package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Entity for the teachers table.
 * Represents the teacher profile (global owner of all classes).
 */
@Entity(tableName = "teachers")
public class TeacherEntity {

  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  public int id;

  @ColumnInfo(name = "name")
  public String name;

  @ColumnInfo(name = "profile_image_path")
  public String profileImagePath;

  @ColumnInfo(name = "created_at")
  public long createdAt;

  @ColumnInfo(name = "updated_at")
  public long updatedAt;

  public TeacherEntity() {
  }

  public TeacherEntity(String name) {
    this.name = name;
    this.createdAt = System.currentTimeMillis();
    this.updatedAt = System.currentTimeMillis();
  }
}
