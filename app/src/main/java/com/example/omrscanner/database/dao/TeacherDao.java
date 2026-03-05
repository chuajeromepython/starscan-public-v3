package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.TeacherEntity;

import java.util.List;

/**
 * DAO for teacher operations.
 */
@Dao
public interface TeacherDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  long insert(TeacherEntity teacher);

  @Update
  void update(TeacherEntity teacher);

  @Delete
  void delete(TeacherEntity teacher);

  @Query("SELECT * FROM teachers WHERE id = :id")
  TeacherEntity getById(int id);

  @Query("SELECT * FROM teachers ORDER BY name ASC")
  List<TeacherEntity> getAll();

  @Query("SELECT COUNT(*) FROM teachers")
  int count();

  /** Returns the first (and usually only) teacher row, or null if none. */
  @Query("SELECT * FROM teachers ORDER BY id ASC LIMIT 1")
  TeacherEntity getFirst();
}
