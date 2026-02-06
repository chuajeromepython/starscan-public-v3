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
    private static final double MIN_BUBBLE_AREA_RATIO = 0.00005; // 0.005% of image
    private static final double MAX_BUBBLE_AREA_RATIO = 0.001;   // 0.1% of image
    private static final double BUBBLE_CIRCULARITY_THRESHOLD = 0.7; // How round (0-1)

    // Shading detection threshold (0-255, lower = darker)
    private static final double SHADING_THRESHOLD = 127;
    private static final double FILLED_RATIO_THRESHOLD = 0.3; // 30% filled = shaded

    /**
     * OMR Answer result
     */
    public static class OMRResult {
        public Map<Integer, Character> answers; // Question number -> Answer (A/B/C/D)
        public Bitmap annotatedImage; // Image with highlighted bubbles
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
        int row; // Question number
        int col; // Choice index (0=A, 1=B, 2=C, 3=D)
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

        // Apply adaptive threshold (invert: bubbles = white, background = black)
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(
                gray,
                thresh,
                255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                15,
                3
        );

        // Find all contours (potential bubbles)
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

        // Filter circular contours (bubbles)
        List<Bubble> bubbles = filterBubbles(contours, src.width(), src.height());

        Log.d(TAG, "Filtered bubbles: " + bubbles.size());

        // Check which bubbles are shaded
        for (Bubble bubble : bubbles) {
            bubble.fillPercentage = calculateFillPercentage(gray, bubble.rect);
            bubble.isShaded = bubble.fillPercentage >= FILLED_RATIO_THRESHOLD;
        }

        // Organize bubbles into rows and columns
        organizeBubblesIntoGrid(bubbles);

        // Extract answers
        extractAnswers(bubbles, result);

        // Create annotated image
        result.annotatedImage = drawBubbles(src, bubbles);

        // Cleanup
        src.release();
        gray.release();
        thresh.release();
        hierarchy.release();

        return result;
    }

    /**
     * Filter contours to find circular bubbles
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

            // Check area
            double area = rect.area();
            double areaRatio = area / imageArea;

            if (areaRatio < MIN_BUBBLE_AREA_RATIO || areaRatio > MAX_BUBBLE_AREA_RATIO) {
                continue;
            }

            // Check circularity (aspect ratio close to 1)
            double aspectRatio = (double) rect.width / rect.height;
            if (aspectRatio < 0.7 || aspectRatio > 1.3) {
                continue;
            }

            // Calculate actual circularity
            double perimeter = Imgproc.arcLength(
                    new org.opencv.core.MatOfPoint2f(contour.toArray()),
                    true
            );
            double circularity = 4 * Math.PI * area / (perimeter * perimeter);

            if (circularity < BUBBLE_CIRCULARITY_THRESHOLD) {
                continue;
            }

            bubbles.add(new Bubble(rect));
        }

        return bubbles;
    }

    /**
     * Calculate how filled/shaded a bubble is (0.0 to 1.0)
     */
    private static double calculateFillPercentage(Mat gray, Rect bubbleRect) {
        Mat roi = gray.submat(bubbleRect);

        // Count dark pixels
        Mat threshROI = new Mat();
        Imgproc.threshold(roi, threshROI, SHADING_THRESHOLD, 255, Imgproc.THRESH_BINARY_INV);

        int darkPixels = Core.countNonZero(threshROI);
        int totalPixels = bubbleRect.width * bubbleRect.height;

        double fillRatio = (double) darkPixels / totalPixels;

        threshROI.release();
        roi.release();

        return fillRatio;
    }

    /**
     * Organize bubbles into grid (rows = questions, cols = choices)
     */
    private static void organizeBubblesIntoGrid(List<Bubble> bubbles) {
        if (bubbles.isEmpty()) return;

        // Sort by Y position (top to bottom)
        Collections.sort(bubbles, Comparator.comparingDouble(b -> b.center.y));

        // Group into rows (tolerance for slight misalignment)
        List<List<Bubble>> rows = new ArrayList<>();
        List<Bubble> currentRow = new ArrayList<>();
        currentRow.add(bubbles.get(0));

        double rowTolerance = bubbles.get(0).rect.height * 1.5;

        for (int i = 1; i < bubbles.size(); i++) {
            Bubble current = bubbles.get(i);
            Bubble previous = bubbles.get(i - 1);

            if (Math.abs(current.center.y - previous.center.y) < rowTolerance) {
                currentRow.add(current);
            } else {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
                currentRow.add(current);
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        // Assign row numbers
        for (int r = 0; r < rows.size(); r++) {
            List<Bubble> row = rows.get(r);

            // Sort row by X position (left to right)
            Collections.sort(row, Comparator.comparingDouble(b -> b.center.x));

            // Assign column indices
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

        // Group by question number
        for (Bubble bubble : bubbles) {
            if (!questionMap.containsKey(bubble.row)) {
                questionMap.put(bubble.row, new ArrayList<>());
            }
            questionMap.get(bubble.row).add(bubble);
        }

        result.totalQuestions = questionMap.size();

        // Find shaded bubble for each question
        for (Map.Entry<Integer, List<Bubble>> entry : questionMap.entrySet()) {
            int questionNum = entry.getKey() + 1; // 1-indexed
            List<Bubble> choices = entry.getValue();

            // Find most shaded bubble
            Bubble mostShaded = null;
            double maxFill = FILLED_RATIO_THRESHOLD;

            for (Bubble bubble : choices) {
                if (bubble.fillPercentage > maxFill) {
                    maxFill = bubble.fillPercentage;
                    mostShaded = bubble;
                }
            }

            if (mostShaded != null) {
                char answer = (char) ('A' + mostShaded.col);
                result.answers.put(questionNum, answer);
                result.answeredQuestions++;

                Log.d(TAG, "Q" + questionNum + ": " + answer +
                        " (fill: " + String.format("%.2f", mostShaded.fillPercentage * 100) + "%)");
            }
        }
    }

    /**
     * Draw visualization on image
     */
    private static Bitmap drawBubbles(Mat src, List<Bubble> bubbles) {
        Mat output = src.clone();

        Scalar greenCircle = new Scalar(0, 255, 0);   // Shaded bubbles
        Scalar blueCircle = new Scalar(255, 0, 0);    // Unshaded bubbles
        Scalar greenFill = new Scalar(0, 255, 0, 80); // Semi-transparent

        for (Bubble bubble : bubbles) {
            Scalar color = bubble.isShaded ? greenCircle : blueCircle;
            int thickness = bubble.isShaded ? 3 : 1;

            // Draw circle
            Imgproc.circle(
                    output,
                    bubble.center,
                    bubble.rect.width / 2,
                    color,
                    thickness
            );

            // Highlight shaded bubbles
            if (bubble.isShaded) {
                Imgproc.rectangle(
                        output,
                        bubble.rect.tl(),
                        bubble.rect.br(),
                        greenFill,
                        -1 // Fill
                );

                // Draw answer letter
                char answer = (char) ('A' + bubble.col);
                Imgproc.putText(
                        output,
                        String.valueOf(answer),
                        new Point(bubble.center.x - 15, bubble.center.y + 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.8,
                        new Scalar(255, 255, 255),
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