package com.example.omrscanner.omr;

import com.google.gson.annotations.SerializedName;

/**
 * Data model for a single block on an OMR template.
 *
 * A block is a rectangular grid of bubbles (e.g. "Questions_1_10" or "LNR").
 * Each bubble center is computed by interpolation:
 *   x = start_x + (col_index * dx)
 *   y = start_y + (row_index * dy)
 *
 * Coordinates are in the template's own pixel space (template.width x template.height).
 */
public class OmrBlock {

    /** Human-readable label, e.g. "Questions_1_10", "LNR" */
    @SerializedName("label")
    public String label;

    /** Number of rows (questions per column of this block) */
    @SerializedName("rows")
    public int rows;

    /** Number of columns (choices per question: 4 for ABCD, 12 for LNR digits) */
    @SerializedName("cols")
    public int cols;

    /** X coordinate of the first bubble center (top-left of grid) */
    @SerializedName("start_x")
    public double startX;

    /** Y coordinate of the first bubble center (top-left of grid) */
    @SerializedName("start_y")
    public double startY;

    /** Horizontal spacing between adjacent bubble centers */
    @SerializedName("dx")
    public double dx;

    /** Vertical spacing between adjacent bubble centers */
    @SerializedName("dy")
    public double dy;

    /** Bubble radius in template-space pixels */
    @SerializedName("radius")
    public int radius;

    @Override
    public String toString() {
        return String.format("OmrBlock{label='%s', %dx%d, origin=(%.1f,%.1f), d=(%.2f,%.2f), r=%d}",
                label, rows, cols, startX, startY, dx, dy, radius);
    }
}
