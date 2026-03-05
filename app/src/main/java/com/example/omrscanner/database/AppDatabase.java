package com.example.omrscanner.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.omrscanner.database.dao.AnswerDao;
import com.example.omrscanner.database.dao.AssessmentDao;
import com.example.omrscanner.database.dao.ClassDao;
import com.example.omrscanner.database.dao.ScanDao;
import com.example.omrscanner.database.dao.TeacherDao;
import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.entities.TeacherEntity;

/**
 * Room Database — single source of truth for all OMRScanner data.
 *
 * Tables: teachers → classes → assessments → scans → answers
 * Version: 2 (increment and add a Migration when schema changes in future)
 *
 * Usage:
 * AppDatabase db = AppDatabase.getInstance(context);
 * db.classDao().getByTeacher(teacherId);
 */
@Database(entities = {
    TeacherEntity.class,
    ClassEntity.class,
    AssessmentEntity.class,
    ScanEntity.class,
    AnswerEntity.class
}, version = 2, exportSchema = false)
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

  // ── Abstract DAO accessors (Room generates the implementations) ──────────
  public abstract TeacherDao teacherDao();

  public abstract ClassDao classDao();

  public abstract AssessmentDao assessmentDao();

  public abstract ScanDao scanDao();

  public abstract AnswerDao answerDao();

  // ── Singleton ────────────────────────────────────────────────────────────
  public static AppDatabase getInstance(Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE = Room.databaseBuilder(
              context.getApplicationContext(),
              AppDatabase.class,
              DATABASE_NAME)
              .addMigrations(MIGRATION_1_2)
              .build();
        }
      }
    }
    return INSTANCE;
  }
}
