package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.ClassEntity;

import java.util.List;

/**
 * DAO for class operations.
 */
@Dao
public interface ClassDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(ClassEntity classEntity);

  @Update
  void update(ClassEntity classEntity);

  @Delete
  void delete(ClassEntity classEntity);

  @Query("SELECT * FROM classes WHERE id = :id")
  ClassEntity getById(String id);

  @Query("SELECT * FROM classes WHERE teacher_id = :teacherId ORDER BY created_at DESC")
  List<ClassEntity> getByTeacher(int teacherId);

  @Query("SELECT * FROM classes ORDER BY created_at DESC")
  List<ClassEntity> getAll();

  @Query("SELECT COUNT(*) FROM classes WHERE teacher_id = :teacherId")
  int countByTeacher(int teacherId);

  @Query("SELECT COUNT(*) FROM classes")
  int countAll();
}
