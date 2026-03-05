package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.omrscanner.utils.ImageUtils;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the 4 corner anchor squares on a ZPH OMR sheet using
 * contour-based detection (the "Grid & Warp" method).
 *
 * Pipeline: Grayscale -> GaussianBlur -> AdaptiveThreshold ->
 *           MorphologicalClose -> FindContours -> approxPolyDP ->
 *           Filter (4-vertex, convex, square, solid, dark) -> Select 4 corners
 *
 * This replaces the previous template-matching approach, which was
 * fragile against lighting changes and paper distortion.
 */
public class AnchorDetector {

    private static final String TAG = "AnchorDetector";

    // --- Contour filter thresholds ---

    // Anchor area as ratio of total image area
    private static final double MIN_ANCHOR_AREA_RATIO = 0.0003;  // 0.03% of image
    private static final double MAX_ANCHOR_AREA_RATIO = 0.03;    // 3% of image

    // Aspect ratio range for a "square" (width / height)
    private static final double MIN_SQUARE_ASPECT = 0.6;
    private static final double MAX_SQUARE_ASPECT = 1.4;

    // Solidity: contour area / bounding-rect area (filled vs outlined)
    private static final double MIN_SOLIDITY = 0.65;

    // Maximum mean grayscale intensity for a "dark" filled square
    private static final double MAX_DARKNESS_MEAN = 150;

    // Minimum distance between corners as ratio of image's smaller dimension
    private static final double MIN_CORNER_DISTANCE_RATIO = 0.15;

    /**
     * Detect the 4 corner anchor squares using contour-based analysis.
     *
     * @param bitmap The captured/loaded image of the OMR sheet
     * @return Array of 4 Points [TopLeft, TopRight, BottomLeft, BottomRight], or null if detection fails
     */
    public static Point[] detectAnchors(Bitmap bitmap) {
        // Scale down for performance (process at max 1500px)
        Bitmap scaledBitmap = ImageUtils.scaleBitmap(bitmap, 1500);

        Mat src = new Mat();
        Utils.bitmapToMat(scaledBitmap, src);

        int imgWidth = src.cols();
        int imgHeight = src.rows();
        double imageArea = (double) imgWidth * imgHeight;

        Log.d(TAG, "Processing image: " + imgWidth + "x" + imgHeight);

        // ====== STEP 1: Grayscale ======
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // ====== STEP 2: Gaussian Blur (removes paper grain/noise) ======
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(7, 7), 0);

        // ====== STEP 3: Adaptive Threshold ======
        // BINARY_INV: dark ink (squares) becomes WHITE, paper becomes BLACK.
        // Adaptive handles shadows and uneven lighting across the paper.
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(
                blurred,
                thresh,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                15, // Block size (larger = less sensitive to local noise)
                4.0  // Constant subtracted from mean (higher = less false positives)
        );

        // ====== STEP 4: Morphological Close ======
        // Fills small white gaps INSIDE the black squares caused by
        // printer glare or paper texture ("hollow square" problem fix).
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
        Mat closed = new Mat();
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel);

        // ====== STEP 5: Find Contours ======
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(
                closed,
                contours,
                hierarchy,
                Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE
        );

        Log.d(TAG, "Total contours found: " + contours.size());

        // ====== STEP 6: Filter for filled square quadrilaterals ======
        List<Point> candidates = filterSquareContours(contours, gray, imageArea);

        Log.d(TAG, "Candidate anchor squares: " + candidates.size());

        // ====== STEP 7: Select 4 corner anchors ======
        Point[] corners = selectCornerAnchors(candidates, imgWidth, imgHeight);

        // Cleanup OpenCV Mats
        src.release();
        gray.release();
        blurred.release();
        thresh.release();
        kernel.release();
        closed.release();
        hierarchy.release();

        // Scale coordinates back to original image dimensions
        if (corners != null && scaledBitmap != bitmap) {
            float scaleX = (float) bitmap.getWidth() / scaledBitmap.getWidth();
            float scaleY = (float) bitmap.getHeight() / scaledBitmap.getHeight();
            for (Point p : corners) {
                p.x *= scaleX;
                p.y *= scaleY;
            }
        }

        return corners;
    }

    /**
     * Filter contours to find only filled black squares.
     * Applies: vertex count, area, aspect ratio, convexity, solidity, and darkness checks.
     */
    private static List<Point> filterSquareContours(
            List<MatOfPoint> contours,
            Mat gray,
            double imageArea
    ) {
        List<Point> candidates = new ArrayList<>();

        for (MatOfPoint contour : contours) {
            // --- Approximate polygon ---
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);

            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true);

            // Must have 4-6 vertices (real squares may get 5 or 6 due to rounded/chipped corners)
            if (approx.total() < 4 || approx.total() > 6) {
                contour2f.release();
                approx.release();
                continue;
            }

            // --- Area filter ---
            double area = Imgproc.contourArea(contour);
            double areaRatio = area / imageArea;

            if (areaRatio < MIN_ANCHOR_AREA_RATIO || areaRatio > MAX_ANCHOR_AREA_RATIO) {
                contour2f.release();
                approx.release();
                continue;
            }

            // --- Bounding rect and aspect ratio ---
            Rect bbox = Imgproc.boundingRect(contour);
            double aspect = (double) bbox.width / bbox.height;

            if (aspect < MIN_SQUARE_ASPECT || aspect > MAX_SQUARE_ASPECT) {
                contour2f.release();
                approx.release();
                continue;
            }

            // --- Convexity check ---
            MatOfPoint approxInt = new MatOfPoint(approx.toArray());
            boolean isConvex = Imgproc.isContourConvex(approxInt);
            approxInt.release();

            if (!isConvex) {
                contour2f.release();
                approx.release();
                continue;
            }

            // --- Solidity check ---
            // A filled square has contour area close to bounding rect area.
            // This distinguishes solid markers from outlined boxes or text.
            double solidity = area / bbox.area();

            if (solidity < MIN_SOLIDITY) {
                contour2f.release();
                approx.release();
                continue;
            }

            // --- Darkness check ---
            // Verify the region is actually dark (filled with black ink)
            // by checking mean intensity in the original grayscale image.
            int roiX = Math.max(0, bbox.x);
            int roiY = Math.max(0, bbox.y);
            int roiW = Math.min(bbox.width, gray.cols() - roiX);
            int roiH = Math.min(bbox.height, gray.rows() - roiY);

            if (roiW > 0 && roiH > 0) {
                Mat roi = gray.submat(new Rect(roiX, roiY, roiW, roiH));
                Scalar mean = Core.mean(roi);
                roi.release();

                if (mean.val[0] > MAX_DARKNESS_MEAN) {
                    contour2f.release();
                    approx.release();
                    continue;
                }
            }

            // ---- Passed all filters: this is a valid anchor candidate ----
            Point center = new Point(
                    bbox.x + bbox.width / 2.0,
                    bbox.y + bbox.height / 2.0
            );
            candidates.add(center);

            Log.d(TAG, String.format(
                    "Anchor candidate: center=(%.0f,%.0f) area=%.0f aspect=%.2f solidity=%.2f",
                    center.x, center.y, area, aspect, solidity));

            contour2f.release();
            approx.release();
        }

        return candidates;
    }

    // =====================================================================
    //  Corner Selection & Validation
    // =====================================================================

    /**
     * Select the 4 extreme corner points from a list of candidates.
     * Uses sum/difference heuristics:
     *   TL = min(x + y),  TR = max(x - y),
     *   BL = max(y - x),  BR = max(x + y)
     */
    private static Point[] selectCornerAnchors(
            List<Point> candidates,
            int imgWidth,
            int imgHeight
    ) {
        if (candidates.size() < 4) {
            Log.e(TAG, "Not enough anchor candidates: " + candidates.size());
            return null;
        }

        Point topLeft = findTopLeft(candidates);
        Point topRight = findTopRight(candidates);
        Point bottomLeft = findBottomLeft(candidates);
        Point bottomRight = findBottomRight(candidates);

        if (topLeft == null || topRight == null ||
                bottomLeft == null || bottomRight == null) {
            Log.e(TAG, "Failed to classify all 4 corners");
            return null;
        }

        // Validate minimum separation
        double minDimension = Math.min(imgWidth, imgHeight);
        double minDist = minDimension * MIN_CORNER_DISTANCE_RATIO;

        Point[] corners = {topLeft, topRight, bottomLeft, bottomRight};

        if (!validateCornerDistances(corners, minDist)) {
            Log.e(TAG, "Corners too close together - invalid detection");
            for (int i = 0; i < corners.length; i++) {
                Log.d(TAG, "  Corner " + i + ": (" + corners[i].x + ", " + corners[i].y + ")");
            }
            return null;
        }

        Log.d(TAG, "Successfully detected 4 corners:");
        String[] labels = {"TL", "TR", "BL", "BR"};
        for (int i = 0; i < corners.length; i++) {
            Log.d(TAG, "  " + labels[i] + ": (" + corners[i].x + ", " + corners[i].y + ")");
        }

        return corners;
    }

    private static boolean validateCornerDistances(Point[] corners, double minDist) {
        for (int i = 0; i < corners.length; i++) {
            for (int j = i + 1; j < corners.length; j++) {
                double dist = distance(corners[i], corners[j]);
                if (dist < minDist) {
                    Log.d(TAG, "Distance " + i + "<->" + j + ": " + dist
                            + " (min required: " + minDist + ")");
                    return false;
                }
            }
        }
        return true;
    }

    // =====================================================================
    //  Corner finders (sum / difference heuristics)
    // =====================================================================

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
        double maxDiff = -Double.MAX_VALUE;
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
        double maxDiff = -Double.MAX_VALUE;
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
        double maxSum = -Double.MAX_VALUE;
        for (Point p : points) {
            double sum = p.x + p.y;
            if (sum > maxSum) {
                maxSum = sum;
                result = p;
            }
        }
        return result;
    }

    private static double distance(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // =====================================================================
    //  Debug Visualization
    // =====================================================================

    /**
     * Draw detected anchors and the bounding quadrilateral on the image.
     * Shows green circles at corners, green lines connecting them,
     * and TL/TR/BL/BR labels.
     */
    public static Bitmap drawAnchors(Bitmap bitmap, Point[] anchors) {
        if (anchors == null || anchors.length != 4) return bitmap;

        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        Scalar green = new Scalar(0, 255, 0);
        String[] labels = {"TL", "TR", "BL", "BR"};

        // Draw the bounding quadrilateral (TL->TR->BR->BL->TL)
        int lineThickness = Math.max(2, src.cols() / 300);
        Imgproc.line(src, anchors[0], anchors[1], green, lineThickness); // TL -> TR
        Imgproc.line(src, anchors[1], anchors[3], green, lineThickness); // TR -> BR
        Imgproc.line(src, anchors[3], anchors[2], green, lineThickness); // BR -> BL
        Imgproc.line(src, anchors[2], anchors[0], green, lineThickness); // BL -> TL

        // Draw circles and labels at each corner
        int circleRadius = Math.max(10, src.cols() / 60);
        double fontScale = Math.max(0.8, src.cols() / 800.0);
        int fontThickness = Math.max(2, src.cols() / 500);

        for (int i = 0; i < anchors.length; i++) {
            Imgproc.circle(src, anchors[i], circleRadius, green, lineThickness + 1);
            Imgproc.putText(
                    src,
                    labels[i],
                    new Point(anchors[i].x + circleRadius + 5, anchors[i].y - circleRadius),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    fontScale,
                    green,
                    fontThickness
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
