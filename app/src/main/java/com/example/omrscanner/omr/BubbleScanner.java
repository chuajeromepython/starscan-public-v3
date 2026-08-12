package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Map;

public class BubbleScanner {

    private static final String TAG = "BubbleScanner";

    // Lowered from 0.45 based on real pencil-scan logcat data: fully-shaded
    // No.2 pencil marks were landing at ratios as low as 0.208-0.401 (still
    // clearly the single marked bubble in their row, next to unmarked
    // columns reading ~0.000-0.048), while genuinely blank bubbles never
    // exceeded ~0.05 in the same scan. 0.18 sits well above that noise
    // ceiling with margin to spare, while catching the light-pressure marks
    // that 0.45 was missing.
    private static final double QUESTION_FILL_THRESHOLD = 0.18;
    private static final double DEFAULT_INNER_MASK_RADIUS_FACTOR = 0.60;
    private static final double MAX_OFFSET_SPACING_FRACTION = 0.45;

    // ── Overlay colours (BGR for OpenCV Mat) ────────────────────────────────
    private static final Scalar COLOUR_FILLED = new Scalar(0, 255, 0, 255);   // green
    private static final Scalar COLOUR_EMPTY  = new Scalar(0, 0, 255, 255);   // blue
    private static final Scalar COLOUR_RED    = new Scalar(255, 0, 0, 255);   // red (undetected/incorrect)
    private static final Scalar COLOUR_BLACK  = new Scalar(0, 0, 0, 255);     // black (no answer key)
    private static final Scalar COLOUR_MULTI  = new Scalar(255, 255, 0, 255); // yellow (multiple marks)
    private static final Scalar COLOUR_KEY    = new Scalar(156, 39, 176, 255); // purple (answer-key reveal, reference-only)

    private static final char[] CHOICE_LABELS = {'A', 'B', 'C', 'D'};

    // Kept as an explicit 0.45 (not tied to QUESTION_FILL_THRESHOLD) so that
    // lowering the question-bubble threshold for pencil detection doesn't
    // also loosen LNR digit detection, which is unrelated and already shows
    // several double-shaded columns at the current value.
    // winnerMargin > 0 switches classifyLnrColumn() from "count cells above
    // a flat threshold" to "compare the winner against the runner-up." The
    // flat-count rule (winnerMargin=0) breaks down under uneven lighting:
    // shadow/vignette regions push every blank cell's ratio up together,
    // so several noisy blanks can independently cross a static cutoff even
    // though only one cell is a real mark. Margin-based scoring only flags
    // a column as double-shaded when two cells are both strong AND nearly
    // tied (see doubleShadeStrongThreshold/doubleShadeTieMargin below);
    // a single dominant cell reads as detected regardless of how high the
    // noisy background sits. Genuinely ambiguous columns (no clear winner)
    // fall through to UNDETECTED and get flagged for manual correction
    // instead of being guessed.
    private static final LnrReadProfile DEFAULT_LNR_PROFILE = new LnrReadProfile(
            "default", 0.45, DEFAULT_INNER_MASK_RADIUS_FACTOR, 0.15, 0.60, 0.05
    );

    private static final LnrReadProfile ZPH60_LNR_PROFILE = new LnrReadProfile(
            "ZPH60", 0.52, 0.50, 0.08, 0.60, 0.04
    );

    public ScanResult scan(Bitmap warpedBitmap, OmrTemplate template, TemplateManager templateMgr) {
        return scan(warpedBitmap, template, templateMgr, null);
    }

    /**
     * Same as scan(Bitmap, OmrTemplate, TemplateManager), but colors each marked
     * answer bubble green (correct) or red (incorrect) against the supplied
     * answer key. Pass null when no answer key is attached yet — marked bubbles
     * then draw black instead.
     */
    public ScanResult scan(Bitmap warpedBitmap, OmrTemplate template, TemplateManager templateMgr,
                           String[] correctAnswers) {
        if (warpedBitmap == null || template == null) {
            Log.e(TAG, "scan: null bitmap or template");
            return null;
        }

        int bw = warpedBitmap.getWidth();
        int bh = warpedBitmap.getHeight();
        double scaleX = (double) bw / template.width;
        double scaleY = (double) bh / template.height;

        Mat colour = new Mat();
        Utils.bitmapToMat(warpedBitmap, colour);

        Mat gray = new Mat();
        Imgproc.cvtColor(colour, gray, Imgproc.COLOR_BGR2GRAY);

        Mat overlay = colour.clone();

        // Teacher-reference rendering: identical bubble positions, but always
        // reveals the answer-key column in purple. Built only when a key was
        // supplied. Display-only — never saved alongside overlayBitmap.
        Mat keyOverlay = (correctAnswers != null) ? colour.clone() : null;

        GridAligner aligner = new GridAligner();

        ScanResult result = new ScanResult();
        result.templateId = template.templateId;

        int questionCounter = 0;
        LnrReadProfile lnrProfile = resolveLnrReadProfile(template.templateId);

        for (OmrBlock block : template.blocks) {
            int scaledRadius = Math.max(1,
                    (int) Math.round(block.radius * ((scaleX + scaleY) * 0.5)));

            GridAligner.AlignmentResult alignment =
                    aligner.getBlockAlignment(gray, block, scaleX, scaleY);
            int offX = alignment.offsetX;
            int offY = alignment.offsetY;

            Log.d(TAG, String.format(
                    "Block '%s': offset=(%d,%d) score=%.3f",
                    block.label, offX, offY, alignment.score));

            if (block.label.equals("LNR")) {
                int[] safeOffset = sanitizeAlignmentOffset(block, scaleX, scaleY, scaledRadius, offX, offY);
                result.lnr = scanLnrBlock(block, scaleX, scaleY, scaledRadius,
                        gray, overlay, safeOffset[0], safeOffset[1], result, lnrProfile);
            } else {
                int[] safeOffset = sanitizeAlignmentOffset(block, scaleX, scaleY, scaledRadius, offX, offY);
                questionCounter = scanQuestionBlock(block, scaleX, scaleY,
                        scaledRadius, gray, overlay, keyOverlay,
                        result, questionCounter, safeOffset[0], safeOffset[1], correctAnswers);
            }
        }

        aligner.release();

        Bitmap overlayBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(overlay, overlayBitmap);
        result.overlayBitmap = overlayBitmap;

        if (keyOverlay != null) {
            Bitmap keyBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(keyOverlay, keyBitmap);
            result.keyReferenceBitmap = keyBitmap;
            keyOverlay.release();
        }

        colour.release();
        gray.release();
        overlay.release();

        Log.i(TAG, "Scan complete: " + result);
        return result;
    }

    private String scanLnrBlock(OmrBlock block, double scaleX, double scaleY,
                                int scaledRadius, Mat gray, Mat overlay,
                                int offX, int offY, ScanResult result) {
        return scanLnrBlock(block, scaleX, scaleY, scaledRadius, gray, overlay, offX, offY, result,
                DEFAULT_LNR_PROFILE);
    }
    // Color yellow multiple shaded LRN numbers
    private String scanLnrBlock(OmrBlock block, double scaleX, double scaleY,
                                int scaledRadius, Mat gray, Mat overlay,
                                int offX, int offY, ScanResult result,
                                LnrReadProfile profile) {
        double[][] ratios = new double[block.cols][block.rows];

        for (int row = 0; row < block.rows; row++) {
            for (int col = 0; col < block.cols; col++) {
                int cx = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;
                int cy = (int) Math.round((block.startY + row * block.dy) * scaleY) + offY;

                ratios[col][row] = measureFillRatio(gray, cx, cy, scaledRadius,
                        profile.innerMaskRadiusFactor);
            }
        }

        StringBuilder lnr = new StringBuilder();
        boolean[] doubleShadedCols = new boolean[block.cols];
        int[] bestRowPerCol = new int[block.cols];
        boolean[] hasGuessPerCol = new boolean[block.cols];
        for (int col = 0; col < block.cols; col++) {
            LnrColumnDecision decision = classifyLnrColumn(ratios[col], profile);
            bestRowPerCol[col] = decision.bestRow;
            hasGuessPerCol[col] = decision.kind != LnrColumnDecision.Kind.UNDETECTED;

            if (decision.kind == LnrColumnDecision.Kind.DOUBLE_SHADED) {
                lnr.append('X');
                doubleShadedCols[col] = true;
                result.doubleShadedLnrPositions.add(col);
                Log.w(TAG, String.format("LNR col %d: DOUBLE-SHADED (%d bubbles filled)",
                        col, decision.filledCount));
            } else if (decision.kind == LnrColumnDecision.Kind.DETECTED) {
                lnr.append(decision.bestRow);
            } else {
                lnr.append('_');
                result.undetectedLnrPositions.add(col);
                Log.w(TAG, String.format("LNR col %d: UNDETECTED (best ratio=%.3f < %.3f or ambiguous)",
                        col, decision.bestRatio, profile.fillThreshold));
            }

            Log.d(TAG, String.format("LNR col %d: digit=%d (ratio=%.3f, filled=%d)%s",
                    col, decision.bestRow, decision.bestRatio, decision.filledCount,
                    decision.kind == LnrColumnDecision.Kind.DOUBLE_SHADED ? " [DOUBLE-SHADED]" :
                            decision.kind == LnrColumnDecision.Kind.UNDETECTED ? " [UNDETECTED]" : ""));
        }

        for (int row = 0; row < block.rows; row++) {
            for (int col = 0; col < block.cols; col++) {
                int cx = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;
                int cy = (int) Math.round((block.startY + row * block.dy) * scaleY) + offY;
                boolean filled = doubleShadedCols[col]
                        ? ratios[col][row] >= profile.fillThreshold
                        : (hasGuessPerCol[col] && row == bestRowPerCol[col]);
                drawBubble(overlay, cx, cy, scaledRadius, filled, doubleShadedCols[col]);
            }
        }

        for (int col : result.undetectedLnrPositions) {
            int topCy = (int) Math.round((block.startY) * scaleY) + offY;
            int botCy = (int) Math.round((block.startY + (block.rows - 1) * block.dy) * scaleY) + offY;
            int cx    = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;

            int margin = scaledRadius + 4;
            int x1 = Math.max(0, cx - margin);
            int y1 = Math.max(0, topCy - margin);
            int x2 = Math.min(overlay.cols() - 1, cx + margin);
            int y2 = Math.min(overlay.rows() - 1, botCy + margin);

            drawRedBox(overlay, x1, y1, x2, y2);
        }

        for (int col : result.doubleShadedLnrPositions) {
            int topCy = (int) Math.round((block.startY) * scaleY) + offY;
            int botCy = (int) Math.round((block.startY + (block.rows - 1) * block.dy) * scaleY) + offY;
            int cx    = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;

            int margin = scaledRadius + 4;
            int x1 = Math.max(0, cx - margin);
            int y1 = Math.max(0, topCy - margin);
            int x2 = Math.min(overlay.cols() - 1, cx + margin);
            int y2 = Math.min(overlay.rows() - 1, botCy + margin);

            drawRedBox(overlay, x1, y1, x2, y2);
        }

        Log.i(TAG, "LNR extracted: " + lnr);
        return lnr.toString();
    }

    private int scanQuestionBlock(OmrBlock block, double scaleX, double scaleY,
                                  int scaledRadius, Mat gray, Mat overlay, Mat keyOverlay,
                                  ScanResult result, int questionCounter,
                                  int offX, int offY, String[] correctAnswers) {

        for (int row = 0; row < block.rows; row++) {
            questionCounter++;
            StringBuilder detected = new StringBuilder();

            // Pass 1: measure every column in this row before drawing anything,
            // so we know whether the row is multi-marked before we pick a colour.
            int[] cxs = new int[block.cols];
            int[] cys = new int[block.cols];
            boolean[] filledFlags = new boolean[block.cols];
            Boolean[] isCorrectFlags = new Boolean[block.cols];

            // Which column (if any) holds the answer key's correct choice
            // for this question. -1 when no key, or the key letter is
            // blank/"?"/out of range.
            int keyCol = -1;
            if (correctAnswers != null && questionCounter - 1 < correctAnswers.length) {
                String key = correctAnswers[questionCounter - 1].trim();
                if (!key.isEmpty() && !key.equals("?")) {
                    for (int c = 0; c < CHOICE_LABELS.length && c < block.cols; c++) {
                        if (String.valueOf(CHOICE_LABELS[c]).equals(key)) {
                            keyCol = c;
                            break;
                        }
                    }
                }
            }

            for (int col = 0; col < block.cols; col++) {
                int cx = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;
                int cy = (int) Math.round((block.startY + row * block.dy) * scaleY) + offY;
                cxs[col] = cx;
                cys[col] = cy;

                double ratio = measureFillRatio(gray, cx, cy, scaledRadius,
                        DEFAULT_INNER_MASK_RADIUS_FACTOR);
                boolean filled = ratio >= QUESTION_FILL_THRESHOLD;
                filledFlags[col] = filled;

                if (filled && col < CHOICE_LABELS.length) {
                    detected.append(CHOICE_LABELS[col]);
                }

                Boolean isCorrect = null;
                if (filled && keyCol >= 0) {
                    isCorrect = (col == keyCol);
                }
                isCorrectFlags[col] = isCorrect;
            }

            String answer = detected.toString();
            boolean isMultiMark = answer.length() > 1;

            // Pass 2: draw the real scan overlay (student marks only). This is
            // the bitmap that gets saved/exported, so it never gets the purple
            // answer-key reveal — that stays confined to keyOverlay below.
            for (int col = 0; col < block.cols; col++) {
                drawBubble(overlay, cxs[col], cys[col], scaledRadius,
                        filledFlags[col], isCorrectFlags[col], isMultiMark);
            }

            // Pass 3: draw the teacher-reference overlay (display/toggle only).
            // The answer-key column is always purple here, on every row:
            //   - correct, single mark       → half green / half purple
            //   - key answer in a multi-mark → half yellow / half purple
            //   - key answer left unmarked   → solid purple
            //   - any other marked bubble    → red (single) or yellow (multi)
            if (keyOverlay != null) {
                for (int col = 0; col < block.cols; col++) {
                    drawKeyReferenceBubble(keyOverlay, cxs[col], cys[col], scaledRadius,
                            filledFlags[col], isMultiMark, col == keyCol);
                }
            }

            result.answers.put(questionCounter, answer);

            if (isMultiMark) {
                result.multiLetterAnswerPositions.add(questionCounter);
                Log.w(TAG, String.format(
                        "Q%d: MULTI-LETTER answer detected (%s) — needs teacher correction before grading/upload",
                        questionCounter, answer));
            }
        }

        return questionCounter;
    }

    private double measureFillRatio(Mat gray, int cx, int cy, int radius) {
        return measureFillRatio(gray, cx, cy, radius, DEFAULT_INNER_MASK_RADIUS_FACTOR);
    }

    private double measureFillRatio(Mat gray, int cx, int cy, int radius, double innerMaskRadiusFactor) {
        int x1 = Math.max(0, cx - radius);
        int y1 = Math.max(0, cy - radius);
        int x2 = Math.min(gray.cols(), cx + radius);
        int y2 = Math.min(gray.rows(), cy + radius);

        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) return 0.0;

        Rect roi = new Rect(x1, y1, w, h);
        Mat patch = gray.submat(roi);

        // No.2 pencil shading is graphite hatching, not a solid fill like
        // pen/marker — it leaves tiny gaps of visible paper even when the
        // student has "fully" colored the bubble. Blurring merges the
        // hatching into a consistent gray region before binarizing.
        Mat blurredPatch = new Mat();
        Imgproc.GaussianBlur(patch, blurredPatch, new org.opencv.core.Size(3, 3), 0);

        Mat localBinary = new Mat();
        Imgproc.threshold(blurredPatch, localBinary, 0, 255,
                Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        int localCx = cx - x1;
        int localCy = cy - y1;
        int innerRadius = Math.max(1, (int) Math.round(radius * innerMaskRadiusFactor));

        Mat innerMask = Mat.zeros(h, w, CvType.CV_8UC1);
        Imgproc.circle(innerMask, new Point(localCx, localCy), innerRadius, new Scalar(255), -1);

        Mat maskedInk = new Mat();
        Core.bitwise_and(localBinary, innerMask, maskedInk);

        int foreground = Core.countNonZero(maskedInk);
        int total = Math.max(1, Core.countNonZero(innerMask));
        double otsuRatio = (double) foreground / total;

        // Second signal: how much darker is the bubble's centre than the
        // paper immediately around it — a ring between the inner
        // fill-detection circle and the patch edge, sampled from the SAME
        // small crop as the centre. This replaces an earlier version that
        // compared against a page-wide/region-wide average brightness,
        // which broke down under uneven lighting: a shadow gradient shifts
        // a whole area's brightness together, so blank bubbles under a
        // shadow could read as "darker than average" with zero real
        // contrast at bubble scale. Comparing against the immediate ring
        // sidesteps this — lighting varies over tens/hundreds of pixels,
        // not over an ~18px bubble radius, so ring and centre are always on
        // equal lighting footing, wherever the bubble sits on the page.
        Mat ringMask = Mat.zeros(h, w, CvType.CV_8UC1);
        Imgproc.circle(ringMask, new Point(localCx, localCy), radius, new Scalar(255), -1);
        Imgproc.circle(ringMask, new Point(localCx, localCy), innerRadius, new Scalar(0), -1);

        double innerMeanIntensity = Core.mean(blurredPatch, innerMask).val[0];
        double localBackgroundMean = (Core.countNonZero(ringMask) > 0)
                ? Core.mean(blurredPatch, ringMask).val[0]
                : innerMeanIntensity;
        double darknessRatio = Math.max(0.0,
                (localBackgroundMean - innerMeanIntensity) / Math.max(1.0, localBackgroundMean));

        maskedInk.release();
        innerMask.release();
        ringMask.release();
        localBinary.release();
        blurredPatch.release();
        patch.release();

        double finalRatio = Math.max(otsuRatio, darknessRatio);
        Log.d(TAG, String.format(
                "Bubble (%d,%d): otsuRatio=%.3f darknessRatio=%.3f (localBg=%.1f, innerMean=%.1f) -> %.3f",
                cx, cy, otsuRatio, darknessRatio, localBackgroundMean, innerMeanIntensity, finalRatio));

        return finalRatio;
    }

    static LnrReadProfile resolveLnrReadProfile(String templateId) {
        if ("ZPH60".equalsIgnoreCase(templateId)) {
            return ZPH60_LNR_PROFILE;
        }
        return DEFAULT_LNR_PROFILE;
    }

    static LnrColumnDecision classifyLnrColumn(double[] ratios, LnrReadProfile profile) {
        int filledCount = 0;
        int bestRow = 0;
        int secondBestRow = -1;
        double bestRatio = ratios[0];
        double secondBestRatio = Double.NEGATIVE_INFINITY;

        if (ratios[0] >= profile.fillThreshold) {
            filledCount++;
        }

        for (int row = 1; row < ratios.length; row++) {
            double ratio = ratios[row];
            if (ratio >= profile.fillThreshold) {
                filledCount++;
            }
            if (ratio > bestRatio) {
                secondBestRatio = bestRatio;
                secondBestRow = bestRow;
                bestRatio = ratio;
                bestRow = row;
            } else if (ratio > secondBestRatio) {
                secondBestRatio = ratio;
                secondBestRow = row;
            }
        }

        if (bestRatio < profile.fillThreshold) {
            return new LnrColumnDecision(LnrColumnDecision.Kind.UNDETECTED,
                    bestRow, secondBestRow, bestRatio, secondBestRatio, filledCount);
        }

        if (profile.winnerMargin <= 0.0) {
            if (filledCount >= 2) {
                return new LnrColumnDecision(LnrColumnDecision.Kind.DOUBLE_SHADED,
                        bestRow, secondBestRow, bestRatio, secondBestRatio, filledCount);
            }
            return new LnrColumnDecision(LnrColumnDecision.Kind.DETECTED,
                    bestRow, secondBestRow, bestRatio, secondBestRatio, filledCount);
        }

        double margin = bestRatio - secondBestRatio;
        boolean closeStrongTie = secondBestRatio >= profile.doubleShadeStrongThreshold
                && margin <= profile.doubleShadeTieMargin;
        if (closeStrongTie) {
            return new LnrColumnDecision(LnrColumnDecision.Kind.DOUBLE_SHADED,
                    bestRow, secondBestRow, bestRatio, secondBestRatio, filledCount);
        }
        if (margin < profile.winnerMargin) {
            return new LnrColumnDecision(LnrColumnDecision.Kind.UNDETECTED,
                    bestRow, secondBestRow, bestRatio, secondBestRatio, filledCount);
        }

        return new LnrColumnDecision(LnrColumnDecision.Kind.DETECTED,
                bestRow, secondBestRow, bestRatio, secondBestRatio, filledCount);
    }

    private int[] sanitizeAlignmentOffset(OmrBlock block,
                                          double scaleX,
                                          double scaleY,
                                          int scaledRadius,
                                          int offX,
                                          int offY) {
        int spacingX = block.cols > 1
                ? Math.max(1, (int) Math.round(Math.abs(block.dx * scaleX)))
                : scaledRadius * 2;
        int spacingY = block.rows > 1
                ? Math.max(1, (int) Math.round(Math.abs(block.dy * scaleY)))
                : scaledRadius * 2;

        int maxOffsetX = Math.max(scaledRadius, (int) Math.round(spacingX * MAX_OFFSET_SPACING_FRACTION));
        int maxOffsetY = Math.max(scaledRadius, (int) Math.round(spacingY * MAX_OFFSET_SPACING_FRACTION));

        if (Math.abs(offX) > maxOffsetX || Math.abs(offY) > maxOffsetY) {
            Log.w(TAG, String.format(
                    "Offset rejected for block '%s': (%d,%d) exceeds max (%d,%d)",
                    block.label, offX, offY, maxOffsetX, maxOffsetY));
            return new int[]{0, 0};
        }
        return new int[]{offX, offY};
    }

    private void drawBubble(Mat overlay, int cx, int cy, int radius, boolean filled) {
        org.opencv.core.Point centre = new org.opencv.core.Point(cx, cy);
        if (filled) {
            Imgproc.circle(overlay, centre, radius, COLOUR_FILLED, -1);
        } else {
            Imgproc.circle(overlay, centre, radius, COLOUR_EMPTY, 1);
        }
    }

    /**
     * Colored version used for answer-key questions.
     * isCorrect: null → black (no key), true → green, false → red.
     */
    private void drawBubble(Mat overlay, int cx, int cy, int radius, boolean filled, Boolean isCorrect) {
        org.opencv.core.Point centre = new org.opencv.core.Point(cx, cy);
        if (filled) {
            Scalar color = (isCorrect == null) ? COLOUR_BLACK : (isCorrect ? COLOUR_FILLED : COLOUR_RED);
            Imgproc.circle(overlay, centre, radius, color, -1);
        } else {
            Imgproc.circle(overlay, centre, radius, COLOUR_EMPTY, 1);
        }
    }

    private void drawBubble(Mat overlay, int cx, int cy, int radius, boolean filled, boolean isMultiMark) {
        org.opencv.core.Point centre = new org.opencv.core.Point(cx, cy);
        if (filled) {
            Scalar color = isMultiMark ? COLOUR_MULTI : COLOUR_FILLED;
            Imgproc.circle(overlay, centre, radius, color, -1);
        } else {
            Imgproc.circle(overlay, centre, radius, COLOUR_EMPTY, 1);
        }
    }

    private void drawBubble(Mat overlay, int cx, int cy, int radius, boolean filled, Boolean isCorrect, boolean isMultiMark) {
        org.opencv.core.Point centre = new org.opencv.core.Point(cx, cy);
        if (filled) {
            Scalar color;
            if (isMultiMark) {
                color = COLOUR_MULTI;
            } else {
                color = (isCorrect == null) ? COLOUR_BLACK : (isCorrect ? COLOUR_FILLED : COLOUR_RED);
            }
            Imgproc.circle(overlay, centre, radius, color, -1);
        } else {
            Imgproc.circle(overlay, centre, radius, COLOUR_EMPTY, 1);
        }
    }

    /**
     * Draws one bubble on the teacher-reference overlay. Unlike the saved
     * scan overlay, this always reveals the answer key: the key column is
     * purple on every row (solid if the student didn't mark it, split with
     * the mark's own colour if they did — right half is always purple).
     */
    private void drawKeyReferenceBubble(Mat keyOverlay, int cx, int cy, int radius,
                                        boolean filled, boolean isMultiMark, boolean isKeyCol) {
        org.opencv.core.Point centre = new org.opencv.core.Point(cx, cy);

        if (isKeyCol) {
            if (!filled) {
                // Key answer wasn't marked (left blank, or student picked a different bubble).
                Imgproc.circle(keyOverlay, centre, radius, COLOUR_KEY, -1);
            } else if (isMultiMark) {
                // Key answer was one of several marks in this row.
                drawSplitCircle(keyOverlay, centre, radius, COLOUR_MULTI, COLOUR_KEY);
            } else {
                // Student marked the key answer, and only that one.
                drawSplitCircle(keyOverlay, centre, radius, COLOUR_FILLED, COLOUR_KEY);
            }
        } else if (filled) {
            // A marked bubble that is NOT the key answer.
            Scalar color = isMultiMark ? COLOUR_MULTI : COLOUR_RED;
            Imgproc.circle(keyOverlay, centre, radius, color, -1);
        } else {
            Imgproc.circle(keyOverlay, centre, radius, COLOUR_EMPTY, 1);
        }
    }

    /** Draws a circle split left/right between two colours. Right half is always {@code rightColor}. */
    private void drawSplitCircle(Mat mat, org.opencv.core.Point centre, int radius,
                                 Scalar leftColor, Scalar rightColor) {
        org.opencv.core.Size axes = new org.opencv.core.Size(radius, radius);
        Imgproc.ellipse(mat, centre, axes, 0, -90, 90, rightColor, -1);
        Imgproc.ellipse(mat, centre, axes, 0, 90, 270, leftColor, -1);
    }

    private void drawRedBox(Mat overlay, int x1, int y1, int x2, int y2) {
        Point tl = new Point(x1, y1);
        Point br = new Point(x2, y2);
        Imgproc.rectangle(overlay, tl, br, COLOUR_RED, 3);
    }

    static final class LnrReadProfile {
        final String name;
        final double fillThreshold;
        final double innerMaskRadiusFactor;
        final double winnerMargin;
        final double doubleShadeStrongThreshold;
        final double doubleShadeTieMargin;

        LnrReadProfile(String name,
                       double fillThreshold,
                       double innerMaskRadiusFactor,
                       double winnerMargin,
                       double doubleShadeStrongThreshold,
                       double doubleShadeTieMargin) {
            this.name = name;
            this.fillThreshold = fillThreshold;
            this.innerMaskRadiusFactor = innerMaskRadiusFactor;
            this.winnerMargin = winnerMargin;
            this.doubleShadeStrongThreshold = doubleShadeStrongThreshold;
            this.doubleShadeTieMargin = doubleShadeTieMargin;
        }
    }

    static final class LnrColumnDecision {
        enum Kind {
            DETECTED,
            UNDETECTED,
            DOUBLE_SHADED
        }

        final Kind kind;
        final int bestRow;
        final int secondBestRow;
        final double bestRatio;
        final double secondBestRatio;
        final int filledCount;

        LnrColumnDecision(Kind kind,
                          int bestRow,
                          int secondBestRow,
                          double bestRatio,
                          double secondBestRatio,
                          int filledCount) {
            this.kind = kind;
            this.bestRow = bestRow;
            this.secondBestRow = secondBestRow;
            this.bestRatio = bestRatio;
            this.secondBestRatio = secondBestRatio;
            this.filledCount = filledCount;
        }
    }
}