package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.UserEntity;

import java.util.List;

@Dao
public interface UserDao {

    @Insert
    long insert(UserEntity user);

    @Update
    void update(UserEntity user);

    @Delete
    void delete(UserEntity user);

    @Query("SELECT * FROM users")
    List<UserEntity> getAll();

    @Query("SELECT * FROM users WHERE id = :id")
    UserEntity getById(int id);
}