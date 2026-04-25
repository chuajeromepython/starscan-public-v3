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

/**
 * Scans filled bubbles on a perspective-warped OMR sheet using pixel-density
 * analysis (not contour detection).
 *
 * <h3>Algorithm per bubble</h3>
 * <ol>
 *   <li>Crop a square ROI around the bubble centre (side = 2 * scaledRadius).</li>
 *   <li>Convert the ROI to grayscale.</li>
 *   <li>Apply Otsu thresholding with binary inversion
 *       ({@code THRESH_BINARY_INV + THRESH_OTSU}).</li>
 *   <li>{@code Core.countNonZero()} to count ink (foreground) pixels.</li>
 *   <li>Fill ratio = foreground / total pixels.
 *       If ratio &ge; the block threshold the bubble is considered <b>FILLED</b>.</li>
 * </ol>
 *
 * <h3>LNR (Student ID)</h3>
 * The LNR block has 10 rows (digits 0-9) and 12 columns (digit positions).
 * For each column the row with the highest fill ratio is selected as the digit.
 *
 * <h3>Questions</h3>
 * Each question block has N rows (one per question) and 4 columns (A B C D).
 * Any column whose fill ratio exceeds the threshold is recorded.
 * Multiple fills produce combined strings like "AC".
 *
 * <h3>Visual overlay</h3>
 * After scanning, an overlay bitmap is produced:
 * <ul>
 *   <li><b>Green filled circle</b> — FILLED bubble</li>
 *   <li><b>Blue outline circle</b> — EMPTY bubble</li>
 * </ul>
 */
public class BubbleScanner {

    private static final String TAG = "BubbleScanner";

    // ── Tuneable thresholds ─────────────────────────────────────────────────

    /**
     * Minimum ink-pixel fill ratio to mark a bubble as FILLED.
     * Increase to 0.50 if shadows cause false positives;
     * decrease to 0.30 for faint pencil marks.
     */
    private static final double QUESTION_FILL_THRESHOLD = 0.45;

    /**
     * Inner-circle factor for fill analysis.
     * We intentionally ignore the printed ring outline by measuring only
     * the inner disk.
     */
    private static final double DEFAULT_INNER_MASK_RADIUS_FACTOR = 0.60;

    /**
     * Maximum fraction of bubble spacing allowed for per-block alignment offset.
     * Prevents a wrong template match from jumping by one full bubble step.
     */
    private static final double MAX_OFFSET_SPACING_FRACTION = 0.45;

    // ── Overlay colours (BGR for OpenCV Mat) ────────────────────────────────
    private static final Scalar COLOUR_FILLED = new Scalar(0, 255, 0);   // green
    private static final Scalar COLOUR_EMPTY  = new Scalar(255, 0, 0);   // blue
    private static final Scalar COLOUR_RED    = new Scalar(0, 0, 255);   // red (undetected)

    // ── Choice labels ───────────────────────────────────────────────────────
    private static final char[] CHOICE_LABELS = {'A', 'B', 'C', 'D'};

    private static final LnrReadProfile DEFAULT_LNR_PROFILE = new LnrReadProfile(
            "default",
            QUESTION_FILL_THRESHOLD,
            DEFAULT_INNER_MASK_RADIUS_FACTOR,
            0.0,
            QUESTION_FILL_THRESHOLD,
            0.0
    );

    private static final LnrReadProfile ZPH60_LNR_PROFILE = new LnrReadProfile(
            "ZPH60",
            0.52,
            0.50,
            0.08,
            0.60,
            0.04
    );

    // =====================================================================
    //  Public API
    // =====================================================================

    /**
     * Scan an entire warped OMR sheet and return a {@link ScanResult}.
     *
     * <p>Before reading each block, a {@link GridAligner} finds the actual
     * position of the first bubble via template matching and computes a
     * per-block pixel offset.  Every coordinate in the block is then shifted
     * by that offset, ensuring the scanner hits the real bubble centres even
     * when the perspective warp introduces a slight translation.</p>
     *
     * @param warpedBitmap  the perspective-aligned bitmap (1000x1414)
     * @param template      the matched {@link OmrTemplate}
     * @param templateMgr   a {@link TemplateManager} (for radius scaling)
     * @return fully populated ScanResult including overlay bitmap
     */
    public ScanResult scan(Bitmap warpedBitmap, OmrTemplate template, TemplateManager templateMgr) {
        if (warpedBitmap == null || template == null) {
            Log.e(TAG, "scan: null bitmap or template");
            return null;
        }

        int bw = warpedBitmap.getWidth();
        int bh = warpedBitmap.getHeight();
        double scaleX = (double) bw / template.width;
        double scaleY = (double) bh / template.height;

        // Convert bitmap → OpenCV Mats
        Mat colour = new Mat();
        Utils.bitmapToMat(warpedBitmap, colour);

        Mat gray = new Mat();
        Imgproc.cvtColor(colour, gray, Imgproc.COLOR_BGR2GRAY);

        // Overlay mat — draw on a copy of the colour image
        Mat overlay = colour.clone();

        // ── Grid alignment ─────────────────────────────────────────────
        GridAligner aligner = new GridAligner();

        // ── Results ─────────────────────────────────────────────────────
        ScanResult result = new ScanResult();
        result.templateId = template.templateId;

        int questionCounter = 0; // 1-based running counter across all question blocks
        LnrReadProfile lnrProfile = resolveLnrReadProfile(template.templateId);

        for (OmrBlock block : template.blocks) {
            int scaledRadius = Math.max(1,
                    (int) Math.round(block.radius * ((scaleX + scaleY) * 0.5)));

            // ── Per-block alignment offset ──────────────────────────────
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
                        scaledRadius, gray, overlay,
                        result.answers, questionCounter, safeOffset[0], safeOffset[1]);
            }
        }

        aligner.release();

        // ── Build overlay bitmap ────────────────────────────────────────
        Bitmap overlayBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(overlay, overlayBitmap);
        result.overlayBitmap = overlayBitmap;

        // Cleanup
        colour.release();
        gray.release();
        overlay.release();

        Log.i(TAG, "Scan complete: " + result);
        return result;
    }

    // =====================================================================
    //  LNR scanning
    // =====================================================================

    /**
     * Scan the LNR block and return the 12-digit student ID string.
     *
     * Layout: 10 rows (digits 0-9) x 12 columns (digit positions).
     * For each column, the row with the highest fill ratio wins.
     * If no row in a column exceeds the active LNR threshold, the digit is
     * marked as undetected ('_') and a red box is drawn on the overlay.
     *
     * @param offX   per-block horizontal offset from GridAligner
     * @param offY   per-block vertical offset from GridAligner
     * @param result the ScanResult to populate with undetected positions
     */
    private String scanLnrBlock(OmrBlock block, double scaleX, double scaleY,
                                int scaledRadius, Mat gray, Mat overlay,
                                int offX, int offY, ScanResult result) {
        return scanLnrBlock(block, scaleX, scaleY, scaledRadius, gray, overlay, offX, offY, result,
                DEFAULT_LNR_PROFILE);
    }

    private String scanLnrBlock(OmrBlock block, double scaleX, double scaleY,
                                int scaledRadius, Mat gray, Mat overlay,
                                int offX, int offY, ScanResult result,
                                LnrReadProfile profile) {
        // block.rows = 10 (digits 0-9), block.cols = 12 (positions)
        // Fill ratios: [col][row]
        double[][] ratios = new double[block.cols][block.rows];

        for (int row = 0; row < block.rows; row++) {
            for (int col = 0; col < block.cols; col++) {
                int cx = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;
                int cy = (int) Math.round((block.startY + row * block.dy) * scaleY) + offY;

                ratios[col][row] = measureFillRatio(gray, cx, cy, scaledRadius,
                        profile.innerMaskRadiusFactor);
            }
        }

        // For each column pick the row with the highest ratio
        // Also detect double-shaded columns (2+ bubbles above threshold)
        StringBuilder lnr = new StringBuilder();
        for (int col = 0; col < block.cols; col++) {
            LnrColumnDecision decision = classifyLnrColumn(ratios[col], profile);

            if (decision.kind == LnrColumnDecision.Kind.DOUBLE_SHADED) {
                lnr.append('X');
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

        // Draw overlay for LNR block
        for (int row = 0; row < block.rows; row++) {
            for (int col = 0; col < block.cols; col++) {
                int cx = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;
                int cy = (int) Math.round((block.startY + row * block.dy) * scaleY) + offY;
                boolean filled = ratios[col][row] >= profile.fillThreshold;
                drawBubble(overlay, cx, cy, scaledRadius, filled);
            }
        }

        // Draw red boxes around undetected LNR columns
        for (int col : result.undetectedLnrPositions) {
            // Calculate the bounding box for this column (spans all rows)
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

        // Draw red boxes around double-shaded LNR columns
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

    // =====================================================================
    //  Question-block scanning
    // =====================================================================

    /**
     * Scan a question block (e.g. "Questions_1_10") and populate the answers map.
     *
     * Layout: N rows (one per question) x 4 columns (A B C D).
     *
     * @param questionCounter current 1-based question counter (from previous blocks)
     * @param offX per-block horizontal offset from GridAligner
     * @param offY per-block vertical offset from GridAligner
     * @return updated question counter after this block
     */
    private int scanQuestionBlock(OmrBlock block, double scaleX, double scaleY,
                                  int scaledRadius, Mat gray, Mat overlay,
                                  Map<Integer, String> answers, int questionCounter,
                                  int offX, int offY) {

        for (int row = 0; row < block.rows; row++) {
            questionCounter++;
            StringBuilder detected = new StringBuilder();

            for (int col = 0; col < block.cols; col++) {
                int cx = (int) Math.round((block.startX + col * block.dx) * scaleX) + offX;
                int cy = (int) Math.round((block.startY + row * block.dy) * scaleY) + offY;

                double ratio = measureFillRatio(gray, cx, cy, scaledRadius,
                        DEFAULT_INNER_MASK_RADIUS_FACTOR);
                boolean filled = ratio >= QUESTION_FILL_THRESHOLD;

                if (filled && col < CHOICE_LABELS.length) {
                    detected.append(CHOICE_LABELS[col]);
                }

                drawBubble(overlay, cx, cy, scaledRadius, filled);
            }

            String answer = detected.toString();
            answers.put(questionCounter, answer);

            Log.d(TAG, String.format("Q%d: %s", questionCounter,
                    answer.isEmpty() ? "(blank)" : answer));
        }

        return questionCounter;
    }

    // =====================================================================
    //  Pixel-density measurement
    // =====================================================================

    /**
     * Measure the ink fill ratio of a single bubble.
     *
     * Crops a square ROI of side {@code 2 * radius} centred at (cx, cy)
     * from the pre-thresholded binary image, then counts non-zero (ink) pixels.
     *
     * @return fill ratio in [0.0, 1.0]
     */
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

        // Local Otsu threshold per bubble ROI for better lighting robustness.
        Mat localBinary = new Mat();
        Imgproc.threshold(patch, localBinary, 0, 255,
                Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        // Use an inner circular mask to ignore the printed ring outline.
        int localCx = cx - x1;
        int localCy = cy - y1;
        int innerRadius = Math.max(1, (int) Math.round(radius * innerMaskRadiusFactor));

        Mat mask = Mat.zeros(h, w, CvType.CV_8UC1);
        Imgproc.circle(mask, new Point(localCx, localCy), innerRadius, new Scalar(255), -1);

        Mat maskedInk = new Mat();
        Core.bitwise_and(localBinary, mask, maskedInk);

        int foreground = Core.countNonZero(maskedInk);
        int total = Math.max(1, Core.countNonZero(mask));

        maskedInk.release();
        mask.release();
        localBinary.release();
        patch.release();

        return (double) foreground / total;
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

    /**
     * Reject implausibly large offsets to avoid snapping to the wrong nearby bubble.
     */
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

    // =====================================================================
    //  Overlay drawing
    // =====================================================================

    /**
     * Draw a single bubble indicator on the overlay image.
     *
     * @param filled true → green filled circle; false → blue outline circle
     */
    private void drawBubble(Mat overlay, int cx, int cy, int radius, boolean filled) {
        org.opencv.core.Point centre = new org.opencv.core.Point(cx, cy);
        if (filled) {
            // Green filled circle (thickness -1 = filled)
            Imgproc.circle(overlay, centre, radius, COLOUR_FILLED, -1);
        } else {
            // Blue outline circle (thickness 1)
            Imgproc.circle(overlay, centre, radius, COLOUR_EMPTY, 1);
        }
    }

    /**
     * Draw a red box (rectangle) on the overlay to indicate an undetected region.
     * Used mainly for LNR columns where no bubble met the fill threshold.
     */
    private void drawRedBox(Mat overlay, int x1, int y1, int x2, int y2) {
        Point tl = new Point(x1, y1);
        Point br = new Point(x2, y2);
        // Outer red rectangle, 3px thick for visibility
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
