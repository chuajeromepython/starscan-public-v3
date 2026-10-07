package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.omrscanner.database.entities.EcdcStudentDateEntity;

import java.util.List;

@Dao
public interface EcdcStudentDateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(EcdcStudentDateEntity row);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EcdcStudentDateEntity> rows);

    @Query("SELECT * FROM ecdc_student_dates "
            + "WHERE class_id = :classId AND lrn = :lrn AND period = :period LIMIT 1")
    EcdcStudentDateEntity get(String classId, String lrn, String period);

    @Query("SELECT * FROM ecdc_student_dates WHERE class_id = :classId AND period = :period")
    List<EcdcStudentDateEntity> getForClassPeriod(String classId, String period);

    @Query("SELECT * FROM ecdc_student_dates")
    List<EcdcStudentDateEntity> getAllSync();
}