package com.example.omrscanner;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.omrscanner.dashboard.ClassExporter;
import com.example.omrscanner.database.AppDatabase;
import com.example.omrscanner.database.entities.AnswerEntity;
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.QuizEntity;
import com.example.omrscanner.database.entities.QuizScanAnswerEntity;
import com.example.omrscanner.database.entities.QuizScanEntity;
import com.example.omrscanner.database.entities.ScanEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * BackupManager
 *
 * Handles manual "back up my data" / "restore from backup" for the data that
 * has NO copy on the server and is otherwise lost on uninstall:
 *
 *   assessments, scans, answers, answer_keys  + scan images
 *   quizzes, quiz_scans, quiz_scan_answers (quizzes are local-only, so this
 *   backup is their ONLY copy anywhere — there is no server fallback for them)
 *
 * Classes, student_lrn, and the teacher/user profile are intentionally
 * excluded — they are fully re-derived from the system via QR scan + sync,
 * so backing them up would just be redundant and stale the moment the user
 * re-syncs.
 *
 * The one thing that makes this non-trivial: a class's LOCAL id
 * (ClassEntity.id) is NOT stable across uninstall/reinstall — every fresh
 * sync generates a brand-new random id (see OMRRepository.upsertClassFromSync).
 * The only stable identifier is the server's classroom_id. So assessments are
 * exported keyed by classroomId, and remapped back to whatever the CURRENT
 * local class id is at restore time.
 *
 * The backup file itself must be written outside app-private storage (via
 * Storage Access Framework Uris passed in from the Activity) — anything
 * written to filesDir/getExternalFilesDir() is deleted along with the app on
 * uninstall, same as the data it's trying to protect.
 */
public class BackupManager {

    private static final String TAG = "BackupManager";

    /** Bump if the exported JSON shape changes in a way old restores can't read. */
    private static final int BACKUP_FORMAT_VERSION = 1;

    private static final String ENTRY_MANIFEST = "backup.json";
    private static final String ENTRY_IMAGES_PREFIX = "images/";

    private final Context appContext;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BackupManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getInstance(appContext);
    }

    // ─────────────────────────────────────────────────────────────────
    // Public callback contracts
    // ─────────────────────────────────────────────────────────────────

    public interface ExportCallback {
        void onSuccess(int assessmentCount, int scanCount, int answerKeyCount,
                       int quizCount, int quizScanCount);
        void onError(Exception e);
    }

    public interface RestoreCallback {
        /**
         * skippedAssessments = assessments whose class isn't synced locally (yet).
         * skippedQuizzes = quizzes whose class isn't synced locally (yet) — same
         * reason as skippedAssessments, but quizzes have no server copy at all,
         * so a skipped quiz is gone for good, not just "re-syncable later."
         * failedExports = assessments whose Downloads/OMRScanner CSV/image
         * export could not be rebuilt after restore (e.g. storage permission
         * denied) — DB data for these is still restored fine, but the teacher
         * will need to resolve storage access before uploading them. Quizzes
         * never touch Downloads/OMRScanner, so they have no equivalent step.
         */
        void onSuccess(int restoredAssessments, int restoredScans, int restoredAnswerKeys,
                       int restoredQuizzes, int restoredQuizScans,
                       int skippedAssessments, int skippedQuizzes, int failedExports);
        void onError(Exception e);
    }

    // ─────────────────────────────────────────────────────────────────
    // EXPORT
    // ─────────────────────────────────────────────────────────────────

    /**
     * @param destination a Uri obtained from ACTION_CREATE_DOCUMENT — MUST be
     *                    outside app-private storage or the backup is pointless.
     */
    public void exportBackup(Uri destination, ExportCallback callback) {
        executor.execute(() -> {
            try {
                // Resolve the active teacher so this export only ever contains
                // their own data, even if other teachers have used this device.
                com.example.omrscanner.database.entities.UserEntity activeUser = db.userDao().getActiveUser();
                int teacherId = -1;
                if (activeUser != null && activeUser.userId != null) {
                    com.example.omrscanner.database.entities.TeacherEntity teacher =
                            db.teacherDao().getByUserId(activeUser.userId);
                    if (teacher != null) teacherId = teacher.id;
                }

                Map<String, Integer> classIdToClassroomId = new HashMap<>();
                for (ClassEntity c : db.classDao().getByTeacher(teacherId)) {
                    if (c.classroomId != null) {
                        classIdToClassroomId.put(c.id, c.classroomId);
                    }
                }

                JSONObject manifest = new JSONObject();
                manifest.put("formatVersion", BACKUP_FORMAT_VERSION);
                manifest.put("exportedAt", System.currentTimeMillis());

                Set<String> keptAssessmentIds = new HashSet<>();
                Set<String> keptAnswerKeyIds = new HashSet<>();
                JSONArray assessmentsJson = new JSONArray();
                int skippedNoClassroom = 0;
                for (AssessmentEntity a : db.assessmentDao().getAllSync()) {
                    Integer classroomId = classIdToClassroomId.get(a.classId);
                    if (classroomId == null) {
                        // Not one of the active teacher's classes (or not a
                        // synced/server class) — skip defensively rather than
                        // write an unrestoreable or cross-teacher row.
                        skippedNoClassroom++;
                        continue;
                    }
                    assessmentsJson.put(assessmentToJson(a, classroomId));
                    keptAssessmentIds.add(a.id);
                    if (a.answerKeyId != null) keptAnswerKeyIds.add(a.answerKeyId);
                }
                if (skippedNoClassroom > 0) {
                    Log.w(TAG, "Skipped " + skippedNoClassroom + " assessment(s) with no synced classroom_id");
                }
                manifest.put("assessments", assessmentsJson);

                Set<Integer> keptScanIds = new HashSet<>();
                JSONArray scansJson = new JSONArray();
                for (ScanEntity s : db.scanDao().getAllSync()) {
                    if (!keptAssessmentIds.contains(s.assessmentId)) continue;
                    scansJson.put(scanToJson(s));
                    keptScanIds.add(s.id);
                }
                manifest.put("scans", scansJson);

                JSONArray answersJson = new JSONArray();
                for (AnswerEntity ans : db.answerDao().getAllSync()) {
                    if (!keptScanIds.contains(ans.scanId)) continue;
                    answersJson.put(answerToJson(ans));
                }
                manifest.put("answers", answersJson);

                // ── Quizzes are local-only (no server copy), so this backup is
                // their only safety net. Keyed by classroomId exactly like
                // assessments, for the same reason: local class ids aren't
                // stable across uninstall/reinstall.
                Set<String> keptQuizIds = new HashSet<>();
                JSONArray quizzesJson = new JSONArray();
                int skippedQuizzesNoClassroom = 0;
                for (QuizEntity q : db.quizDao().getAllSync()) {
                    Integer classroomId = classIdToClassroomId.get(q.classId);
                    if (classroomId == null) {
                        skippedQuizzesNoClassroom++;
                        continue;
                    }
                    quizzesJson.put(quizToJson(q, classroomId));
                    keptQuizIds.add(q.id);
                    if (q.answerKeyId != null) keptAnswerKeyIds.add(q.answerKeyId);
                }
                if (skippedQuizzesNoClassroom > 0) {
                    Log.w(TAG, "Skipped " + skippedQuizzesNoClassroom + " quiz(zes) with no synced classroom_id");
                }
                manifest.put("quizzes", quizzesJson);

                Set<Integer> keptQuizScanIds = new HashSet<>();
                JSONArray quizScansJson = new JSONArray();
                for (QuizScanEntity qs : db.quizScanDao().getAllSync()) {
                    if (!keptQuizIds.contains(qs.quizId)) continue;
                    quizScansJson.put(quizScanToJson(qs));
                    keptQuizScanIds.add(qs.id);
                }
                manifest.put("quizScans", quizScansJson);

                JSONArray quizScanAnswersJson = new JSONArray();
                for (QuizScanAnswerEntity ans : db.quizScanAnswerDao().getAllSync()) {
                    if (!keptQuizScanIds.contains(ans.quizScanId)) continue;
                    quizScanAnswersJson.put(quizScanAnswerToJson(ans));
                }
                manifest.put("quizScanAnswers", quizScanAnswersJson);

                JSONArray keysJson = new JSONArray();
                for (AnswerKeyEntity k : db.answerKeyDao().getAll()) {
                    // answer_keys is a shared/global bank (no teacher_id column),
                    // so scope the export to keys this teacher's kept assessments
                    // and quizzes actually reference, rather than dumping the
                    // whole bank.
                    if (!keptAnswerKeyIds.contains(k.id)) continue;
                    keysJson.put(answerKeyToJson(k));
                }
                manifest.put("answerKeys", keysJson);

                writeZip(destination, manifest);

                callback.onSuccess(assessmentsJson.length(), scansJson.length(), keysJson.length(),
                        quizzesJson.length(), quizScansJson.length());
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                callback.onError(e);
            }
        });
    }

    private void writeZip(Uri destination, JSONObject manifest) throws IOException {
        try (OutputStream rawOut = appContext.getContentResolver().openOutputStream(destination)) {
            if (rawOut == null) throw new IOException("Could not open destination for writing");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
                zos.putNextEntry(new ZipEntry(ENTRY_MANIFEST));
                zos.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                File imagesDir = new File(appContext.getFilesDir(), "images");
                File[] images = imagesDir.exists() ? imagesDir.listFiles() : null;
                if (images != null) {
                    byte[] buf = new byte[8192];
                    for (File img : images) {
                        if (!img.isFile()) continue;
                        zos.putNextEntry(new ZipEntry(ENTRY_IMAGES_PREFIX + img.getName()));
                        try (InputStream fis = new FileInputStream(img)) {
                            int read;
                            while ((read = fis.read(buf)) != -1) {
                                zos.write(buf, 0, read);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // RESTORE
    // ─────────────────────────────────────────────────────────────────

    /**
     * @param source a Uri obtained from ACTION_OPEN_DOCUMENT pointing at a
     *               previously exported backup zip.
     */
    public void restoreBackup(Uri source, RestoreCallback callback) {
        executor.execute(() -> {
            try {
                // Scope restore to the CURRENTLY signed-in teacher's classes only —
                // never trust an unscoped "all classes in the DB" query here. On a
                // shared device, another teacher's classes/assessments can still be
                // sitting in local storage from a previous session, and matching a
                // restored assessment's classroomId against those would attach it
                // to the wrong teacher's class. Mirrors DashboardActivity#ensureTeacherId,
                // which re-resolves against the active user for the same reason.
                com.example.omrscanner.database.entities.UserEntity activeUser = db.userDao().getActiveUser();
                if (activeUser == null || activeUser.userId == null) {
                    callback.onError(new IllegalStateException(
                            "Please sign in before restoring a backup."));
                    return;
                }
                com.example.omrscanner.database.entities.TeacherEntity activeTeacher =
                        db.teacherDao().getByUserId(activeUser.userId);
                if (activeTeacher == null) {
                    callback.onError(new IllegalStateException(
                            "No classes found yet. Scan your QR code and sync your classes first, " +
                                    "then restore the backup."));
                    return;
                }
                List<ClassEntity> localClasses = db.classDao().getByTeacher(activeTeacher.id);
                if (localClasses.isEmpty()) {
                    callback.onError(new IllegalStateException(
                            "No classes found yet. Scan your QR code and sync your classes first, " +
                                    "then restore the backup."));
                    return;
                }
                Map<Integer, String> classroomIdToLocalClassId = new HashMap<>();
                for (ClassEntity c : localClasses) {
                    if (c.classroomId != null) {
                        classroomIdToLocalClassId.put(c.classroomId, c.id);
                    }
                }

                JSONObject manifest = readZip(source);

                int restoredAssessments = 0;
                int skippedAssessments = 0;
                Set<String> restoredAssessmentIds = new HashSet<>();
                // assessmentId -> classId, so we can rebuild the Downloads/OMRScanner
                // export for every assessment actually restored (see below).
                Map<String, String> restoredAssessmentClassIds = new HashMap<>();

                JSONArray assessmentsJson = manifest.optJSONArray("assessments");
                if (assessmentsJson != null) {
                    for (int i = 0; i < assessmentsJson.length(); i++) {
                        JSONObject o = assessmentsJson.getJSONObject(i);
                        int classroomId = o.optInt("classroomId", -1);
                        String localClassId = classroomIdToLocalClassId.get(classroomId);
                        if (localClassId == null) {
                            // Teacher no longer has this class synced locally —
                            // can't safely restore its assessments without a
                            // valid class_id foreign key.
                            skippedAssessments++;
                            continue;
                        }
                        AssessmentEntity a = assessmentFromJson(o, localClassId);
                        db.assessmentDao().insert(a); // REPLACE on conflict
                        restoredAssessmentIds.add(a.id);
                        restoredAssessmentClassIds.put(a.id, localClassId);
                        restoredAssessments++;
                    }
                }

                int restoredScans = 0;
                Set<Integer> restoredScanIds = new HashSet<>();
                JSONArray scansJson = manifest.optJSONArray("scans");
                if (scansJson != null) {
                    for (int i = 0; i < scansJson.length(); i++) {
                        JSONObject o = scansJson.getJSONObject(i);
                        String assessmentId = o.optString("assessmentId", null);
                        if (assessmentId == null || !restoredAssessmentIds.contains(assessmentId)) continue;
                        ScanEntity scan = scanFromJson(o);
                        db.scanDao().insert(scan);
                        restoredScanIds.add(scan.id);
                        restoredScans++;
                    }
                }

                // Only keep answers whose parent scan actually got restored above —
                // answers.scan_id has a FOREIGN KEY (ON DELETE CASCADE) to scans.id,
                // so inserting an answer for a scan that was skipped (e.g. its
                // assessment's class isn't synced locally) throws a foreign key
                // constraint failure and aborts the WHOLE restore, even though
                // assessments/scans/keys were already committed successfully.
                JSONArray answersJson = manifest.optJSONArray("answers");
                if (answersJson != null) {
                    List<AnswerEntity> batch = new ArrayList<>();
                    for (int i = 0; i < answersJson.length(); i++) {
                        JSONObject o = answersJson.getJSONObject(i);
                        int scanId = o.optInt("scanId", -1);
                        if (!restoredScanIds.contains(scanId)) continue;
                        batch.add(answerFromJson(o));
                    }
                    if (!batch.isEmpty()) db.answerDao().insertAll(batch);
                }

                int restoredAnswerKeys = 0;
                JSONArray keysJson = manifest.optJSONArray("answerKeys");
                if (keysJson != null) {
                    for (int i = 0; i < keysJson.length(); i++) {
                        db.answerKeyDao().insert(answerKeyFromJson(keysJson.getJSONObject(i)));
                        restoredAnswerKeys++;
                    }
                }

                // ── Quizzes (local-only — this backup is their only copy) ──
                int restoredQuizzes = 0;
                int skippedQuizzes = 0;
                Set<String> restoredQuizIds = new HashSet<>();

                JSONArray quizzesJson = manifest.optJSONArray("quizzes");
                if (quizzesJson != null) {
                    for (int i = 0; i < quizzesJson.length(); i++) {
                        JSONObject o = quizzesJson.getJSONObject(i);
                        int classroomId = o.optInt("classroomId", -1);
                        String localClassId = classroomIdToLocalClassId.get(classroomId);
                        if (localClassId == null) {
                            skippedQuizzes++;
                            continue;
                        }
                        QuizEntity q = quizFromJson(o, localClassId);
                        db.quizDao().insert(q); // REPLACE on conflict
                        restoredQuizIds.add(q.id);
                        restoredQuizzes++;
                    }
                }

                int restoredQuizScans = 0;
                Set<Integer> restoredQuizScanIds = new HashSet<>();
                JSONArray quizScansJson = manifest.optJSONArray("quizScans");
                if (quizScansJson != null) {
                    for (int i = 0; i < quizScansJson.length(); i++) {
                        JSONObject o = quizScansJson.getJSONObject(i);
                        String quizId = o.optString("quizId", null);
                        if (quizId == null || !restoredQuizIds.contains(quizId)) continue;
                        QuizScanEntity scan = quizScanFromJson(o);
                        db.quizScanDao().insert(scan);
                        restoredQuizScanIds.add(scan.id);
                        restoredQuizScans++;
                    }
                }

                // Same reasoning as the "answers" block above — only keep answers
                // whose parent quiz scan actually got restored, to avoid a
                // foreign key constraint failure aborting the whole restore.
                JSONArray quizScanAnswersJson = manifest.optJSONArray("quizScanAnswers");
                if (quizScanAnswersJson != null) {
                    List<QuizScanAnswerEntity> batch = new ArrayList<>();
                    for (int i = 0; i < quizScanAnswersJson.length(); i++) {
                        JSONObject o = quizScanAnswersJson.getJSONObject(i);
                        int quizScanId = o.optInt("quizScanId", -1);
                        if (!restoredQuizScanIds.contains(quizScanId)) continue;
                        batch.add(quizScanAnswerFromJson(o));
                    }
                    if (!batch.isEmpty()) db.quizScanAnswerDao().insertAll(batch);
                }

                // ── Rebuild Downloads/OMRScanner from what we just restored ──
                //
                // Restoring only touches the Room DB + the private overlay
                // images — it never writes anything under the public
                // Downloads/OMRScanner folder, which is what uploadAssessment()
                // actually reads. Without this, upload after a restore either
                // reads a stale pre-restore CSV (if the folder survived the
                // data clear) or fails outright if the folder is missing.
                // Re-running the same export used for a normal scan save
                // fixes both: it re-creates the folder tree via mkdirs() if
                // missing, and overwrites stale files if present.
                int failedExports = 0;
                for (Map.Entry<String, String> entry : restoredAssessmentClassIds.entrySet()) {
                    String assessmentId = entry.getKey();
                    String classId = entry.getValue();
                    try {
                        ClassExporter.exportAssessmentSync(appContext, classId, assessmentId);
                    } catch (Exception exportEx) {
                        failedExports++;
                        Log.e(TAG, "Post-restore export failed for assessment " + assessmentId, exportEx);
                    }
                }

                callback.onSuccess(restoredAssessments, restoredScans, restoredAnswerKeys,
                        restoredQuizzes, restoredQuizScans, skippedAssessments, skippedQuizzes, failedExports);
            } catch (Exception e) {
                Log.e(TAG, "Restore failed", e);
                callback.onError(e);
            }
        });
    }

    private JSONObject readZip(Uri source) throws IOException, JSONException {
        File imagesDir = new File(appContext.getFilesDir(), "images");
        //noinspection ResultOfMethodCallIgnored
        imagesDir.mkdirs();

        JSONObject manifest = null;
        try (InputStream rawIn = appContext.getContentResolver().openInputStream(source)) {
            if (rawIn == null) throw new IOException("Could not open backup file for reading");
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
                ZipEntry entry;
                byte[] buf = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (ENTRY_MANIFEST.equals(name)) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        int read;
                        while ((read = zis.read(buf)) != -1) bos.write(buf, 0, read);
                        manifest = new JSONObject(bos.toString("UTF-8"));
                    } else if (!entry.isDirectory() && name.startsWith(ENTRY_IMAGES_PREFIX)) {
                        String fileName = name.substring(ENTRY_IMAGES_PREFIX.length());
                        if (fileName.isEmpty() || fileName.contains("..")) continue; // zip-slip guard
                        File outFile = new File(imagesDir, fileName);
                        try (OutputStream fos = new FileOutputStream(outFile)) {
                            int read;
                            while ((read = zis.read(buf)) != -1) fos.write(buf, 0, read);
                        }
                    }
                    zis.closeEntry();
                }
            }
        }
        if (manifest == null) {
            throw new IOException("This file doesn't look like a valid backup (missing " + ENTRY_MANIFEST + ")");
        }
        return manifest;
    }

    // ─────────────────────────────────────────────────────────────────
    // JSON <-> Entity mapping
    // ─────────────────────────────────────────────────────────────────

    private JSONObject assessmentToJson(AssessmentEntity a, int classroomId) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", a.id);
        o.put("classroomId", classroomId);
        o.put("name", a.name);
        o.put("sheetType", a.sheetType);
        o.put("examDate", a.examDate);
        o.put("examDateEpoch", a.examDateEpoch);
        o.put("createdAt", a.createdAt);
        o.put("answerKeyId", a.answerKeyId);
        o.put("assessmentType", a.assessmentType);
        o.put("hotSync", a.hotSync);
        // Preserve the server's assessment id so a post-restore "Sync Assessments"
        // recognizes this as the same assessment (matched by classId + this field)
        // and updates it in place instead of inserting a duplicate card.
        if (a.serverAssessmentId != null) o.put("serverAssessmentId", a.serverAssessmentId);
        return o;
    }

    private AssessmentEntity assessmentFromJson(JSONObject o, String localClassId) throws JSONException {
        AssessmentEntity a = new AssessmentEntity();
        a.id = o.getString("id");
        a.classId = localClassId;
        a.name = o.optString("name", null);
        a.sheetType = o.optString("sheetType", null);
        a.examDate = o.optString("examDate", null);
        a.examDateEpoch = o.optLong("examDateEpoch", 0);
        a.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        a.answerKeyId = o.isNull("answerKeyId") ? null : o.optString("answerKeyId", null);
        a.assessmentType = o.isNull("assessmentType") ? null : o.optString("assessmentType", null);
        a.hotSync = o.optInt("hotSync", 0);
        a.serverAssessmentId = o.has("serverAssessmentId") && !o.isNull("serverAssessmentId")
                ? o.optInt("serverAssessmentId") : null;
        return a;
    }

    private JSONObject scanToJson(ScanEntity s) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", s.id); // preserved as-is; restore writes it back explicitly (not autoGenerated)
        o.put("assessmentId", s.assessmentId);
        o.put("studentLrn", s.studentLrn);
        o.put("detectedBubbles", s.detectedBubbles);
        o.put("score", s.score == null ? JSONObject.NULL : s.score);
        o.put("numItems", s.numItems);
        o.put("imagePath", s.imagePath);
        o.put("overlayImagePath", s.overlayImagePath);
        o.put("timestamp", s.timestamp);
        o.put("updatedAt", s.updatedAt);
        return o;
    }

    private ScanEntity scanFromJson(JSONObject o) throws JSONException {
        ScanEntity s = new ScanEntity();
        s.id = o.optInt("id", 0); // explicit id -> Room/SQLite keeps it (not treated as "generate new")
        s.assessmentId = o.getString("assessmentId");
        s.studentLrn = o.optString("studentLrn", null);
        s.detectedBubbles = o.optInt("detectedBubbles", 0);
        s.score = o.isNull("score") ? null : o.optInt("score");
        s.numItems = o.optInt("numItems", 0);
        s.imagePath = o.optString("imagePath", null);
        s.overlayImagePath = o.optString("overlayImagePath", null);
        s.timestamp = o.optLong("timestamp", System.currentTimeMillis());
        s.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        return s;
    }

    private JSONObject answerToJson(AnswerEntity a) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", a.id);
        o.put("scanId", a.scanId);
        o.put("itemNumber", a.itemNumber);
        o.put("answer", a.answer);
        return o;
    }

    private AnswerEntity answerFromJson(JSONObject o) throws JSONException {
        AnswerEntity a = new AnswerEntity();
        a.id = o.optInt("id", 0);
        a.scanId = o.getInt("scanId");
        a.itemNumber = o.getInt("itemNumber");
        a.answer = o.optString("answer", "");
        return a;
    }

    private JSONObject quizToJson(QuizEntity q, int classroomId) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", q.id);
        o.put("classroomId", classroomId);
        o.put("name", q.name);
        o.put("term", q.term);
        o.put("sheetType", q.sheetType);
        o.put("examDate", q.examDate);
        o.put("examDateEpoch", q.examDateEpoch);
        o.put("createdAt", q.createdAt);
        o.put("updatedAt", q.updatedAt);
        o.put("answerKeyId", q.answerKeyId == null ? JSONObject.NULL : q.answerKeyId);
        return o;
    }

    private QuizEntity quizFromJson(JSONObject o, String localClassId) throws JSONException {
        QuizEntity q = new QuizEntity();
        q.id = o.getString("id");
        q.classId = localClassId;
        q.name = o.optString("name", null);
        q.term = o.optString("term", null);
        q.sheetType = o.optString("sheetType", "ZPH40");
        q.examDate = o.optString("examDate", null);
        q.examDateEpoch = o.optLong("examDateEpoch", 0);
        q.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        q.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        q.answerKeyId = o.isNull("answerKeyId") ? null : o.optString("answerKeyId", null);
        return q;
    }

    private JSONObject quizScanToJson(QuizScanEntity s) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", s.id); // preserved as-is; restore writes it back explicitly (not autoGenerated)
        o.put("quizId", s.quizId);
        o.put("studentLrn", s.studentLrn);
        o.put("detectedBubbles", s.detectedBubbles);
        o.put("score", s.score == null ? JSONObject.NULL : s.score);
        o.put("numItems", s.numItems);
        o.put("imagePath", s.imagePath);
        o.put("overlayImagePath", s.overlayImagePath);
        o.put("keyReferenceImagePath", s.keyReferenceImagePath == null ? JSONObject.NULL : s.keyReferenceImagePath);
        o.put("timestamp", s.timestamp);
        o.put("updatedAt", s.updatedAt);
        return o;
    }

    private QuizScanEntity quizScanFromJson(JSONObject o) throws JSONException {
        QuizScanEntity s = new QuizScanEntity();
        s.id = o.optInt("id", 0); // explicit id -> Room/SQLite keeps it (not treated as "generate new")
        s.quizId = o.getString("quizId");
        s.studentLrn = o.optString("studentLrn", null);
        s.detectedBubbles = o.optInt("detectedBubbles", 0);
        s.score = o.isNull("score") ? null : o.optInt("score");
        s.numItems = o.optInt("numItems", 0);
        s.imagePath = o.optString("imagePath", null);
        s.overlayImagePath = o.optString("overlayImagePath", null);
        s.keyReferenceImagePath = o.isNull("keyReferenceImagePath") ? null : o.optString("keyReferenceImagePath", null);
        s.timestamp = o.optLong("timestamp", System.currentTimeMillis());
        s.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        return s;
    }

    private JSONObject quizScanAnswerToJson(QuizScanAnswerEntity a) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", a.id);
        o.put("quizScanId", a.quizScanId);
        o.put("itemNumber", a.itemNumber);
        o.put("answer", a.answer);
        return o;
    }

    private QuizScanAnswerEntity quizScanAnswerFromJson(JSONObject o) throws JSONException {
        QuizScanAnswerEntity a = new QuizScanAnswerEntity();
        a.id = o.optInt("id", 0);
        a.quizScanId = o.getInt("quizScanId");
        a.itemNumber = o.getInt("itemNumber");
        a.answer = o.optString("answer", "");
        return a;
    }

    private JSONObject answerKeyToJson(AnswerKeyEntity k) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", k.id);
        o.put("name", k.name);
        o.put("schoolYear", k.schoolYear);
        o.put("sheetType", k.sheetType);
        o.put("answers", k.answers);
        o.put("createdAt", k.createdAt);
        o.put("updatedAt", k.updatedAt);
        return o;
    }

    private AnswerKeyEntity answerKeyFromJson(JSONObject o) throws JSONException {
        AnswerKeyEntity k = new AnswerKeyEntity();
        k.id = o.getString("id");
        k.name = o.optString("name", null);
        k.schoolYear = o.optString("schoolYear", null);
        k.sheetType = o.optString("sheetType", null);
        k.answers = o.optString("answers", null);
        k.createdAt = o.optLong("createdAt", 0);
        k.updatedAt = o.optLong("updatedAt", 0);
        return k;
    }
}