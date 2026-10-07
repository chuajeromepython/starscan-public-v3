package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.omrscanner.database.entities.QuizScanAnswerEntity;

import java.util.List;

/**
 * DAO for quiz scan answer operations.
 * Answers are always written in bulk (one insert per scan) and read as a group.
 */
@Dao
public interface QuizScanAnswerDao {

    /**
     * Insert a full list of answers for a quiz scan.
     * OnConflictStrategy.REPLACE updates the answer if (quiz_scan_id, item_number)
     * already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuizScanAnswerEntity> answers);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuizScanAnswerEntity answer);

    @Query("SELECT * FROM quiz_scan_answers WHERE quiz_scan_id = :quizScanId ORDER BY item_number ASC")
    List<QuizScanAnswerEntity> getByQuizScan(int quizScanId);

    @Query("SELECT * FROM quiz_scan_answers WHERE quiz_scan_id = :quizScanId AND item_number = :itemNumber")
    QuizScanAnswerEntity getByItem(int quizScanId, int itemNumber);

    /** Delete all answers for a quiz scan — used when re-scanning a paper. */
    @Query("DELETE FROM quiz_scan_answers WHERE quiz_scan_id = :quizScanId")
    void deleteByQuizScan(int quizScanId);

    @Query("SELECT COUNT(*) FROM quiz_scan_answers WHERE quiz_scan_id = :quizScanId AND answer != ''")
    int countAnsweredItems(int quizScanId);

    /** Every quiz scan answer, unfiltered — used for full local backup export. */
    @Query("SELECT * FROM quiz_scan_answers")
    List<QuizScanAnswerEntity> getAllSync();
}