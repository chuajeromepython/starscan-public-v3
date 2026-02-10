package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.omrscanner.utils.ImageUtils;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AnchorDetector {

    private static final String TAG = "AnchorDetector";

    // Template matching threshold (0.0 to 1.0)
    private static final double MATCH_THRESHOLD = 0.75; // Lowered slightly for better detection

    // Template sizes to try (multi-scale)
    private static final int[] TEMPLATE_SIZES = {25, 30, 35, 40, 45, 50, 55, 60, 70}; // More sizes

    // Minimum distance between detected anchors
    private static final double MIN_ANCHOR_DISTANCE_RATIO = 0.15; // Adjusted for portrait

    /**
     * Detect anchors using template matching (better for printed marks)
     */
    public static Point[] detectAnchors(Bitmap bitmap) {
        // Scale down if too large, maintaining aspect ratio
        Bitmap scaledBitmap = ImageUtils.scaleBitmap(bitmap, 1500);

        Mat src = new Mat();
        Utils.bitmapToMat(scaledBitmap, src);

        // Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // Apply slight blur
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(3, 3), 0);

        // Detect using multiple template sizes
        List<Point> allMatches = new ArrayList<>();

        for (int templateSize : TEMPLATE_SIZES) {
            Mat template = ImageUtils.createAnchorTemplate(templateSize);
            List<Point> matches = matchTemplate(blurred, template, MATCH_THRESHOLD);
            allMatches.addAll(matches);
            template.release();
        }

        Log.d(TAG, "Total matches found: " + allMatches.size());
        Log.d(TAG, "Image dimensions: " + gray.width() + " x " + gray.height());

        // Calculate minimum distance based on smaller dimension (works for both portrait and landscape)
        double minDimension = Math.min(gray.width(), gray.height());
        double clusterDistance = minDimension * MIN_ANCHOR_DISTANCE_RATIO;

        Log.d(TAG, "Cluster distance: " + clusterDistance);

        // Cluster nearby matches (same anchor detected at different scales)
        List<Point> clusteredMatches = clusterMatches(allMatches, clusterDistance);

        Log.d(TAG, "After clustering: " + clusteredMatches.size());

        // Select 4 corner anchors
        Point[] corners = selectCornerAnchors(clusteredMatches, gray.width(), gray.height());

        // Cleanup
        src.release();
        gray.release();
        blurred.release();

        // Scale back to original coordinates if needed
        if (scaledBitmap != bitmap) {
            float scaleX = (float) bitmap.getWidth() / scaledBitmap.getWidth();
            float scaleY = (float) bitmap.getHeight() / scaledBitmap.getHeight();

            if (corners != null) {
                for (Point p : corners) {
                    p.x *= scaleX;
                    p.y *= scaleY;
                }
            }
        }

        return corners;
    }

    /**
     * Template matching
     */
    private static List<Point> matchTemplate(Mat image, Mat template, double threshold) {
        List<Point> matches = new ArrayList<>();

        if (template.cols() > image.cols() || template.rows() > image.rows()) {
            return matches; // Template too large
        }

        Mat result = new Mat();
        Imgproc.matchTemplate(image, template, result, Imgproc.TM_CCOEFF_NORMED);

        // Find all matches above threshold
        for (int y = 0; y < result.rows(); y++) {
            for (int x = 0; x < result.cols(); x++) {
                double matchValue = result.get(y, x)[0];

                if (matchValue >= threshold) {
                    // Center of matched region
                    Point center = new Point(
                            x + template.cols() / 2.0,
                            y + template.rows() / 2.0
                    );
                    matches.add(center);
                }
            }
        }

        result.release();
        return matches;
    }

    /**
     * Cluster nearby matches (merge duplicates from multi-scale detection)
     */
    private static List<Point> clusterMatches(List<Point> matches, double minDistance) {
        if (matches.isEmpty()) return matches;

        List<Point> clustered = new ArrayList<>();
        List<Boolean> used = new ArrayList<>(Collections.nCopies(matches.size(), false));

        for (int i = 0; i < matches.size(); i++) {
            if (used.get(i)) continue;

            Point current = matches.get(i);
            List<Point> cluster = new ArrayList<>();
            cluster.add(current);
            used.set(i, true);

            // Find nearby points
            for (int j = i + 1; j < matches.size(); j++) {
                if (used.get(j)) continue;

                if (distance(current, matches.get(j)) < minDistance) {
                    cluster.add(matches.get(j));
                    used.set(j, true);
                }
            }

            // Average of cluster = final point
            double avgX = 0, avgY = 0;
            for (Point p : cluster) {
                avgX += p.x;
                avgY += p.y;
            }
            clustered.add(new Point(avgX / cluster.size(), avgY / cluster.size()));
        }

        return clustered;
    }

    /**
     * Select 4 extreme corners
     */
    private static Point[] selectCornerAnchors(
            List<Point> candidates,
            int imgWidth,
            int imgHeight
    ) {
        if (candidates.size() < 4) {
            Log.e(TAG, "Not enough anchors: " + candidates.size());
            return null;
        }

        Point topLeft = findTopLeft(candidates);
        Point topRight = findTopRight(candidates);
        Point bottomLeft = findBottomLeft(candidates);
        Point bottomRight = findBottomRight(candidates);

        if (topLeft == null || topRight == null ||
                bottomLeft == null || bottomRight == null) {
            Log.e(TAG, "Failed to find all 4 corners");
            return null;
        }

        // Validate minimum distance - use smaller dimension for better portrait support
        double minDimension = Math.min(imgWidth, imgHeight);
        double minDist = minDimension * 0.25; // Reduced from 0.3 for better portrait detection
        Point[] corners = {topLeft, topRight, bottomLeft, bottomRight};

        if (!validateCornerDistances(corners, minDist)) {
            Log.e(TAG, "Corners too close - invalid detection");
            Log.d(TAG, "Min distance required: " + minDist);
            for (int i = 0; i < corners.length; i++) {
                Log.d(TAG, "Corner " + i + ": (" + corners[i].x + ", " + corners[i].y + ")");
            }
            return null;
        }

        Log.d(TAG, "✓ Successfully detected 4 corners");
        Log.d(TAG, "TopLeft: (" + topLeft.x + ", " + topLeft.y + ")");
        Log.d(TAG, "TopRight: (" + topRight.x + ", " + topRight.y + ")");
        Log.d(TAG, "BottomLeft: (" + bottomLeft.x + ", " + bottomLeft.y + ")");
        Log.d(TAG, "BottomRight: (" + bottomRight.x + ", " + bottomRight.y + ")");

        return corners;
    }

    private static boolean validateCornerDistances(Point[] corners, double minDist) {
        for (int i = 0; i < corners.length; i++) {
            for (int j = i + 1; j < corners.length; j++) {
                double dist = distance(corners[i], corners[j]);
                if (dist < minDist) {
                    Log.d(TAG, "Distance between corner " + i + " and " + j + ": " + dist + " (min: " + minDist + ")");
                    return false;
                }
            }
        }
        return true;
    }

    private static double distance(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ========== CORNER FINDING ==========

    private static Point findTopLeft(List<Point> points) {
        Point result = null;
        double minSum = Double.MAX_VALUE;
        for (Point p : points) {
            double sum = p.x + p.y;
            if (sum < minSum) {
                minSum = sum;
                result = p;
            }
        }
        return result;
    }

    private static Point findTopRight(List<Point> points) {
        Point result = null;
        double maxDiff = Double.MIN_VALUE;
        for (Point p : points) {
            double diff = p.x - p.y;
            if (diff > maxDiff) {
                maxDiff = diff;
                result = p;
            }
        }
        return result;
    }

    private static Point findBottomLeft(List<Point> points) {
        Point result = null;
        double maxDiff = Double.MIN_VALUE;
        for (Point p : points) {
            double diff = p.y - p.x;
            if (diff > maxDiff) {
                maxDiff = diff;
                result = p;
            }
        }
        return result;
    }

    private static Point findBottomRight(List<Point> points) {
        Point result = null;
        double maxSum = Double.MIN_VALUE;
        for (Point p : points) {
            double sum = p.x + p.y;
            if (sum > maxSum) {
                maxSum = sum;
                result = p;
            }
        }
        return result;
    }

    /**
     * Debug visualization
     */
    public static Bitmap drawAnchors(Bitmap bitmap, Point[] anchors) {
        if (anchors == null || anchors.length != 4) return bitmap;

        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        Scalar green = new Scalar(0, 255, 0);
        String[] labels = {"TL", "TR", "BL", "BR"};

        for (int i = 0; i < anchors.length; i++) {
            Imgproc.circle(src, anchors[i], 25, green, 5);
            Imgproc.putText(
                    src,
                    labels[i],
                    new Point(anchors[i].x + 30, anchors[i].y - 20),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.5,
                    green,
                    3
            );
        }

        Bitmap result = Bitmap.createBitmap(
                src.cols(),
                src.rows(),
                Bitmap.Config.ARGB_8888
        );
        Utils.matToBitmap(src, result);
        src.release();

        return result;
    }
}