package com.example.omrscanner.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase; // database

import com.example.omrscanner.database.dao.AnswerDao;
import com.example.omrscanner.database.dao.AnswerKeyDao;
import com.example.omrscanner.database.dao.AssessmentDao;
import com.example.omrscanner.database.dao.QuizDao;
import com.example.omrscanner.database.dao.QuizScanDao;
import com.example.omrscanner.database.dao.QuizScanAnswerDao;
import com.example.omrscanner.database.dao.ClassDao;
import com.example.omrscanner.database.dao.ScanDao;
import com.example.omrscanner.database.dao.StudentLrnDao;
import com.example.omrscanner.database.dao.TeacherDao;
import com.example.omrscanner.database.dao.UserDao;
import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.QuizEntity;
import com.example.omrscanner.database.entities.QuizScanEntity;
import com.example.omrscanner.database.entities.QuizScanAnswerEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.database.entities.StudentLrnEntity;
import com.example.omrscanner.database.entities.TeacherEntity;
import com.example.omrscanner.database.entities.UserEntity;

/**
 * Room Database — single source of truth for all OMRScanner data.
 *
 * Tables: teachers → classes → assessments → scans → answers
 *         answer_keys (global, reusable; soft-linked from assessments)
 *
 * Version history:
 *   1 → 2: Added exam_date_epoch + class/assessment indices.
 *   2 → 3: Added answer_keys table + assessments.answer_key_id column.
 *   4 → 5: Added users table
 *   5 → 6: Added assessment_type
 *   13 → 14: Deduped student_lrn and added unique (lrn, className) index
 *   14 → 15: Added scans.key_reference_image_path (in-app only; not exported/backed up)
 *   15 → 16: Added users.role (Teacher/Student) — drives which dashboard opens after QR scan.
 *   16 → 17: Added teachers.user_id — ties each local teacher row to a server
 *            account so switching accounts on one device no longer shares
 *            one global teacher/roster.
 *   17 → 18: Added student_lrn.teacher_id (FK -> teachers.id, CASCADE) so a
 *            roster row can no longer be matched across teachers sharing one
 *            device. Backfilled from each row's class owner.
 *   18 → 19: Added assessments.server_assessment_id.
 *   19 → 20: Added student_lrn.first_name/middle_name/last_name so scan
 *            cards can show the student's name above their LRN. Existing
 *            rows stay NULL until the class is re-synced.
 *   21 → 22: Added quiz_scans + quiz_scan_answers tables. Quiz scans are
 *            stored separately from assessments' scans/answers tables so
 *            quizzes stay fully isolated, per their local-only design.
 *
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
        AnswerKeyEntity.class,
        UserEntity.class,
        StudentLrnEntity.class,
        QuizEntity.class,
        QuizScanEntity.class,
        QuizScanAnswerEntity.class
}, version = 22, exportSchema = false)
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

  private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("CREATE TABLE IF NOT EXISTS users ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "username TEXT, "
              + "userId INTEGER NOT NULL DEFAULT 0, "
              + "passkey TEXT, "
              + "serverIp TEXT, "
              + "firstName TEXT, "
              + "middleName TEXT, "
              + "lastName TEXT, "
              + "suffix TEXT, "
              + "school TEXT)");
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

  private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE assessments ADD COLUMN assessment_type TEXT");
    }
  };

  private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("CREATE TABLE IF NOT EXISTS student_lrn ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "lrn INTEGER, "
              + "className TEXT)");
    }
  };

  private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // Drop old table and recreate with INTEGER lrn
      db.execSQL("DROP TABLE IF EXISTS student_lrn");
      db.execSQL("CREATE TABLE IF NOT EXISTS student_lrn ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "lrn INTEGER, "
              + "className TEXT)");
    }
  };

  private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("DROP TABLE IF EXISTS student_lrn");
      db.execSQL("CREATE TABLE IF NOT EXISTS student_lrn ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "lrn TEXT, "
              + "className TEXT)");
    }
  };

  // Hot sync fields
  private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE assessments ADD COLUMN hot_sync INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN hot_sync INTEGER NOT NULL DEFAULT 0");
    }
  };

  private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE users ADD COLUMN is_active INTEGER NOT NULL DEFAULT 0");
    }
  };

  private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE classes ADD COLUMN classroom_id INTEGER");
      db.execSQL("ALTER TABLE classes ADD COLUMN section_id INTEGER");
      db.execSQL("ALTER TABLE classes ADD COLUMN advisor TEXT");
      db.execSQL("ALTER TABLE classes ADD COLUMN subject TEXT");
      db.execSQL("ALTER TABLE classes ADD COLUMN classes TEXT");
      db.execSQL("ALTER TABLE classes ADD COLUMN is_advisory INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE classes ADD COLUMN teacher_class_id INTEGER");
    }
  };

  private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN sectionId INTEGER");
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN gradeLevelId INTEGER");
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN classroomId INTEGER");
    }
  };

  private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // Collapse any duplicates that accumulated from repeated syncs before
      // this migration, keeping the earliest row per (lrn, className) pair.
      db.execSQL(
              "DELETE FROM student_lrn WHERE id NOT IN ("
                      + "SELECT MIN(id) FROM student_lrn GROUP BY lrn, className)");

      db.execSQL(
              "CREATE UNIQUE INDEX IF NOT EXISTS index_student_lrn_lrn_className "
                      + "ON student_lrn(lrn, className)");
    }
  };

  /**
   * v14 → v15: Adds the answer-key reference image path. This column is
   * intentionally NOT read by ClassExporter or BackupManager — it only
   * backs the in-app "Show Answer Key" toggle on the Detection Results screen.
   */
  private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE scans ADD COLUMN key_reference_image_path TEXT");
    }
  };

  private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE users ADD COLUMN role TEXT DEFAULT 'Teacher'");
    }
  };

  private static final Migration MIGRATION_16_17 = new Migration(16, 17) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE teachers ADD COLUMN user_id INTEGER");
      // Attribute the existing single teacher row to whichever account is
      // currently active, so upgrading an existing install doesn't orphan it.
      db.execSQL("UPDATE teachers SET user_id = "
              + "(SELECT userId FROM users WHERE is_active = 1 LIMIT 1) "
              + "WHERE user_id IS NULL");
    }
  };

  private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // SQLite's ALTER TABLE ADD COLUMN can only add a plain column -- it
      // cannot attach a new FOREIGN KEY constraint to an existing table.
      // Room verifies the real schema (via sqlite_master) against the
      // entity's declared FK on every open, so a plain ADD COLUMN here
      // would pass this migration but crash on the very next launch with
      // a schema-mismatch IllegalStateException. The table has to be
      // rebuilt (SQLite's standard recreate-table pattern).
      db.execSQL("CREATE TABLE student_lrn_new ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "teacher_id INTEGER, "
              + "lrn TEXT, "
              + "className TEXT, "
              + "sectionId INTEGER, "
              + "gradeLevelId INTEGER, "
              + "classroomId INTEGER, "
              + "hot_sync INTEGER NOT NULL DEFAULT 0, "
              + "FOREIGN KEY(teacher_id) REFERENCES teachers(id) ON DELETE CASCADE)");

      // Copy every existing row across, backfilling teacher_id from each
      // row's owning class (className stores the class UUID).
      db.execSQL("INSERT INTO student_lrn_new "
              + "(id, teacher_id, lrn, className, sectionId, gradeLevelId, classroomId, hot_sync) "
              + "SELECT s.id, "
              + "(SELECT c.teacher_id FROM classes c WHERE c.id = s.className), "
              + "s.lrn, s.className, s.sectionId, s.gradeLevelId, s.classroomId, s.hot_sync "
              + "FROM student_lrn s");

      db.execSQL("DROP TABLE student_lrn");
      db.execSQL("ALTER TABLE student_lrn_new RENAME TO student_lrn");

      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_student_lrn_lrn_className "
              + "ON student_lrn(lrn, className)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_student_lrn_teacher_id "
              + "ON student_lrn(teacher_id)");
    }
  };

  private static final Migration MIGRATION_18_19 = new Migration(18, 19) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // Stores the numeric assessment ID from the STARS backend so a
      // re-sync updates the existing local assessment/answer key instead
      // of duplicating it, and so a future upload could reuse the ID
      // without the teacher retyping it.
      db.execSQL("ALTER TABLE assessments ADD COLUMN server_assessment_id INTEGER");
    }
  };

  private static final Migration MIGRATION_19_20 = new Migration(19, 20) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // Plain ADD COLUMN is safe here -- no new FK/constraint involved,
      // unlike MIGRATION_17_18 which needed a table rebuild.
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN first_name TEXT");
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN middle_name TEXT");
      db.execSQL("ALTER TABLE student_lrn ADD COLUMN last_name TEXT");
    }
  };

  private static final Migration MIGRATION_20_21 = new Migration(20, 21) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // New local-only "quizzes" table. No sync columns by design.
      db.execSQL("CREATE TABLE IF NOT EXISTS quizzes ("
              + "id TEXT NOT NULL PRIMARY KEY, "
              + "class_id TEXT NOT NULL, "
              + "name TEXT, "
              + "term TEXT, "
              + "sheet_type TEXT, "
              + "exam_date TEXT, "
              + "exam_date_epoch INTEGER NOT NULL DEFAULT 0, "
              + "created_at INTEGER NOT NULL DEFAULT 0, "
              + "updated_at INTEGER NOT NULL DEFAULT 0, "
              + "answer_key_id TEXT, "
              + "FOREIGN KEY(class_id) REFERENCES classes(id) ON DELETE CASCADE)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_quizzes_class_id ON quizzes(class_id)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_quizzes_created_at ON quizzes(created_at)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_quizzes_exam_date_epoch ON quizzes(exam_date_epoch)");
    }
  };

  private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      // New local-only "quiz_scans" table — kept separate from "scans" so
      // quiz data never shares storage (or a foreign key) with assessments.
      db.execSQL("CREATE TABLE IF NOT EXISTS quiz_scans ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "quiz_id TEXT NOT NULL, "
              + "student_lrn TEXT, "
              + "detected_bubbles INTEGER NOT NULL DEFAULT 0, "
              + "score INTEGER, "
              + "num_items INTEGER NOT NULL DEFAULT 0, "
              + "image_path TEXT, "
              + "overlay_image_path TEXT, "
              + "key_reference_image_path TEXT, "
              + "timestamp INTEGER NOT NULL DEFAULT 0, "
              + "updated_at INTEGER NOT NULL DEFAULT 0, "
              + "FOREIGN KEY(quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE)");
      db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_scans_quiz_id ON quiz_scans(quiz_id)");

      // Mirrors "answers", but foreign-keyed to quiz_scans instead of scans.
      db.execSQL("CREATE TABLE IF NOT EXISTS quiz_scan_answers ("
              + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
              + "quiz_scan_id INTEGER NOT NULL, "
              + "item_number INTEGER NOT NULL, "
              + "answer TEXT NOT NULL DEFAULT '', "
              + "FOREIGN KEY(quiz_scan_id) REFERENCES quiz_scans(id) ON DELETE CASCADE)");
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quiz_scan_answers_quiz_scan_id_item_number "
              + "ON quiz_scan_answers(quiz_scan_id, item_number)");
    }
  };

  // ── Abstract DAO accessors (Room generates the implementations) ──────────
  public abstract TeacherDao teacherDao();

  public abstract ClassDao classDao();

  public abstract AssessmentDao assessmentDao();

  public abstract ScanDao scanDao();

  public abstract AnswerDao answerDao();

  public abstract AnswerKeyDao answerKeyDao();

  public abstract UserDao userDao();

  public abstract StudentLrnDao studentLrnDao();

  public abstract QuizDao quizDao();

  public abstract QuizScanDao quizScanDao();

  public abstract QuizScanAnswerDao quizScanAnswerDao();

  // ── Singleton ────────────────────────────────────────────────────────────
  public static AppDatabase getInstance(Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE = Room.databaseBuilder(
              context.getApplicationContext(),
              AppDatabase.class,
              DATABASE_NAME)
                  .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
              .build();
        }
      }
    }
    return INSTANCE;
  }
}
