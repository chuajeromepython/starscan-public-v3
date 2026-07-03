package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "student_lrn")
public class StudentLrnEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "lrn")
    public String lrn;

    @ColumnInfo(name = "className")
    public String className;

    @ColumnInfo(name = "hot_sync")
    public int hotSync = 0;
}