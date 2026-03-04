package com.example.omrscanner.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Transparent overlay drawn on top of the camera preview.
 * Draws green rectangles around detected anchor corners and
 * connecting lines forming the bounding quadrilateral.
 */
public class AnchorOverlayView extends View {

    // Anchor points scaled to this view's coordinate system
    // Order: [TopLeft, TopRight, BottomLeft, BottomRight]
    private PointF[] anchorPoints = null;

    // Size of the square box drawn around each anchor (in dp, converted to px)
    private float anchorBoxSizePx;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final String[] LABELS = {"TL", "TR", "BL", "BR"};

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
        anchorBoxSizePx = 28 * density; // 28dp box around each anchor

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(Color.parseColor("#4CAF50")); // Material green
        boxPaint.setStrokeWidth(3 * density);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(Color.parseColor("#664CAF50")); // Semi-transparent green
        linePaint.setStrokeWidth(2 * density);

        labelPaint.setColor(Color.parseColor("#4CAF50"));
        labelPaint.setTextSize(12 * density);
        labelPaint.setStyle(Paint.Style.FILL);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#224CAF50")); // Very faint green fill
    }

    /**
     * Update the anchor positions to draw. Pass null to clear.
     *
     * @param points 4 PointF values in view coordinates [TL, TR, BL, BR], or null
     */
    public void setAnchors(@Nullable PointF[] points) {
        this.anchorPoints = points;
        invalidate(); // Trigger redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (anchorPoints == null || anchorPoints.length != 4) return;

        float half = anchorBoxSizePx / 2f;

        // Draw connecting lines: TL→TR→BR→BL→TL
        canvas.drawLine(anchorPoints[0].x, anchorPoints[0].y,
                anchorPoints[1].x, anchorPoints[1].y, linePaint);
        canvas.drawLine(anchorPoints[1].x, anchorPoints[1].y,
                anchorPoints[3].x, anchorPoints[3].y, linePaint);
        canvas.drawLine(anchorPoints[3].x, anchorPoints[3].y,
                anchorPoints[2].x, anchorPoints[2].y, linePaint);
        canvas.drawLine(anchorPoints[2].x, anchorPoints[2].y,
                anchorPoints[0].x, anchorPoints[0].y, linePaint);

        // Draw boxes and labels at each anchor
        for (int i = 0; i < anchorPoints.length; i++) {
            float cx = anchorPoints[i].x;
            float cy = anchorPoints[i].y;

            // Faint green fill
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, fillPaint);
            // Green border
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, boxPaint);
            // Label
            canvas.drawText(LABELS[i], cx + half + 4, cy - half + labelPaint.getTextSize(), labelPaint);
        }
    }
}
