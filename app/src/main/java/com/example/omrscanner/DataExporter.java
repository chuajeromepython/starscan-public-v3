package com.example.omrscanner;

import android.content.Context;
import android.util.Log;

import com.example.omrscanner.database.OMRRepository;
import com.example.omrscanner.database.entities.AssessmentEntity;
import com.example.omrscanner.database.entities.StudentLrnEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * DataExporter
 *
 * Collects assessment and student LRN data, builds JSON, and prints them.
 *
 * Usage (from any Activity):
 *   new DataExporter(context).exportAll();
 */
public class DataExporter {

    private static final String TAG = "DataExporter";

    private final OMRRepository repository;

    public DataExporter(Context context) {
        this.repository = new OMRRepository(context);
    }

    public void exportAll() {
        Log.d(TAG, "========================================");
        Log.d(TAG, "  DATA EXPORTER — building JSON");
        Log.d(TAG, "========================================");

        exportAssessments();
        exportStudentLrns();
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. ASSESSMENTS JSON
    // ─────────────────────────────────────────────────────────────────

    private void exportAssessments() {
        repository.getAllClasses(classes -> {
            if (classes == null || classes.isEmpty()) {
                Log.d(TAG, "[ASSESSMENTS JSON] No classes found.");
                return;
            }

            JSONArray assessmentsArray = new JSONArray();

            for (com.example.omrscanner.database.entities.ClassEntity cls : classes) {
                repository.getAssessmentsByClass(cls.id, assessments -> {
                    if (assessments == null || assessments.isEmpty()) return;

                    for (AssessmentEntity a : assessments) {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("name", a.name != null ? a.name : "");
                            obj.put("examDate", a.examDate != null ? a.examDate : "");
                            obj.put("sheetType", a.sheetType != null ? a.sheetType.replaceAll("\\D+", "") : "");
                            obj.put("answerKeyId", a.answerKeyId != null ? a.answerKeyId : "");
                            obj.put("assessmentType", a.assessmentType != null ? a.assessmentType : "");
                            obj.put("hotSync", a.hotSync);
                            assessmentsArray.put(obj);
                        } catch (Exception e) {
                            Log.e(TAG, "Error building assessment JSON", e);
                        }
                    }

                    Log.d(TAG, "----------------------------------------");
                    Log.d(TAG, "[ASSESSMENTS JSON]");
                    Log.d(TAG, assessmentsArray.toString());
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. STUDENT LRN JSON
    // ─────────────────────────────────────────────────────────────────

    private void exportStudentLrns() {
        repository.getAllStudentLrns(lrns -> {
            JSONArray lrnsArray = new JSONArray();

            if (lrns == null || lrns.isEmpty()) {
                Log.d(TAG, "[STUDENT LRN JSON] No records found.");
                return;
            }

            for (StudentLrnEntity s : lrns) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("lrn", s.lrn != null ? s.lrn : "");
                    obj.put("className", s.className != null ? s.className : "");
                    obj.put("hotSync", s.hotSync);
                    lrnsArray.put(obj);
                } catch (Exception e) {
                    Log.e(TAG, "Error building LRN JSON", e);
                }
            }

            Log.d(TAG, "----------------------------------------");
            Log.d(TAG, "[STUDENT LRN JSON]");
            Log.d(TAG, lrnsArray.toString());
        });
    }
}