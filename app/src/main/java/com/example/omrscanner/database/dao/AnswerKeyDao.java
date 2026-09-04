package com.example.omrscanner.database.dao;

import com.example.omrscanner.database.projections.AnswerKeyLinkInfo;
import com.example.omrscanner.database.projections.AnswerKeyLinkedAssessment;
import com.example.omrscanner.database.projections.AnswerKeyLinkedQuiz;

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

    /** For each key: is it linked to an assessment and/or a quiz, and if so which one (most recent) + its sheet type. */
    @Query("SELECT ak.id AS id, " +
            "(SELECT a.name FROM assessments a WHERE a.answer_key_id = ak.id ORDER BY a.created_at DESC LIMIT 1) AS linkedAssessmentName, " +
            "(SELECT a.sheet_type FROM assessments a WHERE a.answer_key_id = ak.id ORDER BY a.created_at DESC LIMIT 1) AS linkedSheetType, " +
            "(SELECT COUNT(*) FROM assessments a WHERE a.answer_key_id = ak.id) AS linkedCount, " +
            "(SELECT q.name FROM quizzes q WHERE q.answer_key_id = ak.id ORDER BY q.created_at DESC LIMIT 1) AS linkedQuizName, " +
            "(SELECT COUNT(*) FROM quizzes q WHERE q.answer_key_id = ak.id) AS linkedQuizCount " +
            "FROM answer_keys ak")
    List<AnswerKeyLinkInfo> getLinkInfo();

    /** Every assessment currently linked to any answer key, newest-first — grouped
     *  client-side by answerKeyId to populate the "Linked to" dropdown per card. */
    @Query("SELECT a.answer_key_id AS answerKeyId, a.id AS id, a.name AS name, a.sheet_type AS sheetType " +
            "FROM assessments a " +
            "WHERE a.answer_key_id IS NOT NULL " +
            "ORDER BY a.created_at DESC")
    List<AnswerKeyLinkedAssessment> getLinkedAssessments();

    /** Every quiz currently linked to any answer key, newest-first — grouped
     *  client-side by answerKeyId to populate the "Linked to Quiz" dropdown per card. */
    @Query("SELECT q.answer_key_id AS answerKeyId, q.id AS id, q.name AS name, q.sheet_type AS sheetType " +
            "FROM quizzes q " +
            "WHERE q.answer_key_id IS NOT NULL " +
            "ORDER BY q.created_at DESC")
    List<AnswerKeyLinkedQuiz> getLinkedQuizzes();
}
