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

    // Number of expected columns for answer choices (A, B, C, D)
    private static final int ANSWER_COLUMNS = 4;

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
        int questionNumber;
        int choice;
        boolean isShaded;

        Bubble(Rect rect) {
            this.rect = rect;
            this.center = new Point(
                    rect.x + rect.width / 2.0,
                    rect.y + rect.height / 2.0
            );
            this.questionNumber = -1;
            this.choice = -1;
        }
    }

    /**
     * Question group (one row of 4 bubbles for answers A, B, C, D)
     */
    private static class QuestionGroup {
        List<Bubble> bubbles;
        double centerY;
        double centerX;
        int questionNumber;

        QuestionGroup() {
            bubbles = new ArrayList<>();
            questionNumber = -1;
        }

        void calculateCenter() {
            if (bubbles.isEmpty()) return;
            double sumX = 0, sumY = 0;
            for (Bubble b : bubbles) {
                sumX += b.center.x;
                sumY += b.center.y;
            }
            centerX = sumX / bubbles.size();
            centerY = sumY / bubbles.size();
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

        // Group bubbles into questions (ONLY 4-column groups)
        List<QuestionGroup> questions = groupBubblesIntoQuestions(bubbles);
        Log.d(TAG, "Detected " + questions.size() + " answer questions (4-column groups only)");

        // Assign question numbers
        assignQuestionNumbers(questions);

        // Extract answers
        extractAnswers(questions, result);

        // Draw visualization
        result.annotatedImage = drawBubbles(src, questions);

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

        
            double area = rect.area();
            double areaRatio = area / imageArea;

            if (areaRatio < MIN_BUBBLE_AREA_RATIO || areaRatio > MAX_BUBBLE_AREA_RATIO) {
                continue;
            }

            double aspectRatio = (double) rect.width / rect.height;
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
                continue;
            }

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
     * Group bubbles into questions - ONLY groups with exactly 4 bubbles (A, B, C, D)
     * This excludes the LNR number section which has 12 columns
     */
    private static List<QuestionGroup> groupBubblesIntoQuestions(List<Bubble> bubbles) {
        if (bubbles.isEmpty()) return new ArrayList<>();

        // Calculate median bubble dimensions
        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (Bubble b : bubbles) {
            widths.add(b.rect.width);
            heights.add(b.rect.height);
        }
        Collections.sort(widths);
        Collections.sort(heights);
        int medianWidth = widths.get(widths.size() / 2);
        int medianHeight = heights.get(heights.size() / 2);

        // More relaxed tolerance for grouping bubbles
        double yTolerance = medianHeight * 0.8;      // More relaxed Y tolerance
        double minXSpacing = medianWidth * 0.3;      // Minimum spacing (not too close)
        double maxXSpacing = medianWidth * 3.5;      // Maximum spacing (not too far)

        Log.d(TAG, String.format("Median bubble: %dx%d, Y tolerance: %.1f, X spacing: %.1f-%.1f",
                medianWidth, medianHeight, yTolerance, minXSpacing, maxXSpacing));

        // Sort bubbles by Y then X
        List<Bubble> sortedBubbles = new ArrayList<>(bubbles);
        Collections.sort(sortedBubbles, (b1, b2) -> {
            int yCompare = Double.compare(b1.center.y, b2.center.y);
            if (yCompare != 0) return yCompare;
            return Double.compare(b1.center.x, b2.center.x);
        });

        // Group bubbles into rows first
        List<List<Bubble>> rows = new ArrayList<>();
        List<Bubble> currentRow = new ArrayList<>();

        for (Bubble bubble : sortedBubbles) {
            if (currentRow.isEmpty()) {
                currentRow.add(bubble);
            } else {
                Bubble lastInRow = currentRow.get(currentRow.size() - 1);
                double yDiff = Math.abs(bubble.center.y - lastInRow.center.y);

                if (yDiff < yTolerance) {
                    currentRow.add(bubble);
                } else {
                    if (!currentRow.isEmpty()) {
                        rows.add(new ArrayList<>(currentRow));
                    }
                    currentRow.clear();
                    currentRow.add(bubble);
                }
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        Log.d(TAG, "Grouped bubbles into " + rows.size() + " rows");

        // Now split each row into question groups of 4
        List<QuestionGroup> allGroups = new ArrayList<>();

        for (List<Bubble> row : rows) {
            // Sort row by X position
            Collections.sort(row, Comparator.comparingDouble(b -> b.center.x));

            List<QuestionGroup> rowGroups = splitRowIntoGroups(row, minXSpacing, maxXSpacing);
            allGroups.addAll(rowGroups);
        }

        Log.d(TAG, "Split into " + allGroups.size() + " total groups");

        // Filter to keep ONLY groups with exactly 4 bubbles
        List<QuestionGroup> answerQuestions = new ArrayList<>();

        for (QuestionGroup group : allGroups) {
            if (group.bubbles.size() == ANSWER_COLUMNS) {
                // Sort bubbles left to right and assign choices
                Collections.sort(group.bubbles, Comparator.comparingDouble(b -> b.center.x));
                for (int i = 0; i < group.bubbles.size(); i++) {
                    group.bubbles.get(i).choice = i;
                }
                group.calculateCenter();
                answerQuestions.add(group);

                Log.d(TAG, String.format("Valid 4-bubble group at Y=%.0f, X=%.0f-%.0f",
                        group.centerY,
                        group.bubbles.get(0).center.x,
                        group.bubbles.get(3).center.x));
            } else {
                Log.d(TAG, String.format("Filtered out %d-bubble group at Y=%.0f",
                        group.bubbles.size(),
                        group.bubbles.isEmpty() ? 0 : group.bubbles.get(0).center.y));
            }
        }

        Log.d(TAG, "Final: " + answerQuestions.size() + " valid 4-column answer questions");
        return answerQuestions;
    }

    /**
     * Split a row of bubbles into groups based on X-spacing gaps
     */
    private static List<QuestionGroup> splitRowIntoGroups(List<Bubble> row, double minSpacing, double maxSpacing) {
        List<QuestionGroup> groups = new ArrayList<>();
        if (row.isEmpty()) return groups;

        QuestionGroup currentGroup = new QuestionGroup();
        currentGroup.bubbles.add(row.get(0));

        for (int i = 1; i < row.size(); i++) {
            Bubble current = row.get(i);
            Bubble previous = row.get(i - 1);
            double spacing = current.center.x - previous.center.x;

            // If spacing is within normal range, add to current group
            if (spacing >= minSpacing && spacing <= maxSpacing) {
                currentGroup.bubbles.add(current);
            } else {
                // Large gap detected - start new group
                if (!currentGroup.bubbles.isEmpty()) {
                    groups.add(currentGroup);
                }
                currentGroup = new QuestionGroup();
                currentGroup.bubbles.add(current);
            }
        }

        // Don't forget last group
        if (!currentGroup.bubbles.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    /**
     * Assign question numbers based on position - numbering goes DOWN columns, not across rows
     */
    private static void assignQuestionNumbers(List<QuestionGroup> questions) {
        if (questions.isEmpty()) return;

        // First, sort all questions by Y position (top to bottom)
        Collections.sort(questions, Comparator.comparingDouble(q -> q.centerY));

        // Group questions into columns based on X position
        List<List<QuestionGroup>> columns = new ArrayList<>();

        for (QuestionGroup question : questions) {
            boolean addedToColumn = false;

            // Try to add to existing column
            for (List<QuestionGroup> column : columns) {
                if (!column.isEmpty()) {
                    QuestionGroup firstInColumn = column.get(0);
                    double xDiff = Math.abs(question.centerX - firstInColumn.centerX);

                    // If X positions are close (within reasonable range), same column
                    if (xDiff < 80) { // Adjust this threshold if needed
                        column.add(question);
                        addedToColumn = true;
                        break;
                    }
                }
            }

            // Create new column if not added
            if (!addedToColumn) {
                List<QuestionGroup> newColumn = new ArrayList<>();
                newColumn.add(question);
                columns.add(newColumn);
            }
        }

        // Sort columns by X position (left to right)
        Collections.sort(columns, (c1, c2) -> {
            if (c1.isEmpty() || c2.isEmpty()) return 0;
            return Double.compare(c1.get(0).centerX, c2.get(0).centerX);
        });

        // Sort each column by Y position (top to bottom)
        for (List<QuestionGroup> column : columns) {
            Collections.sort(column, Comparator.comparingDouble(q -> q.centerY));
        }

        // Assign question numbers: go down each column, then move to next column
        int questionNumber = 1;

        for (List<QuestionGroup> column : columns) {
            for (QuestionGroup question : column) {
                question.questionNumber = questionNumber;
                for (Bubble b : question.bubbles) {
                    b.questionNumber = questionNumber;
                }
                questionNumber++;
            }
        }

        // Log first few questions for debugging
        Log.d(TAG, String.format("Organized into %d columns", columns.size()));
        for (int col = 0; col < Math.min(columns.size(), 5); col++) {
            Log.d(TAG, String.format("Column %d has %d questions", col + 1, columns.get(col).size()));
        }

        for (int i = 0; i < Math.min(10, questions.size()); i++) {
            QuestionGroup q = questions.get(i);
            StringBuilder sb = new StringBuilder(String.format("Q%d at (%.0f, %.0f): ",
                    q.questionNumber, q.centerX, q.centerY));
            for (Bubble b : q.bubbles) {
                char letter = (char) ('A' + b.choice);
                sb.append(letter);
                if (b.isShaded) sb.append("*");
                sb.append(" ");
            }
            Log.d(TAG, sb.toString());
        }
    }

    /**
     * Extract answers from shaded bubbles
     */
    private static void extractAnswers(List<QuestionGroup> questions, OMRResult result) {
        result.totalQuestions = questions.size();

        for (QuestionGroup question : questions) {
            // Find most filled bubble
            Bubble mostFilled = null;
            double maxFill = FILLED_RATIO_THRESHOLD;

            for (Bubble bubble : question.bubbles) {
                if (bubble.fillPercentage > maxFill) {
                    maxFill = bubble.fillPercentage;
                    mostFilled = bubble;
                }
            }

            if (mostFilled != null && mostFilled.choice >= 0 && mostFilled.choice < ANSWER_COLUMNS) {
                char answer = (char) ('A' + mostFilled.choice);
                result.answers.put(question.questionNumber, answer);
                result.answeredQuestions++;

                Log.d(TAG, String.format("Q%d: %c (fill: %.1f%%)",
                        question.questionNumber, answer,
                        mostFilled.fillPercentage * 100));
            }
        }
    }

    /**
     * Draw visualization
     */
    private static Bitmap drawBubbles(Mat src, List<QuestionGroup> questions) {
        Mat output = src.clone();

        Scalar greenCircle = new Scalar(0, 255, 0);
        Scalar blueCircle = new Scalar(255, 100, 0);
        Scalar redCircle = new Scalar(0, 0, 255);

        for (QuestionGroup question : questions) {
            for (Bubble bubble : question.bubbles) {
                Scalar color;
                int thickness;

                if (bubble.isShaded && bubble.choice >= 0 && bubble.choice < ANSWER_COLUMNS) {
                    color = greenCircle;
                    thickness = 3;
                } else if (bubble.isShaded) {
                    color = redCircle; // Invalid shaded bubble
                    thickness = 2;
                } else {
                    color = blueCircle;
                    thickness = 1;
                }

                Imgproc.circle(
                        output,
                        bubble.center,
                        Math.max(bubble.rect.width, bubble.rect.height) / 2,
                        color,
                        thickness
                );

                // Draw letter on shaded bubbles
                if (bubble.isShaded && bubble.choice >= 0 && bubble.choice < ANSWER_COLUMNS) {
                    char answer = (char) ('A' + bubble.choice);
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

                // Draw question number near first bubble (choice A)
                if (bubble.choice == 0) {
                    Imgproc.putText(
                            output,
                            String.valueOf(bubble.questionNumber),
                            new Point(bubble.center.x - 20, bubble.center.y - 15),
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            0.4,
                            new Scalar(255, 0, 0),
                            1
                    );
                }
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