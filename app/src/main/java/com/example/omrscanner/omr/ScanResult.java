package com.example.omrscanner.omr;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the complete result of scanning one OMR sheet.
 *
 * <ul>
 *   <li>{@link #templateId} — which template was detected (ZPH30 / ZPH50 / ZPH60)</li>
 *   <li>{@link #lnr} — the 12-digit student ID extracted from the LNR block</li>
 *   <li>{@link #answers} — question number → detected choice(s), e.g. 1→"A", 5→"AC"</li>
 *   <li>{@link #overlayBitmap} — the warped image with coloured circles drawn on bubbles</li>
 * </ul>
 */
public class ScanResult {

    /** Template identifier: "ZPH30", "ZPH50", or "ZPH60". */
    public String templateId;

    /** Student ID string (12 digits) extracted from the LNR bubble block. */
    public String lnr;

    /**
     * 0-based column indices of LRN digit positions where no bubble was
     * clearly shaded (best fill ratio was below the threshold).
     * Empty if all 12 digits were detected.
     */
    public List<Integer> undetectedLnrPositions = new ArrayList<>();

    /**
     * Detected answers keyed by 1-based question number.
     * Value is one or more uppercase letters, e.g. "A", "BD", or "" if blank.
     * Insertion order matches question numbering (LinkedHashMap).
     */
    public Map<Integer, String> answers;

    /**
     * Copy of the warped bitmap with visual overlay:
     * green filled circles for FILLED bubbles,
     * blue outline circles for EMPTY bubbles.
     */
    public Bitmap overlayBitmap;

    public ScanResult() {
        this.answers = new LinkedHashMap<>();
    }

    /** @return true if any LRN digit position was not clearly shaded. */
    public boolean hasUndetectedLrnDigits() {
        return undetectedLnrPositions != null && !undetectedLnrPositions.isEmpty();
    }

    /**
     * Convenience: total number of questions that were scanned.
     */
    public int getQuestionCount() {
        return answers.size();
    }

    /**
     * Convenience: count questions with at least one detected answer.
     */
    public int getAnsweredCount() {
        int count = 0;
        for (String v : answers.values()) {
            if (v != null && !v.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("ScanResult{template='%s', lnr='%s', questions=%d, answered=%d}",
                templateId, lnr, getQuestionCount(), getAnsweredCount());
    }
}
