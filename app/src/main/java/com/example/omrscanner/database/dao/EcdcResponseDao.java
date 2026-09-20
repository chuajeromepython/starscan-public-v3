package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.omrscanner.database.entities.EcdcResponseEntity;

import java.util.List;

@Dao
public interface EcdcResponseDao {

    // REPLACE hits the unique (class_id, lrn, period, competency_id) index, so
    // re-saving a competency overwrites the previous answer rather than duplicating it.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EcdcResponseEntity> responses);

    // Used when a teacher un-selects a mark: no answer is stored for that competency.
    @Query("DELETE FROM ecdc_responses "
            + "WHERE class_id = :classId AND lrn = :lrn AND period = :period "
            + "AND competency_id IN (:competencyIds)")
    void deleteForCompetencies(String classId, String lrn, String period, List<Integer> competencyIds);

    @Query("SELECT * FROM ecdc_responses "
            + "WHERE class_id = :classId AND lrn = :lrn AND period = :period")
    List<EcdcResponseEntity> getForStudentPeriod(String classId, String lrn, String period);
}