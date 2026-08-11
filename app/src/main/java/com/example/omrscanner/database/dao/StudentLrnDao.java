package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.StudentLrnEntity;

import java.util.List;

@Dao
public interface StudentLrnDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(StudentLrnEntity student);

    @Update
    void update(StudentLrnEntity student);

    @Delete
    void delete(StudentLrnEntity student);

    @Query("SELECT * FROM student_lrn")
    List<StudentLrnEntity> getAll();

    @Query("SELECT * FROM student_lrn WHERE id = :id")
    StudentLrnEntity getById(int id);

    @Query("SELECT * FROM student_lrn WHERE lrn = :lrn LIMIT 1")
    StudentLrnEntity findByLrn(String lrn);

    // Flips hot_sync on WITHOUT touching sectionId/gradeLevelId/classroomId --
    // used when a scan comes in for a student already present (e.g. synced
    // from the server). Returns the number of rows updated (0 if no match).
    @Query("UPDATE student_lrn SET hot_sync = 1 WHERE lrn = :lrn AND className = :className")
    int markHotSynced(String lrn, String className);

    @Query("SELECT * FROM student_lrn WHERE className = :className")
    List<StudentLrnEntity> findByClass(String className);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<StudentLrnEntity> students);

    @Query("SELECT COUNT(DISTINCT lrn) FROM student_lrn WHERE className = :className")
    int countByClass(String className);
}