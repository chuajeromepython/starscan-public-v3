package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class PerspectiveAligner {

    private static final String TAG = "PerspectiveAligner";

    // Target output width (height will be calculated based on actual aspect ratio)
    private static final int TARGET_WIDTH = 1200;

    /**
     * Apply perspective transformation to align OMR sheet
     * Uses ACTUAL dimensions from anchors to prevent stretching
     *
     * @param bitmap Original captured image
     * @param anchors 4 corner points [TopLeft, TopRight, BottomLeft, BottomRight]
     * @return Aligned/warped bitmap WITHOUT distortion
     */
    public static Bitmap alignPerspective(Bitmap bitmap, Point[] anchors) {
        if (anchors == null || anchors.length != 4) {
            Log.e(TAG, "Invalid anchors - need exactly 4 points");
            return null;
        }

        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        // Calculate ACTUAL dimensions from the detected anchors
        double topWidth = distance(anchors[0], anchors[1]);
        double bottomWidth = distance(anchors[2], anchors[3]);
        double leftHeight = distance(anchors[0], anchors[2]);
        double rightHeight = distance(anchors[1], anchors[3]);

        // Use maximum values to avoid cutting off content
        double actualWidth = Math.max(topWidth, bottomWidth);
        double actualHeight = Math.max(leftHeight, rightHeight);

        // Calculate aspect ratio from ACTUAL paper dimensions
        double aspectRatio = actualHeight / actualWidth;

        // Scale to target width while maintaining aspect ratio
        int outputWidth = TARGET_WIDTH;
        int outputHeight = (int) (TARGET_WIDTH * aspectRatio);

        Log.d(TAG, "Actual dimensions - Width: " + actualWidth + ", Height: " + actualHeight);
        Log.d(TAG, "Aspect ratio: " + aspectRatio);
        Log.d(TAG, "Output size: " + outputWidth + "x" + outputHeight);

        // Source points (detected anchors in original image)
        Point[] srcPoints = new Point[4];
        srcPoints[0] = anchors[0]; // Top-Left
        srcPoints[1] = anchors[1]; // Top-Right
        srcPoints[2] = anchors[2]; // Bottom-Left
        srcPoints[3] = anchors[3]; // Bottom-Right

        // Destination points (perfect rectangle with correct aspect ratio)
        Point[] dstPoints = new Point[4];
        dstPoints[0] = new Point(0, 0);                              // Top-Left
        dstPoints[1] = new Point(outputWidth - 1, 0);                // Top-Right
        dstPoints[2] = new Point(0, outputHeight - 1);               // Bottom-Left
        dstPoints[3] = new Point(outputWidth - 1, outputHeight - 1); // Bottom-Right

        // Convert to MatOfPoint2f
        MatOfPoint2f srcMat = new MatOfPoint2f(srcPoints);
        MatOfPoint2f dstMat = new MatOfPoint2f(dstPoints);

        // Calculate perspective transform matrix
        Mat transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);

        // Apply warp perspective
        Mat warped = new Mat();
        Imgproc.warpPerspective(
                src,
                warped,
                transformMatrix,
                new Size(outputWidth, outputHeight)
        );

        // Convert back to Bitmap
        Bitmap result = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
        );
        Utils.matToBitmap(warped, result);

        // Cleanup
        src.release();
        warped.release();
        transformMatrix.release();
        srcMat.release();
        dstMat.release();

        Log.d(TAG, "Perspective alignment successful - NO stretching!");
        return result;
    }

    /**
     * Calculate Euclidean distance between two points
     */
    private static double distance(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Validate if anchors form a valid quadrilateral
     */
    public static boolean validateAnchors(Point[] anchors) {
        if (anchors == null || anchors.length != 4) {
            return false;
        }

        // Check if points are not null
        for (Point p : anchors) {
            if (p == null) return false;
        }

        // Check if points form a convex quadrilateral
        // Top-Left should be top-left of all points
        if (anchors[0].x > anchors[1].x || anchors[0].y > anchors[2].y) {
            Log.w(TAG, "Anchor ordering may be incorrect");
            return false;
        }

        // Minimum area check (prevent degenerate cases)
        double area = calculateQuadrilateralArea(anchors);
        if (area < 10000) { // Arbitrary minimum
            Log.w(TAG, "Anchor area too small: " + area);
            return false;
        }

        return true;
    }

    /**
     * Calculate area of quadrilateral using Shoelace formula
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