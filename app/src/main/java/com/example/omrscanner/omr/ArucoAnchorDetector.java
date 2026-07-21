package com.example.omrscanner.omr;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects the 4 corner anchors on an OMR sheet using ArUco marker IDENTITY
 * rather than geometric position.
 *
 * AnchorDetector's contour-based corner labeling (TL/TR/BL/BR) is assigned
 * purely from where a candidate square LANDS in the frame (top-left =
 * smallest x+y, etc). That's only safe when the sheet is right-side-up
 * relative to the camera. Tilt Agnostic Mode needs to work at ANY
 * orientation -- including upside-down -- at which point "geometric
 * top-left in the photo" is actually the sheet's physical bottom-right,
 * and PerspectiveAligner ends up warping the capture inside-out.
 *
 * Each corner on the printed sheet carries its own unique ArUco marker ID,
 * so we know which physical corner we're looking at no matter how it
 * happens to land in the frame.
 */
public class ArucoAnchorDetector {

    private static final String TAG = "ArucoAnchorDetector";

    // Must match whatever dictionary was used to print the markers on the
    // sheet template. 4x4_50 gives 50 unique IDs at a small physical size --
    // far more headroom than the 4 we need.
    private static final int DICTIONARY_ID = Objdetect.DICT_4X4_50;

    // Marker IDs printed at each physical corner of the sheet template.
    // These MUST match the IDs baked into the printed template. If the
    // template generator assigns different IDs, update these to match.
    public static final int MARKER_ID_TOP_LEFT = 0;
    public static final int MARKER_ID_TOP_RIGHT = 1;
    public static final int MARKER_ID_BOTTOM_LEFT = 2;
    public static final int MARKER_ID_BOTTOM_RIGHT = 3;

    private static volatile ArucoDetector detectorInstance;

    private static ArucoDetector getDetector() {
        // Lazy singleton -- rebuilding the dictionary/detector every frame
        // would be wasteful on the live-preview hot path.
        ArucoDetector local = detectorInstance;
        if (local == null) {
            synchronized (ArucoAnchorDetector.class) {
                local = detectorInstance;
                if (local == null) {
                    Dictionary dictionary = Objdetect.getPredefinedDictionary(DICTIONARY_ID);
                    DetectorParameters params = new DetectorParameters();
                    local = new ArucoDetector(dictionary, params);
                    detectorInstance = local;
                }
            }
        }
        return local;
    }

    /**
     * Detects all 4 identity anchors and returns them in canonical
     * [TL, TR, BL, BR] order -- regardless of where each marker physically
     * landed in the frame. Returns null unless all 4 required IDs were found.
     */
    @Nullable
    public static Point[] detectIdentityAnchors(@NonNull Mat image) {
        Map<Integer, Point> byId = detectMarkerCenters(image);

        if (!byId.containsKey(MARKER_ID_TOP_LEFT)
                || !byId.containsKey(MARKER_ID_TOP_RIGHT)
                || !byId.containsKey(MARKER_ID_BOTTOM_LEFT)
                || !byId.containsKey(MARKER_ID_BOTTOM_RIGHT)) {
            return null;
        }

        return new Point[] {
                byId.get(MARKER_ID_TOP_LEFT),
                byId.get(MARKER_ID_TOP_RIGHT),
                byId.get(MARKER_ID_BOTTOM_LEFT),
                byId.get(MARKER_ID_BOTTOM_RIGHT)
        };
    }

    /**
     * Runs raw ArUco detection and returns a map of marker ID -> center
     * point in the image. Returns ALL detected markers (not just the 4 we
     * care about) so callers can build a "some anchors locked" style
     * progress UI similar to guide-square mode if desired.
     */
    @NonNull
    public static Map<Integer, Point> detectMarkerCenters(@NonNull Mat image) {
        Map<Integer, Point> result = new HashMap<>();

        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();

        try {
            getDetector().detectMarkers(image, corners, ids);

            for (int i = 0; i < corners.size(); i++) {
                Point center = centerOfMarker(corners.get(i));
                if (center == null) continue;

                int id = (int) ids.get(i, 0)[0];
                result.put(id, center);
            }
        } catch (Exception e) {
            Log.e(TAG, "ArUco detection failed", e);
        } finally {
            for (Mat c : corners) c.release();
            ids.release();
        }

        return result;
    }

    @Nullable
    private static Point centerOfMarker(Mat markerCorners) {
        // markerCorners is a 1x4 CV_32FC2 Mat -- the 4 corners of a single
        // marker, clockwise from its own top-left. Average them for center.
        if (markerCorners.rows() < 1 || markerCorners.cols() < 4) return null;

        float[] buffer = new float[8];
        markerCorners.get(0, 0, buffer);

        double sumX = 0, sumY = 0;
        for (int i = 0; i < 4; i++) {
            sumX += buffer[i * 2];
            sumY += buffer[i * 2 + 1];
        }
        return new Point(sumX / 4.0, sumY / 4.0);
    }

    // --- Convenience overloads mirroring AnchorDetector's entry points ---

    @Nullable
    public static Point[] detectIdentityAnchors(@NonNull ImageProxy imageProxy) {
        Mat gray = AnchorDetector.toGrayMat(imageProxy);
        if (gray == null) return null;
        try {
            return detectIdentityAnchors(gray);
        } finally {
            gray.release();
        }
    }

    @Nullable
    public static Point[] detectIdentityAnchors(@NonNull Bitmap bitmap) {
        Mat mat = new Mat();
        try {
            Utils.bitmapToMat(bitmap, mat);
            return detectIdentityAnchors(mat);
        } finally {
            mat.release();
        }
    }
}