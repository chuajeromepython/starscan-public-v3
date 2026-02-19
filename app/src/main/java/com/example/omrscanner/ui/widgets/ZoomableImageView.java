package com.example.omrscanner.ui.widgets;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Custom ImageView with pinch-to-zoom and pan functionality
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;

    private Matrix matrix;
    private float[] matrixValues = new float[9];

    // Gesture detectors
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1f;

    // Pan/drag functionality
    private PointF last = new PointF();
    private PointF start = new PointF();
    private int mode = NONE;

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        super.setClickable(true);
        matrix = new Matrix();
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(matrix);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        PointF current = new PointF(event.getX(), event.getY());

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                last.set(current);
                start.set(last);
                mode = DRAG;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                last.set(current);
                start.set(last);
                mode = ZOOM;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float deltaX = current.x - last.x;
                    float deltaY = current.y - last.y;
                    float fixTransX = getFixDragTrans(deltaX, getWidth(), getImageWidth() * scaleFactor);
                    float fixTransY = getFixDragTrans(deltaY, getHeight(), getImageHeight() * scaleFactor);
                    matrix.postTranslate(fixTransX, fixTransY);
                    fixTranslation();
                    last.set(current.x, current.y);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }

        setImageMatrix(matrix);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));

            matrix.setScale(scaleFactor, scaleFactor);
            fixTranslation();
            setImageMatrix(matrix);
            return true;
        }
    }

    private void fixTranslation() {
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, getWidth(), getImageWidth() * scaleFactor);
        float fixTransY = getFixTrans(transY, getHeight(), getImageHeight() * scaleFactor);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans;
        float maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans) {
            return minTrans - trans;
        }
        if (trans > maxTrans) {
            return maxTrans - trans;
        }
        return 0;
    }

    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    private float getImageWidth() {
        if (getDrawable() == null) {
            return 0;
        }
        return getDrawable().getIntrinsicWidth();
    }

    private float getImageHeight() {
        if (getDrawable() == null) {
            return 0;
        }
        return getDrawable().getIntrinsicHeight();
    }

    public void resetZoom() {
        scaleFactor = 1f;
        matrix.reset();
        setImageMatrix(matrix);
    }
}