package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.QuizScanEntity;

import java.util.List;

/**
 * DAO for quiz scan operations. Deliberately separate from {@link ScanDao} —
 * quiz scans never join against assessments/classes tables, keeping quizzes
 * fully isolated from the rest of the app's data model.
 */
@Dao
public interface QuizScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(QuizScanEntity scan);

    @Update
    void update(QuizScanEntity scan);

    @Delete
    void delete(QuizScanEntity scan);

    @Query("SELECT * FROM quiz_scans WHERE id = :id")
    QuizScanEntity getById(int id);

    @Query("SELECT * FROM quiz_scans WHERE quiz_id = :quizId ORDER BY timestamp DESC")
    List<QuizScanEntity> getByQuiz(String quizId);

    @Query("SELECT COUNT(*) FROM quiz_scans WHERE quiz_id = :quizId")
    int countByQuiz(String quizId);

    @Query("SELECT COUNT(*) FROM quiz_scans")
    int countAll();

    /** Every quiz scan, unfiltered — used for full local backup export. */
    @Query("SELECT * FROM quiz_scans")
    List<QuizScanEntity> getAllSync();

    /**
     * Update the score received from grading against the quiz's answer key.
     * Also updates updated_at timestamp.
     */
    @Query("UPDATE quiz_scans SET score = :score, updated_at = :updatedAt WHERE id = :scanId")
    void updateScore(int scanId, int score, long updatedAt);

    /**
     * Update the student LRN (e.g. when corrected after scan).
     * Also updates updated_at timestamp.
     */
    @Query("UPDATE quiz_scans SET student_lrn = :lrn, updated_at = :updatedAt WHERE id = :scanId")
    void updateLrn(int scanId, String lrn, long updatedAt);

    /**
     * Clear graded scores for all scans in a given quiz.
     * Called when the quiz's answer key is unlinked — scores are no longer valid.
     */
    @Query("UPDATE quiz_scans SET score = NULL, updated_at = :updatedAt WHERE quiz_id = :quizId")
    void clearScoresByQuiz(String quizId, long updatedAt);

    /**
     * Clear graded scores for all scans whose quiz uses the given answer key.
     * Called BEFORE the answer key is deleted so the subquery still resolves.
     */
    @Query("UPDATE quiz_scans SET score = NULL, updated_at = :updatedAt " +
            "WHERE quiz_id IN (SELECT id FROM quizzes WHERE answer_key_id = :keyId)")
    void clearScoresByAnswerKey(String keyId, long updatedAt);

    /**
     * Look up a scan by quiz + LRN — used for duplicate-LRN detection
     * and for replace-on-rescan logic. Returns null if not found.
     */
    @Query("SELECT * FROM quiz_scans WHERE quiz_id = :quizId AND student_lrn = :lrn LIMIT 1")
    QuizScanEntity getByQuizAndLrn(String quizId, String lrn);

    /**
     * Same lookup as getByQuizAndLrn, but excludes a given scan id —
     * used when editing an existing scan's LRN, so the scan doesn't match
     * itself and only a genuinely different conflicting scan is returned.
     */
    @Query("SELECT * FROM quiz_scans WHERE quiz_id = :quizId AND student_lrn = :lrn " +
            "AND id != :excludeScanId LIMIT 1")
    QuizScanEntity getByQuizAndLrnExcluding(String quizId, String lrn, int excludeScanId);
}