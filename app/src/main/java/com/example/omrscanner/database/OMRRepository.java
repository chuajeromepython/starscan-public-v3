package com.example.omrscanner.database;

import android.content.Context;

import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.entities.TeacherEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OMRRepository — single point of data access for all Activities and services.
 *
 * Why a Repository?
 * - Room cannot run on the main thread; this class handles background
 * execution.
 * - Activities call clean, simple methods instead of touching DAOs directly.
 *
 * Usage:
 * OMRRepository repo = new OMRRepository(context);
 * repo.insertClass(classEntity, id -> { /* use the result on main thread *\/
 * });
 */
public class OMRRepository {

  private final AppDatabase db;
  private final ExecutorService executor;

  public OMRRepository(Context context) {
    this.db = AppDatabase.getInstance(context);
    this.executor = Executors.newSingleThreadExecutor();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Callback interface — returns result to main thread
  // ─────────────────────────────────────────────────────────────────────────
  public interface Callback<T> {
    void onResult(T result);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // TEACHER
  // ═════════════════════════════════════════════════════════════════════════

  public void insertTeacher(TeacherEntity teacher, Callback<Long> callback) {
    executor.execute(() -> {
      long id = db.teacherDao().insert(teacher);
      if (callback != null)
        callback.onResult(id);
    });
  }

  public void updateTeacher(TeacherEntity teacher, Callback<Void> callback) {
    executor.execute(() -> {
      db.teacherDao().update(teacher);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void getTeacherById(int id, Callback<TeacherEntity> callback) {
    executor.execute(() -> {
      TeacherEntity teacher = db.teacherDao().getById(id);
      if (callback != null)
        callback.onResult(teacher);
    });
  }

  public void getAllTeachers(Callback<List<TeacherEntity>> callback) {
    executor.execute(() -> {
      List<TeacherEntity> list = db.teacherDao().getAll();
      if (callback != null)
        callback.onResult(list);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // CLASS
  // ═════════════════════════════════════════════════════════════════════════

  public void insertClass(ClassEntity classEntity, Callback<Void> callback) {
    executor.execute(() -> {
      db.classDao().insert(classEntity);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void updateClass(ClassEntity classEntity, Callback<Void> callback) {
    executor.execute(() -> {
      db.classDao().update(classEntity);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void deleteClass(ClassEntity classEntity, Callback<Void> callback) {
    executor.execute(() -> {
      db.classDao().delete(classEntity);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void getClassById(String id, Callback<ClassEntity> callback) {
    executor.execute(() -> {
      ClassEntity result = db.classDao().getById(id);
      if (callback != null)
        callback.onResult(result);
    });
  }

  public void getClassesByTeacher(int teacherId, Callback<List<ClassEntity>> callback) {
    executor.execute(() -> {
      List<ClassEntity> list = db.classDao().getByTeacher(teacherId);
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void getAllClasses(Callback<List<ClassEntity>> callback) {
    executor.execute(() -> {
      List<ClassEntity> list = db.classDao().getAll();
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void countClasses(Callback<Integer> callback) {
    executor.execute(() -> {
      int count = db.classDao().countAll();
      if (callback != null)
        callback.onResult(count);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // ASSESSMENT
  // ═════════════════════════════════════════════════════════════════════════

  public void insertAssessment(AssessmentEntity assessment, Callback<Void> callback) {
    executor.execute(() -> {
      db.assessmentDao().insert(assessment);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void updateAssessment(AssessmentEntity assessment, Callback<Void> callback) {
    executor.execute(() -> {
      db.assessmentDao().update(assessment);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void deleteAssessment(AssessmentEntity assessment, Callback<Void> callback) {
    executor.execute(() -> {
      db.assessmentDao().delete(assessment);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void getAssessmentById(String id, Callback<AssessmentEntity> callback) {
    executor.execute(() -> {
      AssessmentEntity result = db.assessmentDao().getById(id);
      if (callback != null)
        callback.onResult(result);
    });
  }

  public void getAssessmentsByClass(String classId, Callback<List<AssessmentEntity>> callback) {
    executor.execute(() -> {
      List<AssessmentEntity> list = db.assessmentDao().getByClass(classId);
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void getAssessmentsBySheetType(String classId, String sheetType,
      Callback<List<AssessmentEntity>> callback) {
    executor.execute(() -> {
      List<AssessmentEntity> list = db.assessmentDao().getByClassAndSheetType(classId, sheetType);
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void countAssessments(Callback<Integer> callback) {
    executor.execute(() -> {
      int count = db.assessmentDao().countAll();
      if (callback != null)
        callback.onResult(count);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // SCAN
  // ═════════════════════════════════════════════════════════════════════════

  public void insertScan(ScanEntity scan, Callback<Long> callback) {
    executor.execute(() -> {
      long id = db.scanDao().insert(scan);
      if (callback != null)
        callback.onResult(id);
    });
  }

  public void updateScan(ScanEntity scan, Callback<Void> callback) {
    executor.execute(() -> {
      db.scanDao().update(scan);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void deleteScan(ScanEntity scan, Callback<Void> callback) {
    executor.execute(() -> {
      db.scanDao().delete(scan);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void getScanById(int id, Callback<ScanEntity> callback) {
    executor.execute(() -> {
      ScanEntity result = db.scanDao().getById(id);
      if (callback != null)
        callback.onResult(result);
    });
  }

  public void getScansByAssessment(String assessmentId, Callback<List<ScanEntity>> callback) {
    executor.execute(() -> {
      List<ScanEntity> list = db.scanDao().getByAssessment(assessmentId);
      if (callback != null)
        callback.onResult(list);
    });
  }

  /** Called when the external scoring system sends back a score. */
  public void updateScanScore(int scanId, int score, Callback<Void> callback) {
    executor.execute(() -> {
      db.scanDao().updateScore(scanId, score, System.currentTimeMillis());
      if (callback != null)
        callback.onResult(null);
    });
  }

  /** Called when a teacher corrects a misread LRN. */
  public void updateScanLrn(int scanId, String lrn, Callback<Void> callback) {
    executor.execute(() -> {
      db.scanDao().updateLrn(scanId, lrn, System.currentTimeMillis());
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void countScans(Callback<Integer> callback) {
    executor.execute(() -> {
      int count = db.scanDao().countAll();
      if (callback != null)
        callback.onResult(count);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // ANSWER
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Convert and save a Map<Integer, String> answers (from OMR engine output)
   * into individual AnswerEntity rows in the DB.
   *
   * @param scanId  The scan this answer set belongs to (returned by insertScan
   *                callback).
   * @param answers Map of item_number → answer string from the OMR engine.
   */
  public void insertAnswersFromMap(int scanId, Map<Integer, String> answers,
      Callback<Void> callback) {
    executor.execute(() -> {
      List<AnswerEntity> entities = new java.util.ArrayList<>();
      if (answers != null) {
        for (Map.Entry<Integer, String> entry : answers.entrySet()) {
          String val = entry.getValue() != null ? entry.getValue() : "";
          entities.add(new AnswerEntity(scanId, entry.getKey(), val));
        }
      }
      db.answerDao().insertAll(entities);
      if (callback != null)
        callback.onResult(null);
    });
  }

  public void getAnswersByScan(int scanId, Callback<List<AnswerEntity>> callback) {
    executor.execute(() -> {
      List<AnswerEntity> list = db.answerDao().getByScan(scanId);
      if (callback != null)
        callback.onResult(list);
    });
  }

  /** Delete all answers for a scan — used before re-scanning a paper. */
  public void deleteAnswersByScan(int scanId, Callback<Void> callback) {
    executor.execute(() -> {
      db.answerDao().deleteByScan(scanId);
      if (callback != null)
        callback.onResult(null);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // TEACHER CONVENIENCE
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Load the first (and only) teacher row in the database.
   * Returns null if no teacher has been saved yet.
   */
  public void getFirstTeacher(Callback<TeacherEntity> callback) {
    executor.execute(() -> {
      TeacherEntity teacher = db.teacherDao().getFirst();
      if (callback != null)
        callback.onResult(teacher);
    });
  }

  /**
   * Insert-or-update the global teacher name.
   * If no teacher row exists yet one is created; otherwise the existing
   * row is updated in-place. Returns the final TeacherEntity.
   */
  public void upsertTeacher(String name, Callback<TeacherEntity> callback) {
    executor.execute(() -> {
      TeacherEntity existing = db.teacherDao().getFirst();
      if (existing == null) {
        existing = new TeacherEntity(name);
        long newId = db.teacherDao().insert(existing);
        existing.id = (int) newId;
      } else {
        existing.name = name;
        existing.updatedAt = System.currentTimeMillis();
        db.teacherDao().update(existing);
      }
      final TeacherEntity result = existing;
      if (callback != null)
        callback.onResult(result);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
  // SCAN CONVENIENCE
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Find a scan within an assessment that matches a given LRN.
   * Returns null if no match is found. Used for duplicate-LRN detection
   * and for replace-on-rescan logic.
   */
  public void getScanByAssessmentAndLrn(String assessmentId, String lrn,
      Callback<ScanEntity> callback) {
    executor.execute(() -> {
      ScanEntity result = db.scanDao().getByAssessmentAndLrn(assessmentId, lrn);
      if (callback != null)
        callback.onResult(result);
    });
  }

  /**
   * Synchronous LRN existence check — safe to call from a background thread
   * (used by the static isLrnExists replacement in DashboardActivity).
   */
  public boolean isLrnExistsSync(String assessmentId, String lrn) {
    return db.scanDao().getByAssessmentAndLrn(assessmentId, lrn) != null;
  }

  /**
   * Synchronous scan retrieval by assessmentId+lrn — safe to call from a
   * background thread (used by the static saveScanResult replacement).
   */
  public ScanEntity getScanByAssessmentAndLrnSync(String assessmentId, String lrn) {
    return db.scanDao().getByAssessmentAndLrn(assessmentId, lrn);
  }
}
