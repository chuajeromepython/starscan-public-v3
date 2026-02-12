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
    public static Bitmap alignPerspective(Bitmap bitmap, Point[] anchors) {
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

        // Destination points: the 4 corners of a perfect rectangle
        MatOfPoint2f dstMat = new MatOfPoint2f(
                new Point(0, 0),                                    // Top-Left
                new Point(CANONICAL_WIDTH, 0),                      // Top-Right
                new Point(0, CANONICAL_HEIGHT),                     // Bottom-Left
                new Point(CANONICAL_WIDTH, CANONICAL_HEIGHT)        // Bottom-Right
        );

        // Calculate the 3x3 perspective transform matrix
        Mat transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);

        // Apply the warp to produce a flat top-down view
        Mat warped = new Mat();
        Imgproc.warpPerspective(
                src,
                warped,
                transformMatrix,
                new Size(CANONICAL_WIDTH, CANONICAL_HEIGHT)
        );

        // Convert back to Bitmap
        Bitmap result = Bitmap.createBitmap(
                CANONICAL_WIDTH,
                CANONICAL_HEIGHT,
                Bitmap.Config.ARGB_8888
        );
        Utils.matToBitmap(warped, result);

        // Cleanup
        src.release();
        warped.release();
        transformMatrix.release();
        srcMat.release();
        dstMat.release();

        Log.d(TAG, "Perspective alignment successful -> " +
                CANONICAL_WIDTH + "x" + CANONICAL_HEIGHT);
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
