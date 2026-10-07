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

    @Query("SELECT * FROM users WHERE is_active = 1 LIMIT 1")
    UserEntity getActiveUser();

    @Query("UPDATE users SET profile_photo_path = :path WHERE userId = :userId")
    void updateProfilePhotoPath(int userId, String path);

    /** Latest stored photo path for this server account, across all of its rows. */
    @Query("SELECT profile_photo_path FROM users WHERE userId = :userId "
            + "AND profile_photo_path IS NOT NULL ORDER BY id DESC LIMIT 1")
    String getProfilePhotoPathForUserId(int userId);

    // This prevents two or more users getting 1s
    @Query("UPDATE users SET is_active = 0")
    void deactivateAll();

    @androidx.room.Transaction
    default long insertAsOnlyActive(UserEntity user) {
        deactivateAll();
        user.isActive = 1;
        return insert(user);
    }
}