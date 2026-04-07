package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.example.omrscanner.utils.ImageUtils;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects the 4 corner anchor squares on an OMR sheet using contour analysis.
 * Live camera detection uses profile-specific thresholds so the same pipeline
 * can support both close handheld scans and fixed elevated mounts.
 */
public class AnchorDetector {

    private static final String TAG = "AnchorDetector";

    public enum LiveDetectionMode {
        HANDHELD,
        FIXED_MOUNT
    }

    private static final DetectionProfile STILL_PROFILE = new DetectionProfile(
            "still-base",
            1500,
            7,
            15,
            4.0,
            7,
            Imgproc.RETR_TREE,
            0.00005,
            0.08,
            0.6,
            1.4,
            0.65,
            150.0,
            0.45,
            0.98,
            0.15,
            0.0,
            0.0,
            0.0,
            false,
            1.0,
            true
    );

    private static final DetectionProfile LIVE_HANDHELD_PROFILE = new DetectionProfile(
            "live-handheld",
            960,
            5,
            11,
            3.0,
            5,
            Imgproc.RETR_EXTERNAL,
            0.0003,
            0.03,
            0.6,
            1.4,
            0.65,
            150.0,
            0.45,
            0.98,
            0.15,
            0.0,
            0.0,
            0.0,
            false,
            1.0,
            false
    );

    private static final DetectionProfile LIVE_FIXED_BASE_PROFILE = new DetectionProfile(
            "live-fixed-base",
            960,
            5,
            11,
            3.0,
            5,
            Imgproc.RETR_EXTERNAL,
            0.00005,
            0.08,
            0.6,
            1.4,
            0.72,
            140.0,
            0.50,
            0.98,
            0.04,
            0.18,
            0.08,
            0.08,
            false,
            1.0,
            false
    );

    private static final DetectionProfile LIVE_FIXED_FAR_PROFILE = new DetectionProfile(
            "live-fixed-far",
            1440,
            5,
            11,
            3.0,
            5,
            Imgproc.RETR_EXTERNAL,
            0.00003,
            0.08,
            0.7,
            1.3,
            0.78,
            125.0,
            0.55,
            0.98,
            0.04,
            0.18,
            0.08,
            0.08,
            true,
            1.5,
            false
    );

    /**
     * Detect anchors from a still bitmap. This path stays verbose and tolerant
     * because it is off the hot live-analysis path.
     */
    public static Point[] detectAnchors(Bitmap bitmap) {
        Bitmap scaledBitmap = ImageUtils.scaleBitmap(bitmap, STILL_PROFILE.targetDimension);
        Mat src = new Mat();
        Mat gray = new Mat();

        try {
            Utils.bitmapToMat(scaledBitmap, src);

            if (src.channels() == 4) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
            } else if (src.channels() == 3) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY);
            } else {
                gray = src.clone();
            }

            return detectWithProfile(gray, bitmap.getWidth(), bitmap.getHeight(), STILL_PROFILE);
        } finally {
            src.release();
            gray.release();
            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled()) {
                scaledBitmap.recycle();
            }
        }
    }

    public static Point[] detectAnchors(@NonNull ImageProxy imageProxy) {
        return detectAnchors(imageProxy, LiveDetectionMode.HANDHELD);
    }

    public static Point[] detectAnchors(@NonNull ImageProxy imageProxy, @NonNull LiveDetectionMode mode) {
        Mat gray = imageProxyToGrayMat(imageProxy);
        if (gray == null) {
            return null;
        }

        try {
            if (mode == LiveDetectionMode.FIXED_MOUNT) {
                Point[] fixedBase = detectWithProfile(
                        gray,
                        imageProxy.getWidth(),
                        imageProxy.getHeight(),
                        LIVE_FIXED_BASE_PROFILE
                );
                if (fixedBase != null) {
                    return fixedBase;
                }

                return detectWithProfile(
                        gray,
                        imageProxy.getWidth(),
                        imageProxy.getHeight(),
                        LIVE_FIXED_FAR_PROFILE
                );
            }

            return detectWithProfile(
                    gray,
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    LIVE_HANDHELD_PROFILE
            );
        } finally {
            gray.release();
        }
    }

    private static Point[] detectWithProfile(
            Mat grayInput,
            int originalWidth,
            int originalHeight,
            DetectionProfile profile
    ) {
        Mat processingGray = prepareGrayForDetection(grayInput, profile);
        Mat blurred = new Mat();
        Mat thresh = new Mat();
        Mat kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                new Size(profile.morphKernel, profile.morphKernel)
        );
        Mat closed = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();

        try {
            int imgWidth = processingGray.cols();
            int imgHeight = processingGray.rows();
            double imageArea = (double) imgWidth * imgHeight;

            if (profile.verboseLogging) {
                Log.d(TAG, "Processing " + profile.name + " image: " + imgWidth + "x" + imgHeight);
            }

            Imgproc.GaussianBlur(
                    processingGray,
                    blurred,
                    new Size(profile.blurKernel, profile.blurKernel),
                    0
            );
            Imgproc.adaptiveThreshold(
                    blurred,
                    thresh,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    profile.adaptiveBlockSize,
                    profile.adaptiveC
            );
            Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel);

            Imgproc.findContours(
                    closed,
                    contours,
                    hierarchy,
                    profile.contourRetrievalMode,
                    Imgproc.CHAIN_APPROX_SIMPLE
            );

            if (profile.verboseLogging) {
                Log.d(TAG, "Contours found for " + profile.name + ": " + contours.size());
            }

            List<Point> candidates = filterSquareContours(contours, processingGray, closed, imageArea, profile);
            if (profile.verboseLogging) {
                Log.d(TAG, "Anchor candidates for " + profile.name + ": " + candidates.size());
            }

            Point[] corners = selectCornerAnchors(candidates, imgWidth, imgHeight, profile);
            if (corners == null) {
                return null;
            }

            float scaleX = (float) originalWidth / imgWidth;
            float scaleY = (float) originalHeight / imgHeight;
            for (Point point : corners) {
                point.x *= scaleX;
                point.y *= scaleY;
            }
            return corners;
        } finally {
            for (MatOfPoint contour : contours) {
                contour.release();
            }
            if (processingGray != grayInput) {
                processingGray.release();
            }
            blurred.release();
            thresh.release();
            kernel.release();
            closed.release();
            hierarchy.release();
        }
    }

    private static List<Point> filterSquareContours(
            List<MatOfPoint> contours,
            Mat gray,
            Mat binary,
            double imageArea,
            DetectionProfile profile
    ) {
        List<Point> candidates = new ArrayList<>();

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            double areaRatio = area / imageArea;
            if (areaRatio < profile.minAnchorAreaRatio || areaRatio > profile.maxAnchorAreaRatio) {
                continue;
            }

            Rect bbox = Imgproc.boundingRect(contour);
            if (bbox.width <= 0 || bbox.height <= 0) {
                continue;
            }

            double aspect = (double) bbox.width / bbox.height;
            if (aspect < profile.minSquareAspect || aspect > profile.maxSquareAspect) {
                continue;
            }

            double solidity = area / bbox.area();
            if (solidity < profile.minSolidity) {
                continue;
            }

            Rect safeRect = clampRectToImage(bbox, gray.cols(), gray.rows());
            if (safeRect.width <= 0 || safeRect.height <= 0) {
                continue;
            }

            Mat grayRoi = gray.submat(safeRect);
            Scalar mean = Core.mean(grayRoi);
            grayRoi.release();
            if (mean.val[0] > profile.maxDarknessMean) {
                continue;
            }

            Mat binaryRoi = binary.submat(safeRect);
            double fillRatio = Core.countNonZero(binaryRoi) / safeRect.area();
            binaryRoi.release();
            if (fillRatio < profile.minFillRatio || fillRatio > profile.maxFillRatio) {
                continue;
            }

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true);

            if (approx.total() < 4 || approx.total() > 6) {
                contour2f.release();
                approx.release();
                continue;
            }

            MatOfPoint approxInt = new MatOfPoint(approx.toArray());
            boolean isConvex = Imgproc.isContourConvex(approxInt);
            approxInt.release();

            if (!isConvex) {
                contour2f.release();
                approx.release();
                continue;
            }

            Point center = new Point(
                    safeRect.x + safeRect.width / 2.0,
                    safeRect.y + safeRect.height / 2.0
            );
            candidates.add(center);

            if (profile.verboseLogging) {
                Log.d(TAG, String.format(
                        "%s candidate center=(%.0f,%.0f) area=%.0f aspect=%.2f solidity=%.2f fill=%.2f",
                        profile.name, center.x, center.y, area, aspect, solidity, fillRatio
                ));
            }

            contour2f.release();
            approx.release();
        }

        return candidates;
    }

    private static Point[] selectCornerAnchors(
            List<Point> candidates,
            int imgWidth,
            int imgHeight,
            DetectionProfile profile
    ) {
        if (candidates.size() < 4) {
            if (profile.verboseLogging) {
                Log.e(TAG, "Not enough anchor candidates for " + profile.name + ": " + candidates.size());
            }
            return null;
        }

        Point topLeft = findTopLeft(candidates);
        Point topRight = findTopRight(candidates);
        Point bottomLeft = findBottomLeft(candidates);
        Point bottomRight = findBottomRight(candidates);

        if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null) {
            if (profile.verboseLogging) {
                Log.e(TAG, "Failed to classify all 4 corners for " + profile.name);
            }
            return null;
        }

        Point[] corners = {topLeft, topRight, bottomLeft, bottomRight};
        if (!cornersAreDistinct(corners)) {
            if (profile.verboseLogging) {
                Log.e(TAG, "Corner classification produced duplicate points for " + profile.name);
            }
            return null;
        }

        if (!validateCornerLayout(corners, imgWidth, imgHeight, profile)) {
            if (profile.verboseLogging) {
                Log.e(TAG, "Corner layout rejected for " + profile.name);
            }
            return null;
        }

        if (profile.verboseLogging) {
            String[] labels = {"TL", "TR", "BL", "BR"};
            for (int i = 0; i < corners.length; i++) {
                Log.d(TAG, labels[i] + ": (" + corners[i].x + ", " + corners[i].y + ")");
            }
        }

        return corners;
    }

    private static boolean validateCornerLayout(
            Point[] corners,
            int imgWidth,
            int imgHeight,
            DetectionProfile profile
    ) {
        double frameMinDimension = Math.min(imgWidth, imgHeight);
        Rect spanRect = boundingRect(corners);

        if (profile.minCornerSpanWidthRatio > 0.0
                && (spanRect.width / (double) imgWidth) < profile.minCornerSpanWidthRatio) {
            return false;
        }
        if (profile.minCornerSpanHeightRatio > 0.0
                && (spanRect.height / (double) imgHeight) < profile.minCornerSpanHeightRatio) {
            return false;
        }

        double candidateSpanMinDimension = Math.min(spanRect.width, spanRect.height);
        double minDistance = frameMinDimension * profile.frameMinCornerDistanceRatio;
        if (profile.candidateMinCornerDistanceRatio > 0.0) {
            minDistance = Math.max(
                    minDistance,
                    candidateSpanMinDimension * profile.candidateMinCornerDistanceRatio
            );
        }

        return validateCornerDistances(corners, minDistance, profile.verboseLogging);
    }

    private static boolean cornersAreDistinct(Point[] corners) {
        Set<String> seen = new HashSet<>();
        for (Point corner : corners) {
            String key = Math.round(corner.x) + ":" + Math.round(corner.y);
            if (!seen.add(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateCornerDistances(Point[] corners, double minDist, boolean verboseLogging) {
        for (int i = 0; i < corners.length; i++) {
            for (int j = i + 1; j < corners.length; j++) {
                double dist = distance(corners[i], corners[j]);
                if (dist < minDist) {
                    if (verboseLogging) {
                        Log.d(TAG, "Distance " + i + "<->" + j + ": " + dist
                                + " (min required: " + minDist + ")");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private static Point findTopLeft(List<Point> points) {
        Point result = null;
        double minSum = Double.MAX_VALUE;
        for (Point point : points) {
            double sum = point.x + point.y;
            if (sum < minSum) {
                minSum = sum;
                result = point;
            }
        }
        return result;
    }

    private static Point findTopRight(List<Point> points) {
        Point result = null;
        double maxDiff = -Double.MAX_VALUE;
        for (Point point : points) {
            double diff = point.x - point.y;
            if (diff > maxDiff) {
                maxDiff = diff;
                result = point;
            }
        }
        return result;
    }

    private static Point findBottomLeft(List<Point> points) {
        Point result = null;
        double maxDiff = -Double.MAX_VALUE;
        for (Point point : points) {
            double diff = point.y - point.x;
            if (diff > maxDiff) {
                maxDiff = diff;
                result = point;
            }
        }
        return result;
    }

    private static Point findBottomRight(List<Point> points) {
        Point result = null;
        double maxSum = -Double.MAX_VALUE;
        for (Point point : points) {
            double sum = point.x + point.y;
            if (sum > maxSum) {
                maxSum = sum;
                result = point;
            }
        }
        return result;
    }

    private static double distance(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static Rect clampRectToImage(Rect rect, int width, int height) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int w = Math.min(rect.width, width - x);
        int h = Math.min(rect.height, height - y);
        return new Rect(x, y, Math.max(0, w), Math.max(0, h));
    }

    private static Rect boundingRect(Point[] points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        return new Rect(
                (int) Math.floor(minX),
                (int) Math.floor(minY),
                Math.max(1, (int) Math.ceil(maxX - minX)),
                Math.max(1, (int) Math.ceil(maxY - minY))
        );
    }

    private static Mat prepareGrayForDetection(Mat grayInput, DetectionProfile profile) {
        int width = grayInput.cols();
        int height = grayInput.rows();
        int largestDimension = Math.max(width, height);

        if (largestDimension <= 0) {
            return grayInput;
        }

        double scale = 1.0;
        if (largestDimension > profile.targetDimension) {
            scale = (double) profile.targetDimension / largestDimension;
        } else if (profile.allowUpscale && largestDimension < profile.targetDimension) {
            scale = Math.min((double) profile.targetDimension / largestDimension, profile.maxUpscaleFactor);
        }

        if (Math.abs(scale - 1.0) < 0.01) {
            return grayInput;
        }

        int resizedWidth = Math.max(1, (int) Math.round(width * scale));
        int resizedHeight = Math.max(1, (int) Math.round(height * scale));
        Mat resized = new Mat();
        int interpolation = scale > 1.0 ? Imgproc.INTER_CUBIC : Imgproc.INTER_AREA;
        Imgproc.resize(grayInput, resized, new Size(resizedWidth, resizedHeight), 0, 0, interpolation);
        return resized;
    }

    private static Mat imageProxyToGrayMat(@NonNull ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        if (planes.length == 0) {
            return null;
        }

        ImageProxy.PlaneProxy yPlane = planes[0];
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        int rowStride = yPlane.getRowStride();

        byte[] grayBytes = new byte[width * height];
        ByteBuffer buffer = yPlane.getBuffer().duplicate();
        buffer.rewind();

        if (rowStride == width && buffer.remaining() >= grayBytes.length) {
            buffer.get(grayBytes, 0, grayBytes.length);
        } else {
            for (int row = 0; row < height; row++) {
                int sourceIndex = row * rowStride;
                if (sourceIndex >= buffer.limit()) {
                    break;
                }
                buffer.position(sourceIndex);
                int destIndex = row * width;
                int length = Math.min(width, buffer.remaining());
                buffer.get(grayBytes, destIndex, length);
            }
        }

        Mat gray = new Mat(height, width, CvType.CV_8UC1);
        gray.put(0, 0, grayBytes);
        return gray;
    }

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

        int lineThickness = Math.max(2, src.cols() / 300);
        Imgproc.line(src, anchors[0], anchors[1], green, lineThickness);
        Imgproc.line(src, anchors[1], anchors[3], green, lineThickness);
        Imgproc.line(src, anchors[3], anchors[2], green, lineThickness);
        Imgproc.line(src, anchors[2], anchors[0], green, lineThickness);

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

        Bitmap result = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(src, result);
        src.release();

        return result;
    }

    private static final class DetectionProfile {
        final String name;
        final int targetDimension;
        final int blurKernel;
        final int adaptiveBlockSize;
        final double adaptiveC;
        final int morphKernel;
        final int contourRetrievalMode;
        final double minAnchorAreaRatio;
        final double maxAnchorAreaRatio;
        final double minSquareAspect;
        final double maxSquareAspect;
        final double minSolidity;
        final double maxDarknessMean;
        final double minFillRatio;
        final double maxFillRatio;
        final double frameMinCornerDistanceRatio;
        final double candidateMinCornerDistanceRatio;
        final double minCornerSpanWidthRatio;
        final double minCornerSpanHeightRatio;
        final boolean allowUpscale;
        final double maxUpscaleFactor;
        final boolean verboseLogging;

        DetectionProfile(
                String name,
                int targetDimension,
                int blurKernel,
                int adaptiveBlockSize,
                double adaptiveC,
                int morphKernel,
                int contourRetrievalMode,
                double minAnchorAreaRatio,
                double maxAnchorAreaRatio,
                double minSquareAspect,
                double maxSquareAspect,
                double minSolidity,
                double maxDarknessMean,
                double minFillRatio,
                double maxFillRatio,
                double frameMinCornerDistanceRatio,
                double candidateMinCornerDistanceRatio,
                double minCornerSpanWidthRatio,
                double minCornerSpanHeightRatio,
                boolean allowUpscale,
                double maxUpscaleFactor,
                boolean verboseLogging
        ) {
            this.name = name;
            this.targetDimension = targetDimension;
            this.blurKernel = blurKernel;
            this.adaptiveBlockSize = adaptiveBlockSize;
            this.adaptiveC = adaptiveC;
            this.morphKernel = morphKernel;
            this.contourRetrievalMode = contourRetrievalMode;
            this.minAnchorAreaRatio = minAnchorAreaRatio;
            this.maxAnchorAreaRatio = maxAnchorAreaRatio;
            this.minSquareAspect = minSquareAspect;
            this.maxSquareAspect = maxSquareAspect;
            this.minSolidity = minSolidity;
            this.maxDarknessMean = maxDarknessMean;
            this.minFillRatio = minFillRatio;
            this.maxFillRatio = maxFillRatio;
            this.frameMinCornerDistanceRatio = frameMinCornerDistanceRatio;
            this.candidateMinCornerDistanceRatio = candidateMinCornerDistanceRatio;
            this.minCornerSpanWidthRatio = minCornerSpanWidthRatio;
            this.minCornerSpanHeightRatio = minCornerSpanHeightRatio;
            this.allowUpscale = allowUpscale;
            this.maxUpscaleFactor = maxUpscaleFactor;
            this.verboseLogging = verboseLogging;
        }
    }
}
