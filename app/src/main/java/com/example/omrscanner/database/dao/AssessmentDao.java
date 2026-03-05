package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.AssessmentEntity;

import java.util.List;

/**
 * DAO for assessment operations.
 */
@Dao
public interface AssessmentDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(AssessmentEntity assessment);

  @Update
  void update(AssessmentEntity assessment);

  @Delete
  void delete(AssessmentEntity assessment);

  @Query("SELECT * FROM assessments WHERE id = :id")
  AssessmentEntity getById(String id);

  @Query("SELECT * FROM assessments WHERE class_id = :classId ORDER BY created_at DESC")
  List<AssessmentEntity> getByClass(String classId);

  @Query("SELECT * FROM assessments WHERE class_id = :classId AND sheet_type = :sheetType ORDER BY created_at DESC")
  List<AssessmentEntity> getByClassAndSheetType(String classId, String sheetType);

  @Query("SELECT COUNT(*) FROM assessments WHERE class_id = :classId")
  int countByClass(String classId);

  @Query("SELECT COUNT(*) FROM assessments")
  int countAll();
}
