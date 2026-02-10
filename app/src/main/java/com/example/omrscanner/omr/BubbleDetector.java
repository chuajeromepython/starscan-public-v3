package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BubbleDetector {

    private static final String TAG = "BubbleDetector";

    // Bubble detection parameters
    private static final double MIN_BUBBLE_AREA_RATIO = 0.00005;
    private static final double MAX_BUBBLE_AREA_RATIO = 0.002;
    private static final double BUBBLE_CIRCULARITY_THRESHOLD = 0.65;
    private static final double MIN_ASPECT_RATIO = 0.7;
    private static final double MAX_ASPECT_RATIO = 1.3;

    // Shading detection thresholds
    private static final double SHADING_THRESHOLD = 127;
    private static final double FILLED_RATIO_THRESHOLD = 0.35;

    /**
     * OMR Answer result
     */
    public static class OMRResult {
        public Map<Integer, Character> answers;
        public Bitmap annotatedImage;
        public int totalQuestions;
        public int answeredQuestions;

        public OMRResult() {
            answers = new HashMap<>();
        }
    }

    /**
     * Bubble information
     */
    private static class Bubble {
        Rect rect;
        Point center;
        double fillPercentage;
        int row;
        int col;
        boolean isShaded;

        Bubble(Rect rect) {
            this.rect = rect;
            this.center = new Point(
                    rect.x + rect.width / 2.0,
                    rect.y + rect.height / 2.0
            );
        }
    }

    /**
     * Main detection function
     */
    public static OMRResult detectBubbles(Bitmap alignedBitmap) {
        OMRResult result = new OMRResult();

        Mat src = new Mat();
        Utils.bitmapToMat(alignedBitmap, src);

        // Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // Apply bilateral filter to reduce noise while keeping edges
        Mat filtered = new Mat();
        Imgproc.bilateralFilter(gray, filtered, 9, 75, 75);

        // Use adaptive thresholding
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(
                filtered,
                thresh,
                255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                21,
                5
        );

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(
                thresh,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
        );

        Log.d(TAG, "Total contours found: " + contours.size());

        // Filter bubbles
        List<Bubble> bubbles = filterBubbles(contours, src.width(), src.height());
        Log.d(TAG, "Filtered bubbles: " + bubbles.size());

        // Detect shading
        for (Bubble bubble : bubbles) {
            detectShading(gray, bubble);
        }

        // Organize into grid
        organizeBubblesIntoGrid(bubbles);

        // Extract answers
        extractAnswers(bubbles, result);

        // Draw visualization
        result.annotatedImage = drawBubbles(src, bubbles);

        // Cleanup
        src.release();
        gray.release();
        filtered.release();
        thresh.release();
        hierarchy.release();

        return result;
    }

    /**
     * Filter contours to find bubbles
     */
    private static List<Bubble> filterBubbles(
            List<MatOfPoint> contours,
            int imgWidth,
            int imgHeight
    ) {
        List<Bubble> bubbles = new ArrayList<>();
        double imageArea = imgWidth * imgHeight;

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);

            // Area check
            double area = rect.area();
            double areaRatio = area / imageArea;

            if (areaRatio < MIN_BUBBLE_AREA_RATIO || areaRatio > MAX_BUBBLE_AREA_RATIO) {
                continue;
            }

            // Aspect ratio check
            double aspectRatio = (double) rect.width / rect.height;
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
                continue;
            }

            // Circularity check
            double perimeter = Imgproc.arcLength(
                    new org.opencv.core.MatOfPoint2f(contour.toArray()),
                    true
            );

            if (perimeter == 0) continue;

            double circularity = 4 * Math.PI * area / (perimeter * perimeter);

            if (circularity < BUBBLE_CIRCULARITY_THRESHOLD) {
                continue;
            }

            bubbles.add(new Bubble(rect));
        }

        return bubbles;
    }

    /**
     * Detect if bubble is shaded
     */
    private static void detectShading(Mat gray, Bubble bubble) {
        // Use center region of bubble to avoid circle edges
        int padding = Math.max(2, bubble.rect.width / 4);
        int x = Math.max(0, bubble.rect.x + padding);
        int y = Math.max(0, bubble.rect.y + padding);
        int width = Math.max(1, Math.min(bubble.rect.width - 2 * padding, gray.cols() - x));
        int height = Math.max(1, Math.min(bubble.rect.height - 2 * padding, gray.rows() - y));

        if (width <= 0 || height <= 0) {
            bubble.fillPercentage = 0;
            bubble.isShaded = false;
            return;
        }

        Rect innerRect = new Rect(x, y, width, height);
        Mat roi = gray.submat(innerRect);

        // Count dark pixels
        Mat threshROI = new Mat();
        Imgproc.threshold(roi, threshROI, SHADING_THRESHOLD, 255, Imgproc.THRESH_BINARY_INV);

        int darkPixels = Core.countNonZero(threshROI);
        int totalPixels = innerRect.width * innerRect.height;
        bubble.fillPercentage = (double) darkPixels / totalPixels;

        bubble.isShaded = bubble.fillPercentage >= FILLED_RATIO_THRESHOLD;

        threshROI.release();
        roi.release();
    }

    /**
     * Organize bubbles into grid
     */
    private static void organizeBubblesIntoGrid(List<Bubble> bubbles) {
        if (bubbles.isEmpty()) return;

        // Sort by Y
        Collections.sort(bubbles, Comparator.comparingDouble(b -> b.center.y));

        // Calculate median height
        List<Integer> heights = new ArrayList<>();
        for (Bubble b : bubbles) {
            heights.add(b.rect.height);
        }
        Collections.sort(heights);
        int medianHeight = heights.isEmpty() ? 20 : heights.get(heights.size() / 2);

        double rowTolerance = medianHeight * 0.7;

        // Group into rows
        List<List<Bubble>> rows = new ArrayList<>();
        List<Bubble> currentRow = new ArrayList<>();
        currentRow.add(bubbles.get(0));

        for (int i = 1; i < bubbles.size(); i++) {
            Bubble current = bubbles.get(i);
            Bubble previous = bubbles.get(i - 1);

            if (Math.abs(current.center.y - previous.center.y) < rowTolerance) {
                currentRow.add(current);
            } else {
                if (currentRow.size() >= 4) {
                    rows.add(new ArrayList<>(currentRow));
                }
                currentRow.clear();
                currentRow.add(current);
            }
        }
        if (currentRow.size() >= 4) {
            rows.add(currentRow);
        }

        // Assign row and column
        for (int r = 0; r < rows.size(); r++) {
            List<Bubble> row = rows.get(r);
            Collections.sort(row, Comparator.comparingDouble(b -> b.center.x));

            for (int c = 0; c < row.size(); c++) {
                row.get(c).row = r;
                row.get(c).col = c;
            }
        }

        Log.d(TAG, "Organized into " + rows.size() + " rows");
    }

    /**
     * Extract answers from shaded bubbles
     */
    private static void extractAnswers(List<Bubble> bubbles, OMRResult result) {
        Map<Integer, List<Bubble>> questionMap = new HashMap<>();

        for (Bubble bubble : bubbles) {
            if (!questionMap.containsKey(bubble.row)) {
                questionMap.put(bubble.row, new ArrayList<>());
            }
            questionMap.get(bubble.row).add(bubble);
        }

        result.totalQuestions = questionMap.size();

        for (Map.Entry<Integer, List<Bubble>> entry : questionMap.entrySet()) {
            int questionNum = entry.getKey() + 1;
            List<Bubble> choices = entry.getValue();

            // Find most filled bubble
            Bubble mostFilled = null;
            double maxFill = FILLED_RATIO_THRESHOLD;

            for (Bubble bubble : choices) {
                if (bubble.fillPercentage > maxFill) {
                    maxFill = bubble.fillPercentage;
                    mostFilled = bubble;
                }
            }

            if (mostFilled != null && mostFilled.col < 4) {
                char answer = (char) ('A' + mostFilled.col);
                result.answers.put(questionNum, answer);
                result.answeredQuestions++;

                Log.d(TAG, String.format("Q%d: %c (fill: %.1f%%)",
                        questionNum, answer,
                        mostFilled.fillPercentage * 100));
            }
        }
    }

    /**
     * Draw visualization
     */
    private static Bitmap drawBubbles(Mat src, List<Bubble> bubbles) {
        Mat output = src.clone();

        Scalar greenCircle = new Scalar(0, 255, 0);
        Scalar blueCircle = new Scalar(255, 100, 0);

        for (Bubble bubble : bubbles) {
            Scalar color = bubble.isShaded ? greenCircle : blueCircle;
            int thickness = bubble.isShaded ? 3 : 1;

            Imgproc.circle(
                    output,
                    bubble.center,
                    Math.max(bubble.rect.width, bubble.rect.height) / 2,
                    color,
                    thickness
            );

            if (bubble.isShaded && bubble.col < 4) {
                char answer = (char) ('A' + bubble.col);
                Imgproc.putText(
                        output,
                        String.valueOf(answer),
                        new Point(bubble.center.x - 8, bubble.center.y + 6),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        new Scalar(0, 255, 0),
                        2
                );
            }
        }

        Bitmap result = Bitmap.createBitmap(
                output.cols(),
                output.rows(),
                Bitmap.Config.ARGB_8888
        );
        Utils.matToBitmap(output, result);
        output.release();

        return result;
    }
}