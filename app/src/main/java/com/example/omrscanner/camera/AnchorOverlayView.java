package com.example.omrscanner.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * Transparent overlay drawn on top of the camera preview.
 *
 * Guide-square mode: draws 4 fixed, static guide squares (light/red) that
 * tell the teacher where to align the sheet's real anchor squares. Each
 * guide square also draws a green progress ring around its own perimeter,
 * representing how close that specific corner is to a stable detection.
 * A square is "locked" once the ring completes a full lap.
 *
 * This view no longer draws anchors at detected/free-floating positions —
 * detection is confined to these 4 fixed regions, so there's nothing else
 * to draw.
 */
public class AnchorOverlayView extends View {
    /**
     * One live-tracked marker box: the marker's 4 corners in THIS view's
     * coordinate space (already rotated/scaled from image space by the
     * caller), plus the label to draw above it (e.g. "TL", or "#7" for a
     * marker that isn't one of the 4 known identity anchors).
     */
    public static final class TrackedMarker {
        public final PointF[] quad;
        @Nullable public final String label;

        public TrackedMarker(PointF[] quad, @Nullable String label) {
            this.quad = quad;
            this.label = label;
        }
    }

    // Live ArUco tracking boxes (Tilt Agnostic Mode only). Null/empty when
    // nothing is currently detected. Unlike guideSquares these move every
    // frame -- they follow wherever the physical marker currently is. This
    // is completely independent of guideSquares/progress/locked, which
    // remain the legacy scanner's red/green boxes, untouched.
    @Nullable
    private List<TrackedMarker> trackedMarkers = null;

    private final Paint trackedBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackedLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackedLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path trackedPath = new Path();
    private float density = 1f;

    // Default labels, portrait orientation. Order matches CameraActivity's
    // GUIDE_CENTER_X/Y_FRACTION arrays: [TL, TR, BL, BR]. Updated live via
    // setCornerLabels() as the phone tilts, so each box's label always
    // reflects which sheet corner it logically represents for the user's
    // current hand orientation — the boxes themselves never move.
    private String[] labels = {"TL", "TR", "BL", "BR"};

    // Degrees to rotate each label's text around its own box's center, so
    // the letters stay upright for the user as the phone is physically
    // tilted — mirrors CameraActivity's applyIconRotation() treatment of
    // the other on-screen icons/status text. The boxes themselves never
    // move or rotate, only the glyphs drawn inside them.
    private int labelRotationDegrees = 0;

    // Fixed guide squares, in this view's own coordinate system.
    // Set once the parent knows its layout size.
    @Nullable
    private RectF[] guideSquares = null;

    // Per-corner progress, 0f..1f. 1f == full lap == locked.
    private float[] progress = new float[]{0f, 0f, 0f, 0f};
    private boolean[] locked = new boolean[]{false, false, false, false};

    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guideFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reused per-square to avoid allocating a Path every frame.
    private final Path perimeterPath = new Path();
    private final PathMeasure perimeterMeasure = new PathMeasure();

    public AnchorOverlayView(Context context) {
        super(context);
        init(context);
    }

    public AnchorOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AnchorOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        this.density = density;

        // Live ArUco tracking box -- green outline that follows the
        // marker's actual detected corners frame to frame. Separate paint
        // objects from the legacy guide-square paints below.
        trackedBoxPaint.setStyle(Paint.Style.STROKE);
        trackedBoxPaint.setColor(Color.parseColor("#4CAF50")); // green
        trackedBoxPaint.setStrokeWidth(3f * density);
        trackedBoxPaint.setStrokeJoin(Paint.Join.ROUND);

        trackedLabelBgPaint.setStyle(Paint.Style.FILL);
        trackedLabelBgPaint.setColor(Color.parseColor("#4CAF50"));

        trackedLabelPaint.setColor(Color.parseColor("#FFFFFF"));
        trackedLabelPaint.setTextSize(12 * density);
        trackedLabelPaint.setStyle(Paint.Style.FILL);
        trackedLabelPaint.setTextAlign(Paint.Align.CENTER);
        trackedLabelPaint.setFakeBoldText(true);

        // Static guide square outline — light/red, always visible, marks
        // where the teacher should line up the real anchor.
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setColor(Color.parseColor("#FF5252")); // light red
        guidePaint.setStrokeWidth(2.5f * density);

        guideFillPaint.setStyle(Paint.Style.FILL);
        guideFillPaint.setColor(Color.parseColor("#1FFF5252")); // faint red fill

        // Progress ring — sweeps around the guide square perimeter as
        // consecutive-hit count increases.
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setColor(Color.parseColor("#4CAF50")); // green
        progressPaint.setStrokeWidth(4f * density);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // Once a corner locks, fill it solid-ish green so it reads as "done"
        // at a glance without needing to track the animation.
        lockedFillPaint.setStyle(Paint.Style.FILL);
        lockedFillPaint.setColor(Color.parseColor("#334CAF50"));

        labelPaint.setColor(Color.parseColor("#FFFFFF"));
        labelPaint.setTextSize(11 * density);
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setShadowLayer(3f * density, 0f, 0f, Color.parseColor("#CC000000"));
    }

    /**
     * Sets the 4 static guide squares, in this view's own coordinate space.
     * Pass null to hide them (e.g. before layout size is known).
     *
     * @param squares [TL, TR, BL, BR]
     */
    public void setGuideSquares(@Nullable RectF[] squares) {
        this.guideSquares = squares;
        invalidate();
    }

    /**
     * Updates per-corner progress/lock state and redraws.
     *
     * @param progress 4 values in [0f, 1f], one per corner [TL, TR, BL, BR]
     * @param locked   4 flags, true once that corner has completed a full lap
     */
    public void setCornerProgress(float[] progress, boolean[] locked) {
        this.progress = progress;
        this.locked = locked;
        invalidate();
    }

    /**
     * Updates the text shown on each guide square, without moving the
     * squares themselves. Used to reflect which sheet corner a fixed
     * on-screen box logically represents as the phone is tilted.
     *
     * @param labels 4 strings, one per corner [TL, TR, BL, BR] slot
     */
    public void setCornerLabels(String[] labels) {
        if (labels != null && labels.length == 4) {
            this.labels = labels;
            invalidate();
        }
    }

    /**
     * Updates the angle each corner label is rotated to, so the text stays
     * upright as the phone is tilted. Call this from the same place that
     * drives icon-rotation compensation (e.g. CameraActivity's
     * orientation listener), passing the same degrees value.
     *
     * @param degrees clockwise rotation in degrees, matching View.rotation()'s convention
     */
    public void setLabelRotationDegrees(int degrees) {
        if (this.labelRotationDegrees != degrees) {
            this.labelRotationDegrees = degrees;
            invalidate();
        }
    }

    /** Resets all progress/lock state back to zero, e.g. after a miss streak or retake. */
    public void resetProgress() {
        this.progress = new float[]{0f, 0f, 0f, 0f};
        this.locked = new boolean[]{false, false, false, false};
        this.trackedMarkers = null;
        invalidate();
    }

    /**
     * Updates the set of live ArUco tracking boxes and redraws. Called every
     * analyzed frame in Tilt Agnostic Mode with whatever markers are
     * currently visible -- pass null/empty to clear the boxes when nothing
     * is detected. Does not touch guideSquares/progress/locked.
     */
    public void setTrackedMarkers(@Nullable List<TrackedMarker> markers) {
        this.trackedMarkers = markers;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (trackedMarkers != null) {
            for (TrackedMarker marker : trackedMarkers) {
                drawTrackedMarker(canvas, marker);
            }
        }

        if (guideSquares == null || guideSquares.length != 4) return;

        for (int i = 0; i < 4; i++) {
            RectF square = guideSquares[i];
            if (square == null) continue;

            // Static guide square — always drawn, this is the alignment target.
            canvas.drawRect(square, guideFillPaint);
            canvas.drawRect(square, guidePaint);

            if (locked[i]) {
                canvas.drawRect(square, lockedFillPaint);
            }

            float p = Math.max(0f, Math.min(1f, progress[i]));
            if (p > 0f) {
                drawProgressRing(canvas, square, p);
            }

            if (labelRotationDegrees != 0) {
                canvas.save();
                canvas.rotate(labelRotationDegrees, square.centerX(), square.centerY());
                canvas.drawText(
                        labels[i] + (locked[i] ? " \u2713" : ""),
                        square.centerX(),
                        square.centerY() + (labelPaint.getTextSize() / 3f), // vertically center the baseline
                        labelPaint
                );
                canvas.restore();
            } else {
                canvas.drawText(
                        labels[i] + (locked[i] ? " \u2713" : ""),
                        square.centerX(),
                        square.centerY() + (labelPaint.getTextSize() / 3f), // vertically center the baseline
                        labelPaint
                );
            }
        }
    }

    /**
     * Draws one live tracking box: a green outline following the marker's
     * actual detected quad (so it follows tilt/perspective, not just a
     * fixed-size box on the center point), plus a small filled label
     * floating just above it.
     */
    private void drawTrackedMarker(Canvas canvas, TrackedMarker marker) {
        PointF[] quad = marker.quad;
        if (quad == null || quad.length != 4) return;

        trackedPath.reset();
        trackedPath.moveTo(quad[0].x, quad[0].y);
        for (int i = 1; i < 4; i++) {
            trackedPath.lineTo(quad[i].x, quad[i].y);
        }
        trackedPath.close();
        canvas.drawPath(trackedPath, trackedBoxPaint);

        if (marker.label == null) return;

        float minY = quad[0].y;
        float cx = 0f;
        for (PointF p : quad) {
            cx += p.x;
            if (p.y < minY) minY = p.y;
        }
        cx /= 4f;

        float paddingH = 8f * density;
        float paddingV = 4f * density;
        float textWidth = trackedLabelPaint.measureText(marker.label);
        float labelHeight = trackedLabelPaint.getTextSize() + paddingV * 2f;
        float labelBottom = minY - (4f * density);
        float labelTop = labelBottom - labelHeight;

        RectF labelRect = new RectF(
                cx - textWidth / 2f - paddingH,
                labelTop,
                cx + textWidth / 2f + paddingH,
                labelBottom);
        canvas.drawRoundRect(labelRect, 6f * density, 6f * density, trackedLabelBgPaint);
        float baseline = labelRect.centerY()
                - ((trackedLabelPaint.descent() + trackedLabelPaint.ascent()) / 2f);
        canvas.drawText(marker.label, cx, baseline, trackedLabelPaint);
    }

    /**
     * Draws a green line that sweeps around the square's perimeter,
     * starting at the top-left corner and going clockwise, covering a
     * fraction `p` of the total perimeter length.
     */
    private void drawProgressRing(Canvas canvas, RectF square, float p) {
        perimeterPath.reset();
        perimeterPath.moveTo(square.left, square.top);
        perimeterPath.lineTo(square.right, square.top);
        perimeterPath.lineTo(square.right, square.bottom);
        perimeterPath.lineTo(square.left, square.bottom);
        perimeterPath.lineTo(square.left, square.top);

        perimeterMeasure.setPath(perimeterPath, false);
        float fullLength = perimeterMeasure.getLength();
        float drawLength = fullLength * p;

        Path segment = new Path();
        perimeterMeasure.getSegment(0f, drawLength, segment, true);
        canvas.drawPath(segment, progressPaint);
    }

    // Kept for compatibility with any legacy caller — no-op now, since
    // guide-square mode doesn't draw floating anchor markers. Remove once
    // all callers have migrated to setGuideSquares/setCornerProgress.
    @Deprecated
    public void setAnchors(@Nullable PointF[] points) {
        // intentionally empty
        // this method servers no purpose
    }
}