package com.example.omrscanner.database;

import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.ScanEntity;
import com.example.omrscanner.models.ActivityFolder;
import com.example.omrscanner.models.ClassFolder;
import com.example.omrscanner.models.ScanEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DataMapper — pure static helpers that convert between the in-memory model
 * objects ({@link ClassFolder}, {@link ActivityFolder}, {@link ScanEntry}) and
 * the Room database entities ({@link ClassEntity}, {@link AssessmentEntity},
 * {@link ScanEntity}).
 * <p>
 * Keeps all mapping logic in one place, away from the Activity and DAOs.
 */
public final class DataMapper {

    private DataMapper() { /* utility class — no instances */ }

    // ═════════════════════════════════════════════════════════════════════════
    // CLASS  ↔  ClassFolder
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Convert a {@link ClassFolder} model to a {@link ClassEntity} for Room.
     *
     * @param cls       the in-memory class folder.
     * @param teacherId the Room primary key of the owning teacher row.
     */
    public static ClassEntity toClassEntity(ClassFolder cls, int teacherId) {
        ClassEntity entity = new ClassEntity(
                cls.getId(),
                teacherId,
                cls.getGrade(),
                cls.getSection(),
                cls.getSchoolYear()
        );
        entity.createdAt = cls.getCreatedAt() > 0
                ? cls.getCreatedAt()
                : System.currentTimeMillis();
        entity.updatedAt = System.currentTimeMillis();
        return entity;
    }

    /**
     * Convert a {@link ClassEntity} from Room back to a {@link ClassFolder}
     * model.  Activities are <em>not</em> populated here; call
     * {@code ActivityFolder} mapping separately and attach them.
     */
    public static ClassFolder toClassFolder(ClassEntity entity, String teacherName) {
        ClassFolder cls = new ClassFolder();
        cls.setId(entity.id);
        String mappedTeacher = (teacherName != null && !teacherName.trim().isEmpty())
                ? teacherName
                : "Unknown Teacher";
        cls.setTeacher(mappedTeacher);
        cls.setGrade(entity.grade);
        cls.setSection(entity.section);
        cls.setSchoolYear(entity.schoolYear);
        cls.setClassroomId(entity.classroomId);
        cls.setCreatedAt(entity.createdAt);
        return cls;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ASSESSMENT  ↔  ActivityFolder
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Convert an {@link ActivityFolder} model to an {@link AssessmentEntity}.
     *
     * @param act     the in-memory activity folder.
     * @param classId the string UUID of the parent class.
     */
    public static AssessmentEntity toAssessmentEntity(ActivityFolder act, String classId) {
        AssessmentEntity entity = new AssessmentEntity(
                act.getId(),
                classId,
                act.getName(),
                act.getSheetType(),
                act.getExamDate()
        );
        entity.createdAt = act.getCreatedAt() > 0
                ? act.getCreatedAt()
                : System.currentTimeMillis();
        entity.examDateEpoch = act.getExamDateEpoch() > 0
                ? act.getExamDateEpoch()
                : entity.createdAt;
        entity.updatedAt = System.currentTimeMillis();
        entity.assessmentType = act.getAssessmentType();
        return entity;
    }

    /**
     * Convert an {@link AssessmentEntity} from Room back to an
     * {@link ActivityFolder}.  Scans are <em>not</em> populated here.
     */
    public static ActivityFolder toActivityFolder(AssessmentEntity entity) {
        ActivityFolder act = new ActivityFolder();
        act.setId(entity.id);
        act.setName(entity.name);
        act.setSheetType(entity.sheetType);
        act.setExamDate(entity.examDate);
        act.setCreatedAt(entity.createdAt);
        act.setExamDateEpoch(entity.examDateEpoch > 0 ? entity.examDateEpoch : entity.createdAt);
        act.setAssessmentType(entity.assessmentType);
        return act;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SCAN  ↔  ScanEntry
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Convert a {@link ScanEntry} model to a {@link ScanEntity} for Room.
     *
     * @param scan         the in-memory scan entry.
     * @param assessmentId the string UUID of the parent assessment.
     */
    public static ScanEntity toScanEntity(ScanEntry scan, String assessmentId) {
        ScanEntity entity = new ScanEntity();
        entity.assessmentId = assessmentId;
        entity.studentLrn = scan.getLrn();
        entity.detectedBubbles = scan.getScore(); // always store detected count
        // Only write the graded score when the scan has actually been graded by an answer key;
        // otherwise keep it null so the UI knows no answer key evaluation has been done yet.
        entity.score = scan.isScored() ? scan.getScore() : null;
        entity.numItems = scan.getNumItems();
        entity.imagePath = scan.getImagePath();
        entity.overlayImagePath = scan.getOverlayImagePath();
        entity.timestamp = scan.getTimestamp() > 0
                ? scan.getTimestamp()
                : System.currentTimeMillis();
        entity.updatedAt = System.currentTimeMillis();
        return entity;
    }

    /**
     * Convert a {@link ScanEntity} from Room back to a {@link ScanEntry}.
     *
     * @param entity  the database row.
     * @param answers the answers map loaded separately from the {@code answers}
     *                table via {@link OMRRepository#getAnswersByScan}.
     *                Pass an empty map (or null) if answers aren't needed yet.
     */
    public static ScanEntry toScanEntry(ScanEntity entity,
            Map<Integer, String> answers) {
        ScanEntry scan = new ScanEntry();
        scan.setLrn(entity.studentLrn);
        boolean hasRealScore = (entity.score != null);
        scan.setScore(hasRealScore ? entity.score : entity.detectedBubbles);
        scan.setScored(hasRealScore);
        scan.setNumItems(entity.numItems);
        scan.setImagePath(entity.imagePath);
        scan.setOverlayImagePath(entity.overlayImagePath);
        scan.setTimestamp(entity.timestamp);
        scan.setAnswers(answers != null ? answers : new LinkedHashMap<>());
        return scan;
    }

    /**
     * Build an answer {@link Map} from a list of {@link com.example.omrscanner.database.entities.AnswerEntity}
     * rows (as returned by {@link OMRRepository#getAnswersByScan}).
     */
    public static Map<Integer, String> toAnswerMap(
            List<com.example.omrscanner.database.entities.AnswerEntity> answerEntities) {
        Map<Integer, String> map = new LinkedHashMap<>();
        if (answerEntities != null) {
            for (com.example.omrscanner.database.entities.AnswerEntity a : answerEntities) {
                map.put(a.itemNumber, a.answer);
            }
        }
        return map;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ANSWER KEY
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Create a new {@link AnswerKeyEntity} from raw fields.
     * Generates a 7-character UUID and sets timestamps automatically.
     *
     * @param name       display name, e.g. "Midterm Science Q1"
     * @param schoolYear e.g. "2025-2026"
     * @param sheetType  e.g. "ZPH50"
     * @param answers    comma-separated answers, e.g. "A,B,C,D,..."
     */
    public static AnswerKeyEntity toAnswerKeyEntity(String name, String schoolYear,
            String sheetType, String answers) {
        // 7-char short UUID — consistent with AssessmentEntity / ClassEntity style
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        return new AnswerKeyEntity(id, name, schoolYear, sheetType, answers);
    }

    /**
     * Returns a short, human-readable label for a UI chip or spinner entry.
     * Format: "Name · ZPH50 · 2025-2026"
     */
    public static String answerKeyDisplayName(AnswerKeyEntity key) {
        if (key == null) return "";
        StringBuilder sb = new StringBuilder();
        if (key.name != null && !key.name.isEmpty()) sb.append(key.name);
        if (key.sheetType != null && !key.sheetType.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(key.sheetType);
        }
        if (key.schoolYear != null && !key.schoolYear.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(key.schoolYear);
        }
        return sb.toString();
    }
}
