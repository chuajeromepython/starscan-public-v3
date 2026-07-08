package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.projections.AssessmentListRow;

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

  /** Lightweight update — links an answer key to an assessment without touching other fields. */
  @Query("UPDATE assessments SET answer_key_id = :keyId WHERE id = :assessmentId")
  void setAnswerKey(String assessmentId, String keyId);

  /** Called when an answer key is deleted — nullifies all references to prevent stale links. */
  @Query("UPDATE assessments SET answer_key_id = NULL WHERE answer_key_id = :keyId")
  void clearAnswerKeyRef(String keyId);

  /** Returns all assessments that currently reference the given answer key. */
  @Query("SELECT * FROM assessments WHERE answer_key_id = :keyId")
  List<AssessmentEntity> getByAnswerKeyId(String keyId);

  @Query("SELECT a.id AS id, a.class_id AS classId, a.name AS name, "
          + "a.sheet_type AS sheetType, a.exam_date AS examDate, "
          + "a.exam_date_epoch AS examDateEpoch, a.created_at AS createdAt, "
          + "a.answer_key_id AS answerKeyId, ak.name AS answerKeyName, "
          + "COUNT(s.id) AS scanCount, "
          + "(SELECT COUNT(DISTINCT sl.lrn) FROM student_lrn sl WHERE sl.className = a.class_id) AS syncedStudentCount "
          + "FROM assessments a "
          + "LEFT JOIN scans s ON s.assessment_id = a.id "
          + "LEFT JOIN answer_keys ak ON ak.id = a.answer_key_id "
          + "WHERE a.class_id = :classId "
          + "AND (:sheetTypeFilter IS NULL OR :sheetTypeFilter = '' OR a.sheet_type = :sheetTypeFilter) "
          + "AND (:search IS NULL OR :search = '' "
          + "OR a.name LIKE '%' || :search || '%' "
          + "OR a.sheet_type LIKE '%' || :search || '%' "
          + "OR a.exam_date LIKE '%' || :search || '%') "
          + "GROUP BY a.id "
          + "ORDER BY "
          + "CASE WHEN :sortKey = 'NEWEST' THEN a.created_at END DESC, "
          + "CASE WHEN :sortKey = 'OLDEST' THEN a.created_at END ASC, "
          + "CASE WHEN :sortKey = 'NAME_ASC' THEN a.name END COLLATE NOCASE ASC, "
          + "CASE WHEN :sortKey = 'NAME_DESC' THEN a.name END COLLATE NOCASE DESC, "
          + "CASE WHEN :sortKey = 'EXAM_DATE_NEWEST' THEN a.exam_date_epoch END DESC, "
          + "CASE WHEN :sortKey = 'EXAM_DATE_OLDEST' THEN a.exam_date_epoch END ASC, "
          + "a.created_at DESC")
  List<AssessmentListRow> queryAssessmentList(String classId, String sheetTypeFilter, String search,
                                              String sortKey);
}
