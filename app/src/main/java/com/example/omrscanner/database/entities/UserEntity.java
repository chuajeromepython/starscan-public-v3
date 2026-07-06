package com.example.omrscanner.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "userId")
    public Integer userId;

    @ColumnInfo(name = "passkey")
    public String passkey;

    @ColumnInfo(name = "serverIp")
    public String serverIp;

    @ColumnInfo(name = "firstName")
    public String firstName;

    @ColumnInfo(name = "middleName")
    public String middleName;

    @ColumnInfo(name = "lastName")
    public String lastName;

    @ColumnInfo(name = "suffix")
    public String suffix;

    @ColumnInfo(name = "school")
    public String school;

    @ColumnInfo(name = "is_active")
    public int isActive = 1;
}