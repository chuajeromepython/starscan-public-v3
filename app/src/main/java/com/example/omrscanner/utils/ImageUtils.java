package com.example.omrscanner.utils;

import android.graphics.Bitmap;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class ImageUtils {

    /**
     * Create anchor template (black square)
     * @param size Template size in pixels
     * @return Mat of anchor template
     */
    public static Mat createAnchorTemplate(int size) {
        Mat template = new Mat(size, size, org.opencv.core.CvType.CV_8UC1, new Scalar(255));

        // Draw black square (75% of size, centered)
        int margin = (int) (size * 0.125);
        int squareSize = size - 2 * margin;

        Imgproc.rectangle(
                template,
                new Point(margin, margin),
                new Point(margin + squareSize, margin + squareSize),
                new Scalar(0),
                -1 // Fill
        );

        return template;
    }

    /**
     * Scale bitmap to max dimension
     */
    public static Bitmap scaleBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float scale = Math.min(
                (float) maxDimension / width,
                (float) maxDimension / height
        );

        if (scale >= 1) return bitmap;

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
}