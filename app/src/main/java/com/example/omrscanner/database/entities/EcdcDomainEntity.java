package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ecdc_domains")
public class EcdcDomainEntity {

    @PrimaryKey
    public int id;

    @ColumnInfo(name = "domain")
    public String domain;
}