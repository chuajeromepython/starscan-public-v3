package com.example.omrscanner.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.omrscanner.database.dao.AnswerDao;
import com.example.omrscanner.database.dao.AnswerKeyDao;
import com.example.omrscanner.database.dao.AssessmentDao;
import com.example.omrscanner.database.dao.ClassDao;
import com.example.omrscanner.database.dao.ScanDao;
import com.example.omrscanner.database.dao.TeacherDao;
import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.entities.TeacherEntity;

/**
 * Room Database — single source of truth for all OMRScanner data.
 *
 * Tables: teachers → classes → assessments → scans → answers
 *         answer_keys (global, reusable; soft-linked from assessments)
 *
 * Version history:
 *   1 → 2: Added exam_date_epoch + class/assessment indices.
 *   2 → 3: Added answer_keys table + assessments.answer_key_id column.
 *
 * Usage:
 * AppDatabase db = AppDatabase.getInstance(context);
 * db.answerKeyDao().getAll();
 */
@Database(entities = {
    TeacherEntity.class,
    ClassEntity.class,
    AssessmentEntity.class,
    ScanEntity.class,
    AnswerEntity.class,
    AnswerKeyEntity.class
}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

  private static final String DATABASE_NAME = "omrscanner.db";
  // Volatile ensures the singleton is visible across threads immediately
  private static volatile AppDatabase INSTANCE;

  private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
      database.execSQL(
          "ALTER TABLE assessments ADD COLUMN exam_date_epoch INTEGER NOT NULL DEFAULT 0");

      database.execSQL(
          "CREATE INDEX IF NOT EXISTS index_classes_grade ON classes(grade)");
      database.execSQL(
          "CREATE INDEX IF NOT EXISTS index_classes_school_year ON classes(school_year)");
      database.execSQL(
          "CREATE INDEX IF NOT EXISTS index_classes_created_at ON classes(created_at)");
      database.execSQL(
          "CREATE INDEX IF NOT EXISTS index_assessments_created_at ON assessments(created_at)");
      database.execSQL(
          "CREATE INDEX IF NOT EXISTS index_assessments_exam_date_epoch ON assessments(exam_date_epoch)");
      database.execSQL(
          "CREATE INDEX IF NOT EXISTS index_assessments_sheet_type ON assessments(sheet_type)");

      // Fast bulk backfill to avoid startup stalls on large datasets.
      database.execSQL(
          "UPDATE assessments SET exam_date_epoch = created_at WHERE exam_date_epoch = 0");
    }
  };

  private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // New answer_keys table — independent of any single assessment
      db.execSQL("CREATE TABLE IF NOT EXISTS answer_keys ("
          + "id TEXT NOT NULL PRIMARY KEY, "
          + "name TEXT, "
          + "school_year TEXT, "
          + "sheet_type TEXT, "
          + "answers TEXT, "
          + "created_at INTEGER NOT NULL DEFAULT 0, "
          + "updated_at INTEGER NOT NULL DEFAULT 0)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_answer_keys_sheet_type "
          + "ON answer_keys(sheet_type)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_answer_keys_created_at "
          + "ON answer_keys(created_at)");

      // Nullable soft-link column on assessments (no FK — safe soft delete)
      db.execSQL("ALTER TABLE assessments ADD COLUMN answer_key_id TEXT");
    }
  };

  /**
   * v3 → v4: Clear the previously-incorrect score values that were written as
   * the raw detected-bubble count instead of a real graded answer-key score.
   * After this migration every scan.score = NULL until an answer key is
   * assigned and linkAnswerKeyToAssessment() runs the grader.
   */
  private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("UPDATE scans SET score = NULL");
    }
  };

  // ── Abstract DAO accessors (Room generates the implementations) ──────────
  public abstract TeacherDao teacherDao();

  public abstract ClassDao classDao();

  public abstract AssessmentDao assessmentDao();

  public abstract ScanDao scanDao();

  public abstract AnswerDao answerDao();

  public abstract AnswerKeyDao answerKeyDao();

  // ── Singleton ────────────────────────────────────────────────────────────
  public static AppDatabase getInstance(Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE = Room.databaseBuilder(
              context.getApplicationContext(),
              AppDatabase.class,
              DATABASE_NAME)
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
              .build();
        }
      }
    }
    return INSTANCE;
  }
}
