package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "student_lrn",
        indices = { @Index(value = {"lrn", "className"}, unique = true) }
)
public class StudentLrnEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "lrn")
    public String lrn;

    @ColumnInfo(name = "className")
    public String className;

    @ColumnInfo(name = "sectionId")
    public Integer sectionId;

    @ColumnInfo(name = "gradeLevelId")
    public Integer gradeLevelId;

    @ColumnInfo(name = "classroomId")
    public Integer classroomId;

    @ColumnInfo(name = "hot_sync")
    public int hotSync = 0;
}