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

    // Standard A4 aspect ratio (portrait)
    // Pwede mo i-adjust based sa actual OMR sheet size
    private static final double PAPER_ASPECT_RATIO = 297.0 / 210.0; // A4 height/width

    // Target output dimensions (pixels)
    private static final int OUTPUT_WIDTH = 1200;
    private static final int OUTPUT_HEIGHT = (int) (OUTPUT_WIDTH * PAPER_ASPECT_RATIO);

    /**
     * Apply perspective transformation to align OMR sheet
     *
     * @param bitmap Original captured image
     * @param anchors 4 corner points [TopLeft, TopRight, BottomLeft, BottomRight]
     * @return Aligned/warped bitmap in standard orientation
     */
    public static Bitmap alignPerspective(Bitmap bitmap, Point[] anchors) {
        if (anchors == null || anchors.length != 4) {
            Log.e(TAG, "Invalid anchors - need exactly 4 points");
            return null;
        }

        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        // Source points (detected anchors)
        Point[] srcPoints = new Point[4];
        srcPoints[0] = anchors[0]; // Top-Left
        srcPoints[1] = anchors[1]; // Top-Right
        srcPoints[2] = anchors[2]; // Bottom-Left
        srcPoints[3] = anchors[3]; // Bottom-Right

        // Destination points (perfect rectangle)
        Point[] dstPoints = new Point[4];
        dstPoints[0] = new Point(0, 0);                           // Top-Left
        dstPoints[1] = new Point(OUTPUT_WIDTH - 1, 0);            // Top-Right
        dstPoints[2] = new Point(0, OUTPUT_HEIGHT - 1);           // Bottom-Left
        dstPoints[3] = new Point(OUTPUT_WIDTH - 1, OUTPUT_HEIGHT - 1); // Bottom-Right

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
                new Size(OUTPUT_WIDTH, OUTPUT_HEIGHT)
        );

        // Convert back to Bitmap
        Bitmap result = Bitmap.createBitmap(
                OUTPUT_WIDTH,
                OUTPUT_HEIGHT,
                Bitmap.Config.ARGB_8888
        );
        Utils.matToBitmap(warped, result);

        // Cleanup
        src.release();
        warped.release();
        transformMatrix.release();
        srcMat.release();
        dstMat.release();

        Log.d(TAG, "Perspective alignment successful");
        return result;
    }

    /**
     * Advanced version: Auto-calculate output size based on actual paper dimensions
     */
    public static Bitmap alignPerspectiveAuto(Bitmap bitmap, Point[] anchors) {
        if (anchors == null || anchors.length != 4) {
            Log.e(TAG, "Invalid anchors - need exactly 4 points");
            return null;
        }

        // Calculate actual dimensions from anchors
        double widthTop = distance(anchors[0], anchors[1]);
        double widthBottom = distance(anchors[2], anchors[3]);
        double heightLeft = distance(anchors[0], anchors[2]);
        double heightRight = distance(anchors[1], anchors[3]);

        // Use average dimensions
        int outputWidth = (int) Math.max(widthTop, widthBottom);
        int outputHeight = (int) Math.max(heightLeft, heightRight);

        Log.d(TAG, "Auto-calculated size: " + outputWidth + "x" + outputHeight);

        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        // Source points
        MatOfPoint2f srcMat = new MatOfPoint2f(
                anchors[0],  // Top-Left
                anchors[1],  // Top-Right
                anchors[2],  // Bottom-Left
                anchors[3]   // Bottom-Right
        );

        // Destination points
        MatOfPoint2f dstMat = new MatOfPoint2f(
                new Point(0, 0),
                new Point(outputWidth - 1, 0),
                new Point(0, outputHeight - 1),
                new Point(outputWidth - 1, outputHeight - 1)
        );

        // Get transform matrix
        Mat transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);

        // Warp
        Mat warped = new Mat();
        Imgproc.warpPerspective(
                src,
                warped,
                transformMatrix,
                new Size(outputWidth, outputHeight)
        );

        // Convert to bitmap
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
     * (Optional quality check)
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