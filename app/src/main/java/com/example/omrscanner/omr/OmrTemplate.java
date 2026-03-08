package com.example.omrscanner.omr;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Data model for a complete OMR sheet template (e.g. ZPH30, ZPH40, ZPH50, ZPH60).
 *
 * Parsed from JSON files stored in assets/templates/.
 * The width/height define the coordinate space in which all block positions are specified.
 * These may differ from the actual bitmap dimensions; use TemplateManager.generateScaledPointList()
 * to map coordinates to the target bitmap size.
 */
public class OmrTemplate {

    /** Unique template identifier, e.g. "ZPH30", "ZPH40", "ZPH50", "ZPH60" */
    @SerializedName("template_id")
    public String templateId;

    /** Template coordinate space width (pixels) */
    @SerializedName("width")
    public int width;

    /** Template coordinate space height (pixels) */
    @SerializedName("height")
    public int height;

    /** Ordered list of bubble-grid blocks on this sheet */
    @SerializedName("blocks")
    public List<OmrBlock> blocks;

    /**
     * Count the total number of question-answer bubbles (excluding LNR).
     */
    public int getQuestionCount() {
        int count = 0;
        for (OmrBlock block : blocks) {
            if (!block.label.equals("LNR")) {
                count += block.rows;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("OmrTemplate{id='%s', %dx%d, %d blocks, %d questions}",
                templateId, width, height, blocks.size(), getQuestionCount());
    }
}
