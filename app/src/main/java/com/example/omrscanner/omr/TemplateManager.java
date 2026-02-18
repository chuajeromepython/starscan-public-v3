package com.example.omrscanner.omr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.google.gson.Gson;

import org.opencv.android.Utils;
import org.opencv.core.Core;
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
    //  Orientation detection & correction
    // =====================================================================

    /**
     * Bundles the result of {@link #detectAndOrient}: the correctly oriented
     * bitmap, the detected template ID, and the best alignment score.
     */
    public static class OrientationResult {
        /** The bitmap rotated to match the template's coordinate system. */
        public final Bitmap orientedBitmap;
        /** The detected template ID (e.g. "ZPH30"). */
        public final String templateId;
        /** The alignment score that won the detection. */
        public final double score;

        public OrientationResult(Bitmap orientedBitmap, String templateId, double score) {
            this.orientedBitmap = orientedBitmap;
            this.templateId = templateId;
            this.score = score;
        }
    }

    /**
     * Detect the correct orientation and sheet type in one pass.
     *
     * <p>The {@link PerspectiveAligner} always outputs a <b>portrait</b>
     * rectangle (1000×1414), but every template JSON defines coordinates in
     * <b>landscape</b> (width &gt; height, ~1609×1134).  When the sheet
     * content is landscape, the warped image needs a 90° rotation before the
     * template grid can be overlaid.</p>
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li>If the image is already landscape (w &gt; h), try it as-is.</li>
     *   <li>If the image is portrait (h &gt; w), try rotating 90° CW and
     *       90° CCW.</li>
     *   <li>For each candidate orientation, run alignment scoring against
     *       every template.</li>
     *   <li>The orientation + template pair with the highest score wins.</li>
     * </ol>
     *
     * @param warpedBitmap the perspective-aligned bitmap from
     *                     {@link PerspectiveAligner} (typically 1000×1414)
     * @return an {@link OrientationResult} with the correctly rotated bitmap,
     *         the detected template ID, and the winning score
     */
    public OrientationResult detectAndOrient(Bitmap warpedBitmap) {
        if (warpedBitmap == null) {
            Log.e(TAG, "detectAndOrient: bitmap is null");
            return new OrientationResult(warpedBitmap, "ZPH50", 0.0);
        }

        int w = warpedBitmap.getWidth();
        int h = warpedBitmap.getHeight();
        boolean isPortrait = h > w;

        Log.d(TAG, String.format("detectAndOrient: input %dx%d, portrait=%b", w, h, isPortrait));

        // ── Build candidate orientations ────────────────────────────────
        // Each candidate: Mat in grayscale + the rotation code used to
        // produce it (or -1 for "as-is").
        // Rotation codes: Core.ROTATE_90_CLOCKWISE,
        //                 Core.ROTATE_90_COUNTERCLOCKWISE

        Mat srcColour = new Mat();
        Utils.bitmapToMat(warpedBitmap, srcColour);
        Mat srcGray = new Mat();
        Imgproc.cvtColor(srcColour, srcGray, Imgproc.COLOR_BGR2GRAY);
        srcColour.release();

        // We'll test up to 3 orientations. For each one, track:
        //   - the grayscale Mat
        //   - the rotation code (-1 = none)
        //   - whether we need to release the Mat afterwards
        int[][] rotations;
        if (isPortrait) {
            // Portrait input → only try CW and CCW rotations
            rotations = new int[][] {
                { Core.ROTATE_90_CLOCKWISE },
                { Core.ROTATE_90_COUNTERCLOCKWISE }
            };
        } else {
            // Already landscape → try as-is first, but also try rotations
            // in case the aligner warped oddly
            rotations = new int[][] {
                { -1 },  // as-is
                { Core.ROTATE_90_CLOCKWISE },
                { Core.ROTATE_90_COUNTERCLOCKWISE }
            };
        }

        String bestTemplateId = "ZPH50";
        double bestScore = -1.0;
        int bestRotation = -1;  // -1 = no rotation

        GridAligner aligner = new GridAligner();

        for (int[] rot : rotations) {
            int rotCode = rot[0];
            Mat candidate;
            if (rotCode == -1) {
                candidate = srcGray; // use directly, don't release
            } else {
                candidate = new Mat();
                Core.rotate(srcGray, candidate, rotCode);
            }

            int cw = candidate.cols();
            int ch = candidate.rows();

            // Score each template against this orientation
            for (Map.Entry<String, OmrTemplate> entry : templates.entrySet()) {
                OmrTemplate tpl = entry.getValue();
                double scaleX = (double) cw / tpl.width;
                double scaleY = (double) ch / tpl.height;

                double score = aligner.getAlignmentScore(candidate, tpl, scaleX, scaleY);

                Log.d(TAG, String.format(
                        "  rot=%d, template=%s, score=%.3f (img %dx%d → tpl %dx%d)",
                        rotCode, tpl.templateId, score, cw, ch, tpl.width, tpl.height));

                if (score > bestScore) {
                    bestScore = score;
                    bestTemplateId = tpl.templateId;
                    bestRotation = rotCode;
                }
            }

            // Release rotated Mat (but not the original srcGray)
            if (rotCode != -1) {
                candidate.release();
            }
        }

        aligner.release();
        srcGray.release();

        Log.i(TAG, String.format(
                "detectAndOrient: winner template=%s rot=%d score=%.3f",
                bestTemplateId, bestRotation, bestScore));

        // ── Produce the correctly-oriented bitmap ───────────────────────
        Bitmap orientedBitmap;
        if (bestRotation == -1) {
            // No rotation needed — use the input as-is
            orientedBitmap = warpedBitmap;
        } else {
            // Rotate the full-colour bitmap via OpenCV
            Mat colourSrc = new Mat();
            Utils.bitmapToMat(warpedBitmap, colourSrc);

            Mat rotated = new Mat();
            Core.rotate(colourSrc, rotated, bestRotation);
            colourSrc.release();

            orientedBitmap = Bitmap.createBitmap(
                    rotated.cols(), rotated.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rotated, orientedBitmap);
            rotated.release();

            Log.d(TAG, String.format("Rotated bitmap to %dx%d",
                    orientedBitmap.getWidth(), orientedBitmap.getHeight()));
        }

        return new OrientationResult(orientedBitmap, bestTemplateId, bestScore);
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
