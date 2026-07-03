package com.example.omrscanner;

import android.content.Context;
import android.util.Log;

import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.entities.AnswerKeyEntity;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.ScanEntity;

import java.util.List;

/**
 * DataInspector
 *
 * Gathers and logs the three data interests:
 *   1. Assessments data   — all assessments in the database
 *   2. LRN data           — all student LRNs from all scans
 *   3. Answer Key data    — all answer keys and their answers
 *
 * Usage (from any Activity):
 *   new DataInspector(context).printAll();
 */
public class DataInspector {

    private static final String TAG = "DataInspector";

    private final OMRRepository repository;

    public DataInspector(Context context) {
        this.repository = new OMRRepository(context);
    }

    /**
     * Fetches and logs all three data interests.
     * Runs on a background thread via OMRRepository — safe to call from the main thread.
     */
    public void printAll() {
        Log.d(TAG, "========================================");
        Log.d(TAG, "  DATA INSPECTOR — printing all data");
        Log.d(TAG, "========================================");

        printAssessments();
        printLRNs();
        //printAnswerKeys();
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. ASSESSMENTS DATA
    // ─────────────────────────────────────────────────────────────────

    private void printAssessments() {
        repository.getAllClasses(classes -> {
            if (classes == null || classes.isEmpty()) {
                Log.d(TAG, "[ASSESSMENTS] No classes found — no assessments to show.");
                return;
            }

            for (com.example.omrscanner.database.entities.ClassEntity cls : classes) {
                repository.getAssessmentsByClass(cls.id, assessments -> {
                    Log.d(TAG, "----------------------------------------");
                    Log.d(TAG, "[ASSESSMENTS] Class: " + cls.getDisplayName()
                            + " (ID: " + cls.id + ")");

                    if (assessments == null || assessments.isEmpty()) {
                        Log.d(TAG, "[ASSESSMENTS]   → No assessments found for this class.");
                        return;
                    }

                    for (AssessmentEntity a : assessments) {
                        //Log.d(TAG, "[ASSESSMENTS]   Assessment ID   : " + a.id);
                        Log.d(TAG, "[ASSESSMENTS]   Name            : " + a.name); // get Name
                        Log.d(TAG, "[ASSESSMENTS]   Exam Date       : " + a.examDate); // get Exam Date
                        Log.d(TAG, "[ASSESSMENTS]   Sheet Type      : " + a.sheetType.replaceAll("\\D+", "")); // get Sheet type
                        Log.d(TAG, "[ASSESSMENTS]   Answer Key ID   : "
                                + (a.answerKeyId != null ? a.answerKeyId : "none assigned")); // get Answer Key ID
                        Log.d(TAG, "[ASSESSMENTS]   Assessment Type      : " + a.assessmentType); // get Assessment Type
                        Log.d(TAG, "[ASSESSMENTS]   ---");
                    }
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. LRN DATA
    // ─────────────────────────────────────────────────────────────────

    private void printLRNs() {
        repository.getAllClasses(classes -> {
            if (classes == null || classes.isEmpty()) {
                Log.d(TAG, "[LRN DATA] No classes found — no LRNs to show.");
                return;
            }

            for (com.example.omrscanner.database.entities.ClassEntity cls : classes) {
                repository.getAssessmentsByClass(cls.id, assessments -> {
                    if (assessments == null) return;

                    for (AssessmentEntity a : assessments) {
                        repository.getScansByAssessment(a.id, scans -> {
                            Log.d(TAG, "----------------------------------------");
                            Log.d(TAG, "[LRN DATA] Assessment: " + cls.getDisplayName());

                            if (scans == null || scans.isEmpty()) {
                                Log.d(TAG, "[LRN DATA]   → No scans found for this assessment.");
                                return;
                            }

                            for (ScanEntity scan : scans) {
                                Log.d(TAG, "[LRN DATA]   LRN      : "
                                        + (scan.studentLrn != null ? scan.studentLrn : "not detected"));
                                //Log.d(TAG, "[LRN DATA]   Scan ID  : " + scan.id);
                                //Log.d(TAG, "[LRN DATA]   Score    : "
                                //        + (scan.score != null ? scan.score + "/" + scan.numItems : "ungraded"));
                                //Log.d(TAG, "[LRN DATA]   Scanned  : " + scan.getFormattedTimestamp());
                                //Log.d(TAG, "[LRN DATA]   ---");
                            }
                        });
                    }
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. ANSWER KEY DATA
    // ─────────────────────────────────────────────────────────────────

    private void printAnswerKeys() {
        repository.getAllAnswerKeys(keys -> {
            Log.d(TAG, "----------------------------------------");

            if (keys == null || keys.isEmpty()) {
                Log.d(TAG, "[ANSWER KEYS] No answer keys found.");
                return;
            }

            Log.d(TAG, "[ANSWER KEYS] Total keys: " + keys.size());

            for (AnswerKeyEntity key : keys) {
                Log.d(TAG, "[ANSWER KEYS]   Key ID      : " + key.id);
                Log.d(TAG, "[ANSWER KEYS]   Name        : " + key.name);
                Log.d(TAG, "[ANSWER KEYS]   School Year : " + key.schoolYear);
                Log.d(TAG, "[ANSWER KEYS]   Sheet Type  : " + key.sheetType
                        + " (" + key.getNumItems() + " items)");
                Log.d(TAG, "[ANSWER KEYS]   Answers     : " + key.answers);
                Log.d(TAG, "[ANSWER KEYS]   Created At  : " + key.createdAt);
                Log.d(TAG, "[ANSWER KEYS]   ---");
            }

            Log.d(TAG, "========================================");
            Log.d(TAG, "  DATA INSPECTOR — done");
            Log.d(TAG, "========================================");
        });
    }
}