package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.AnswerKeyEntity;

import java.util.List;

/**
 * DAO for answer key CRUD operations.
 */
@Dao
public interface AnswerKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AnswerKeyEntity key);

    @Update
    void update(AnswerKeyEntity key);

    @Delete
    void delete(AnswerKeyEntity key);

    @Query("SELECT * FROM answer_keys WHERE id = :id")
    AnswerKeyEntity getById(String id);

    /** All keys, newest-first. */
    @Query("SELECT * FROM answer_keys ORDER BY created_at DESC")
    List<AnswerKeyEntity> getAll();

    /** Filter by sheet type — useful when assigning a key to a specific assessment. */
    @Query("SELECT * FROM answer_keys WHERE sheet_type = :sheetType ORDER BY created_at DESC")
    List<AnswerKeyEntity> getBySheetType(String sheetType);
}
