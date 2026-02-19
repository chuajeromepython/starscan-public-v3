package com.example.omrscanner.utils;

import android.graphics.Bitmap;

public class ImageUtils {

    /**
     * Scale bitmap to max dimension while maintaining aspect ratio.
     * Returns the original bitmap if it's already within the limit.
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
