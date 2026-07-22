package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Applies perspective warp to transform the photographed OMR sheet
 * into a fixed canonical rectangle (1000 x 1414 pixels, A4 ratio).
 *
 * Using a FIXED output size is critical for the grid-masking approach:
 * it guarantees that bubble coordinates are always at the same pixel
 * positions on the warped image, regardless of camera distance, angle,
 * or original photo resolution.
 */
public class PerspectiveAligner {

    private static final String TAG = "PerspectiveAligner";

    // Fixed canonical output size (A4 aspect ratio: 1 : sqrt(2) = 1 : 1.414)
    // This is the EXACT size the grid templates map coordinates against.
    public static final int CANONICAL_WIDTH = 1000;
    public static final int CANONICAL_HEIGHT = 1414;

    /**
     * Apply perspective transformation to flatten the OMR sheet.
     *
     * Takes the 4 detected anchor points and warps the image so
     * the sheet fills a perfect 1000x1414 rectangle.
     *
     * @param bitmap  Original captured image
     * @param anchors 4 corner points [TopLeft, TopRight, BottomLeft, BottomRight]
     * @return Warped/flattened bitmap at exactly 1000x1414, or null on failure
     */

    /**
     * Same sanity checks as {@link #validateAnchors(Point[])} MINUS the
     * "TL must be left of TR / above BL" directional assumption.
     *
     * That assumption only holds when corners were labeled by raw frame
     * position (the legacy geometric detector), where it's tautological.
     * ArUco identity anchors are labeled by physical marker ID instead, so
     * a genuinely-tilted capture can legitimately have the true TL corner
     * land anywhere in the raw buffer -- rejecting that as "invalid
     * ordering" is exactly what silently produces an upside-down result
     * for Tilt Agnostic Mode captures. Use this validator for anchors that
     * came from {@link ArucoAnchorDetector}; use the original
     * {@link #validateAnchors(Point[])} for anything from the geometric
     * detector, unchanged.
     */
    public static boolean validateAnchorsOrientationAgnostic(Point[] anchors) {
        if (anchors == null || anchors.length != 4) {
            return false;
        }

        for (Point p : anchors) {
            if (p == null) return false;
        }

        double area = calculateQuadrilateralArea(anchors);
        if (area < 10000) {
            Log.w(TAG, "Anchor area too small: " + area);
            return false;
        }

        // Reject mirrored correspondences. getPerspectiveTransform() maps
        // src[TL,TR,BL,BR] onto a NORMAL-winding destination rectangle
        // ((0,0),(W,0),(0,H),(W,H)). If the src points themselves wind the
        // opposite way, the resulting homography bakes in a reflection --
        // TemplateManager's CW/CCW rotation search can never undo that,
        // it can only ever pick between two equally-wrong mirrored results.
        // Better to fail fast here with a retake prompt than warp into a
        // permanently unscannable image.
        if (!isWindingNormal(anchors)) {
            Log.w(TAG, "Anchors encode a mirrored correspondence, not a pure rotation -- rejecting");
            return false;
        }

        return true;
    }

    /**
     * Checks whether [TL, TR, BL, BR] anchors wind the same direction as
     * the destination rectangle used in {@link #alignPerspective}. See
     * {@link #validateAnchorsOrientationAgnostic} for why this matters.
     */
    public static boolean isWindingNormal(Point[] anchors) {
        Point tl = anchors[0], tr = anchors[1], bl = anchors[2], br = anchors[3];
        double signedArea =
                (tl.x * tr.y - tr.x * tl.y) +
                        (tr.x * br.y - br.x * tr.y) +
                        (br.x * bl.y - bl.x * br.y) +
                        (bl.x * tl.y - tl.x * bl.y);
        return signedArea > 0;
    }
    public static Bitmap alignPerspective(Bitmap bitmap, Point[] anchors) {
        return alignPerspective(bitmap, anchors, false);
    }

    /**
     * @param landscapeContent When true, warps into a LANDSCAPE canonical
     *        rectangle (CANONICAL_HEIGHT x CANONICAL_WIDTH) instead of the
     *        default portrait one. ArUco-identity-resolved anchors (Tilt
     *        Agnostic Mode) come from a full-res capture that's already
     *        been pre-rotated to normal reading orientation, so they form
     *        a genuinely LANDSCAPE quad matching the sheet's actual content
     *        shape (every ZPH template is landscape -- e.g. ZPH60 is
     *        1609x1134). Forcing that quad onto the portrait 1000x1414 dst
     *        rectangle used by the geometric handheld pipeline doesn't
     *        rotate the content -- TL still maps to TL, TR to TR, etc. --
     *        it anisotropically SQUEEZES it (long edge crushed into the
     *        short dst dimension, short edge stretched into the long dst
     *        dimension). No amount of post-warp 90-degree rotation can
     *        undo that distortion, which is why TemplateManager's
     *        rotate-and-score search was scoring badly in both directions.
     */
    public static Bitmap alignPerspective(Bitmap bitmap, Point[] anchors, boolean landscapeContent) {
        if (anchors == null || anchors.length != 4) {
            Log.e(TAG, "Invalid anchors - need exactly 4 points");
            return null;
        }

        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        // Source points: the 4 detected anchor positions in the original image
        // Order: TL, TR, BL, BR
        MatOfPoint2f srcMat = new MatOfPoint2f(
                anchors[0],  // Top-Left
                anchors[1],  // Top-Right
                anchors[2],  // Bottom-Left
                anchors[3]   // Bottom-Right
        );

        int dstWidth = landscapeContent ? CANONICAL_HEIGHT : CANONICAL_WIDTH;
        int dstHeight = landscapeContent ? CANONICAL_WIDTH : CANONICAL_HEIGHT;

        // Destination points: the 4 corners of a perfect rectangle
        MatOfPoint2f dstMat = new MatOfPoint2f(
                new Point(0, 0),                     // Top-Left
                new Point(dstWidth, 0),               // Top-Right
                new Point(0, dstHeight),              // Bottom-Left
                new Point(dstWidth, dstHeight)        // Bottom-Right
        );

        // Calculate the 3x3 perspective transform matrix
        Mat transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);

        // Apply the warp to produce a flat top-down view
        Mat warped = new Mat();
        Imgproc.warpPerspective(
                src,
                warped,
                transformMatrix,
                new Size(dstWidth, dstHeight)
        );

        // Convert back to Bitmap
        Bitmap result = Bitmap.createBitmap(
                dstWidth,
                dstHeight,
                Bitmap.Config.ARGB_8888
        );
        Utils.matToBitmap(warped, result);

        // Cleanup
        src.release();
        warped.release();
        transformMatrix.release();
        srcMat.release();
        dstMat.release();

        Log.d(TAG, "Perspective alignment successful -> " + dstWidth + "x" + dstHeight
                + (landscapeContent ? " (landscape canonical)" : " (portrait canonical)"));
        return result;
    }

    /**
     * Validate if anchors form a reasonable quadrilateral.
     *
     * @param anchors 4 corner points [TL, TR, BL, BR]
     * @return true if the anchors are geometrically valid
     */
    public static boolean validateAnchors(Point[] anchors) {
        if (anchors == null || anchors.length != 4) {
            return false;
        }

        for (Point p : anchors) {
            if (p == null) return false;
        }

        // Basic sanity: TL should be left of TR and above BL
        if (anchors[0].x > anchors[1].x || anchors[0].y > anchors[2].y) {
            Log.w(TAG, "Anchor ordering may be incorrect");
            return false;
        }

        // Minimum area check (Shoelace formula)
        double area = calculateQuadrilateralArea(anchors);
        if (area < 10000) {
            Log.w(TAG, "Anchor area too small: " + area);
            return false;
        }

        return true;
    }

    /**
     * Calculate area of quadrilateral using the Shoelace formula.
     */
    private static double calculateQuadrilateralArea(Point[] points) {
        double area = 0;
        int j = points.length - 1;

        for (int i = 0; i < points.length; i++) {
            area += (points[j].x + points[i].x) * (points[j].y - points[i].y);
            j = i;
        }

        return Math.abs(area / 2.0);
    }
}
