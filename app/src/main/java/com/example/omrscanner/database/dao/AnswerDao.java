package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.omrscanner.database.entities.AnswerEntity;

import java.util.List;

/**
 * DAO for answer operations.
 * Answers are always written in bulk (one insert per scan) and read as a group.
 */
@Dao
public interface AnswerDao {

  /**
   * Insert a full list of answers for a scan.
   * OnConflictStrategy.REPLACE updates the answer if (scan_id, item_number)
   * already exists.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insertAll(List<AnswerEntity> answers);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(AnswerEntity answer);

  @Query("SELECT * FROM answers WHERE scan_id = :scanId ORDER BY item_number ASC")
  List<AnswerEntity> getByScan(int scanId);

  @Query("SELECT * FROM answers WHERE scan_id = :scanId AND item_number = :itemNumber")
  AnswerEntity getByItem(int scanId, int itemNumber);

  /** Delete all answers for a scan — used when re-scanning a paper. */
  @Query("DELETE FROM answers WHERE scan_id = :scanId")
  void deleteByScan(int scanId);

  @Query("SELECT COUNT(*) FROM answers WHERE scan_id = :scanId AND answer != ''")
  int countAnsweredItems(int scanId);
}
