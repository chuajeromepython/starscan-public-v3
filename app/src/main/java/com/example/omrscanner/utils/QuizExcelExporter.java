package com.example.omrscanner.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.example.omrscanner.database.AppDatabase;
import com.example.omrscanner.database.entities.ClassEntity;
import com.example.omrscanner.database.entities.QuizEntity;
import com.example.omrscanner.database.entities.QuizScanEntity;
import com.example.omrscanner.database.entities.StudentLrnEntity;
import com.example.omrscanner.utils.SimpleXlsxWriter.Cell;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exports one quiz's results (student name + score) to an .xlsx file in
 * Downloads/OMRScanner/&lt;Grade-Section&gt;/Quizzes/.
 *
 * Quizzes are local-only, so everything is read from the on-device database.
 * Call from a background thread.
 */
public final class QuizExcelExporter {

    private static final String TAG = "QuizExcelExporter";
    private static final String NOT_IN_CLASS = "(not in class list)";

    private QuizExcelExporter() { /* static utility class */ }

    /** One output row, before sorting. */
    private static final class Row {
        String lrn;
        String name;
        String sortLast;
        String sortFirst;
        boolean matched;
        Integer score;   // null = not graded yet
        int items;
    }

    /**
     * @return the saved file, or null if the quiz has no scanned students yet
     *         (nothing worth exporting).
     */
    public static File export(Context context, String quizId) throws Exception {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());

        QuizEntity quiz = db.quizDao().getById(quizId);
        if (quiz == null) throw new IllegalStateException("Quiz not found");

        List<QuizScanEntity> scans = db.quizScanDao().getByQuiz(quizId);
        if (scans == null || scans.isEmpty()) return null;

        ClassEntity cls = db.classDao().getById(quiz.classId);
        String grade   = cls != null ? clean(cls.grade)   : "";
        String section = cls != null ? clean(cls.section) : "";

        // Roster for this class, keyed by LRN, so names are matched without a query per student.
        Map<String, StudentLrnEntity> roster = new HashMap<>();
        List<StudentLrnEntity> students = db.studentLrnDao().findByClass(quiz.classId);
        if (students != null) {
            for (StudentLrnEntity s : students) {
                if (s != null && s.lrn != null) roster.put(s.lrn.trim(), s);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (QuizScanEntity scan : scans) {
            Row row = new Row();
            row.lrn = clean(scan.studentLrn);
            row.score = scan.score;
            row.items = scan.numItems;
            StudentLrnEntity s = roster.get(row.lrn);
            if (s != null) {
                row.matched = true;
                row.name = formatName(s);
                row.sortLast = clean(s.lastName).toLowerCase(Locale.ROOT);
                row.sortFirst = clean(s.firstName).toLowerCase(Locale.ROOT);
            } else {
                row.matched = false;
                row.name = NOT_IN_CLASS;
                row.sortLast = row.lrn;
                row.sortFirst = "";
            }
            rows.add(row);
        }

        // Alphabetical by last name, then first name. Unmatched LRNs go last.
        Collections.sort(rows, (a, b) -> {
            if (a.matched != b.matched) return a.matched ? -1 : 1;
            int c = a.sortLast.compareTo(b.sortLast);
            return c != 0 ? c : a.sortFirst.compareTo(b.sortFirst);
        });

        String classLabel = (!grade.isEmpty() || !section.isEmpty())
                ? grade + " \u2014 " + section   // same "grade — section" format the app shows
                : "(unknown class)";
        String quizName = clean(quiz.name).isEmpty() ? "Quiz" : clean(quiz.name);

        SimpleXlsxWriter xlsx = new SimpleXlsxWriter(sanitizeSheetName(quizName));
        xlsx.addRow(Cell.text("Quiz:", SimpleXlsxWriter.STYLE_BOLD),
                Cell.text(quizName, SimpleXlsxWriter.STYLE_TITLE));
        xlsx.addRow(Cell.text("Class:", SimpleXlsxWriter.STYLE_BOLD),
                Cell.text(classLabel, SimpleXlsxWriter.STYLE_DEFAULT));
        xlsx.addRow(Cell.text("Term:", SimpleXlsxWriter.STYLE_BOLD),
                Cell.text(clean(quiz.term), SimpleXlsxWriter.STYLE_DEFAULT));
        xlsx.addRow(Cell.text("Date:", SimpleXlsxWriter.STYLE_BOLD),
                Cell.text(clean(quiz.examDate), SimpleXlsxWriter.STYLE_DEFAULT));
        xlsx.addRow(); // spacer
        xlsx.addRow(Cell.text("No.", SimpleXlsxWriter.STYLE_HEADER),
                Cell.text("LRN", SimpleXlsxWriter.STYLE_HEADER),
                Cell.text("Student Name", SimpleXlsxWriter.STYLE_HEADER),
                Cell.text("Score", SimpleXlsxWriter.STYLE_HEADER),
                Cell.text("Items", SimpleXlsxWriter.STYLE_HEADER));

        int n = 0;
        for (Row r : rows) {
            n++;
            xlsx.addRow(
                    Cell.number(n, SimpleXlsxWriter.STYLE_CELL_CENTER),
                    Cell.text(r.lrn, SimpleXlsxWriter.STYLE_CELL_CENTER),   // text, so a 12-digit LRN never turns into 1E+11
                    Cell.text(r.name, SimpleXlsxWriter.STYLE_CELL_LEFT),
                    r.score != null
                            ? Cell.number(r.score, SimpleXlsxWriter.STYLE_CELL_CENTER)
                            : Cell.blank(SimpleXlsxWriter.STYLE_CELL_CENTER), // ungraded stays blank, not 0
                    Cell.number(r.items, SimpleXlsxWriter.STYLE_CELL_CENTER));
        }
        xlsx.setColumnWidths(14, 16, 30, 10, 10);
        xlsx.freezeRows(6);

        File target = buildTargetFile(grade, section, quizName);
        writeFile(xlsx, target);
        Log.d(TAG, "Quiz exported: " + target.getAbsolutePath() + " (" + rows.size() + " rows)");
        return target;
    }

    // ── file handling ───────────────────────────────────────────

    /** Downloads/OMRScanner/<Grade-Section>/Quizzes/<Grade-Section>_<QuizName>.xlsx — same class folder the scan exports use. */
    private static File buildTargetFile(String grade, String section, String quizName) throws IOException {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String classFolder = sanitizeFilePart(
                (grade.isEmpty() ? "Class" : grade.replaceAll("\\s+", ""))
                        + "-" + (section.isEmpty() ? "Section" : section.replaceAll("\\s+", "")));
        File dir = new File(new File(new File(downloads, "OMRScanner"), classFolder), "Quizzes");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create " + dir.getAbsolutePath());
        }
        return new File(dir, classFolder + "_" + sanitizeFilePart(quizName) + ".xlsx");
    }

    private static void writeFile(SimpleXlsxWriter xlsx, File target) throws IOException {
        // Re-exporting overwrites the previous file (same convention as the scan exports).
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace the existing file: " + target.getAbsolutePath()
                    + " \u2014 try deleting it manually via a file manager, then export again.");
        }
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            xlsx.write(out);
        }
    }

    // ── small helpers ───────────────────────────────────────────

    /** "Last, First M." — skips whatever is missing. */
    private static String formatName(StudentLrnEntity s) {
        String last = clean(s.lastName);
        String first = clean(s.firstName);
        String mid = clean(s.middleName);
        StringBuilder sb = new StringBuilder(last);
        if (!first.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(first);
        }
        if (!mid.isEmpty()) sb.append(' ').append(mid.charAt(0)).append('.');
        return sb.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String sanitizeFilePart(String value) {
        String s = value != null ? value.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim() : "";
        s = s.replaceAll("\\s+", "_");
        return s.isEmpty() ? "item" : s;
    }

    /** Excel tab names: max 31 chars, none of \ / ? * [ ] : */
    private static String sanitizeSheetName(String name) {
        String s = name.replaceAll("[\\\\/?*\\[\\]:]", "-").trim();
        if (s.length() > 31) s = s.substring(0, 31).trim();
        return s.isEmpty() ? "Quiz" : s;
    }
}