package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "student_lrn",
        foreignKeys = @ForeignKey(entity = TeacherEntity.class, parentColumns = "id",
                childColumns = "teacher_id", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"lrn", "className"}, unique = true),
                @Index("teacher_id")
        }
)
public class StudentLrnEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "teacher_id")
    public Integer teacherId;

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