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
 *   String type = tm.detectSheetType(warpedBitmap);   // "ZPH30", "ZPH40", "ZPH50", or "ZPH60"
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
 *   <li><b>ZPH30 / ZPH40</b> — Questions start high (y ~ 247). On a ZPH50/60
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
    private static final String[] TEMPLATE_FILES = {
            "ZPH30.json", "ZPH40.json", "ZPH50.json", "ZPH60.json"
    };

    // ── Alignment-based detection thresholds ───────────────────────────────
    /**
     * Minimum template-match score for a template to be considered a valid
     * candidate during sheet detection.  If no template exceeds this, the
     * detector falls back to "ZPH50".
     */
    private static final double DETECTION_MIN_SCORE = 0.35;
    /**
     * If two orientation candidates differ by less than this score, treat
     * them as equivalent and apply deterministic tie-breaking.
     */
    private static final double ROTATION_TIE_MARGIN = 0.015;
    /**
     * Preferred orientation when CW and CCW are effectively tied.
     */
    private static final int PREFERRED_90_ROTATION = Core.ROTATE_90_COUNTERCLOCKWISE;

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
     * Builds a scan-ready copy of a real template (ZPH40/ZPH60) trimmed to
     * only the first {@code itemCount} questions. Trailing question blocks
     * are dropped entirely, and the boundary block is shortened, so the
     * scanner never measures bubbles the assessment doesn't use. The LNR
     * block is always kept whole. templateId is preserved from the base
     * template so LNR-profile lookups (e.g. ZPH60's special read profile)
     * still apply correctly.
     */
    public OmrTemplate buildTruncatedTemplate(String baseTemplateId, int itemCount) {
        OmrTemplate base = getTemplate(baseTemplateId);
        if (base == null) {
            Log.e(TAG, "buildTruncatedTemplate: unknown base template " + baseTemplateId);
            return null;
        }
        if (itemCount <= 0 || itemCount >= base.getQuestionCount()) {
            return base; // nothing to trim
        }

        OmrTemplate truncated = new OmrTemplate();
        truncated.templateId = base.templateId;
        truncated.width = base.width;
        truncated.height = base.height;
        truncated.blocks = new java.util.ArrayList<>();

        int remaining = itemCount;
        for (OmrBlock block : base.blocks) {
            if (block.label.equals("LNR")) continue;
            if (remaining <= 0) break;
            if (remaining >= block.rows) {
                truncated.blocks.add(block);
                remaining -= block.rows;
            } else {
                OmrBlock partial = new OmrBlock();
                partial.label = block.label;
                partial.rows = remaining;
                partial.cols = block.cols;
                partial.startX = block.startX;
                partial.startY = block.startY;
                partial.dx = block.dx;
                partial.dy = block.dy;
                partial.radius = block.radius;
                truncated.blocks.add(partial);
                remaining = 0;
            }
        }
        for (OmrBlock block : base.blocks) {
            if (block.label.equals("LNR")) {
                truncated.blocks.add(block);
                break;
            }
        }

        Log.i(TAG, String.format("Truncated %s -> %d items (%d question blocks + LNR)",
                baseTemplateId, itemCount, truncated.blocks.size() - 1));
        return truncated;
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
     * @return template ID string: "ZPH30", "ZPH40", "ZPH50", or "ZPH60"
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
     * Per-rotation best candidate (template + score) used for final orientation
     * selection and deterministic tie-breaking.
     */
    private static class RotationCandidate {
        final int rotationCode;
        final String templateId;
        final double score;

        RotationCandidate(int rotationCode, String templateId, double score) {
            this.rotationCode = rotationCode;
            this.templateId = templateId;
            this.score = score;
        }
    }

    /**
     * Bundles orientation winner metadata so callers can log decisions.
     */
    private static class RotationDecision {
        final RotationCandidate winner;
        final RotationCandidate runnerUp;
        final boolean tieBreakApplied;
        final double scoreGap;

        RotationDecision(
                RotationCandidate winner,
                RotationCandidate runnerUp,
                boolean tieBreakApplied,
                double scoreGap) {
            this.winner = winner;
            this.runnerUp = runnerUp;
            this.tieBreakApplied = tieBreakApplied;
            this.scoreGap = scoreGap;
        }
    }

    /**
     * Pick the best rotation candidate, applying deterministic CW/CCW
     * tie-breaking when scores are very close.
     */
    private RotationDecision chooseRotationCandidate(List<RotationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new RotationDecision(new RotationCandidate(-1, "ZPH50", 0.0), null, false, 0.0);
        }

        RotationCandidate best = null;
        RotationCandidate second = null;
        for (RotationCandidate candidate : candidates) {
            if (best == null || candidate.score > best.score) {
                second = best;
                best = candidate;
            } else if (second == null || candidate.score > second.score) {
                second = candidate;
            }
        }

        double scoreGap = (second == null) ? Double.MAX_VALUE : Math.abs(best.score - second.score);
        boolean tieBreakApplied = false;
        RotationCandidate winner = best;

        if (second != null
                && scoreGap <= ROTATION_TIE_MARGIN
                && isOppositeQuarterTurns(best.rotationCode, second.rotationCode)) {
            if (best.rotationCode != PREFERRED_90_ROTATION
                    && second.rotationCode == PREFERRED_90_ROTATION) {
                winner = second;
            }
            tieBreakApplied = true;
        }

        return new RotationDecision(winner, second, tieBreakApplied, scoreGap);
    }

    /**
     * Fraction of the page height, from the top, that should be blank on this
     * specific template. Derived from the template's topmost block (min
     * start_y), not hardcoded -- ZPH30/40 keep LNR beside the questions (blank
     * area ~20% of height), while ZPH50/60 place LNR near the top instead
     * (blank area only ~5-8% of height, just above the LNR block).
     */
    private double computeHeaderFraction(OmrTemplate tpl) {
        if (tpl.blocks == null || tpl.blocks.isEmpty() || tpl.height <= 0) {
            return 0.20;
        }

        double minStartY = Double.MAX_VALUE;
        for (OmrBlock block : tpl.blocks) {
            if (block.startY < minStartY) {
                minStartY = block.startY;
            }
        }

        // Leave a small margin so we don't clip into the first block itself.
        double fraction = (minStartY / tpl.height) * 0.85;
        return Math.max(0.05, Math.min(0.35, fraction));
    }

    /**
     * Returns a [0,1] score for how strongly this candidate's ink distribution
     * matches "sparse header, dense body" for the given header fraction.
     * Unlike matching a specific small bubble pattern -- which can't reliably
     * tell one orientation from its 180-degree opposite on a repetitive dot
     * grid -- overall ink density between the header band and the body is
     * dramatically different, and it flips when the rotation is wrong, making
     * it a much stronger orientation cue.
     */
    private double computeHeaderSparsityScore(Mat grayCandidate, double headerFraction) {
        int h = grayCandidate.rows();
        int w = grayCandidate.cols();
        int headerH = (int) Math.round(h * headerFraction);
        if (headerH <= 0 || headerH >= h) {
            return 0.5;
        }

        Mat header = grayCandidate.submat(0, headerH, 0, w);
        Mat body = grayCandidate.submat(headerH, h, 0, w);

        double headerInk = darkPixelRatio(header);
        double bodyInk = darkPixelRatio(body);

        header.release();
        body.release();

        // Positive when body is denser than header (expected). Map into [0,1].
        double diff = bodyInk - headerInk;
        return Math.max(0.0, Math.min(1.0, 0.5 + diff * 2.0));
    }

    private double darkPixelRatio(Mat gray) {
        Mat binary = new Mat();
        Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        int dark = Core.countNonZero(binary);
        int total = Math.max(1, binary.rows() * binary.cols());
        binary.release();
        return (double) dark / total;
    }

    private static boolean isOppositeQuarterTurns(int a, int b) {
        return (a == Core.ROTATE_90_CLOCKWISE && b == Core.ROTATE_90_COUNTERCLOCKWISE)
                || (a == Core.ROTATE_90_COUNTERCLOCKWISE && b == Core.ROTATE_90_CLOCKWISE);
    }

    /**
     * Human-readable name for a rotation code, for logging only.
     */
    private static String rotationName(int rotCode) {
        switch (rotCode) {
            case -1:
                return "NONE (as-captured)";
            case Core.ROTATE_90_CLOCKWISE:
                return "90° CLOCKWISE";
            case Core.ROTATE_90_COUNTERCLOCKWISE:
                return "90° COUNTER-CLOCKWISE";
            case Core.ROTATE_180:
                return "180°";
            default:
                return "UNKNOWN(" + rotCode + ")";
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
            // Portrait input from PerspectiveAligner is the normal scan path.
            // Sheet content is landscape per the template coordinate system,
            // so the warped output needs a quarter-turn — but WHICH quarter
            // turn depends on which way the phone was physically tilted at
            // capture time (AnchorDetector's still-image corner classification
            // is purely geometric, so the warp's "handedness" follows capture
            // tilt direction). Blindly forcing a single fixed direction here
            // only produces a correct result for one tilt direction and
            // flips the result upside-down/mirrored for the other — try
            // both and let the alignment score (with the tie-break bias
            // below) pick the one that actually matches the grid.
            rotations = new int[][] {
                    { Core.ROTATE_90_CLOCKWISE },
                    { Core.ROTATE_90_COUNTERCLOCKWISE }
            };
        } else {
            // Already landscape → try as-is first, but also try 180 rotation
            // in case the paper is simply upside down. Also try 90 degree
            // rotations just in case the aligner output is weird.
            rotations = new int[][] {
                    { -1 },  // as-is
                    { Core.ROTATE_180 },
                    { Core.ROTATE_90_CLOCKWISE },
                    { Core.ROTATE_90_COUNTERCLOCKWISE }
            };
        }

        List<RotationCandidate> rotationCandidates = new ArrayList<>();

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

            String rotationBestTemplateId = "ZPH50";
            double rotationBestScore = -1.0;

            // Score each template against this orientation
            for (Map.Entry<String, OmrTemplate> entry : templates.entrySet()) {
                OmrTemplate tpl = entry.getValue();
                double scaleX = (double) cw / tpl.width;
                double scaleY = (double) ch / tpl.height;

                double bubbleScore = aligner.getAlignmentScore(candidate, tpl, scaleX, scaleY);

                double headerFraction = computeHeaderFraction(tpl);
                double headerScore = computeHeaderSparsityScore(candidate, headerFraction);

                double combinedScore = (bubbleScore * 0.3) + (headerScore * 0.7);

                Log.d(TAG, String.format(
                        "  rot=%d, template=%s, bubble=%.3f header=%.3f(frac=%.3f) combined=%.3f (img %dx%d → tpl %dx%d)",
                        rotCode, tpl.templateId, bubbleScore, headerScore, headerFraction, combinedScore,
                        cw, ch, tpl.width, tpl.height));

                if (combinedScore > rotationBestScore) {
                    rotationBestScore = combinedScore;
                    rotationBestTemplateId = tpl.templateId;
                }
            }

            rotationCandidates.add(new RotationCandidate(rotCode, rotationBestTemplateId, rotationBestScore));

            // Release rotated Mat (but not the original srcGray)
            if (rotCode != -1) {
                candidate.release();
            }
        }

        aligner.release();
        srcGray.release();

        RotationDecision decision = chooseRotationCandidate(rotationCandidates);
        String bestTemplateId = decision.winner.templateId;
        int bestRotation = decision.winner.rotationCode;
        double bestScore = decision.winner.score;

        if (decision.runnerUp != null) {
            Log.i(TAG, String.format(
                    "detectAndOrient: top1 rot=%d template=%s score=%.3f | top2 rot=%d template=%s score=%.3f | gap=%.4f | tieBreak=%b",
                    decision.winner.rotationCode, decision.winner.templateId, decision.winner.score,
                    decision.runnerUp.rotationCode, decision.runnerUp.templateId, decision.runnerUp.score,
                    decision.scoreGap, decision.tieBreakApplied));
        }

        Log.i(TAG, String.format(
                "detectAndOrient: winner template=%s rot=%d score=%.3f",
                bestTemplateId, bestRotation, bestScore));

        Log.i(TAG, "ORIENTATION_DECISION: input was " + (isPortrait ? "PORTRAIT" : "LANDSCAPE")
                + " (" + w + "x" + h + ") -> applying rotation [" + rotationName(bestRotation)
                + "] -> matched template=" + bestTemplateId);

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

    /**
     * Orient the warped bitmap using a specific, user-selected template only.
     *
     * <p>This is similar to {@link #detectAndOrient(Bitmap)} but skips the
     * cross-template detection step.  Only the given template is used for
     * orientation scoring so that the user's choice is respected.</p>
     *
     * @param warpedBitmap the perspective-aligned bitmap
     * @param templateId   the template ID pre-selected by the user
     *                     (e.g. "ZPH30")
     * @return an {@link OrientationResult} with the correctly rotated bitmap
     */
    public OrientationResult detectAndOrientWithTemplate(Bitmap warpedBitmap, String templateId) {
        if (warpedBitmap == null) {
            Log.e(TAG, "detectAndOrientWithTemplate: bitmap is null");
            return new OrientationResult(warpedBitmap, templateId, 0.0);
        }

        OmrTemplate tpl = templates.get(templateId);
        if (tpl == null) {
            Log.e(TAG, "detectAndOrientWithTemplate: template '" + templateId + "' not found, falling back to detectAndOrient");
            return detectAndOrient(warpedBitmap);
        }

        int w = warpedBitmap.getWidth();
        int h = warpedBitmap.getHeight();
        boolean isPortrait = h > w;

        Log.d(TAG, String.format("detectAndOrientWithTemplate(%s): input %dx%d, portrait=%b",
                templateId, w, h, isPortrait));

        Mat srcColour = new Mat();
        Utils.bitmapToMat(warpedBitmap, srcColour);
        Mat srcGray = new Mat();
        Imgproc.cvtColor(srcColour, srcGray, Imgproc.COLOR_BGR2GRAY);
        srcColour.release();

        int[][] rotations;
        if (isPortrait) {
            // See the matching comment in detectAndOrient() above: try both
            // quarter-turns rather than forcing one fixed direction, since
            // the correct direction depends on which way the phone was
            // tilted at capture time.
            rotations = new int[][] {
                    { Core.ROTATE_90_CLOCKWISE },
                    { Core.ROTATE_90_COUNTERCLOCKWISE }
            };
        } else {
            rotations = new int[][] {
                    { -1 },
                    { Core.ROTATE_180 },
                    { Core.ROTATE_90_CLOCKWISE },
                    { Core.ROTATE_90_COUNTERCLOCKWISE }
            };
        }

        List<RotationCandidate> rotationCandidates = new ArrayList<>();

        GridAligner aligner = new GridAligner();

        for (int[] rot : rotations) {
            int rotCode = rot[0];
            Mat candidate;
            if (rotCode == -1) {
                candidate = srcGray;
            } else {
                candidate = new Mat();
                Core.rotate(srcGray, candidate, rotCode);
            }

            int cw = candidate.cols();
            int ch = candidate.rows();
            double scaleX = (double) cw / tpl.width;
            double scaleY = (double) ch / tpl.height;

            double bubbleScore = aligner.getAlignmentScore(candidate, tpl, scaleX, scaleY);

            // headerScore is still computed and logged for visibility, but no
            // longer decides the rotation — see conversation history for why.
            double headerFraction = computeHeaderFraction(tpl);
            double headerScore = computeHeaderSparsityScore(candidate, headerFraction);
            double combinedScore = bubbleScore;

            Log.d(TAG, String.format(
                    "  rot=%d, template=%s, bubble=%.3f header=%.3f(frac=%.3f, informational only) decision=%.3f",
                    rotCode, templateId, bubbleScore, headerScore, headerFraction, combinedScore));

            rotationCandidates.add(new RotationCandidate(rotCode, templateId, combinedScore));

            if (rotCode != -1) {
                candidate.release();
            }
        }

        aligner.release();
        srcGray.release();

        RotationDecision decision = chooseRotationCandidate(rotationCandidates);
        int bestRotation = decision.winner.rotationCode;
        double bestScore = decision.winner.score;

        if (decision.runnerUp != null) {
            Log.i(TAG, String.format(
                    "detectAndOrientWithTemplate: top1 rot=%d score=%.3f | top2 rot=%d score=%.3f | gap=%.4f | tieBreak=%b",
                    decision.winner.rotationCode, decision.winner.score,
                    decision.runnerUp.rotationCode, decision.runnerUp.score,
                    decision.scoreGap, decision.tieBreakApplied));
        }

        Log.i(TAG, String.format("detectAndOrientWithTemplate: template=%s rot=%d score=%.3f",
                templateId, bestRotation, bestScore));

        Log.i(TAG, "ORIENTATION_DECISION: input was " + (isPortrait ? "PORTRAIT" : "LANDSCAPE")
                + " (" + w + "x" + h + ") -> applying rotation [" + rotationName(bestRotation)
                + "] -> forced template=" + templateId);

        // Produce the correctly-oriented bitmap
        Bitmap orientedBitmap;
        if (bestRotation == -1) {
            orientedBitmap = warpedBitmap;
        } else {
            Mat colourSrc = new Mat();
            Utils.bitmapToMat(warpedBitmap, colourSrc);

            Mat rotated = new Mat();
            Core.rotate(colourSrc, rotated, bestRotation);
            colourSrc.release();

            orientedBitmap = Bitmap.createBitmap(
                    rotated.cols(), rotated.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rotated, orientedBitmap);
            rotated.release();
        }

        return new OrientationResult(orientedBitmap, templateId, bestScore);
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