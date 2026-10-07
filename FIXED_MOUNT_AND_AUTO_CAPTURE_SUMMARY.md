# Fixed-Mount Support and Auto-Capture: Status and History

> **Read this first.** This file started as a change summary for two features: immediate auto-capture, and fixed-mount distance compensation. The code for both still exists, but **most of it is no longer what the app runs by default**. Guide-square mode and ArUco mode were added afterward and now sit in front of it. This version records what is true today, keeps the history, and explains how to bring the old behavior back.
>
> Audited against `main` at commit `c699946` by reading the code. Nothing here was run on a device. See also [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md#camera-modes).

---

## Status at a glance

| Claim from the original summary | Today |
| --- | --- |
| The dialog offers "Handheld Scan" and "Fixed Mount Scan" | The dialog offers **"Fixed Mount"** and **"Handheld"**, but they mean something different now (see below). |
| Fixed Mount adds distance compensation (tolerant profile, far fallback, zoom stepping) | **Not reachable from the UI.** The UI never sends `fixedMountMode = true`. The code is intact. |
| Capture triggers on the first frame with 4 valid anchors | **Only true on the legacy whole-frame path, which is switched off.** Both live modes now require sustained locking first. |
| No frame skipping | Mostly true. One exception: the handheld recovery pass runs on every second frame after 3 misses (legacy path only). |
| No `NV21 -> JPEG -> Bitmap` conversion on the live path | **Still true.** Live detection reads the Y plane directly (`AnchorDetector.toGrayMat`). |
| Overlay smoothing does not affect capture | Still true. |
| Upscaling happens only in the fixed-mount far pass | Mostly true. The handheld **recovery** profile also upscales (up to 1.25x). |

---

## How the camera modes map today

`DashboardActivity.showCameraModeDialog` always calls `launchCamera(false, tiltAgnostic)`, so `fixedMountMode` is always `false`.

| Dialog label | Flags | Live path in `CameraActivity` | When it captures |
| --- | --- | --- | --- |
| "Fixed Mount" | `fixedMount=false`, `tiltAgnostic=false` | `analyzeFrameGuideSquareMode` | When all four on-screen guide squares are locked. Each needs 8 consecutive hits and survives a 1 s dropout. Capture is blocked unless the phone is tilted to the one supported orientation. |
| "Handheld" | `fixedMount=false`, `tiltAgnostic=true` | `analyzeFrameArucoIdentityMode` | When all four ArUco markers (IDs 0 to 3) are locked per marker (8 hits, 1 s grace), then 5 consecutive full detections. |
| *(not offered)* | `fixedMount=false`, guide squares off | `analyzeFrameWholeFrameLegacy` | **First valid 4-anchor frame** (`REQUIRED_CONSECUTIVE_DETECTIONS = 1`). This is the behavior the original summary describes. |
| *(not offered)* | `fixedMount=true`, guide squares off | `analyzeFrameWholeFrameLegacy` | Same, with the fixed-mount profiles and zoom stepping. |

The legacy path is chosen only when `GUIDE_SQUARE_MODE_ENABLED` is `false` and `tiltAgnosticMode` is `false`. It is currently `true`.

---

## History: what the original change did

Two pieces of work, in order:

1. **Immediate auto-capture.** Removed the multi-frame "stay steady" countdown and the analyze-every-third-frame skipping, and removed the per-frame `NV21 -> JPEG -> Bitmap` conversion. The analyzer now reads the Y plane straight from `ImageProxy`. Overlay smoothing stayed, but only for display.
2. **Fixed-mount support**, added on top, for an elevated phone with sheets slid underneath. Smaller 30- and 40-item sheets occupy only a small part of the frame in that setup, so the corner anchors look tiny.

The first item survived all later changes. The second is dormant. Guide-square and ArUco modes replaced the whole-frame approach as the default after both were written.

---

## The fixed-mount mechanism (still in the code)

Everything below only runs when `EXTRA_FIXED_MOUNT_MODE` is `true` **and** the legacy whole-frame path is active.

### Detection profiles (`omr/AnchorDetector`)

`detectAnchors(imageProxy, FIXED_MOUNT)` tries the base profile first, then the far profile if the base pass misses.

| Profile | Analysis size | Min anchor area ratio | Solidity (min) | Darkness mean (max) | Fill ratio (min) | Upscale |
| --- | --- | --- | --- | --- | --- | --- |
| `LIVE_HANDHELD` | 960 px | 0.0003 | 0.65 | 150 | 0.45 | no |
| `LIVE_HANDHELD_RECOVERY` | 1280 px | 0.00012 | 0.62 | 155 | 0.42 | up to 1.25x |
| `LIVE_FIXED_BASE` | 960 px | 0.00005 | 0.72 | 140 | 0.50 | no |
| `LIVE_FIXED_FAR` | 1440 px | 0.00003 | 0.78 | 125 | 0.55 | up to 1.5x |

(A `STILL` profile exists for still images. Values were read from the profile constants and may drift, so check the file before relying on them.)

### How the fixed-mount profiles stay safe

Smaller anchors are accepted, so the other checks get stricter to reject text and dark blobs: higher solidity, darker mean, higher fill ratio, duplicate-corner rejection, and a corner-layout sanity check. The fixed profiles also validate corner spacing against the detected sheet span (`minCornerSpanWidthRatio` and `minCornerSpanHeightRatio`, 0.08 each) rather than the whole frame, so a small but valid sheet near the centre is not rejected.

### Zoom stepping (`camera/CameraActivity`)

After repeated misses the camera zoom steps up through a fixed ladder, then wraps around:

| Constant | Value |
| --- | --- |
| `FIXED_MOUNT_ZOOM_STEPS` | 1.0, 1.25, 1.5, 1.75 (clamped to the device's zoom range) |
| `FIXED_MOUNT_MISS_THRESHOLD` | 6 misses |
| `FIXED_MOUNT_ZOOM_COOLDOWN_MS` | 250 ms |

Zoom does not change every frame, and once anchors are found capture is immediate on this path.

### Where the delay comes from

In fixed-mount mode, capture is not delayed after a valid detection. Any slowness comes before detection succeeds: the base pass missing, the far pass running, the zoom ladder stepping, or the 1440 px analysis image costing more CPU. That is the performance trade-off, and handheld detection stays the lighter path.

### The handheld recovery pass

On the legacy path without fixed-mount, after 3 misses (`HANDHELD_RECOVERY_MISS_THRESHOLD`) the analyzer also runs the recovery profile on every second frame (`HANDHELD_RECOVERY_FRAME_INTERVAL`). This is the one place frames are deliberately skipped.

---

## Do the fixed-mount parts also apply in guide-square or ArUco mode?

Partly, and only if someone passes `fixedMountMode = true`:

- **Guide-square mode:** the per-square detector (`detectAnchorInRegion`) switches to `LIVE_FIXED_BASE_PROFILE`. The far profile and zoom stepping are **not** used, because that path never calls `onDetectionMiss()`.
- **ArUco mode:** the flag changes nothing in detection. Zoom stepping can run because `onDetectionMiss()` is called on misses, but `initializeFixedMountZoomRatios` only builds a ladder when `fixedMountMode` is true.

**(verify)** These two bullets come from reading the call sites. Test on a device before relying on them.

---

## Re-enabling the original fixed-mount behavior

To get what the old summary describes:

1. In `CameraActivity`, set `GUIDE_SQUARE_MODE_ENABLED = false` so non-ArUco scans use the whole-frame detector.
2. In `DashboardActivity`, make a camera-mode option call `launchCamera(true, false)` so `EXTRA_FIXED_MOUNT_MODE` is `true`. `PREF_FIXED_MOUNT_MODE` is still defined there, unused.
3. Rename the dialog options so the labels match the behavior.
4. Test with the phone mounted at its real height, on real ZPH40 and ZPH60 sheets, in the lighting the installation will have.

Note the interactions with later work: turning guide squares off removes the on-screen guide boxes, but the tilt gate stays, because `takePhoto()` blocks capture whenever the scan is not in ArUco mode and the phone is not tilted to the supported orientation. That would be awkward for a fixed mount, so decide whether to relax it. The template orientation logic for non-ArUco captures assumes the tilt-right placement (`REQUIRED_PORTRAIT_ROTATION`). Check orientation on real captures after the change.

## Removing the dormant code instead

If the team decides fixed mount is not coming back, these can go together: the `LIVE_FIXED_BASE` and `LIVE_FIXED_FAR` profiles and the `FIXED_MOUNT` branch in `AnchorDetector`, the zoom ladder and miss counters in `CameraActivity`, `EXTRA_FIXED_MOUNT_MODE` and its pass-through in `ResultActivity` and `PreviewActivity`, and `PREF_FIXED_MOUNT_MODE`. Keep immediate Y-plane analysis, which everything else depends on.

---

## Summary

- The fast Y-plane analysis path is current and should stay.
- "Immediate capture on the first valid frame" is true only on the disabled legacy path. Today's modes lock corners over several frames first.
- Fixed-mount distance compensation exists in the code but is unreachable from the UI, and the dialog's "Fixed Mount" label now launches guide-square mode.
- Pick a direction: restore it properly (steps above) or delete it (list above). The current state, with dormant code and a misleading label, is the worst of both.
