package com.example.omrscanner.dashboard;

import android.util.Log;

import com.example.omrscanner.database.entities.EcdcCompetencyEntity;
import com.example.omrscanner.database.entities.EcdcDomainEntity;
import com.example.omrscanner.database.entities.EcdcResponseEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Builds the JSON that the ECDC "Upload" button will send to the STARS system,
 * for ONE class and ONE period (BOSY / MOSY / EOSY).
 *
 * Shape:
 * <pre>
 * {
 *   "classroom_id": 12,
 *   "user_id": 5,
 *   "period": "BOSY",
 *   "students": [
 *     {
 *       "lrn": "108357260002",
 *       "last_ticked_at": "2026-10-10",
 *       "responses": [
 *         { "domain_id": 1, "domain": "GROSS MOTOR DOMAIN",
 *           "competency_id": 1, "competency": "Nakaaakyat na ng mga silya...",
 *           "status": "PRESENT" }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * - There is no single top-level date: each student carries {@code last_ticked_at},
 *   the newest {@code updated_at} among that student's saved marks. Saving only
 *   stamps the marks that actually changed, so this is the last time the teacher
 *   ticked something for that student. It's sent as a plain "yyyy-MM-dd" date,
 *   fixed to GMT-8 regardless of the device's own time zone.
 * - Every mark carries the domain and the competency it was ticked under (ids AND
 *   the server's wording). If a competency is no longer in the local reference
 *   list (e.g. removed by a later ECDC sync) its id and status are still sent and
 *   the domain/competency fields are null.
 * - {@code status} is encoded as a single character: "1" = PRESENT,
 *   "-" = NOT_PRESENT, "*" = NOT_TESTED (see {@link #encodeStatus}).
 * - Only students with at least one saved mark appear. Competencies the teacher
 *   never marked are simply absent.
 * - The LRN is a string so leading zeros survive.
 */
public final class EcdcUploadPayloadBuilder {

    public static final String TAG = "OMR_ECDC_UPLOAD";

    // A single logcat entry is cut at roughly 4 KB, so long payloads are logged in pieces.
    private static final int LOG_CHUNK_CHARS = 3000;

    private EcdcUploadPayloadBuilder() { /* static utility class — no instances */ }

    /**
     * @param responses    every saved mark for the class + period (any order)
     * @param domains      the local ECDC domain list (for the domain names)
     * @param competencies the local ECDC competency list (for the competency wording + its domain)
     */
    public static JSONObject build(int classroomId, int userId, String period,
                                   List<EcdcResponseEntity> responses,
                                   List<EcdcDomainEntity> domains,
                                   List<EcdcCompetencyEntity> competencies) throws JSONException {
        // Lookups so each mark can carry its competency wording and its domain.
        Map<Integer, EcdcDomainEntity> domainById = new HashMap<>();
        if (domains != null) {
            for (EcdcDomainEntity d : domains) domainById.put(d.id, d);
        }
        Map<Integer, EcdcCompetencyEntity> competencyById = new HashMap<>();
        if (competencies != null) {
            for (EcdcCompetencyEntity c : competencies) competencyById.put(c.id, c);
        }

        // Group by student; LinkedHashMap keeps first-seen (LRN) order.
        Map<String, List<EcdcResponseEntity>> byStudent = new LinkedHashMap<>();
        for (EcdcResponseEntity r : responses) {
            List<EcdcResponseEntity> marks = byStudent.get(r.lrn);
            if (marks == null) {
                marks = new ArrayList<>();
                byStudent.put(r.lrn, marks);
            }
            marks.add(r);
        }

        JSONArray students = new JSONArray();
        for (Map.Entry<String, List<EcdcResponseEntity>> entry : byStudent.entrySet()) {
            List<EcdcResponseEntity> marks = entry.getValue();
            Collections.sort(marks, (a, b) -> Integer.compare(a.competencyId, b.competencyId));

            long lastTickedAt = 0;
            JSONArray marksJson = new JSONArray();
            for (EcdcResponseEntity r : marks) {
                if (r.updatedAt > lastTickedAt) lastTickedAt = r.updatedAt;

                EcdcCompetencyEntity competency = competencyById.get(r.competencyId);
                EcdcDomainEntity domain =
                        competency != null ? domainById.get(competency.domainId) : null;

                JSONObject mark = new JSONObject();
                mark.put("domain_id", domain != null ? (Object) domain.id : JSONObject.NULL);
                mark.put("domain", domain != null && domain.domain != null
                        ? (Object) domain.domain : JSONObject.NULL);
                mark.put("competency_id", r.competencyId);
                mark.put("competency", competency != null && competency.competency != null
                        ? (Object) competency.competency : JSONObject.NULL);
                mark.put("status", encodeStatus(r.status));
                marksJson.put(mark);
            }

            JSONObject student = new JSONObject();
            student.put("lrn", entry.getKey());
            student.put("last_ticked_at",
                    lastTickedAt > 0 ? formatTimestamp(lastTickedAt) : JSONObject.NULL);
            student.put("responses", marksJson);
            students.put(student);
        }

        JSONObject root = new JSONObject();
        root.put("classroom_id", classroomId);
        root.put("user_id", userId);
        root.put("period", period);
        root.put("students", students);
        return root;
    }

    /**
     * Maps a {@code EcdcResponseEntity.STATUS_*} value to the single-character
     * code the JSON payload uses: "1" = present, "-" = not present, "*" = not
     * tested. Falls back to "*" for anything unrecognized.
     */
    static String encodeStatus(String status) {
        if (EcdcResponseEntity.STATUS_PRESENT.equals(status)) {
            return "1";
        } else if (EcdcResponseEntity.STATUS_NOT_PRESENT.equals(status)) {
            return "-";
        } else {
            return "*";
        }
    }

    /** Date-only, fixed to GMT-8 regardless of the device's time zone, e.g. 2026-10-10. */
    static String formatTimestamp(long epochMillis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("GMT-8"));
        return f.format(new Date(epochMillis));
    }

    /**
     * Prints the exact JSON string to logcat under {@link #TAG}. Long payloads are
     * split into several lines; joining the lines between the BEGIN and END markers
     * (in order) gives back the original JSON.
     */
    public static void logPayload(String json) {
        int total = (json.length() + LOG_CHUNK_CHARS - 1) / LOG_CHUNK_CHARS;
        Log.d(TAG, "===== BEGIN ECDC UPLOAD JSON (" + json.length() + " chars, "
                + total + " log line" + (total == 1 ? "" : "s") + ") =====");
        for (int i = 0; i < json.length(); i += LOG_CHUNK_CHARS) {
            Log.d(TAG, json.substring(i, Math.min(json.length(), i + LOG_CHUNK_CHARS)));
        }
        Log.d(TAG, "===== END ECDC UPLOAD JSON =====");
    }
}