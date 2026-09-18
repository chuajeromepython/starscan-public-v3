package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.omrscanner.database.entities.EcdcCompetencyEntity;

import java.util.List;

@Dao
public interface EcdcCompetencyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EcdcCompetencyEntity> competencies);

    @Query("SELECT * FROM ecdc_competencies WHERE domain_id = :domainId ORDER BY id ASC")
    List<EcdcCompetencyEntity> getByDomain(int domainId);

    @Query("SELECT * FROM ecdc_competencies ORDER BY id ASC")
    List<EcdcCompetencyEntity> getAll();

    @Query("SELECT COUNT(*) FROM ecdc_competencies")
    int count();

    @Query("DELETE FROM ecdc_competencies")
    void deleteAll();
}