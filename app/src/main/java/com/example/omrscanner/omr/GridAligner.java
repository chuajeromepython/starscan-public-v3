package com.example.omrscanner.omr;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Dynamically aligns a JSON-defined bubble grid to the actual warped image by
 * finding the real position of each block's first bubble via template matching.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>A synthetic "reference bubble" (circle outline on white background) is
 *       generated algorithmically from the block's {@code radius}.</li>
 *   <li>A Search Window (ROI) of {@code ±SEARCH_MARGIN} pixels is extracted
 *       from the source image around the expected first-bubble position.</li>
 *   <li>{@link Imgproc#matchTemplate} ({@code TM_CCOEFF_NORMED}) locates the
 *       best match within the window.</li>
 *   <li>The pixel offset (deltaX, deltaY) between the expected and actual
 *       positions is returned so the caller can shift every coordinate in the
 *       block.</li>
 * </ol>
 *
 * <h3>Sheet detection helper</h3>
 * {@link #getAlignmentScore} returns the peak confidence of the template match.
 * A high score (≥ 0.5) indicates the template genuinely matches the image;
 * a low score means the expected bubble region contains unrelated content.
 */
public class GridAligner {

    private static final String TAG = "GridAligner";

    // ── Tuneable parameters ──────────────────────────────────────────────────

    /**
     * Half-size of the search window around the expected bubble position.
     * The search ROI will be {@code 2 * SEARCH_MARGIN + templateSize} wide/tall.
     * Increase if the warp shift can be larger; decrease for speed.
     */
    private static final int SEARCH_MARGIN = 40;

    /**
     * Minimum acceptable match score. Below this the offset is considered
     * unreliable, and (0, 0) is returned instead.
     */
    private static final double MIN_MATCH_SCORE = 0.35;

    /**
     * Thickness of the circle outline in the synthetic bubble template (pixels).
     */
    private static final int BUBBLE_OUTLINE_THICKNESS = 2;

    // ── Cached template ──────────────────────────────────────────────────────
    private Mat cachedBubbleTemplate = null;
    private int cachedRadius = -1;

    // =====================================================================
    //  Public API
    // =====================================================================

    /**
     * Result of a single block alignment: the pixel offset to apply and the
     * template-matching confidence score.
     */
    public static class AlignmentResult {
        /** Horizontal shift to add to every X coordinate in the block. */
        public final int offsetX;
        /** Vertical shift to add to every Y coordinate in the block. */
        public final int offsetY;
        /** Peak normalised cross-correlation score (0.0 – 1.0). */
        public final double score;

        public AlignmentResult(int offsetX, int offsetY, double score) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("AlignmentResult{offset=(%d,%d), score=%.3f}",
                    offsetX, offsetY, score);
        }
    }

    /**
     * Compute the pixel offset between the expected and actual position of
     * the first bubble in a block.
     *
     * @param graySource  the full warped image in <b>grayscale</b> (CV_8UC1)
     * @param block       the OmrBlock whose first bubble we want to locate
     * @param scaleX      {@code bitmapWidth / template.width}
     * @param scaleY      {@code bitmapHeight / template.height}
     * @return AlignmentResult with the (deltaX, deltaY) offset and confidence
     */
    public AlignmentResult getBlockAlignment(Mat graySource,
                                             OmrBlock block,
                                             double scaleX,
                                             double scaleY) {
        // ── 1. Expected position in bitmap space ────────────────────────
        int expectedX = (int) Math.round(block.startX * scaleX);
        int expectedY = (int) Math.round(block.startY * scaleY);
        int scaledRadius = Math.max(1, (int) Math.round(block.radius * scaleX));

        // ── 2. Build (or reuse) the synthetic bubble template ───────────
        Mat bubbleTpl = getBubbleTemplate(scaledRadius);
        int tplW = bubbleTpl.cols();
        int tplH = bubbleTpl.rows();

        // ── 3. Define the search ROI ────────────────────────────────────
        int roiX = Math.max(0, expectedX - SEARCH_MARGIN - tplW / 2);
        int roiY = Math.max(0, expectedY - SEARCH_MARGIN - tplH / 2);
        int roiW = tplW + 2 * SEARCH_MARGIN;
        int roiH = tplH + 2 * SEARCH_MARGIN;

        // Clamp to image bounds
        if (roiX + roiW > graySource.cols()) roiW = graySource.cols() - roiX;
        if (roiY + roiH > graySource.rows()) roiH = graySource.rows() - roiY;

        // ROI must be at least as large as the template
        if (roiW < tplW || roiH < tplH) {
            Log.w(TAG, "Search ROI too small for block " + block.label);
            return new AlignmentResult(0, 0, 0.0);
        }

        Rect searchRect = new Rect(roiX, roiY, roiW, roiH);
        Mat searchROI = graySource.submat(searchRect);

        // ── 4. Template matching ────────────────────────────────────────
        int resultW = roiW - tplW + 1;
        int resultH = roiH - tplH + 1;
        if (resultW <= 0 || resultH <= 0) {
            searchROI.release();
            return new AlignmentResult(0, 0, 0.0);
        }

        Mat result = new Mat(resultH, resultW, CvType.CV_32FC1);
        Imgproc.matchTemplate(searchROI, bubbleTpl, result, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
        double score = mmr.maxVal;
        Point matchLoc = mmr.maxLoc; // top-left corner of best match inside ROI

        result.release();
        searchROI.release();

        // ── 5. Calculate offset ─────────────────────────────────────────
        // The match location is the top-left of the template inside the ROI.
        // The bubble centre in absolute image coords:
        int foundCX = roiX + (int) matchLoc.x + tplW / 2;
        int foundCY = roiY + (int) matchLoc.y + tplH / 2;

        int deltaX = foundCX - expectedX;
        int deltaY = foundCY - expectedY;

        Log.d(TAG, String.format(
                "Block '%s': expected=(%d,%d) found=(%d,%d) delta=(%d,%d) score=%.3f",
                block.label, expectedX, expectedY, foundCX, foundCY,
                deltaX, deltaY, score));

        if (score < MIN_MATCH_SCORE) {
            Log.w(TAG, String.format(
                    "Low score %.3f for block '%s' — ignoring offset",
                    score, block.label));
            return new AlignmentResult(0, 0, score);
        }

        return new AlignmentResult(deltaX, deltaY, score);
    }

    /**
     * Convenience method: returns just the confidence score of aligning the
     * first question block of a template against the warped image.
     * Used by {@link TemplateManager} for sheet-type detection.
     *
     * @param graySource the full warped grayscale image
     * @param template   the candidate template
     * @param scaleX     bitmapWidth / template.width
     * @param scaleY     bitmapHeight / template.height
     * @return best match score for the first non-LNR block (0.0 – 1.0)
     */
    public double getAlignmentScore(Mat graySource,
                                    OmrTemplate template,
                                    double scaleX,
                                    double scaleY) {
        OmrBlock probeBlock = getFirstQuestionBlock(template);
        if (probeBlock == null) {
            Log.w(TAG, "No question block found in template " + template.templateId);
            return 0.0;
        }
        AlignmentResult ar = getBlockAlignment(graySource, probeBlock, scaleX, scaleY);
        return ar.score;
    }

    /**
     * Release cached resources. Call when the GridAligner is no longer needed.
     */
    public void release() {
        if (cachedBubbleTemplate != null) {
            cachedBubbleTemplate.release();
            cachedBubbleTemplate = null;
            cachedRadius = -1;
        }
    }

    // =====================================================================
    //  Internals
    // =====================================================================

    /**
     * Generate (or return cached) a small binary Mat representing a "perfect
     * empty bubble": a dark circle outline on a white background.
     *
     * The Mat is {@code (2*radius + 4) x (2*radius + 4)} pixels, CV_8UC1.
     */
    private Mat getBubbleTemplate(int radius) {
        if (cachedBubbleTemplate != null && cachedRadius == radius) {
            return cachedBubbleTemplate;
        }
        // Release old template if radius changed
        if (cachedBubbleTemplate != null) {
            cachedBubbleTemplate.release();
        }

        int side = 2 * radius + 4; // small padding around the circle
        cachedBubbleTemplate = new Mat(side, side, CvType.CV_8UC1, new Scalar(255));

        // Draw dark circle outline
        Point centre = new Point(side / 2.0, side / 2.0);
        Imgproc.circle(cachedBubbleTemplate, centre, radius,
                new Scalar(0), BUBBLE_OUTLINE_THICKNESS);

        cachedRadius = radius;

        Log.d(TAG, String.format(
                "Generated bubble template: radius=%d, size=%dx%d",
                radius, side, side));

        return cachedBubbleTemplate;
    }

    /**
     * Find the first non-LNR (question) block in a template.
     */
    static OmrBlock getFirstQuestionBlock(OmrTemplate template) {
        for (OmrBlock block : template.blocks) {
            if (!block.label.equals("LNR")) {
                return block;
            }
        }
        return null;
    }
}
