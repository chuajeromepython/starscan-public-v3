package com.example.omrscanner.database;

import android.content.Context;
import android.util.Log;

import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.entities.StudentLrnEntity;
import com.example.omrscanner.database.entities.TeacherEntity;
import com.example.omrscanner.database.projections.AssessmentListRow;
import com.example.omrscanner.database.projections.ClassListRow;
import com.example.omrscanner.database.entities.UserEntity;

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

  public void queryClassList(String search, String gradeFilter, String schoolYearFilter, String sortKey,
      Callback<List<ClassListRow>> callback) {
    executor.execute(() -> {
      List<ClassListRow> list = db.classDao().queryClassList(search, gradeFilter, schoolYearFilter,
          sortKey);
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void getDistinctClassGrades(Callback<List<String>> callback) {
    executor.execute(() -> {
      List<String> list = db.classDao().getDistinctGrades();
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void getDistinctClassSchoolYears(Callback<List<String>> callback) {
    executor.execute(() -> {
      List<String> list = db.classDao().getDistinctSchoolYears();
      if (callback != null)
        callback.onResult(list);
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
// USER
// ═════════════════════════════════════════════════════════════════════════

  public void insertUser(UserEntity user, Callback<Long> callback) {
    executor.execute(() -> {
      long id = db.userDao().insert(user);
      if (callback != null)
        callback.onResult(id);
    });
  }

  public void getActiveUser(Callback<UserEntity> callback) {
    executor.execute(() -> {
      UserEntity user = db.userDao().getActiveUser();
      if (callback != null)
        callback.onResult(user);
    });
  }

  /**
   * Insert or update a class row that came from server sync, keyed by
   * classroom_id (the server's identity for the classroom) rather than our
   * local UUID. Reuses the existing row's id/created_at if one already
   * exists for this classroom_id, so re-syncing updates in place instead
   * of creating duplicate class cards.
   */
  public void upsertClassFromSync(ClassEntity incoming, Callback<Void> callback) {
    executor.execute(() -> {
      ClassEntity existing = (incoming.classroomId != null)
              ? db.classDao().getByClassroomId(incoming.classroomId) : null;
      if (existing != null) {
        incoming.id = existing.id;
        incoming.createdAt = existing.createdAt;
      } else {
        incoming.id = java.util.UUID.randomUUID().toString().substring(0, 7);
        incoming.createdAt = System.currentTimeMillis();
      }
      incoming.updatedAt = System.currentTimeMillis();
      db.classDao().insert(incoming); // REPLACE strategy on primary key id
      if (callback != null) callback.onResult(null);
    });
  }

  public void insertUserAsActive(UserEntity user, Callback<Long> callback) {
    executor.execute(() -> {
      long id = db.userDao().insertAsOnlyActive(user);
      if (callback != null)
        callback.onResult(id);
    });
  }

  public void getAllUsers(Callback<List<UserEntity>> callback) {
    executor.execute(() -> {
      List<UserEntity> list = db.userDao().getAll();
      if (callback != null)
        callback.onResult(list);
    });
  }

  public void queryAssessmentList(String classId, String sheetTypeFilter, String search, String sortKey,
      Callback<List<AssessmentListRow>> callback) {
    executor.execute(() -> {
      List<AssessmentListRow> list = db.assessmentDao().queryAssessmentList(classId, sheetTypeFilter,
          search, sortKey);
      if (callback != null)
        callback.onResult(list);
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
   * @param answers Map of item_number - answer string from the OMR engine.
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

  // ═════════════════════════════════════════════════════════════════════════
  // ANSWER KEY
  // ═════════════════════════════════════════════════════════════════════════

  /** Insert a new answer key row. */
  public void insertAnswerKey(AnswerKeyEntity key, Callback<Void> callback) {
    executor.execute(() -> {
      db.answerKeyDao().insert(key);
      if (callback != null)
        callback.onResult(null);
    });
  }

  /** Update an existing answer key (name, school year, answers). */
  public void updateAnswerKey(AnswerKeyEntity key, Callback<Void> callback) {
    executor.execute(() -> {
      key.updatedAt = System.currentTimeMillis();
      db.answerKeyDao().update(key);
      // Re-grade all assessments that reference this key so scores stay in sync
      List<AssessmentEntity> linked = db.assessmentDao().getByAnswerKeyId(key.id);
      for (AssessmentEntity assessment : linked) {
        gradeAllScansSync(assessment.id, key);
      }
      if (callback != null)
        callback.onResult(null);
    });
  }

  /**
   * Delete an answer key and nullify all assessment references to it.
   * Also clears scores for all scans in those assessments — the scores are
   * no longer valid without the key. Order matters: clear scores FIRST
   * (while the subquery can still find the linked assessments), then nullify
   * the reference, then delete the key row.
   */
  public void deleteAnswerKey(AnswerKeyEntity key, Callback<Void> callback) {
    executor.execute(() -> {
      // 1. Clear scores while we can still identify linked assessments
      db.scanDao().clearScoresByAnswerKey(key.id, System.currentTimeMillis());
      // 2. Nullify assessment references
      db.assessmentDao().clearAnswerKeyRef(key.id);
      // 3. Delete the key row
      db.answerKeyDao().delete(key);
      if (callback != null)
        callback.onResult(null);
    });
  }

  /** Load all answer keys, newest-first. */
  public void getAllAnswerKeys(Callback<List<AnswerKeyEntity>> callback) {
    executor.execute(() -> {
      List<AnswerKeyEntity> list = db.answerKeyDao().getAll();
      if (callback != null)
        callback.onResult(list);
    });
  }

  /** Load answer keys for a specific sheet type (for contextual assignment UI). */
  public void getAnswerKeysBySheetType(String sheetType, Callback<List<AnswerKeyEntity>> callback) {
    executor.execute(() -> {
      List<AnswerKeyEntity> list = db.answerKeyDao().getBySheetType(sheetType);
      if (callback != null)
        callback.onResult(list);
    });
  }

  /** Load a single answer key by its ID. */
  public void getAnswerKeyById(String id, Callback<AnswerKeyEntity> callback) {
    executor.execute(() -> {
      AnswerKeyEntity key = db.answerKeyDao().getById(id);
      if (callback != null)
        callback.onResult(key);
    });
  }

  /**
   * Synchronous assessment fetch — safe to call from any background thread
   * (used by the static saveScanResult helper in DashboardActivity).
   */
  public AssessmentEntity getAssessmentByIdSync(String id) {
    return db.assessmentDao().getById(id);
  }

  public ClassEntity getClassByIdSync(String id) {
    return db.classDao().getById(id);
  }

  public List<ScanEntity> getScansByAssessmentSync(String assessmentId) {
    return db.scanDao().getByAssessment(assessmentId);
  }

  public List<AnswerEntity> getAnswersByScanSync(int scanId) {
    return db.answerDao().getByScan(scanId);
  }

  /**
   * Synchronous answer-key fetch — safe to call from any background thread
   * (used by the static saveScanResult helper in DashboardActivity).
   */
  public AnswerKeyEntity getAnswerKeyByIdSync(String id) {
    return db.answerKeyDao().getById(id);
  }

  /**
   * Grade (or re-grade) every scan that belongs to an assessment against the
   * supplied answer key. Writes the computed score to ScanEntity.score for
   * each scan, then calls back with the number of scans graded.
   *
   * <p>Each item in the key's CSV is compared with the student's AnswerEntity
   * for that item number. A "?" in the key means "any answer is correct".
   * A blank student answer counts as wrong unless the key position is also
   * blank or "?".
   */
  public void gradeAllScans(String assessmentId, AnswerKeyEntity key, Callback<Integer> callback) {
    executor.execute(() -> {
      int graded = gradeAllScansSync(assessmentId, key);
      if (callback != null) callback.onResult(graded);
    });
  }

  public void insertStudentLrnFromSync(String lrn, String classId,
                                       Integer sectionId, Integer gradeLevelId, Integer classroomId, Callback<Void> callback) {
    executor.execute(() -> {
      try {
        StudentLrnEntity student = new StudentLrnEntity();
        student.lrn = lrn;
        student.className = classId;
        student.sectionId = sectionId;
        student.gradeLevelId = gradeLevelId;
        student.classroomId = classroomId;
        db.studentLrnDao().insert(student);
        if (callback != null) callback.onResult(null);
      } catch (Exception e) {
        Log.e("StudentLRN", "Failed to insert synced student: " + lrn, e);
        if (callback != null) callback.onResult(null);
      }
    });
  }

  /**
   * Synchronous version of gradeAllScans — can be called inline from another
   * executor task (e.g. inside linkAnswerKeyToAssessment).
   */
  private int gradeAllScansSync(String assessmentId, AnswerKeyEntity key) {
    String[] correctAnswers = (key.answers != null && !key.answers.isEmpty())
        ? key.answers.split(",") : new String[0];

    List<ScanEntity> scans = db.scanDao().getByAssessment(assessmentId);
    int graded = 0;
    for (ScanEntity scan : scans) {
      List<AnswerEntity> answerEntities = db.answerDao().getByScan(scan.id);
      java.util.Map<Integer, String> answerMap = new java.util.LinkedHashMap<>();
      for (AnswerEntity ae : answerEntities) answerMap.put(ae.itemNumber, ae.answer);

      int correct = 0;
      for (int i = 0; i < correctAnswers.length; i++) {
        String keyAns = correctAnswers[i].trim();
        if (keyAns.isEmpty() || keyAns.equals("?")) continue; // skip blank/wildcard keys
        String studentAns = answerMap.containsKey(i + 1) ? answerMap.get(i + 1) : "";
        if (keyAns.equals(studentAns)) correct++;
      }
      db.scanDao().updateScore(scan.id, correct, System.currentTimeMillis());
      graded++;
    }
    return graded;
  }

  /**
   * Assign an answer key to an assessment, then immediately grade all existing
   * scans for that assessment. Uses a targeted UPDATE — does not reload the
   * full AssessmentEntity.
   */
  public void linkAnswerKeyToAssessment(String assessmentId, String answerKeyId,
      Callback<Void> callback) {
    executor.execute(() -> {
      // 1. Persist the link
      db.assessmentDao().setAnswerKey(assessmentId, answerKeyId);
      // 2. Immediately grade all existing scans
      AnswerKeyEntity key = db.answerKeyDao().getById(answerKeyId);
      if (key != null) gradeAllScansSync(assessmentId, key);
      if (callback != null) callback.onResult(null);
    });
  }

  // ═════════════════════════════════════════════════════════════════════════
// STUDENT LRN
// ═════════════════════════════════════════════════════════════════════════

  public void insertStudentLrn(String lrn, String className, Callback<Void> callback) {
    executor.execute(() -> {
      try {
        StudentLrnEntity student = new StudentLrnEntity();
        student.lrn = lrn;  // just assign directly, no parseInt
        student.className = className;
        db.studentLrnDao().insert(student);
        if (callback != null)
          callback.onResult(null);
      } catch (Exception e) {
        Log.e("StudentLRN", "Failed to insert: " + lrn, e);
        if (callback != null)
          callback.onResult(null);
      }
    });
  }

  public void getAllStudentLrns(Callback<List<StudentLrnEntity>> callback) {
    executor.execute(() -> {
      List<StudentLrnEntity> list = db.studentLrnDao().getAll();
      if (callback != null)
        callback.onResult(list);
    });
  }

  /**
   * Remove the answer key assignment from an assessment (set to null).
   * Also clears all graded scores — they are no longer valid without a key.
   */
  public void unlinkAnswerKeyFromAssessment(String assessmentId, Callback<Void> callback) {
    executor.execute(() -> {
      // Clear scores first — they have no source of truth without an answer key
      db.scanDao().clearScoresByAssessment(assessmentId, System.currentTimeMillis());
      // Then nullify the link
      db.assessmentDao().setAnswerKey(assessmentId, null);
      if (callback != null)
        callback.onResult(null);
    });
  }
}
