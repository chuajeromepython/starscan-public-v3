package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.StudentLrnEntity;

import java.util.List;

@Dao
public interface StudentLrnDao {

    @Insert
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

    @Query("SELECT * FROM student_lrn WHERE className = :className")
    List<StudentLrnEntity> findByClass(String className);
}