package com.example.omrscanner.omr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.HashMap;
import java.util.Map;

/**
 * Pro Mode calibration surface: shows the raw reference photo, draws every
 * block's CURRENT bubble grid on top of it (so the user can see how wrong
 * it is), and lets them drag two handles per block -- the top-left bubble
 * center and the bottom-right bubble center -- to where the real bubbles
 * actually are.
 *
 * All the coordinate-space math (raw photo <-> template space) lives in
 * {@link TemplateCalibrator}; this view only ever works in two spaces:
 *   - "raw" = pixel coordinates on the original photo (org.opencv.core.Point)
 *   - "view" = pixel coordinates on screen (android.graphics.PointF)
 * and converts between them for drawing/hit-testing only.
 */
public class CalibrationOverlayView extends View {

    private static final float HANDLE_RADIUS_PX = 28f;
    private static final float HANDLE_TOUCH_SLOP_PX = 48f;

    private Bitmap photo;
    private OmrTemplate template;
    private Mat forwardHomography;
    private Mat inverseHomography;
    private boolean landscapeContent;

    /** Per-block drag handles, in RAW PHOTO space: [0]=top-left bubble, [1]=bottom-right bubble. */
    private final Map<String, Point[]> handlesByBlock = new HashMap<>();
    private String activeBlockLabel;
    private int draggingHandleIndex = -1; // -1 = nothing being dragged

    // View <-> raw photo display transform (photo is letterboxed to fit the view)
    private float displayScale = 1f;
    private float displayOffsetX = 0f;
    private float displayOffsetY = 0f;

    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeGridDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boundsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleEndPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CalibrationOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridDotPaint.setColor(Color.argb(140, 90, 90, 255));
        gridDotPaint.setStyle(Paint.Style.FILL);

        activeGridDotPaint.setColor(Color.argb(200, 30, 30, 255));
        activeGridDotPaint.setStyle(Paint.Style.FILL);

        boundsPaint.setColor(Color.argb(220, 30, 30, 255));
        boundsPaint.setStyle(Paint.Style.STROKE);
        boundsPaint.setStrokeWidth(3f);

        handleStartPaint.setColor(Color.argb(230, 0, 200, 0));   // green = top-left / row1
        handleStartPaint.setStyle(Paint.Style.FILL);

        handleEndPaint.setColor(Color.argb(230, 220, 30, 30));   // red = bottom-right / last row
        handleEndPaint.setStyle(Paint.Style.FILL);

        handleLabelPaint.setColor(Color.WHITE);
        handleLabelPaint.setTextSize(24f);
        handleLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Must be called once after the raw photo and the 4 detected ArUco
     * anchors are available, before any block becomes active. Initializes
     * every block's drag handles from its CURRENT (possibly wrong)
     * geometry, so there's always something on screen to drag away from.
     */
    public void setup(Bitmap photo, Point[] anchors, boolean landscapeContent, OmrTemplate template) {
        this.photo = photo;
        this.landscapeContent = landscapeContent;
        this.template = template;

        if (forwardHomography != null) forwardHomography.release();
        if (inverseHomography != null) inverseHomography.release();
        forwardHomography = TemplateCalibrator.buildForwardHomography(anchors, landscapeContent);
        inverseHomography = TemplateCalibrator.buildInverseHomography(anchors, landscapeContent);

        handlesByBlock.clear();
        for (OmrBlock block : template.blocks) {
            handlesByBlock.put(block.label, initialHandlesFor(block));
        }

        activeBlockLabel = template.blocks.isEmpty() ? null : template.blocks.get(0).label;
        requestLayout();
        invalidate();
    }

    private Point[] initialHandlesFor(OmrBlock block) {
        Point topLeftTemplateSpace = new Point(block.startX, block.startY);
        Point bottomRightTemplateSpace = new Point(
                block.startX + (block.cols - 1) * block.dx,
                block.startY + (block.rows - 1) * block.dy
        );
        Point topLeftRaw = TemplateCalibrator.templateSpaceToRaw(
                inverseHomography, topLeftTemplateSpace, landscapeContent, template.width, template.height);
        Point bottomRightRaw = TemplateCalibrator.templateSpaceToRaw(
                inverseHomography, bottomRightTemplateSpace, landscapeContent, template.width, template.height);
        return new Point[]{topLeftRaw, bottomRightRaw};
    }

    /** Switches which block's handles are shown/draggable. Does not affect other blocks' in-progress edits. */
    public void setActiveBlock(String label) {
        this.activeBlockLabel = label;
        invalidate();
    }

    /** Undoes any dragging on the active block, snapping its handles back to the original template geometry. */
    public void resetActiveBlockHandles() {
        if (activeBlockLabel == null) return;
        for (OmrBlock block : template.blocks) {
            if (block.label.equals(activeBlockLabel)) {
                handlesByBlock.put(activeBlockLabel, initialHandlesFor(block));
                break;
            }
        }
        invalidate();
    }

    /** Current handles for a block, in RAW PHOTO pixel space: [0]=top-left bubble, [1]=bottom-right bubble. */
    @Nullable
    public Point[] getHandles(String label) {
        return handlesByBlock.get(label);
    }

    public Mat getForwardHomography() {
        return forwardHomography;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        computeDisplayTransform();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeDisplayTransform();
    }

    private void computeDisplayTransform() {
        if (photo == null || getWidth() == 0 || getHeight() == 0) return;
        float scaleX = getWidth() / (float) photo.getWidth();
        float scaleY = getHeight() / (float) photo.getHeight();
        displayScale = Math.min(scaleX, scaleY);
        displayOffsetX = (getWidth() - photo.getWidth() * displayScale) / 2f;
        displayOffsetY = (getHeight() - photo.getHeight() * displayScale) / 2f;
    }

    private PointF rawToView(Point raw) {
        return new PointF(
                (float) (raw.x * displayScale + displayOffsetX),
                (float) (raw.y * displayScale + displayOffsetY)
        );
    }

    private Point viewToRaw(float viewX, float viewY) {
        return new Point(
                (viewX - displayOffsetX) / displayScale,
                (viewY - displayOffsetY) / displayScale
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (photo == null || template == null) return;

        RectF dst = new RectF(displayOffsetX, displayOffsetY,
                displayOffsetX + photo.getWidth() * displayScale,
                displayOffsetY + photo.getHeight() * displayScale);
        canvas.drawBitmap(photo, null, dst, photoPaint);

        for (OmrBlock block : template.blocks) {
            boolean isActive = block.label.equals(activeBlockLabel);
            drawBlockGrid(canvas, block, isActive);
        }

        if (activeBlockLabel != null) {
            Point[] handles = handlesByBlock.get(activeBlockLabel);
            if (handles != null) {
                PointF start = rawToView(handles[0]);
                PointF end = rawToView(handles[1]);
                canvas.drawRect(Math.min(start.x, end.x), Math.min(start.y, end.y),
                        Math.max(start.x, end.x), Math.max(start.y, end.y), boundsPaint);
                canvas.drawCircle(start.x, start.y, HANDLE_RADIUS_PX, handleStartPaint);
                canvas.drawCircle(end.x, end.y, HANDLE_RADIUS_PX, handleEndPaint);
                canvas.drawText("1", start.x, start.y + 8f, handleLabelPaint);
                canvas.drawText("N", end.x, end.y + 8f, handleLabelPaint);
            }
        }
    }

    /**
     * Draws every bubble in a block at its CURRENT geometry (from the live
     * handle positions if this is the active block being dragged, otherwise
     * from the template's stored values) so the user gets visual feedback
     * of the whole grid moving as they drag, not just the two handles.
     */
    private void drawBlockGrid(Canvas canvas, OmrBlock block, boolean isActive) {
        Paint dot = isActive ? activeGridDotPaint : gridDotPaint;

        double startX = block.startX, startY = block.startY, dx = block.dx, dy = block.dy;
        if (isActive) {
            Point[] handles = handlesByBlock.get(block.label);
            if (handles != null) {
                // Live-preview geometry from the current (possibly mid-drag) handle
                // positions, using the same derivation as an actual save would.
                TemplateCalibrator.BlockGeometry g = TemplateCalibrator.deriveBlockGeometry(
                        forwardHomography, handles[0], handles[1],
                        landscapeContent, template.width, template.height, block.rows, block.cols);
                startX = g.startX; startY = g.startY; dx = g.dx; dy = g.dy;
            }
        }

        for (int row = 0; row < block.rows; row++) {
            for (int col = 0; col < block.cols; col++) {
                Point templateSpace = new Point(startX + col * dx, startY + row * dy);
                Point raw = TemplateCalibrator.templateSpaceToRaw(
                        inverseHomography, templateSpace, landscapeContent, template.width, template.height);
                PointF view = rawToView(raw);
                canvas.drawCircle(view.x, view.y, isActive ? 7f : 4f, dot);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (activeBlockLabel == null) return false;
        Point[] handles = handlesByBlock.get(activeBlockLabel);
        if (handles == null) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                PointF startView = rawToView(handles[0]);
                PointF endView = rawToView(handles[1]);
                float x = event.getX(), y = event.getY();
                if (distance(x, y, startView.x, startView.y) <= HANDLE_TOUCH_SLOP_PX) {
                    draggingHandleIndex = 0;
                    return true;
                } else if (distance(x, y, endView.x, endView.y) <= HANDLE_TOUCH_SLOP_PX) {
                    draggingHandleIndex = 1;
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (draggingHandleIndex == -1) return false;
                handles[draggingHandleIndex] = viewToRaw(event.getX(), event.getY());
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                draggingHandleIndex = -1;
                return true;
            }
        }
        return false;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}