package com.example.omrscanner.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.projections.ClassListRow;

import java.util.List;

/**
 * DAO for class operations.
 */
@Dao
public interface ClassDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(ClassEntity classEntity);

  @Update
  void update(ClassEntity classEntity);

  @Delete
  void delete(ClassEntity classEntity);

  @Query("SELECT * FROM classes WHERE id = :id")
  ClassEntity getById(String id);

  @Query("SELECT * FROM classes WHERE classroom_id = :classroomId LIMIT 1")
  ClassEntity getByClassroomId(int classroomId);

  @Query("SELECT * FROM classes WHERE teacher_id = :teacherId ORDER BY created_at DESC")
  List<ClassEntity> getByTeacher(int teacherId);

  @Query("SELECT * FROM classes ORDER BY created_at DESC")
  List<ClassEntity> getAll();

  @Query("SELECT COUNT(*) FROM classes WHERE teacher_id = :teacherId")
  int countByTeacher(int teacherId);

  @Query("SELECT COUNT(*) FROM classes")
  int countAll();

  @Query("SELECT c.id AS id, c.grade AS grade, c.section AS section, "
      + "c.school_year AS schoolYear, c.created_at AS createdAt, "
      + "COUNT(a.id) AS assessmentCount "
      + "FROM classes c "
      + "LEFT JOIN assessments a ON a.class_id = c.id "
      + "WHERE (:search IS NULL OR :search = '' "
      + "OR c.grade LIKE '%' || :search || '%' "
      + "OR c.section LIKE '%' || :search || '%' "
      + "OR c.school_year LIKE '%' || :search || '%') "
      + "AND (:gradeFilter IS NULL OR :gradeFilter = '' OR c.grade = :gradeFilter) "
      + "AND (:schoolYearFilter IS NULL OR :schoolYearFilter = '' OR c.school_year = :schoolYearFilter) "
      + "GROUP BY c.id "
      + "ORDER BY "
      + "CASE WHEN :sortKey = 'NEWEST' THEN c.created_at END DESC, "
      + "CASE WHEN :sortKey = 'OLDEST' THEN c.created_at END ASC, "
      + "CASE WHEN :sortKey = 'GRADE_ASC' THEN c.grade END COLLATE NOCASE ASC, "
      + "CASE WHEN :sortKey = 'SECTION_ASC' THEN c.section END COLLATE NOCASE ASC, "
      + "c.created_at DESC")
  List<ClassListRow> queryClassList(String search, String gradeFilter, String schoolYearFilter,
      String sortKey);

  @Query("SELECT DISTINCT grade FROM classes "
      + "WHERE grade IS NOT NULL AND grade != '' "
      + "ORDER BY grade COLLATE NOCASE ASC")
  List<String> getDistinctGrades();

  @Query("SELECT DISTINCT school_year FROM classes "
      + "WHERE school_year IS NOT NULL AND school_year != '' "
      + "ORDER BY school_year DESC")
  List<String> getDistinctSchoolYears();
}
