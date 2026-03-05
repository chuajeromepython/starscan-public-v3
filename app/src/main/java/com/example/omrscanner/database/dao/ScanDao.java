package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.ScanEntity;

import java.util.List;

/**
 * DAO for scan operations.
 */
@Dao
public interface ScanDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  long insert(ScanEntity scan);

  @Update
  void update(ScanEntity scan);

  @Delete
  void delete(ScanEntity scan);

  @Query("SELECT * FROM scans WHERE id = :id")
  ScanEntity getById(int id);

  @Query("SELECT * FROM scans WHERE assessment_id = :assessmentId ORDER BY timestamp DESC")
  List<ScanEntity> getByAssessment(String assessmentId);

  @Query("SELECT COUNT(*) FROM scans WHERE assessment_id = :assessmentId")
  int countByAssessment(String assessmentId);

  @Query("SELECT COUNT(*) FROM scans")
  int countAll();

  /**
   * Update the score received from the external grading system.
   * Also updates updated_at timestamp.
   */
  @Query("UPDATE scans SET score = :score, updated_at = :updatedAt WHERE id = :scanId")
  void updateScore(int scanId, int score, long updatedAt);

  /**
   * Update the student LRN (e.g. when corrected after scan).
   * Also updates updated_at timestamp.
   */
  @Query("UPDATE scans SET student_lrn = :lrn, updated_at = :updatedAt WHERE id = :scanId")
  void updateLrn(int scanId, String lrn, long updatedAt);

  /**
   * Look up a scan by assessment + LRN — used for duplicate-LRN detection
   * and for replace-on-rescan logic. Returns null if not found.
   */
  @Query("SELECT * FROM scans WHERE assessment_id = :assessmentId AND student_lrn = :lrn LIMIT 1")
  ScanEntity getByAssessmentAndLrn(String assessmentId, String lrn);
}
