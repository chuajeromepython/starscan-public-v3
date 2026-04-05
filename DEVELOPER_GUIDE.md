# OMRScanner Developer Guide

## Purpose

This document is the maintainer handoff for the `OMRScanner` Android app. It explains:

- how the project is laid out
- how the camera and OMR pipeline work end to end
- where data is stored
- where recent performance and fixed-mount behavior live
- what parts are primary, legacy, or risky to change

The app is a Java-based Android application that scans OMR answer sheets using CameraX and OpenCV, aligns the sheet, detects the template, reads LRN and answers, then stores and exports the results.

## High-Level Architecture

The codebase is organized around two major subsystems:

1. Dashboard/data management
   - class and assessment management
   - scan history
   - answer key assignment and grading
   - export flows

2. OMR capture and processing
   - live camera preview and auto-capture
   - anchor detection
   - perspective correction
   - template/orientation detection
   - bubble scanning

This is not an MVVM codebase. It is activity-driven, with helper classes used to keep `DashboardActivity` from becoming even larger than it already is.

## Project Structure

### Top level

- `app/`
  - main Android application module
- `sdk/`
  - imported OpenCV Android SDK module
- `gradle/`, `gradlew`, `gradlew.bat`
  - Gradle wrapper and version catalog support
- `FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md`
  - recent scanning behavior summary
- `answer_key_implementation_plan.md`
  - planning artifact

### Main app code

Under `app/src/main/java/com/example/omrscanner/`:

- `MainActivity`
  - current launcher/splash router
- `DashboardActivity`
  - central app shell and main navigation host
- `BetaExpiredActivity`
  - blocks use after the configured beta expiry date
- `camera/`
  - live camera preview, analyzer, overlay
- `dashboard/`
  - dashboard UI helpers, renderers, dialogs, export logic
- `database/`
  - Room database, DAOs, repository, mappers, entities, projections
- `models/`
  - legacy/in-memory domain models still used heavily by the UI
- `omr/`
  - OpenCV and sheet-processing pipeline
- `ui/`
  - result screen, preview screen, scan detail, CSV view, etc.
- `utils/`
  - storage, CSV helpers, image utilities, beta expiry checker

### Templates

Under `app/src/main/assets/templates/`:

- `ZPH30.json`
- `ZPH40.json`
- `ZPH50.json`
- `ZPH60.json`

These JSON files define the coordinate system and bubble blocks for each supported OMR sheet type.

### Resources

Primary Android resources live under `app/src/main/res/`:

- `layout/`
- `drawable/`
- `font/`
- `values/`
- `xml/`

There are also extra directories in the repo such as `app/res-backgrounds`, `app/res-buttons`, `app/res-components`, `app/res-icons`, plus matching folders under `app/src/main/`. These are not standard Android resource directories and are not wired in `app/build.gradle` via `sourceSets`. Treat them as design/staging folders unless you verify they are intentionally consumed elsewhere.

## Build and Runtime Basics

### Toolchain

- App module:
  - `compileSdk 36`
  - `targetSdk 36`
  - `minSdk 24`
  - Java 11
- OpenCV SDK module:
  - imported as `:sdk`
  - based on OpenCV `4.12.0`

### Important dependencies

From `app/build.gradle`:

- CameraX `1.3.2`
  - `camera-core`
  - `camera-camera2`
  - `camera-lifecycle`
  - `camera-view`
- OpenCV via `implementation project(":sdk")`
- Room `2.6.1`
- Gson `2.10.1`
- Zip4j `2.11.5`
- Material Components

### Useful commands

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugJavaWithJavac
```

## App Startup Flow

### Entry path

1. `MainActivity`
   - shows splash UI briefly
   - calls `BetaExpiryChecker.isExpired()`
2. If expired
   - opens `BetaExpiredActivity`
3. If valid
   - opens `DashboardActivity`

### Important note

`ui/SplashActivity` still exists, but the manifest launcher is `MainActivity`, not `SplashActivity`. Treat `SplashActivity` as a secondary or legacy screen unless product requirements explicitly bring it back.

## Main User Flows

### Dashboard flow

`DashboardActivity` is the main coordinator. It delegates rendering and dialog construction to:

- `dashboard/HomeScreenRenderer`
- `dashboard/ClassScreenRenderer`
- `dashboard/ActivityScreenRenderer`
- `dashboard/DashboardDialogs`
- `dashboard/DashboardUiHelper`
- `dashboard/ClassExporter`

The dashboard handles:

- teacher profile
- class creation and filtering
- assessment creation and filtering
- scan method selection
- exporting class data
- opening scan details

### Scan entry points

There are two scan entry paths:

1. Camera flow
   - `DashboardActivity -> CameraActivity -> ResultActivity`
   - current primary flow

2. Gallery/manual preview flow
   - `DashboardActivity -> PreviewActivity -> ResultActivity`
   - still valid, but no longer the main camera path

`PreviewActivity` is still useful for gallery imports and manual anchor confirmation on a saved image.

## Camera and Auto-Capture Flow

The live capture logic is in `camera/CameraActivity`.

### Current behavior

Recent changes made auto-capture immediate once anchors are validly detected.

What this means:

- no multi-frame stability countdown before capture
- no analyze-every-third-frame skipping
- no live `NV21 -> JPEG -> Bitmap` conversion path
- overlay smoothing remains, but it no longer delays capture

### Camera stack

`CameraActivity` binds three CameraX use cases:

- `Preview`
- `ImageCapture`
- `ImageAnalysis`

`ImageAnalysis` uses:

- `STRATEGY_KEEP_ONLY_LATEST`
- target analysis resolution of `1280x720`

That keeps the analyzer responsive on lower-end devices by dropping stale frames instead of queueing them.

### Handheld vs fixed-mount mode

`CameraActivity` supports two live detection modes:

- handheld
- fixed mount

The mode is selected from `DashboardActivity`, which now presents:

- `Handheld Scan`
- `Fixed Mount Scan`

The choice is passed via `CameraActivity.EXTRA_FIXED_MOUNT_MODE` and persisted in shared preferences.

### Immediate capture trigger

The live loop is:

1. analyzer receives `ImageProxy`
2. `AnchorDetector.detectAnchors(imageProxy, mode)` runs
3. if 4 valid anchors are returned
   - overlay is updated
   - `takePhoto()` is called immediately

There is no confirmation countdown anymore. The first valid detection frame wins.

### Fixed-mount behavior

Fixed-mount mode exists for elevated, hands-free installations where users slide sheets under a mounted phone.

It adds:

- more tolerant anchor-detection thresholds for distant sheets
- a second far-distance detection fallback pass
- bounded zoom stepping when repeated misses occur

Important behavior detail:

- fixed-mount mode is still immediate once anchors are detected
- the only extra latency happens before detection succeeds
- the extra time comes from fallback processing and zoom adjustment when anchors are too small at the current framing

### Overlay behavior

`camera/AnchorOverlayView` draws:

- 4 labeled anchor boxes
- connecting lines between the corners

CameraActivity still smooths overlay points with EMA-style logic to reduce flicker, but that smoothing is now visual only.

## OMR Processing Pipeline

The main processing classes are under `omr/`.

### 1. Anchor detection

Class: `omr/AnchorDetector`

Responsibilities:

- detect the 4 corner black squares on the OMR sheet
- support still-image and live-camera paths
- support handheld and fixed-mount live profiles

#### Still-image path

`detectAnchors(Bitmap bitmap)`

- used by preview/manual flows and fallback processing
- scales the image to a max dimension
- runs grayscale, blur, adaptive threshold, morphology, contour filtering

#### Live-camera path

`detectAnchors(ImageProxy imageProxy, LiveDetectionMode mode)`

- reads the Y plane directly from `ImageProxy`
- avoids expensive bitmap conversions
- uses profile-specific detection thresholds

#### Current live profiles

- `LIVE_HANDHELD_PROFILE`
- `LIVE_FIXED_BASE_PROFILE`
- `LIVE_FIXED_FAR_PROFILE`

Fixed-mount uses a base pass first, then a farther-distance fallback pass if the base pass misses.

#### False-positive control

To compensate for looser fixed-mount thresholds, anchor filtering also checks:

- area ratio
- aspect ratio
- solidity
- darkness mean
- thresholded fill ratio
- polygon shape
- convexity
- distinct corner selection
- corner span / corner distance validation

This is important: the distant-sheet support did not simply lower thresholds and accept anything dark.

### 2. Perspective alignment

Class: `omr/PerspectiveAligner`

Responsibilities:

- validate the 4-anchor geometry
- warp the photographed sheet into a fixed canonical rectangle

Canonical output size:

- width: `1000`
- height: `1414`

That fixed size is critical because all downstream template coordinates assume a stable reference space.

### 3. Template and orientation detection

Class: `omr/TemplateManager`

Responsibilities:

- load JSON templates from `assets/templates`
- detect sheet type using alignment scoring
- determine the correct rotation/orientation
- provide template lookup

Detection is based on `GridAligner`, not simple pixel probes.

### 4. Grid alignment

Class: `omr/GridAligner`

Responsibilities:

- find local offsets for bubble blocks using template matching
- improve robustness when the perspective warp is close but not perfect

This is used both for:

- sheet/template detection
- per-block bubble scanning alignment

### 5. Bubble scanning

Class: `omr/BubbleScanner`

Responsibilities:

- scan LRN and question bubbles on the aligned image
- produce `ScanResult`
- generate an overlay image showing detected filled/empty bubbles

Key idea:

- this is pixel-density based
- it does not use contour detection for each bubble
- fill ratio threshold decides filled vs empty

Current main fill threshold:

- `FILL_THRESHOLD = 0.45`

### 6. Scan result model

Class: `omr/ScanResult`

Contains:

- `templateId`
- `lnr`
- `undetectedLnrPositions`
- `doubleShadedLnrPositions`
- `answers`
- `overlayBitmap`

## Template System

### Template data model

Templates are parsed into:

- `OmrTemplate`
- `OmrBlock`

Each block defines:

- `label`
- `rows`
- `cols`
- `start_x`
- `start_y`
- `dx`
- `dy`
- `radius`

Bubble centers are computed from those values.

### Supported templates

Currently supported:

- `ZPH30`
- `ZPH40`
- `ZPH50`
- `ZPH60`

### Adding a new sheet type

To add a new template safely:

1. add a new JSON file under `app/src/main/assets/templates/`
2. add the file name to `TemplateManager.TEMPLATE_FILES`
3. verify `AssessmentEntity.getNumItems()` and `ActivityFolder.getNumItems()`
4. verify answer-key sizing logic also understands the new sheet type
5. test orientation detection and alignment scoring
6. test anchor placement and bubble scan accuracy on real paper

## Result Processing and Persistence Flow

`ui/ResultActivity` is where captured or imported sheets are processed and committed.

### Result flow

1. load original image
2. use passed anchor points if available, otherwise detect anchors again
3. validate anchors
4. warp with `PerspectiveAligner`
5. detect orientation and template using `TemplateManager`
6. scan bubbles with `BubbleScanner`
7. present image overlay and LRN verification UI
8. export/save results

### LRN verification

`ResultActivity` requires user confirmation of the detected LRN before final save/export. It also tracks:

- undetected LRN digits
- double-shaded LRN columns

This is one of the last guardrails before a scan becomes stored data.

### Retake behavior

`ResultActivity` now propagates `EXTRA_FIXED_MOUNT_MODE` when returning to `CameraActivity`, so fixed-mount mode survives retakes.

## Data Layer

The app uses Room as the durable source of truth, but the UI still leans on legacy model objects.

### Database classes

- `database/AppDatabase`
- `database/OMRRepository`
- `database/DataMapper`

### Entities

- `TeacherEntity`
- `ClassEntity`
- `AssessmentEntity`
- `ScanEntity`
- `AnswerEntity`
- `AnswerKeyEntity`

### Relationship model

Logical hierarchy:

- teacher
  - classes
    - assessments
      - scans
        - answers

Separately:

- answer keys are global/reusable
- assessments soft-link to answer keys with nullable `answer_key_id`

### Schema summary

- `teachers`
  - one teacher profile for the app/user
- `classes`
  - grade, section, school year
- `assessments`
  - activity/exam name, sheet type, exam date, optional answer key
- `scans`
  - image paths, detected bubble count, optional graded score, LRN
- `answers`
  - one row per scan/question item
- `answer_keys`
  - reusable correct-answer sets

### Important repository behavior

`OMRRepository` uses a single-thread executor and provides callback-style methods.

Important gotcha:

- despite the comment saying callbacks return to the main thread, they do not
- repository callbacks execute on the repository executor thread
- callers that touch UI must wrap updates in `runOnUiThread`

This is already visible in several activities and should be preserved unless the repository contract is intentionally changed.

### Data mapping layer

`DataMapper` converts between:

- Room entities
- legacy UI/domain models like `ClassFolder`, `ActivityFolder`, `ScanEntry`

That bridge is important because the app is in a partially modernized state:

- persistence is Room-based
- much of the dashboard/UI still expects model objects

## Key Activities and Responsibilities

### `MainActivity`

- app launch gate
- beta expiry routing

### `DashboardActivity`

- main app shell
- class/activity browsing
- scan entry point
- mode selection for handheld vs fixed mount

### `camera/CameraActivity`

- live preview
- anchor analysis loop
- fixed-mount zoom stepping
- immediate auto-capture

### `ui/PreviewActivity`

- preview imported image
- detect anchors on a still image
- manual confirmation path before scan

Note: camera capture currently goes directly to `ResultActivity`, so `PreviewActivity` is no longer the normal camera path.

### `ui/ResultActivity`

- alignment
- template/orientation detection
- bubble scan
- LRN confirmation
- save/export

### `ui/ScanDetailActivity`

- inspect stored scan results
- edit answers and LRN
- display graded score when answer key exists

### `ui/CSVFileActivity`

- share CSV output
- save CSV to Downloads

## Export and File Handling

### CSV utilities

Relevant files:

- `utils/CsvHelper`
- `utils/CSVExporter`
- `ui/CSVFileActivity`

### Class export

Class-level export is handled by `dashboard/ClassExporter`.

It:

- collects class data
- compresses images for export
- writes CSVs
- builds a password-protected ZIP using Zip4j

### Storage note

`ClassExporter` and `CSVFileActivity` still rely on public Downloads directory style APIs. This works for the current project setup, but it should be re-evaluated carefully for long-term scoped-storage compatibility on newer Android versions.

### Legacy storage utility

`utils/StorageManager` persists folders/images/CSV metadata in shared preferences and private file directories.

At the time of writing, the main production flow is Room-centric, and `StorageManager` appears legacy or secondary. Do not expand it unless you have confirmed a real active use case.

## Current Tuning and Maintenance Hotspots

If you need to tune scanning behavior, these are the first files to inspect:

### Live anchor detection sensitivity

File: `omr/AnchorDetector`

Look for:

- `DetectionProfile`
- `LIVE_HANDHELD_PROFILE`
- `LIVE_FIXED_BASE_PROFILE`
- `LIVE_FIXED_FAR_PROFILE`

Use this file when tuning:

- distant-sheet detection
- false positives
- contour filtering
- fill ratio filtering
- live image scaling

### Fixed-mount zoom behavior

File: `camera/CameraActivity`

Look for:

- `FIXED_MOUNT_ZOOM_STEPS`
- `FIXED_MOUNT_MISS_THRESHOLD`
- `FIXED_MOUNT_ZOOM_COOLDOWN_MS`

Use this file when tuning:

- how aggressively zoom adjusts
- how quickly fixed-mount mode reacts to misses
- whether zoom resets too often

### Bubble read sensitivity

File: `omr/BubbleScanner`

Main tuning point:

- `FILL_THRESHOLD`

Change this only with real sample sheets. It directly affects false positives vs faint-mark misses.

### Alignment sensitivity

File: `omr/GridAligner`

Main tuning points:

- `SEARCH_MARGIN`
- `MIN_MATCH_SCORE`

### Template detection confidence

File: `omr/TemplateManager`

Main tuning points:

- `DETECTION_MIN_SCORE`
- `ROTATION_TIE_MARGIN`

### Beta release gate

File: `utils/BetaExpiryChecker`

Main tuning points:

- `FORCE_EXPIRED`
- expiry year/month/day

## Known Architectural Realities

These are the things future maintainers should understand before refactoring:

### 1. The codebase is hybrid, not clean-layered

- dashboard logic is activity-centric
- UI state is not centralized in ViewModels
- legacy model classes still coexist with Room entities

### 2. `DashboardActivity` is still the orchestration center

Even after helper extraction, it remains the hub for:

- navigation
- current class/activity selection
- scan launch
- filters
- dialog interactions

### 3. Repository callbacks are background-thread callbacks

Do not assume `OMRRepository` returns on the UI thread.

### 4. Real-world sheet testing matters more than synthetic tuning

Most detection constants are image-quality sensitive. Do not tune thresholds based only on emulators or screenshots.

### 5. Camera and still-image paths are intentionally different

- live path is optimized for speed
- still path is more tolerant and can afford heavier processing

Do not “simplify” them into one identical pipeline unless you re-test on low-end devices.

## Testing Status

Current automated test coverage is minimal.

Existing tests:

- `app/src/test/.../ExampleUnitTest.java`
- `app/src/androidTest/.../ExampleInstrumentedTest.java`

In practice, this project currently depends heavily on manual testing for:

- different sheet types
- different lighting conditions
- handheld vs fixed-mount scanning
- answer-key grading flows
- export flows

## Recommended Manual Regression Checklist

Whenever you touch the scan pipeline, manually verify:

1. handheld close-up capture still snaps immediately on valid anchors
2. fixed-mount mode still detects and captures small distant sheets
3. overlay boxes line up with the visible anchors
4. perspective alignment still produces a sensible sheet crop
5. correct template is chosen for ZPH30/40/50/60
6. LRN detection still flags undetected and double-shaded columns
7. answers still save correctly to DB
8. retake from `ResultActivity` preserves the selected scan mode
9. class export still produces images, CSVs, and ZIP output

## Suggested Next Refactors

These are the highest-value cleanup opportunities:

1. formalize the scan pipeline contract
   - separate capture, alignment, detection, grading, and persistence more clearly
2. move repository callbacks to main-thread dispatch or document a strict contract in code
3. reduce `DashboardActivity` responsibilities further
4. decide whether `PreviewActivity`, `SplashActivity`, and `StorageManager` are active or legacy
5. add real instrumentation/manual test scripts for scan regression

## File Map for New Maintainers

If you only have 10 minutes to find the right file:

- live auto-capture behavior
  - `app/src/main/java/com/example/omrscanner/camera/CameraActivity.java`
- anchor detection thresholds
  - `app/src/main/java/com/example/omrscanner/omr/AnchorDetector.java`
- perspective warp
  - `app/src/main/java/com/example/omrscanner/omr/PerspectiveAligner.java`
- template/orientation detection
  - `app/src/main/java/com/example/omrscanner/omr/TemplateManager.java`
- bubble read logic
  - `app/src/main/java/com/example/omrscanner/omr/BubbleScanner.java`
- dashboard scan launch and mode selection
  - `app/src/main/java/com/example/omrscanner/DashboardActivity.java`
- Room schema
  - `app/src/main/java/com/example/omrscanner/database/AppDatabase.java`
- repository access patterns
  - `app/src/main/java/com/example/omrscanner/database/OMRRepository.java`
- data mapping between entities and UI models
  - `app/src/main/java/com/example/omrscanner/database/DataMapper.java`

## Final Notes

This app’s core value is not just “can it scan a sheet.” It is:

- can it scan quickly on real Android hardware
- can it survive shaky capture conditions
- can it persist results correctly
- can it support both handheld and fixed-mount deployment

When making changes, optimize for real paper, real lighting, and real devices first.