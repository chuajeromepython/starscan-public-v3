package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.QuizEntity;
import com.example.omrscanner.database.projections.AssessmentListRow;

import java.util.List;

/** DAO for quizzes. Quizzes are local-only — no sync-related queries. */
@Dao
public interface QuizDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuizEntity quiz);

    @Update
    void update(QuizEntity quiz);

    @Delete
    void delete(QuizEntity quiz);

    @Query("SELECT * FROM quizzes WHERE id = :id")
    QuizEntity getById(String id);

    /** Every quiz, unfiltered — used for full local backup export. */
    @Query("SELECT * FROM quizzes")
    List<QuizEntity> getAllSync();

    @Query("UPDATE quizzes SET answer_key_id = :keyId WHERE id = :quizId")
    void setAnswerKey(String quizId, String keyId);

    @Query("UPDATE quizzes SET answer_key_id = NULL WHERE answer_key_id = :keyId")
    void clearAnswerKeyRef(String keyId);

    /** Reuses AssessmentListRow as the read model — same column aliases, so the
     *  existing assessment card UI (ClassScreenRenderer.createActivityCard)
     *  renders quiz rows with zero changes. sheetType is formatted as
     *  "ZPH40 • 1st Term" so the term shows in the card subtitle. */
    @Query("SELECT q.id AS id, q.class_id AS classId, q.name AS name, "
            + "(q.sheet_type || ' \u2022 ' || q.term) AS sheetType, q.exam_date AS examDate, "
            + "q.exam_date_epoch AS examDateEpoch, q.created_at AS createdAt, "
            + "q.answer_key_id AS answerKeyId, ak.name AS answerKeyName, "
            + "(c.grade || ' \u2014 ' || c.section) AS className, "
            + "COUNT(qs.id) AS scanCount, "
            + "(SELECT COUNT(DISTINCT sl.lrn) FROM student_lrn sl WHERE sl.className = q.class_id) AS syncedStudentCount, "
            + "(SELECT COUNT(DISTINCT ans.quiz_scan_id) FROM quiz_scan_answers ans "
            + "  JOIN quiz_scans qs2 ON qs2.id = ans.quiz_scan_id "
            + "  WHERE qs2.quiz_id = q.id AND LENGTH(ans.answer) > 1) AS needsCorrectionCount "
            + "FROM quizzes q "
            + "LEFT JOIN quiz_scans qs ON qs.quiz_id = q.id "
            + "LEFT JOIN answer_keys ak ON ak.id = q.answer_key_id "
            + "LEFT JOIN classes c ON c.id = q.class_id "
            + "WHERE (:termFilter IS NULL OR :termFilter = '' OR q.term = :termFilter) "
            + "AND (:classIdFilter IS NULL OR :classIdFilter = '' OR q.class_id = :classIdFilter) "
            + "AND (:search IS NULL OR :search = '' "
            + "OR q.name LIKE '%' || :search || '%' "
            + "OR q.term LIKE '%' || :search || '%' "
            + "OR q.exam_date LIKE '%' || :search || '%') "
            + "GROUP BY q.id "
            + "ORDER BY "
            + "CASE WHEN :sortKey = 'NEWEST' THEN q.created_at END DESC, "
            + "CASE WHEN :sortKey = 'OLDEST' THEN q.created_at END ASC, "
            + "CASE WHEN :sortKey = 'NAME_ASC' THEN q.name END COLLATE NOCASE ASC, "
            + "CASE WHEN :sortKey = 'NAME_DESC' THEN q.name END COLLATE NOCASE DESC, "
            + "CASE WHEN :sortKey = 'EXAM_DATE_NEWEST' THEN q.exam_date_epoch END DESC, "
            + "CASE WHEN :sortKey = 'EXAM_DATE_OLDEST' THEN q.exam_date_epoch END ASC, "
            + "q.created_at DESC")
    List<AssessmentListRow> queryAllQuizzes(String termFilter, String classIdFilter,
                                            String search, String sortKey);
}