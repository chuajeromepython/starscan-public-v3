package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.projections.ScanListRow;

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

  /** Every scan, unfiltered — used for full local backup export. */
  @Query("SELECT * FROM scans")
  List<ScanEntity> getAllSync();

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
   * Clear graded scores for all scans in a given assessment.
   * Called when an answer key is unlinked — scores are no longer valid.
   */
  @Query("UPDATE scans SET score = NULL, updated_at = :updatedAt WHERE assessment_id = :assessmentId")
  void clearScoresByAssessment(String assessmentId, long updatedAt);

  /**
   * Clear graded scores for all scans whose assessment uses the given answer key.
   * Called BEFORE the answer key is deleted so the subquery still resolves.
   */
  @Query("UPDATE scans SET score = NULL, updated_at = :updatedAt " +
         "WHERE assessment_id IN (SELECT id FROM assessments WHERE answer_key_id = :keyId)")
  void clearScoresByAnswerKey(String keyId, long updatedAt);

  /**
   * Look up a scan by assessment + LRN — used for duplicate-LRN detection
   * and for replace-on-rescan logic. Returns null if not found.
   */
  @Query("SELECT * FROM scans WHERE assessment_id = :assessmentId AND student_lrn = :lrn LIMIT 1")
  ScanEntity getByAssessmentAndLrn(String assessmentId, String lrn);

  /**
   * Every scan across every class/assessment, newest first — powers the
   * read-only "Scans" tab. Joined with assessments/classes purely for
   * display (class + assessment name) and filtering; no write access is
   * exposed here.
   *
   * All filter params are nullable/blank-safe — pass null or "" to mean "All".
   * needsCorrectionFilter accepts "YES", "NO", or null/"" for All.
   */
  @Query("SELECT s.id AS id, s.assessment_id AS assessmentId, a.class_id AS classId, " +
          "(c.grade || ' \u2014 ' || c.section) AS className, a.name AS assessmentName, " +
          "a.sheet_type AS sheetType, s.student_lrn AS studentLrn, " +
          "TRIM(COALESCE(sl.first_name || ' ', '') || COALESCE(sl.middle_name || ' ', '') || COALESCE(sl.last_name, '')) AS studentName, " +
          "s.score AS score, " +
          "s.detected_bubbles AS detectedBubbles, s.num_items AS numItems, " +
          "s.timestamp AS timestamp, (a.answer_key_id IS NOT NULL) AS isGraded, " +
          "EXISTS(SELECT 1 FROM answers ans WHERE ans.scan_id = s.id AND LENGTH(ans.answer) > 1) AS needsCorrection " +
          "FROM scans s " +
          "JOIN assessments a ON a.id = s.assessment_id " +
          "LEFT JOIN classes c ON c.id = a.class_id " +
          "LEFT JOIN student_lrn sl ON sl.lrn = s.student_lrn AND sl.className = a.class_id " +
          "WHERE (:classIdFilter IS NULL OR :classIdFilter = '' OR a.class_id = :classIdFilter) " +
          "AND (:assessmentIdFilter IS NULL OR :assessmentIdFilter = '' OR a.id = :assessmentIdFilter) " +
          "AND (:sheetTypeFilter IS NULL OR :sheetTypeFilter = '' OR a.sheet_type = :sheetTypeFilter) " +
          "AND (:needsCorrectionFilter IS NULL OR :needsCorrectionFilter = '' " +
          "     OR (:needsCorrectionFilter = 'YES' AND EXISTS(SELECT 1 FROM answers ans2 WHERE ans2.scan_id = s.id AND LENGTH(ans2.answer) > 1)) " +
          "     OR (:needsCorrectionFilter = 'NO' AND NOT EXISTS(SELECT 1 FROM answers ans2 WHERE ans2.scan_id = s.id AND LENGTH(ans2.answer) > 1))) " +
          "AND (:search IS NULL OR :search = '' " +
          "     OR s.student_lrn LIKE '%' || :search || '%' " +
          "     OR a.name LIKE '%' || :search || '%' " +
          "     OR a.sheet_type LIKE '%' || :search || '%' " +
          "     OR (c.grade || ' ' || c.section) LIKE '%' || :search || '%') " +
          "ORDER BY s.timestamp DESC")
  List<ScanListRow> queryAllScans(String classIdFilter, String assessmentIdFilter,
                                  String sheetTypeFilter, String needsCorrectionFilter, String search);
}
