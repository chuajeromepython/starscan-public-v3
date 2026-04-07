# OMR Scanner Update Summary

## What stayed from the earlier improvement

The earlier performance improvement is still intact.

- Auto-capture now triggers immediately on the first frame where all 4 anchors are validly detected.
- The old multi-frame confirmation delay is gone.
- The old frame-skipping delay is gone.
- The live camera path no longer uses the expensive `NV21 -> JPEG -> Bitmap` conversion before running anchor detection.
- The visual overlay smoothing still exists, but it is no longer part of the capture decision.

In simple terms:

1. The camera analyzes the latest frame.
2. If the 4 corner anchors are found and validated, the app captures immediately.
3. The app no longer waits for a “stay steady” countdown before snapping.

## What was added for fixed-mount use

The new work adds support for an elevated fixed-mount setup where the phone stays in one place and users slide sheets underneath it.

This was added without removing the normal handheld behavior.

### New camera modes

The app now offers two camera modes when opening the camera:

- `Handheld Scan`
- `Fixed Mount Scan`

### Handheld Scan

This keeps the fast close-up behavior.

- Best for normal use where the user holds the phone and can move closer to the sheet.
- Immediate auto-capture is still active.
- Detection remains optimized for sheets that occupy a larger part of the frame.

### Fixed Mount Scan

This adds distance compensation for elevated mounting setups.

- Best for installations where the phone is mounted above a desk or tray.
- Helps when smaller sheets, such as 30-item or 40-item forms, occupy only a small part of the camera frame.
- Immediate auto-capture is still active once anchors are found.

## What fixed-mount compensation does

When `Fixed Mount Scan` is used, the app now does the following:

### 1. Uses more tolerant anchor thresholds

The detector now accepts smaller anchor candidates than before.

This helps when:

- the camera is mounted higher above the paper
- the paper occupies only a small portion of the frame
- the 4 black corner anchors appear much smaller in the live preview

### 2. Relaxes corner spacing validation for distant sheets

Previously, anchors could fail if the whole sheet looked too small inside the full camera frame.

Now, in fixed-mount mode:

- corner validation considers the detected sheet span itself, not only the full frame size
- small but valid sheets are less likely to be rejected just because they are clustered near the center

### 3. Adds a far-distance fallback pass

If the first fixed-mount detection pass fails, the detector runs one more fallback pass using a more distance-tolerant profile.

This helps when:

- the sheet is especially small in the frame
- anchors are near the lower limit of visibility

### 4. Allows limited scale-up for distant small sheets

The detector can now conditionally enlarge the grayscale analysis image during the far-distance fallback pass.

Important:

- it does **not** upscale every frame
- it only happens in the fixed-mount far fallback path
- this avoids wasting CPU on handheld scans and normal frames

### 5. Adds bounded camera zoom stepping

In fixed-mount mode, if the app keeps missing anchors for several frames, it can step the camera zoom upward in a controlled way.

Current zoom ladder:

- `1.0x`
- `1.25x`
- `1.5x`
- `1.75x`

This helps make the sheet larger in the frame without requiring the user to physically move the phone.

Important:

- zoom only advances after repeated misses
- zoom does not keep changing every frame
- once anchors are found, capture still happens immediately

## How false positives are controlled

Because fixed-mount mode allows smaller anchor candidates, the detector also became stricter in other ways to avoid random false detections.

The app now also checks:

- solidity of the candidate region
- darkness of the candidate region
- fill ratio inside the thresholded candidate box
- duplicate-corner rejection
- corner layout sanity

This means the detector is more tolerant of small distant anchors, but still tries to reject text, noise, and random dark blobs.

## What happens now in each mode

### Handheld mode flow

1. Analyze live frame.
2. Run the normal fast live detector.
3. If 4 anchors are valid, capture immediately.

### Fixed-mount mode flow

1. Analyze live frame.
2. Run the fixed-mount base detector profile.
3. If that fails, run the fixed-mount far-distance fallback profile.
4. If repeated misses continue, step camera zoom upward.
5. As soon as 4 anchors are valid, capture immediately.

## Important clarification about speed in fixed-mount mode

Fixed-mount mode is still fast in terms of auto-capture.

The capture itself is **not** delayed once the anchors are detected.

That means:

- if the anchors are already detectable at the current zoom and analysis scale, the app captures immediately
- the app does **not** wait for extra stable frames before snapping
- the app does **not** add a separate confirmation delay after valid detection

What can make fixed-mount mode feel slower is the time spent trying to make the anchors detectable in the first place.

That extra time can come from:

- the first fixed-mount detection pass missing the sheet
- the far-distance fallback detection pass running after a miss
- the camera stepping through zoom levels so the sheet appears larger in frame

So the delay, when it happens, is in the **detection recovery stage**, not in the **capture trigger stage**.

In short:

- **anchor found = capture immediately**
- **anchor too small or not yet detectable = fallback and zoom logic may add time before detection succeeds**

## Performance tradeoff

### What remains fast

- Handheld mode remains the fast path.
- Immediate auto-capture remains intact.
- The old heavy bitmap conversion remains removed.

### What becomes slightly heavier

Fixed-mount mode is more expensive than handheld mode because it may:

- run a second detection pass after a miss
- conditionally upscale the analysis image
- occasionally adjust camera zoom

This is intentional and limited only to the fixed-mount use case.

## Can the fixed-mount feature be reverted later?

Yes.

The earlier immediate auto-capture optimization is separate enough that the fixed-mount additions can be removed later if needed while keeping:

- immediate auto-capture
- no stability countdown
- no frame skipping
- no live `NV21 -> JPEG -> Bitmap` conversion

In other words:

- the **fast auto-capture improvement** can remain
- the **fixed-mount support** can still be rolled back later if testing shows it needs adjustment

## Overall result

The app now supports both use cases:

- fast handheld scanning with immediate auto-capture
- elevated fixed-mount scanning with distance compensation for smaller sheets

The earlier improvement was not replaced. It is still active, and the fixed-mount work was added on top of it.