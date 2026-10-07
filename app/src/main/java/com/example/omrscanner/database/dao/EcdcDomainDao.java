package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.omrscanner.database.entities.EcdcDomainEntity;

import java.util.List;

@Dao
public interface EcdcDomainDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EcdcDomainEntity> domains);

    @Query("SELECT * FROM ecdc_domains ORDER BY id ASC")
    List<EcdcDomainEntity> getAll();

    @Query("SELECT * FROM ecdc_domains WHERE id = :id LIMIT 1")
    EcdcDomainEntity getById(int id);

    @Query("SELECT COUNT(*) FROM ecdc_domains")
    int count();

    @Query("DELETE FROM ecdc_domains")
    void deleteAll();
}