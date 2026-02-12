package com.example.omrscanner.omr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.google.gson.Gson;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages OMR sheet templates: loading from JSON, detecting sheet type via
 * alignment scoring, and generating scaled bubble-center coordinate lists.
 *
 * <h3>Usage</h3>
 * <pre>
 *   TemplateManager tm = new TemplateManager(context);
 *
 *   // After perspective-warping the sheet image:
 *   String type = tm.detectSheetType(warpedBitmap);   // "ZPH30", "ZPH50", or "ZPH60"
 *   OmrTemplate tpl = tm.getTemplate(type);
 *
 *   // Get bubble centers mapped to the bitmap's actual pixel size:
 *   List&lt;Point&gt; points = tm.generateScaledPointList(tpl, warpedBitmap.getWidth(), warpedBitmap.getHeight());
 * </pre>
 *
 * <h3>Alignment-Based Detection</h3>
 * Rather than probing individual pixels (which is fragile when the warp has
 * a slight shift), detection works by using {@link GridAligner} to
 * template-match the first question block of each candidate template against
 * the warped image.  The template with the highest match score wins.
 * <ul>
 *   <li><b>ZPH30</b> — Questions start high (y ~ 247).  On a ZPH50/60
 *       sheet this region is blank, so the match score will be very low.</li>
 *   <li><b>ZPH50 / ZPH60</b> — Questions start low (y ~ 650).  ZPH60 has
 *       a 6th column (start_x ~ 1409) that ZPH50 lacks, giving it a
 *       distinct match signature.</li>
 * </ul>
 */
public class TemplateManager {

    private static final String TAG = "TemplateManager";

    // ── Asset path ───────────────────────────────────────────────────────────
    private static final String TEMPLATE_DIR = "templates";

    // ── Template file names ──────────────────────────────────────────────────
    private static final String[] TEMPLATE_FILES = {"ZPH30.json", "ZPH50.json", "ZPH60.json"};

    // ── Alignment-based detection thresholds ───────────────────────────────
    /**
     * Minimum template-match score for a template to be considered a valid
     * candidate during sheet detection.  If no template exceeds this, the
     * detector falls back to "ZPH50".
     */
    private static final double DETECTION_MIN_SCORE = 0.35;

    // ── State ────────────────────────────────────────────────────────────────
    private final Map<String, OmrTemplate> templates = new HashMap<>();
    private final Gson gson = new Gson();

    // =====================================================================
    //  Construction
    // =====================================================================

    /**
     * Create and immediately load all template JSONs from assets/templates/.
     *
     * @param context Android context (used to open assets)
     */
    public TemplateManager(Context context) {
        loadTemplates(context);
    }

    // =====================================================================
    //  JSON loading
    // =====================================================================

    /**
     * Load every template JSON from the assets folder.
     */
    private void loadTemplates(Context context) {
        for (String fileName : TEMPLATE_FILES) {
            try {
                String path = TEMPLATE_DIR + "/" + fileName;
                InputStream is = context.getAssets().open(path);
                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                OmrTemplate template = gson.fromJson(reader, OmrTemplate.class);
                reader.close();

                if (template != null && template.templateId != null) {
                    templates.put(template.templateId, template);
                    Log.d(TAG, "Loaded template: " + template);
                } else {
                    Log.w(TAG, "Failed to parse template from " + fileName);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error loading template " + fileName, e);
            }
        }
        Log.d(TAG, "Total templates loaded: " + templates.size());
    }

    // =====================================================================
    //  Template access
    // =====================================================================

    /**
     * Retrieve a loaded template by ID.
     *
     * @param templateId e.g. "ZPH30"
     * @return the template, or null if not found
     */
    public OmrTemplate getTemplate(String templateId) {
        return templates.get(templateId);
    }

    /**
     * @return all loaded template IDs
     */
    public List<String> getAvailableTemplateIds() {
        return new ArrayList<>(templates.keySet());
    }

    // =====================================================================
    //  Geometric-probing sheet detection
    // =====================================================================

    /**
     * Detect which template the given warped bitmap matches by using
     * <b>alignment scoring</b> — template-matching every candidate's first
     * question block against the actual image.
     *
     * <p>The template whose first bubble grid scores the highest
     * {@link Imgproc#matchTemplate} confidence wins.  This is far more
     * robust than single-pixel probing because it tolerates the slight
     * translation / scale shifts that remain after perspective warping.</p>
     *
     * @param bitmap the perspective-warped sheet image (any resolution)
     * @return template ID string: "ZPH30", "ZPH50", or "ZPH60"
     */
    public String detectSheetType(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "detectSheetType: bitmap is null, defaulting to ZPH50");
            return "ZPH50";
        }

        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();

        // Convert to grayscale once — GridAligner works on CV_8UC1
        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        src.release();

        GridAligner aligner = new GridAligner();

        String bestId = "ZPH50";   // fallback
        double bestScore = -1.0;

        // Try every loaded template and keep the highest score
        for (Map.Entry<String, OmrTemplate> entry : templates.entrySet()) {
            OmrTemplate candidate = entry.getValue();
            double scaleX = (double) bw / candidate.width;
            double scaleY = (double) bh / candidate.height;

            double score = aligner.getAlignmentScore(gray, candidate, scaleX, scaleY);

            Log.d(TAG, String.format("Alignment score for %s: %.3f",
                    candidate.templateId, score));

            if (score > bestScore) {
                bestScore = score;
                bestId = candidate.templateId;
            }
        }

        aligner.release();
        gray.release();

        if (bestScore < DETECTION_MIN_SCORE) {
            Log.w(TAG, String.format(
                    "Best score %.3f < %.2f — detection uncertain, using %s",
                    bestScore, DETECTION_MIN_SCORE, bestId));
        }

        Log.i(TAG, String.format("Detected sheet type: %s (score=%.3f)", bestId, bestScore));
        return bestId;
    }

    // =====================================================================
    //  Coordinate generation
    // =====================================================================

    /**
     * Generate the flat list of every bubble-center coordinate for a template,
     * in the template's own pixel space (template.width x template.height).
     *
     * <p>Points are emitted block-by-block, row-by-row within each block,
     * column-by-column within each row.</p>
     *
     * @param template the parsed template
     * @return list of (x, y) points in template-space
     */
    public List<Point> generatePointList(OmrTemplate template) {
        List<Point> points = new ArrayList<>();

        for (OmrBlock block : template.blocks) {
            for (int row = 0; row < block.rows; row++) {
                for (int col = 0; col < block.cols; col++) {
                    int x = (int) Math.round(block.startX + col * block.dx);
                    int y = (int) Math.round(block.startY + row * block.dy);
                    points.add(new Point(x, y));
                }
            }
        }

        Log.d(TAG, "Generated " + points.size() + " points for " + template.templateId);
        return points;
    }

    /**
     * Generate bubble-center coordinates scaled from template-space to the
     * actual bitmap dimensions.
     *
     * <p>This accounts for the fact that the JSON coordinate space
     * (e.g. 1607x1131) may differ from the warped bitmap size
     * (e.g. 1000x1414 from PerspectiveAligner).</p>
     *
     * @param template     the parsed template
     * @param bitmapWidth  target bitmap width (pixels)
     * @param bitmapHeight target bitmap height (pixels)
     * @return list of (x, y) points in bitmap-space
     */
    public List<Point> generateScaledPointList(OmrTemplate template, int bitmapWidth, int bitmapHeight) {
        double scaleX = (double) bitmapWidth / template.width;
        double scaleY = (double) bitmapHeight / template.height;

        List<Point> points = new ArrayList<>();

        for (OmrBlock block : template.blocks) {
            for (int row = 0; row < block.rows; row++) {
                for (int col = 0; col < block.cols; col++) {
                    double rawX = block.startX + col * block.dx;
                    double rawY = block.startY + row * block.dy;
                    int px = (int) Math.round(rawX * scaleX);
                    int py = (int) Math.round(rawY * scaleY);
                    points.add(new Point(px, py));
                }
            }
        }

        Log.d(TAG, String.format("Generated %d scaled points for %s (scale %.3f x %.3f)",
                points.size(), template.templateId, scaleX, scaleY));
        return points;
    }

    /**
     * Generate scaled bubble-center coordinates grouped by block label.
     *
     * <p>Useful when you need to process question blocks and LNR separately.</p>
     *
     * @param template     the parsed template
     * @param bitmapWidth  target bitmap width (pixels)
     * @param bitmapHeight target bitmap height (pixels)
     * @return map of block label &rarr; list of scaled (x, y) points
     */
    public Map<String, List<Point>> generateGroupedPointList(
            OmrTemplate template, int bitmapWidth, int bitmapHeight) {

        double scaleX = (double) bitmapWidth / template.width;
        double scaleY = (double) bitmapHeight / template.height;

        Map<String, List<Point>> grouped = new HashMap<>();

        for (OmrBlock block : template.blocks) {
            List<Point> blockPoints = new ArrayList<>();

            for (int row = 0; row < block.rows; row++) {
                for (int col = 0; col < block.cols; col++) {
                    double rawX = block.startX + col * block.dx;
                    double rawY = block.startY + row * block.dy;
                    int px = (int) Math.round(rawX * scaleX);
                    int py = (int) Math.round(rawY * scaleY);
                    blockPoints.add(new Point(px, py));
                }
            }

            grouped.put(block.label, blockPoints);
            Log.d(TAG, String.format("Block '%s': %d points", block.label, blockPoints.size()));
        }

        return grouped;
    }

    /**
     * Get the bubble radius scaled to the target bitmap size.
     *
     * @param block        the block containing the radius
     * @param template     the template (for coordinate-space dimensions)
     * @param bitmapWidth  target bitmap width
     * @return scaled radius in bitmap-space pixels
     */
    public int getScaledRadius(OmrBlock block, OmrTemplate template, int bitmapWidth) {
        double scaleX = (double) bitmapWidth / template.width;
        return Math.max(1, (int) Math.round(block.radius * scaleX));
    }

    // =====================================================================
    //  Debug / visualisation helpers
    // =====================================================================

    /**
     * Draw all bubble-center coordinates on a bitmap for visual verification.
     * Each block is drawn in a different colour.
     *
     * @param bitmap       the warped sheet image
     * @param template     the matched template
     * @return a copy of the bitmap with coloured circles at every bubble position
     */
    public Bitmap drawTemplateOverlay(Bitmap bitmap, OmrTemplate template) {
        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);

        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        double scaleX = (double) bw / template.width;
        double scaleY = (double) bh / template.height;

        // Colours for up to 8 blocks
        Scalar[] colours = {
                new Scalar(0, 255, 0),      // green
                new Scalar(255, 0, 0),      // blue (BGR)
                new Scalar(0, 0, 255),      // red
                new Scalar(255, 255, 0),    // cyan
                new Scalar(0, 255, 255),    // yellow
                new Scalar(255, 0, 255),    // magenta
                new Scalar(128, 255, 0),    // lime
                new Scalar(0, 128, 255),    // orange
        };

        for (int b = 0; b < template.blocks.size(); b++) {
            OmrBlock block = template.blocks.get(b);
            Scalar colour = colours[b % colours.length];
            int scaledRadius = getScaledRadius(block, template, bw);

            for (int row = 0; row < block.rows; row++) {
                for (int col = 0; col < block.cols; col++) {
                    double rawX = block.startX + col * block.dx;
                    double rawY = block.startY + row * block.dy;
                    int px = (int) Math.round(rawX * scaleX);
                    int py = (int) Math.round(rawY * scaleY);

                    Imgproc.circle(src,
                            new org.opencv.core.Point(px, py),
                            scaledRadius, colour, 2);
                }
            }

            // Label the block
            double labelX = block.startX * scaleX;
            double labelY = block.startY * scaleY - 10;
            Imgproc.putText(src, block.label,
                    new org.opencv.core.Point(labelX, Math.max(15, labelY)),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, colour, 1);
        }

        Bitmap result = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(src, result);
        src.release();

        return result;
    }
}
