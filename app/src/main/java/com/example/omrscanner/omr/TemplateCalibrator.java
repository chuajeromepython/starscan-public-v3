package com.example.omrscanner.omr;

import androidx.annotation.NonNull;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

/**
 * Derives OmrBlock geometry (start_x, start_y, dx, dy) from two points a
 * human drags onto a RAW reference photo -- the top-left bubble center and
 * the bottom-right bubble center of a block -- instead of hand-editing JSON
 * numbers.
 *
 * This is exactly the workflow used to calibrate ZPH40/ZPH60 by hand:
 *   1. Detect the 4 ArUco markers in the raw photo.
 *   2. Solve the same homography PerspectiveAligner uses to warp the sheet
 *      into the fixed canonical rectangle.
 *   3. Push the two raw-photo points through that homography.
 *   4. Rescale from canonical pixel space into the template's own
 *      coordinate space (template.width x template.height).
 *   5. start_x/start_y come directly from the transformed top-left point;
 *      dx/dy come from dividing the transformed span by (cols-1)/(rows-1).
 *
 * No rotation term -- both ZPH40 and ZPH60 masters were measured and
 * confirmed rotation-free (row-to-row x-drift under ~2 template units
 * across all blocks tested). If a future sheet format turns out to have a
 * genuinely rotated block, this model would need a shear term added; it's
 * deliberately left out here rather than guessed at.
 */
public final class TemplateCalibrator {

    private TemplateCalibrator() {}

    /** Result of deriving one block's geometry from two dragged points. */
    public static final class BlockGeometry {
        public final double startX, startY, dx, dy;
        public BlockGeometry(double startX, double startY, double dx, double dy) {
            this.startX = startX;
            this.startY = startY;
            this.dx = dx;
            this.dy = dy;
        }
    }

    /**
     * Builds the same forward homography PerspectiveAligner.alignPerspective
     * uses: raw-photo anchors [TL,TR,BL,BR] -> fixed canonical rectangle.
     */
    @NonNull
    public static Mat buildForwardHomography(@NonNull Point[] anchors, boolean landscapeContent) {
        if (anchors == null || anchors.length != 4) {
            throw new IllegalArgumentException("Need exactly 4 anchors [TL,TR,BL,BR]");
        }
        int dstWidth = landscapeContent ? PerspectiveAligner.CANONICAL_HEIGHT : PerspectiveAligner.CANONICAL_WIDTH;
        int dstHeight = landscapeContent ? PerspectiveAligner.CANONICAL_WIDTH : PerspectiveAligner.CANONICAL_HEIGHT;

        MatOfPoint2f srcMat = new MatOfPoint2f(anchors[0], anchors[1], anchors[2], anchors[3]);
        MatOfPoint2f dstMat = new MatOfPoint2f(
                new Point(0, 0),
                new Point(dstWidth, 0),
                new Point(0, dstHeight),
                new Point(dstWidth, dstHeight)
        );
        Mat h = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        srcMat.release();
        dstMat.release();
        return h;
    }

    /** Inverse of {@link #buildForwardHomography}: canonical -> raw photo. */
    @NonNull
    public static Mat buildInverseHomography(@NonNull Point[] anchors, boolean landscapeContent) {
        Mat forward = buildForwardHomography(anchors, landscapeContent);
        Mat inverse = forward.inv();
        forward.release();
        return inverse;
    }

    @NonNull
    private static Point transform(@NonNull Mat homography3x3, @NonNull Point p) {
        MatOfPoint2f src = new MatOfPoint2f(p);
        MatOfPoint2f dst = new MatOfPoint2f();
        Core_perspectiveTransform(src, dst, homography3x3);
        Point result = dst.toArray()[0];
        src.release();
        dst.release();
        return result;
    }

    // Small indirection so the only place that ever calls the OpenCV Core
    // method is this one line -- keeps the import list obvious and makes it
    // trivial to swap in a batched version later if we ever need to
    // transform many points per call instead of one.
    private static void Core_perspectiveTransform(MatOfPoint2f src, MatOfPoint2f dst, Mat m) {
        org.opencv.core.Core.perspectiveTransform(src, dst, m);
    }

    /**
     * Raw photo point -> template coordinate space (template.width x
     * template.height), via the forward homography then a canonical ->
     * template rescale. Used when SAVING a dragged calibration point.
     */
    @NonNull
    public static Point rawToTemplateSpace(@NonNull Mat forwardHomography, @NonNull Point rawPoint,
                                           boolean landscapeContent, int templateWidth, int templateHeight) {
        Point canonical = transform(forwardHomography, rawPoint);
        int canonicalWidth = landscapeContent ? PerspectiveAligner.CANONICAL_HEIGHT : PerspectiveAligner.CANONICAL_WIDTH;
        int canonicalHeight = landscapeContent ? PerspectiveAligner.CANONICAL_WIDTH : PerspectiveAligner.CANONICAL_HEIGHT;
        double scaleX = (double) templateWidth / canonicalWidth;
        double scaleY = (double) templateHeight / canonicalHeight;
        return new Point(canonical.x * scaleX, canonical.y * scaleY);
    }

    /**
     * Template coordinate space -> raw photo point, via a template ->
     * canonical rescale then the inverse homography. Used to draw the
     * CURRENT (possibly wrong) block position onto the raw photo, so the
     * user has something to see and drag away from.
     */
    @NonNull
    public static Point templateSpaceToRaw(@NonNull Mat inverseHomography, @NonNull Point templatePoint,
                                           boolean landscapeContent, int templateWidth, int templateHeight) {
        int canonicalWidth = landscapeContent ? PerspectiveAligner.CANONICAL_HEIGHT : PerspectiveAligner.CANONICAL_WIDTH;
        int canonicalHeight = landscapeContent ? PerspectiveAligner.CANONICAL_WIDTH : PerspectiveAligner.CANONICAL_HEIGHT;
        double scaleX = (double) canonicalWidth / templateWidth;
        double scaleY = (double) canonicalHeight / templateHeight;
        Point canonical = new Point(templatePoint.x * scaleX, templatePoint.y * scaleY);
        return transform(inverseHomography, canonical);
    }

    /**
     * The core "drag two dots" derivation. Give it where the user dropped
     * the top-left bubble handle and the bottom-right bubble handle, in RAW
     * PHOTO pixel coordinates, plus the block's row/col count, and get back
     * the block geometry to write into the template JSON.
     *
     * @param forwardHomography  from {@link #buildForwardHomography}
     * @param rawTopLeftBubble   where the user dragged the (row0,col0) handle, in raw photo pixels
     * @param rawBottomRightBubble where the user dragged the (rowN-1,colM-1) handle, in raw photo pixels
     * @param rows  block's row count (e.g. 10)
     * @param cols  block's column count (e.g. 4, or 12 for LNR)
     */
    @NonNull
    public static BlockGeometry deriveBlockGeometry(@NonNull Mat forwardHomography,
                                                    @NonNull Point rawTopLeftBubble,
                                                    @NonNull Point rawBottomRightBubble,
                                                    boolean landscapeContent,
                                                    int templateWidth, int templateHeight,
                                                    int rows, int cols) {
        if (rows < 2 || cols < 2) {
            throw new IllegalArgumentException("Need at least a 2x2 grid to derive spacing; got " + rows + "x" + cols);
        }
        Point start = rawToTemplateSpace(forwardHomography, rawTopLeftBubble, landscapeContent, templateWidth, templateHeight);
        Point end = rawToTemplateSpace(forwardHomography, rawBottomRightBubble, landscapeContent, templateWidth, templateHeight);

        double dx = (end.x - start.x) / (cols - 1);
        double dy = (end.y - start.y) / (rows - 1);
        return new BlockGeometry(start.x, start.y, dx, dy);
    }
}