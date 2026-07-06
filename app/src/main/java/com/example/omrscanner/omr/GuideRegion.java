package com.example.omrscanner.omr;

import android.graphics.RectF;

/**
 * ─────────────────────────────────────────────────────────────────────────
 * GuideRegion
 * ─────────────────────────────────────────────────────────────────────────
 *
 * WHAT THIS IS FOR
 * ----------------
 * Defines the 4 fixed "guide boxes" shown on the live camera screen — the
 * squares a teacher is meant to line the sheet's real corner anchors up
 * against before the app captures a photo.
 *
 * This class is the SINGLE SOURCE OF TRUTH for where those 4 boxes are.
 * Two different pieces of code both read from here:
 *
 *   1. AnchorOverlayView — draws the boxes on screen so the teacher can see
 *      them and align the paper.
 *   2. AnchorDetector     — crops the camera frame down to ONLY these 4
 *      regions before running its contour search, so it can never lock
 *      onto a false-positive dark square somewhere else in the frame
 *      (e.g. a laptop keyboard behind the sheet — this was an observed
 *      real bug before this class existed: the detector found a
 *      convincing-looking dark square in the background and captured on
 *      it, even though it was nowhere near the actual printed sheet).
 *
 * Both of those must always agree on where the boxes are. If they used
 * two separately-hardcoded sets of numbers, it would be very easy for
 * someone to change one and forget the other, and then the box the
 * teacher SEES would not match the region the detector is ACTUALLY
 * searching — silently breaking detection. Keeping the numbers in exactly
 * one place prevents that class of bug.
 *
 * WHY FRACTIONS (0f–1f) INSTEAD OF FIXED PIXEL/DP VALUES
 * --------------------------------------------------------
 * Phone screens vary a lot in resolution and aspect ratio. A box defined
 * in fixed pixels (e.g. "200px from the left") would sit in a different
 * relative spot on a small phone vs. a large tablet. Fractions of the
 * preview view's width/height scale correctly on any screen:
 *
 *     pixelX = fraction * viewWidthPx
 *     pixelY = fraction * viewHeightPx
 *
 * WHO USES WHICH PIXEL SPACE
 * ---------------------------
 * - AnchorOverlayView multiplies these fractions by the PreviewView's
 *   on-screen pixel dimensions (what the teacher sees).
 * - AnchorDetector multiplies these same fractions by the analysis
 *   frame's pixel dimensions (the raw camera buffer being processed,
 *   which is a different resolution than what's shown on screen).
 *
 * Both start from the SAME fractional RectF values here, so a box drawn
 * at "10% from the left" always corresponds to searching "10% from the
 * left" of the actual camera data, regardless of preview-vs-analysis
 * resolution differences.
 *
 * WHY THERE ARE TWO GROUPS OF NUMBERS (NOT JUST ONE UNIVERSAL LAYOUT)
 * ---------------------------------------------------------------------
 * The 4 sheet templates are NOT all the same physical proportions.
 * Checked directly against the template JSONs in assets/templates/:
 *
 *     ZPH30: width=1202  height=900   -> aspect ratio 1.336:1
 *     ZPH40: width=1202  height=900   -> aspect ratio 1.336:1   (same as ZPH30)
 *     ZPH50: width=1611  height=1138  -> aspect ratio 1.415:1
 *     ZPH60: width=1609  height=1134  -> aspect ratio 1.415:1   (same as ZPH50)
 *
 * ZPH30/ZPH40 share identical dimensions -> one guide layout safely covers
 * both. ZPH50/ZPH60 share dimensions with EACH OTHER, but not with
 * ZPH30/ZPH40 -> they need their own, separately-tuned layout. A single
 * universal layout would place the guide boxes at the wrong spot for
 * whichever group it wasn't tuned against.
 *
 * This is safe to select per-scan because the sheet type is ALREADY known
 * before the camera opens: the teacher picks it when creating the
 * Assessment (AssessmentEntity.sheetType), and that same value is already
 * required by TemplateManager to pick the right bubble-grid template.
 * We're just using a value that's already being passed in — not adding
 * new state or a "detect sheet type first" step.
 *
 * HOW THE ZPH30/40 NUMBERS BELOW WERE DERIVED (NOT GUESSED)
 * -------------------------------------------------------------
 * Ran actual OpenCV contour detection against a real photographed ZPH30
 * sheet (not eyeballed) to find the true anchor square centers:
 *
 *     top-left      (790,  1258)
 *     top-right     (3923, 1225)
 *     bottom-left   (786,  2306)
 *     bottom-right  (3950, 2281)
 *
 * That gives a measured anchor-to-anchor rectangle of ~3148 x 1052 px,
 * i.e. long:short ratio ≈ 2.99:1 (≈ 3:1). Since ZPH40 shares ZPH30's
 * exact dimensions, this same ratio applies to ZPH40 too.
 *
 * Converting that ratio into on-screen fractions still requires ONE
 * assumed decision this class can't measure by itself: how much of the
 * portrait camera frame's height the sheet's long axis should fill when
 * well-aligned. The numbers below assume:
 *   - sheet's long axis fills ~76% of frame height, centered
 *   - typical portrait screen width:height ≈ 0.5
 * Both are reasonable defaults, NOT measurements — validate against a
 * real on-device camera screenshot before treating these as final.
 *
 * ⚠ ZPH50/ZPH60 NUMBERS ARE STILL PLACEHOLDERS ⚠
 * ---------------------------------------------------
 * No real photo of a ZPH50/60 sheet has been measured yet. The values
 * below are a rough placeholder based only on the JSON aspect ratio
 * (1.415:1), NOT verified against an actual printed sheet the way the
 * ZPH30/40 group was. Replace GROUP_WIDE with real measured values
 * (same OpenCV-against-a-real-photo method used above) before relying
 * on this group in production — right now it is a best-guess only.
 *
 * ORDERING
 * --------
 * Order matches AnchorDetector's existing convention used everywhere
 * else in the OMR pipeline: TopLeft, TopRight, BottomLeft, BottomRight.
 * PerspectiveAligner also expects anchors in this exact order when it
 * warps the sheet into the canonical rectangle — do not reorder this
 * without checking every call site that consumes a Point[4] / RectF[4]
 * in this order.
 * ─────────────────────────────────────────────────────────────────────────
 */
public final class GuideRegion {

    // Not instantiable — this class only holds shared constants + lookup.
    private GuideRegion() {
    }

    // ── ZPH30 / ZPH40 group ────────────────────────────────────────────
    // Originally derived from a real photographed ZPH30 sheet via OpenCV
    // (see class comment for that method). Then CORRECTED against a real
    // on-device camera screenshot showing the drawn guide boxes next to
    // the actual anchors: the true horizontal spread between left/right
    // anchors is narrower than the original portrait-screen-aspect
    // assumption predicted (right-side anchors were sitting visibly
    // inside/left of the originally-drawn right guide boxes). These
    // values reflect that correction. Preview-view pixel bounds were
    // still estimated from the screenshot itself (not read from the
    // actual layout), so continue to treat this as improved, not final.
    // Box size (half-width/half-height around each center) was then
    // reduced from the original ~0.06/0.05 to a tighter ~0.045/0.035 per
    // request — smaller target squares, same measured centers.
    private static final RectF COMPACT_TL = new RectF(0.260f, 0.040f, 0.350f, 0.110f);
    private static final RectF COMPACT_TR = new RectF(0.628f, 0.040f, 0.718f, 0.110f);
    private static final RectF COMPACT_BL = new RectF(0.260f, 0.798f, 0.350f, 0.868f);
    private static final RectF COMPACT_BR = new RectF(0.628f, 0.798f, 0.718f, 0.868f);
    private static final RectF[] GROUP_COMPACT = {COMPACT_TL, COMPACT_TR, COMPACT_BL, COMPACT_BR};

    // ── ZPH50 / ZPH60 group ────────────────────────────────────────────
    // ⚠ PLACEHOLDER — derived only from the JSON aspect ratio (1.415:1),
    // NOT measured against a real printed sheet yet. Re-derive these the
    // same way the COMPACT group was derived once a real ZPH50/60 photo
    // is available, then remove this warning.
    private static final RectF WIDE_TL = new RectF(0.170f, 0.07f, 0.290f, 0.17f);
    private static final RectF WIDE_TR = new RectF(0.710f, 0.07f, 0.830f, 0.17f);
    private static final RectF WIDE_BL = new RectF(0.170f, 0.83f, 0.290f, 0.93f);
    private static final RectF WIDE_BR = new RectF(0.710f, 0.83f, 0.830f, 0.93f);
    private static final RectF[] GROUP_WIDE = {WIDE_TL, WIDE_TR, WIDE_BL, WIDE_BR};

    /**
     * Returns the 4 guide boxes (TL, TR, BL, BR order) appropriate for the
     * given sheet type. Falls back to the COMPACT (ZPH30/40) group for any
     * unrecognized value, since that is the more common/default sheet.
     *
     * @param sheetType e.g. "ZPH30", "ZPH40", "ZPH50", "ZPH60" — expected
     *                  to come from AssessmentEntity.sheetType, which the
     *                  teacher already chose when creating the assessment,
     *                  before the camera screen ever opens.
     */
    public static RectF[] forSheetType(String sheetType) {
        if (sheetType == null) {
            return GROUP_COMPACT;
        }
        switch (sheetType) {
            case "ZPH50":
            case "ZPH60":
                return GROUP_WIDE;
            case "ZPH30":
            case "ZPH40":
            default:
                return GROUP_COMPACT;
        }
    }
}