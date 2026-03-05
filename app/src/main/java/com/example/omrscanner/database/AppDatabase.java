package com.example.omrscanner.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

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
 * Version: 1 (increment and add a Migration when schema changes in future)
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
}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

  private static final String DATABASE_NAME = "omrscanner.db";

  // Volatile ensures the singleton is visible across threads immediately
  private static volatile AppDatabase INSTANCE;

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
              DATABASE_NAME).build();
        }
      }
    }
    return INSTANCE;
  }
}
