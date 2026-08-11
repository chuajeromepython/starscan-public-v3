package com.example.omrscanner.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.example.omrscanner.R;
import com.example.omrscanner.omr.ArucoAnchorDetector;
import com.example.omrscanner.omr.CalibrationOverlayView;
import com.example.omrscanner.omr.OmrBlock;
import com.example.omrscanner.omr.OmrTemplate;
import com.example.omrscanner.omr.TemplateCalibrator;
import com.example.omrscanner.omr.TemplateManager;

import com.google.gson.Gson;

import org.opencv.core.Point;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pro Mode: lets a user re-calibrate a template's bubble geometry by
 * dragging two points per block onto a reference photo of their own
 * physical sheet, instead of hand-editing start_x/start_y/dx/dy in JSON.
 *
 * Flow:
 *   1. Pick a reference photo of the physical sheet (flat, all 4 ArUco
 *      markers visible).
 *   2. Detect the 4 markers via ArucoAnchorDetector -- same detector the
 *      live scanning pipeline uses, so calibration and scanning always
 *      agree on where "TL/TR/BL/BR" are.
 *   3. For each block, drag the green handle onto the true top-left bubble
 *      and the red handle onto the true bottom-right bubble.
 *   4. "Apply Block" derives that block's start_x/start_y/dx/dy from the
 *      handle positions (see TemplateCalibrator) and updates the in-memory
 *      working copy.
 *   5. "Save All" validates and persists the working copy as a
 *      TemplateManager override -- used immediately, on top of (never
 *      overwriting) the bundled default.
 */
public class ProModeCalibrationActivity extends AppCompatActivity {

    private static final String TAG = "ProModeCalibration";
    public static final String EXTRA_TEMPLATE_ID = "extra_template_id";

    private TemplateManager templateManager;
    private OmrTemplate workingCopy;
    private String activeBlockLabel;

    private CalibrationOverlayView overlay;
    private Spinner blockSpinner;
    private TextView instructions;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadReferencePhoto(uri);
            });

    public static Intent newIntent(Context context, String templateId) {
        Intent intent = new Intent(context, ProModeCalibrationActivity.class);
        intent.putExtra(EXTRA_TEMPLATE_ID, templateId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pro_mode_calibration);

        overlay = findViewById(R.id.calibrationOverlay);
        blockSpinner = findViewById(R.id.blockSpinner);
        instructions = findViewById(R.id.calibrationInstructions);
        findViewById(R.id.btnResetBlock).setOnClickListener(v -> overlay.resetActiveBlockHandles());
        findViewById(R.id.btnApplyBlock).setOnClickListener(v -> applyActiveBlock(/*silent=*/false));
        findViewById(R.id.btnSaveCalibration).setOnClickListener(v -> saveAll());

        String templateId = getIntent().getStringExtra(EXTRA_TEMPLATE_ID);
        if (templateId == null) {
            Toast.makeText(this, "No template specified", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        templateManager = new TemplateManager(this);
        OmrTemplate original = templateManager.getTemplate(templateId);
        if (original == null) {
            Toast.makeText(this, "Unknown template: " + templateId, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Deep copy so nothing is written to the shared in-memory template
        // (or disk) until the user explicitly hits "Save All".
        workingCopy = new Gson().fromJson(new Gson().toJson(original), OmrTemplate.class);

        setTitle("Calibrate " + templateId);
        instructions.setText("Pick a flat, well-lit photo of the physical " + templateId + " sheet to calibrate against.");
        pickImageLauncher.launch("image/*");
    }

    private void loadReferencePhoto(Uri uri) {
        try {
            Bitmap photo = decodeAndOrient(uri);
            org.opencv.core.Point[] anchors = ArucoAnchorDetector.detectIdentityAnchors(photo);

            if (anchors == null) {
                new AlertDialog.Builder(this)
                        .setTitle("Could not detect all 4 markers")
                        .setMessage("Make sure the whole sheet is in frame, flat, and well lit, then try another photo.")
                        .setPositiveButton("Pick another photo", (d, w) -> pickImageLauncher.launch("image/*"))
                        .setNegativeButton("Cancel", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                return;
            }

            // Every ZPH template so far is landscape (width > height); this
            // must match how PerspectiveAligner.alignPerspective is called
            // for ArUco-identity-resolved anchors during real scanning, or
            // calibration and scanning will disagree about the coordinate
            // space. If a future portrait template is added, this needs a
            // real decision point instead of inferring from aspect ratio.
            boolean landscapeContent = workingCopy.width > workingCopy.height;

            overlay.setup(photo, anchors, landscapeContent, workingCopy);
            setupBlockSpinner();

        } catch (IOException e) {
            Log.e(TAG, "Failed to load reference photo", e);
            Toast.makeText(this, "Could not open that photo, try another.", Toast.LENGTH_LONG).show();
            pickImageLauncher.launch("image/*");
        }
    }

    /** Decodes the picked image at a sane resolution and corrects EXIF rotation. */
    private Bitmap decodeAndOrient(Uri uri) throws IOException {
        // First pass: read dimensions only, so we can downsample huge photos
        // (modern phone cameras easily produce 4000x3000+) instead of
        // risking an OOM decoding at full size.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        }
        int maxDimension = Math.max(bounds.outWidth, bounds.outHeight);
        int inSampleSize = 1;
        while (maxDimension / inSampleSize > 4096) inSampleSize *= 2;

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        Bitmap decoded;
        try (InputStream dataStream = getContentResolver().openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(dataStream, null, decodeOptions);
        }
        if (decoded == null) throw new IOException("BitmapFactory returned null for " + uri);

        int rotationDegrees = 0;
        boolean flip = false;
        try (InputStream exifStream = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(exifStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotationDegrees = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotationDegrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotationDegrees = 270; break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: flip = true; break;
                default: break;
            }
        }
        if (rotationDegrees == 0 && !flip) return decoded;

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        if (flip) matrix.postScale(-1, 1);
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees);
        Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
        decoded.recycle();
        return rotated;
    }

    private void setupBlockSpinner() {
        List<String> labels = new ArrayList<>();
        for (OmrBlock block : workingCopy.blocks) labels.add(block.label);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        blockSpinner.setAdapter(adapter);

        blockSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // Auto-apply whatever the user was dragging on the block they're leaving,
                // so switching blocks to check on something doesn't silently discard work.
                if (activeBlockLabel != null) applyActiveBlock(/*silent=*/true);

                activeBlockLabel = labels.get(position);
                overlay.setActiveBlock(activeBlockLabel);
                instructions.setText("Block: " + activeBlockLabel
                        + " -- drag the green dot onto its first bubble, red onto its last.");
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        activeBlockLabel = labels.get(0);
    }

    /**
     * Derives geometry from the active block's current handle positions and
     * writes it into the in-memory working copy. This does NOT touch disk --
     * only "Save All" persists anything. Safe to call repeatedly (e.g. on
     * every spinner switch) since it's a pure re-derivation, not a toggle.
     */
    private void applyActiveBlock(boolean silent) {
        if (activeBlockLabel == null) return;
        Point[] handles = overlay.getHandles(activeBlockLabel);
        if (handles == null) return;

        for (OmrBlock block : workingCopy.blocks) {
            if (!block.label.equals(activeBlockLabel)) continue;

            boolean landscapeContent = workingCopy.width > workingCopy.height;
            TemplateCalibrator.BlockGeometry g = TemplateCalibrator.deriveBlockGeometry(
                    overlay.getForwardHomography(), handles[0], handles[1],
                    landscapeContent, workingCopy.width, workingCopy.height,
                    block.rows, block.cols);

            block.startX = g.startX;
            block.startY = g.startY;
            block.dx = g.dx;
            block.dy = g.dy;

            if (!silent) {
                Toast.makeText(this, "Applied " + activeBlockLabel
                        + "  (start=" + round(g.startX) + "," + round(g.startY)
                        + "  d=" + round(g.dx) + "," + round(g.dy) + ")", Toast.LENGTH_SHORT).show();
            }
            return;
        }
    }

    private void saveAll() {
        applyActiveBlock(/*silent=*/true); // capture whatever's currently being dragged

        try {
            templateManager.saveOverrideTemplate(this, workingCopy);
            Toast.makeText(this, workingCopy.templateId + " calibration saved. Used on next scan.", Toast.LENGTH_LONG).show();
            finish();
        } catch (IllegalArgumentException validationError) {
            new AlertDialog.Builder(this)
                    .setTitle("Calibration not saved")
                    .setMessage(validationError.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save override", e);
            new AlertDialog.Builder(this)
                    .setTitle("Save failed")
                    .setMessage("Could not write calibration file: " + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}