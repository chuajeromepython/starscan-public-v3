# STARS (OMRScanner) Developer Guide

Maintainer notes for the Android app. For what the app does and how teachers use it, read the [README](README.md) first. This guide covers how the code is put together, where the risky parts are, and where to look when something breaks.

---

## Table of contents

1. [Orientation](#orientation)
2. [Project layout](#project-layout)
3. [What is live and what is legacy](#what-is-live-and-what-is-legacy)
4. [Build and runtime](#build-and-runtime)
5. [App startup](#app-startup)
6. [Dashboard architecture](#dashboard-architecture)
7. [Scanning pipeline](#scanning-pipeline)
8. [Templates and sheet types](#templates-and-sheet-types)
9. [Saving, grading and exporting scans](#saving-grading-and-exporting-scans)
10. [Data layer](#data-layer)
11. [Server sync and upload](#server-sync-and-upload)
12. [ECDC module](#ecdc-module)
13. [Backup and restore](#backup-and-restore)
14. [Tuning hotspots](#tuning-hotspots)
15. [Debugging](#debugging)
16. [Known issues and risks](#known-issues-and-risks)
17. [Testing](#testing)
18. [Suggested refactors](#suggested-refactors)
19. [File map](#file-map)

---

## Orientation

- Java only (no Kotlin), activity-driven, **not** MVVM. There are no ViewModels or LiveData.
- Most screens are built **programmatically** in Java (renderers in `dashboard/`), not from XML layouts.
- Persistence is Room. The UI still works with older in-memory model objects (`ClassFolder`, `ActivityFolder`, `ScanEntry`), and `DataMapper` converts between the two.
- Scanning uses CameraX for capture and OpenCV (including ArUco marker detection) for processing.
- Almost everything works offline. The network is used only for sync and upload (see [Server sync and upload](#server-sync-and-upload)).
- The app label is **STARS**. The package, Gradle project and many class names still say **OMRScanner**.

Two things shape most decisions here:

1. `DashboardActivity.java` is about 6,400 lines and is the orchestration hub. Helper classes in `dashboard/` take rendering and dialogs out of it, but it still owns navigation, state, sync, upload and the ECDC flow.
2. Scan detection constants are sensitive to real paper, real lighting and real phones. Do not tune them from emulators or screenshots.

---

## Project layout

### Top level

| Path | Purpose |
| --- | --- |
| `app/` | The Android application module |
| `sdk/` | Imported OpenCV Android SDK (Java wrappers plus native libs), version 4.12.0 |
| `gradle/`, `gradlew`, `gradlew.bat` | Wrapper (Gradle 9.1.0) and the version catalog `libs.versions.toml` (AGP 9.0.0) |
| `README.md` | Product and developer overview |
| `NEW_TEAM_QUICKSTART.md`, `FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md`, `answer_key_implementation_plan.md` | Older notes, **not audited**. The fixed-mount summary in particular describes behavior that the UI no longer reaches (see [Camera modes](#camera-modes)). |

### Java packages (`app/src/main/java/com/example/omrscanner/`)

```text
omrscanner
├── MainActivity            launcher: splash, Developer Options guard, then DashboardActivity
├── DashboardActivity       main host: tabs, navigation, sync, uploads, ECDC flow
├── BackupManager           backup .zip export and restore
├── DataInspector, DataExporter   debug dump helpers (see Debugging)
├── StudentDashboardActivity      empty placeholder for Student-role QR codes
├── BetaExpiredActivity     beta gate screen (gate currently disabled)
├── camera/                 CameraActivity, QrScannerActivity, AnchorOverlayView,
│                           BasicCameraActivity and FlatScanCameraActivity (not offered in UI)
├── dashboard/              Home/Class/Activity/Scans/Ecdc screen renderers, DashboardDialogs,
│                           DashboardUiHelper, ClassExporter, EcdcUploadPayloadBuilder
├── database/               AppDatabase, OMRRepository, DataMapper
│   ├── dao/  entities/  projections/
├── models/                 ClassFolder, ActivityFolder, ScanEntry, ... (in-memory UI models)
├── omr/                    detection, alignment, templates, bubble reading, calibration
├── ui/                     ResultActivity, ScanDetailActivity, ProModeCalibrationActivity, ...
└── utils/                  CsvHelper, CSVExporter, ImageUtils, StorageManager (unused), BetaExpiryChecker
```

### Assets and resources

- `app/src/main/assets/templates/`: `ZPH30.json`, `ZPH40.json`, `ZPH50.json`, `ZPH60.json`
- `app/src/main/res/`: layouts, drawables, fonts, values, xml
- `app/src/debug/res/xml/` and `app/src/release/res/xml/`: per-build `network_security_config.xml`
- `app/res-*` and matching `app/src/main/res-*` folders are **not** wired into Gradle (`app/build.gradle` has no `sourceSets` for them). Treat them as design or staging folders unless you confirm otherwise **(verify)**.

---

## What is live and what is legacy

A static search found no callers for the items in the second table. It could miss reflection or string-based intents, so confirm before deleting anything.

### Live

| Component | Role |
| --- | --- |
| `MainActivity` → `DashboardActivity` | Entry and main host |
| `camera/CameraActivity` | Live scanning (two modes, see below) |
| `ui/ResultActivity` | Align, detect, scan, verify LRN, save |
| `ui/ScanDetailActivity` | Inspect and correct a saved scan |
| `camera/QrScannerActivity` | Sign-in by QR code |
| `ui/ProModeCalibrationActivity` | Per-sheet-type calibration (launched from Profile) |
| `omr/ArucoAnchorDetector`, `omr/AnchorDetector`, `PerspectiveAligner`, `TemplateManager`, `GridAligner`, `BubbleScanner`, `TemplateCalibrator` | Scan pipeline |

### Present but unreachable, unused or disabled

| Component | Status |
| --- | --- |
| `ui/PreviewActivity` | Nothing starts it. `ResultActivity` still reads its extra-key constants (`IMAGE_PATH`, `ANCHOR_POINTS`, `IMAGE_SOURCE`), so do not delete the constants. The old gallery/manual-preview flow does not exist anymore. |
| `camera/BasicCameraActivity`, `ui/BasicPreviewActivity`, `camera/FlatScanCameraActivity` | Registered in the manifest and launcher methods exist in `DashboardActivity`, but the camera-mode dialog does not offer them. |
| `ui/CSVFileActivity`, `ui/FullImageViewerActivity`, `ui/LrnErrorActivity` | No `startActivity` call found. |
| `ui/SplashActivity` | Not in the manifest. `MainActivity` reuses its layout (`activity_splash.xml`). |
| `BetaExpiredActivity`, `utils/BetaExpiryChecker` | Gate is commented out in `MainActivity` (see [Known issues](#known-issues-and-risks)). |
| `utils/StorageManager` | No callers. Older shared-preferences file store. |
| `omr/BubbleDetector`, `omr/GuideRegion` | No callers. `BubbleScanner` does all bubble reading. |
| `AnchorDetector` fixed-mount profiles and zoom stepping | Only used when `EXTRA_FIXED_MOUNT_MODE` is true, which nothing passes today. |
| `StudentDashboardActivity` | Opens for Student-role QR codes. Empty stub. |
| Zip4j dependency | Declared in `app/build.gradle`, not imported anywhere. `BackupManager` uses `java.util.zip`. |

---

## Build and runtime

| Item | Value |
| --- | --- |
| `compileSdk` / `targetSdk` / `minSdk` | 36 / 36 / 24 |
| Java (app) | 11 |
| Java (`:sdk`) | 17 |
| Gradle / AGP | 9.1.0 / 9.0.0 |
| `versionCode` / `versionName` | 1 / "1.0" |

Main libraries: CameraX 1.3.2, OpenCV via `:sdk` (4.12.0, ArUco from `org.opencv.objdetect`), Room 2.6.1, ML Kit barcode-scanning 17.2.0 (QR sign-in), Gson 2.10.1, Material 1.11.0, RecyclerView, ViewPager2, ExifInterface.

```bash
./gradlew :app:assembleDebug            # macOS / Linux
.\gradlew.bat :app:assembleDebug        # Windows
./gradlew :app:compileDebugJavaWithJavac
```

### Build types

- **debug**: cleartext HTTP allowed. Lets you point the app at `http://192.168.x.x:8000`.
- **release**: cleartext HTTP **blocked**, minification off (`minifyEnabled false`). A release build only reaches a server over `https://`. The QR flow prepends `http://` to a bare `host:port`, so a release build will fail to sync against such a server.

### Permissions

`CAMERA`, `INTERNET`, `MANAGE_EXTERNAL_STORAGE` (all-files access, sideloaded app), `WRITE_EXTERNAL_STORAGE` up to API 28. The app asks for all-files access before uploads and exports (`runWithStoragePermission` in `DashboardActivity`). The camera feature is declared required.

---

## App startup

1. `MainActivity` shows the splash layout for 1.5 seconds.
2. If **Developer Options** is enabled (`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`), a blocking dialog appears and the app stops there. Add a bypass for debug builds if this gets in your way. Developers will hit it on any phone used for USB debugging.
3. Otherwise it starts `DashboardActivity` and finishes.

The beta expiry check is commented out, so the app always proceeds. See [Known issues](#known-issues-and-risks).

---

## Dashboard architecture

`DashboardActivity implements DashboardDialogs.DialogHost` and tracks a `currentScreen` string. Screen names:

| Screen constant | What it is |
| --- | --- |
| `home`, `class`, `activity` | Classes, a class's assessments, an assessment's scans |
| `assessments`, `quizzes`, `answerkeys`, `scans` | Top-level tabs |
| `user` | Profile tab (QR scan, photo, backup/restore, Pro Mode calibration) |
| `ecd`, `ecd_class`, `ecd_student` | ECDC list, class, and student checklist |

The tab list matches the table in the README. Screens are rendered by `HomeScreenRenderer`, `ClassScreenRenderer`, `ActivityScreenRenderer`, `ScansScreenRenderer` and `EcdcScreenRenderer`. Dialogs live in `DashboardDialogs` (about 2,200 lines) and shared view builders in `DashboardUiHelper`.

Things worth knowing:

- Back-navigation depends on which tab opened an assessment (`activityOpenedFromAssessmentsTab`, `activityOpenedFromQuizzesTab`). When you add an entry point, update the back logic.
- Creating answer keys and assessments goes through `showDisclaimerThen(...)`, which shows an accuracy disclaimer first.
- The floating action menu has a "test" row wired to `DataInspector.printAll()` plus `DataExporter.exportAll()`. It is a developer dump, not a feature.
- `DashboardActivity` also has `static` helpers used from other activities (`saveScanResult`, `hasSyncedStudentsRecently`, intent-extra constants such as `EXTRA_CLASS_ID`). Moving them means touching `ResultActivity` and the camera.
- Sync work runs on `syncExecutor` (a single-thread executor in `DashboardActivity`). Results are posted back with `runOnUiThread`.

---

## Scanning pipeline

```text
DashboardActivity.openCamera()
   -> camera-mode dialog -> launchCamera(fixedMount=false, tiltAgnostic=<choice>)
CameraActivity            CameraX Preview + ImageCapture + ImageAnalysis (keep-only-latest)
   -> anchors locked -> takePhoto()  (saves omr_capture.jpg in the app's external files dir)
ResultActivity.processImage()
   1. pre-rotate raw capture (tilt-agnostic bucket; currently always 0)
   2. anchors: ArUco on the full-res photo (tilt-agnostic) or geometric AnchorDetector (guide-square)
   3. validate anchors (orientation-agnostic check for ArUco, rejects mirrored layouts)
   4. PerspectiveAligner.alignPerspective(...)  -> 1414 x 1000 landscape canvas for ArUco captures
   5. TemplateManager: detect orientation (and sheet type if none preselected)
   6. buildTruncatedTemplate(base, itemCount)   -> only the items the assessment uses
   7. BubbleScanner.scan(...)                   -> LRN, answers, overlay bitmap
   8. UI: LRN verification, multi-mark review
   9. Save: CSV -> saveScanResult -> Room -> auto-export to Downloads/OMRScanner
```

### Camera modes

The mode dialog (`DashboardActivity.showCameraModeDialog`) offers two choices. **The labels do not match what they do.** Read this table before touching anything that mentions "fixed mount".

| Dialog label | Flags sent | `CameraActivity` path | Behavior |
| --- | --- | --- | --- |
| "Fixed Mount" (first option) | `fixedMount=false`, `tiltAgnostic=false` | `analyzeFrameGuideSquareMode` | Four on-screen **guide squares**. Detection runs only inside them (`AnchorDetector.detectAnchorInRegion`, handheld profile). Each corner needs 8 consecutive hits and keeps its lock for a 1 s grace period. The phone must be tilted to the one supported orientation (`REQUIRED_TILT_ROTATION = 90`), otherwise `takePhoto()` is blocked. Corners are labelled by position. |
| "Handheld" (second option) | `fixedMount=false`, `tiltAgnostic=true` | `analyzeFrameArucoIdentityMode` | Whole-frame ArUco detection (`DICT_4X4_50`). Corner identity comes from marker IDs 0 to 3 (TL, TR, BL, BR), so the sheet can be in any orientation. Per-marker lock of 8 hits with 1 s grace, then 5 consecutive full detections. No tilt gate. |
| *(not offered)* | `fixedMount=true` | either of the above, plus zoom stepping | Distance compensation: `LIVE_FIXED_BASE_PROFILE` then `LIVE_FIXED_FAR_PROFILE`, and zoom steps 1.0, 1.25, 1.5, 1.75 after 6 misses with a 250 ms cooldown. `PREF_FIXED_MOUNT_MODE` is kept in `DashboardActivity` but not surfaced. |

`GUIDE_SQUARE_MODE_ENABLED` is `true`. Setting it to `false` switches the non-tilt-agnostic path to the older whole-frame detector (`analyzeFrameWholeFrameLegacy`), which is where the fixed-mount profiles apply.

Capture itself is immediate once the lock conditions are met. There is no extra countdown. `ImageCapture` uses `CAPTURE_MODE_MAXIMIZE_QUALITY`.

`ResultActivity` forwards `EXTRA_FIXED_MOUNT_MODE` and `EXTRA_TILT_AGNOSTIC_MODE` back to `CameraActivity` on retake, so the chosen mode survives a retake.

The wide guide-square layout used for ZPH50/ZPH60 is marked in code as a placeholder derived from aspect ratio and not measured on a printed sheet. Since the UI offers ZPH60, recalibrate it on a real sheet before relying on it **(verify)**.

### Anchor detection

- `omr/ArucoAnchorDetector`: marker IDs `0 = TL`, `1 = TR`, `2 = BL`, `3 = BR`, dictionary `DICT_4X4_50`. The printed sheets must carry matching markers. Detector parameters are tuned for handheld motion blur.
- `omr/AnchorDetector`: contour-based detection of the black corner squares. It has five `DetectionProfile`s (`STILL`, `LIVE_HANDHELD`, `LIVE_HANDHELD_RECOVERY`, `LIVE_FIXED_BASE`, `LIVE_FIXED_FAR`). It filters on area ratio, aspect ratio, solidity, darkness, fill ratio, polygon shape and convexity, so loosening a threshold does not simply accept anything dark. `toGrayMat(ImageProxy)` reads the Y plane directly to avoid bitmap conversion on the live path.
- The live path (speed) and the still-image path (tolerance) are different on purpose. Do not merge them without retesting on low-end phones.

### Alignment, orientation, bubbles

| Class | What it does |
| --- | --- |
| `PerspectiveAligner` | Validates the four anchors and warps the sheet. Canonical size is 1000 x 1414, swapped to 1414 x 1000 when `landscapeContent` is true. ArUco captures always use landscape, because every shipped template is landscape and measuring edge lengths on a steeply angled photo can flip the result. |
| `TemplateManager` | Loads templates (bundled assets, or a Pro Mode override), scores orientation and sheet type by template-matching with `GridAligner`, builds truncated templates. |
| `GridAligner` | Finds a small per-block offset by template matching. Used for both orientation scoring and per-block bubble alignment. |
| `BubbleScanner` | Applies CLAHE, then reads the LRN block and question blocks by fill ratio inside each bubble. Produces `ScanResult` plus an overlay bitmap and an optional answer-key reference bitmap. |
| `ScanResult` | `templateId`, `lnr`, `undetectedLnrPositions`, `doubleShadedLnrPositions`, `answers`, `multiLetterAnswerPositions`, `overlayBitmap`, `keyReferenceBitmap`. |

Orientation has two regimes in `TemplateManager`:

- **Geometric anchors** (guide-square path): only one rotation is tested (`REQUIRED_PORTRAIT_ROTATION`, counter-clockwise), with a minimum confidence of `MIN_ORIENTATION_CONFIDENCE = 0.28`. That number was picked from two samples per template. Collect more logs before trusting it.
- **ArUco anchors** (tilt-agnostic path): a fixed content rotation (`ARUCO_RESOLVED_CONTENT_ROTATION`, clockwise) is applied. The code marks it `TODO verify empirically`.

`ResultActivity` refuses to continue, and asks for a retake, when the oriented bitmap's shape (landscape vs portrait) does not match the template, when anchors fail validation, or when orientation is low-confidence on a non-ArUco tilt-agnostic capture.

Bubble reading details:

- Question bubbles use `QUESTION_FILL_THRESHOLD = 0.18`. It was lowered from 0.45 after real pencil scans landed as low as 0.21 while blank bubbles stayed under about 0.05. **Older documentation that says 0.45 is outdated.**
- The LRN block is read separately with a per-template `LnrReadProfile`. The default profile uses 0.45 and compares the winner against the runner-up instead of counting cells over a flat cutoff, so uneven lighting does not create false "double-shaded" columns. ZPH60 has its own profile.
- Answers can contain several letters (for example `AC`). Those are flagged in `multiLetterAnswerPositions` and must be resolved before upload.

---

## Templates and sheet types

All four JSON templates are landscape and have four choices (A to D) per question and a 12-digit LRN block (`LNR`, 10 rows by 12 columns).

| Template | Size | Questions |
| --- | --- | --- |
| `ZPH30` | 1202 x 900 | 30 |
| `ZPH40` | 1202 x 900 | 40 |
| `ZPH50` | 1611 x 1138 | 50 |
| `ZPH60` | 1609 x 1134 | 60 |

### What the UI offers

Only `ZPH40` and `ZPH60` can be chosen when creating an assessment (`ZPH40` only for quizzes). After choosing the sheet, the teacher picks how many items the assessment uses, in steps of five:

- `ZPH40`: 5 to 40
- `ZPH60`: 45 to 60

The stored `sheetType` string therefore looks like `ZPH40 (30 Items)`. Helpers in `ActivityFolder` parse it:

- `parseBaseTemplateId("ZPH40 (30 Items)")` returns `ZPH40` (the physical bubble geometry)
- `parseItemCountFromSheetType(...)` returns `30`
- plain legacy values such as `ZPH50` still parse for old data

During a scan, `TemplateManager.buildTruncatedTemplate(base, itemCount)` drops trailing question blocks and shortens the boundary block, always keeping the LNR block, so unused bubbles are never read.

`ZPH30` and `ZPH50` still exist as templates and in several parsers, but cannot be selected in the UI.

### Adding a sheet type

1. Add the JSON under `app/src/main/assets/templates/` and add its name to `TemplateManager.TEMPLATE_FILES`.
2. Add it to the sheet pickers in `DashboardDialogs` (assessment, quiz and answer key dialogs each have their own list) and decide its item-count range.
3. Check `ActivityFolder.parseItemCountFromSheetType`, `AssessmentEntity.getNumItems()` and `AnswerKeyEntity.getNumItems()`.
4. Check `BubbleScanner.resolveLnrReadProfile` (it special-cases ZPH60), `CameraActivity.usesWideGuideGroup`, and the orientation scoring in `TemplateManager`.
5. Confirm the printed sheet carries the ArUco markers for IDs 0 to 3.
6. Test on real printed sheets.

### Pro Mode calibration

Under Profile, `ProModeCalibrationActivity` lets a teacher mark two bubble centres per block on a raw photo. `TemplateCalibrator` derives each block's `start_x`, `start_y`, `dx` and `dy` through the same homography `PerspectiveAligner` uses. The result is saved as an **override** in `getFilesDir()/template_overrides/` and fully replaces the bundled template for that ID. "Reset" deletes the override. The bundled asset is never modified. The model has no rotation term, which was confirmed only for ZPH40 and ZPH60.

---

## Saving, grading and exporting scans

### Save gates in `ResultActivity`

A scan can be saved only when all of these hold:

1. The LRN is exactly **12 digits** and the teacher tapped **CONFIRM** (this also clears undetected and double-shaded flags).
2. Any multi-letter answers are either fixed (FIX NOW) or explicitly deferred (CORRECT LATER). Deferred scans are blocked from CSV export and upload until corrected.
3. The LRN exists in `student_lrn` **for that class** (`isLrnInStudentLrnTableSync`). If not, the save is blocked. If the roster has not been synced recently, the dialog offers **Sync Students**.
4. If the LRN already exists in that assessment, the teacher chooses to replace or cancel.

### Save path

`ResultActivity.proceedWithExport` creates a CSV in a temp location, then `saveScanToFolder` converts the scan to a `ScanEntry`, saves the overlay image (and the answer-key reference image to a separate folder), and calls `DashboardActivity.saveScanResult(...)`. That method:

- looks up the assessment's linked answer key and, if present, **computes and stores the score**. Wildcards (`?` or empty) in the key are skipped.
- inserts the scan and answers, or updates them in place when replacing (matched by assessment and LRN)
- calls `ClassExporter.autoSaveClassData(...)` on a background thread

If no key is linked at save time the score stays `null`. Linking a key later re-grades existing scans (see the README).

### Files written to Downloads

`ClassExporter` rewrites the whole assessment's output **in place** on every save. There is no dialog, password or ZIP.

```text
Downloads/OMRScanner/<class folder>/<Section>_<Assessment>/
├── images/   <LRN>.jpg                   compressed to about 100 KB, min edge 900 px
├── result/   <LRN>_<grade>-<section>_<assessment>.csv   one row per student
└── <class folder>_<Assessment>.csv        all students (this is what gets uploaded)
```

CSV rows are the LRN digits each followed by `;`, then one answer per item separated by `;`, with a blank for no answer. The aggregate CSV **skips** scans that still need an answer correction. File names are LRN-based, so rescanning a student overwrites the old files.

Upload re-creates the CSV (`ClassExporter.exportAssessmentSync`) if the folder was deleted outside the app. `BackupManager` does the same after a restore.

---

## Data layer

Room database `omrscanner.db`, **version 28**, `exportSchema = false`. Migrations 1 to 28 are all explicit and there is **no destructive fallback**. A missing or wrong migration crashes at startup, so test upgrades from an older install when you change a schema.

### Tables

| Table | Notes |
| --- | --- |
| `teachers` | One local row per server account (`user_id`). Added in v17 so switching accounts on one phone no longer shares data. |
| `users` | Rows created by QR scans. One is marked active. Holds `server_ip`, name fields, school, `role`, profile photo path. |
| `classes` | FK to teacher (cascade). Has `classroomId`, `sectionId`, `teacherClassId` from the server. |
| `assessments` | FK to class (cascade). `answer_key_id` is a soft link. Has `server_assessment_id`. |
| `scans`, `answers` | Scan per student per assessment, and one answer row per item. |
| `answer_keys` | FK to teacher (cascade). **Owned per teacher since v23.** (Older docs call keys "global".) |
| `student_lrn` | Synced roster. FK to teacher. Unique per (lrn, className). |
| `quizzes`, `quiz_scans`, `quiz_scan_answers` | Local-only quizzes, kept apart from assessment tables. |
| `ecdc_domains`, `ecdc_competencies` | Server reference data, replaced wholesale on each sync. |
| `ecdc_responses`, `ecdc_student_dates` | Teacher's marks and assessment dates. **No foreign keys on purpose** so re-syncs never cascade-delete them. |

Gotchas:

- The version history in the `AppDatabase` header comment is **incomplete**. It stops at 25 to 26 and skips several versions. Read the `Migration` objects instead, and add a line to the header when you add a migration.
- Room validates the schema at startup. Migration SQL must match the entity exactly (column types, nullability, defaults, indices).
- Several tables use string primary keys (7-character short UUIDs for classes, assessments, keys and quizzes).

### Repository and threading

`OMRRepository` wraps the DAOs and returns results through `Callback<T>`.

- **Callbacks run on the repository's background thread, not the main thread**, even though the class comment says otherwise. UI code must use `runOnUiThread`.
- Each `new OMRRepository(context)` creates **its own** single-thread executor. Two instances do not serialize against each other. Code in `ResultActivity` and the static `DashboardActivity.saveScanResult` creates fresh instances.
- Many methods have `...Sync` variants for use from an existing background thread.

### Models and mapping

`DataMapper` converts entities to `ClassFolder`, `ActivityFolder` and `ScanEntry`, and back. Anything that is not persisted (for example derived UI state) lives only on the model objects.

---

## Server sync and upload

The STARS server is **not** in this repository. Everything below is inferred from the client code. Server address, request fields and responses beyond what is listed are not documented here.

### Sign-in (QR)

`QrScannerActivity` scans the QR with ML Kit. The QR holds a Laravel-style encrypted envelope (`iv`, `value`, `mac`). The app verifies the MAC, decrypts it, and parses a JSON payload: `username`, `userId`, `passKey`, `host`, name fields, `schoolName`, and an optional `role` (defaults to `Teacher`).

Then it:

1. normalizes the host (adds `http://` if there is no scheme, strips trailing slashes)
2. pings the server, and fails with "server unreachable" if that fails
3. inserts a new `users` row as the only active user (`insertUserAsActive`), carrying over the profile photo for the same server `userId`
4. routes by role (`Student` goes to the stub dashboard)

The `passKey` is stored locally but never sent with any request. See [Known issues](#known-issues-and-risks) for the logging concern.

### Endpoints

Paths are appended to the stored server address. Methods are from the README and code comments, and the multipart field names are from the upload code.

| Method | Path | Used for |
| --- | --- | --- |
| `POST` | `/api/classrooms/sync` | Pull the teacher's classes |
| `POST` | `/api/students/sync` | Pull a class's roster into `student_lrn` |
| `POST` | `/api/assessment/sync` | Pull assessments and answer keys (sends `user_id`) |
| `GET` | `/api/ecdc/domains` | Pull ECDC domains and competencies |
| `POST` multipart | `/api/upload/assessment` | Fields `assessment_id` (server assessment ID), `class_id` (the class's `teacherClassId`), file part `file_assessment` (the aggregate CSV) |
| `POST` JSON | `/api/ecdc/upload` | ECDC results, single or mass (shape in the README) |

The assessment upload expects a JSON response with `success` and `message`. Timeouts are set per call (connect 3 to 10 s, read 3 to 60 s). The whole-class ECDC upload uses a 60 s read timeout.

Upload guards for assessments: the class needs a `teacherClassId` (it must have come from the server), and no scan may have an unresolved multi-letter answer.

Network code is plain `HttpURLConnection` inside `DashboardActivity` and `QrScannerActivity`. There is no HTTP client abstraction, retry logic or auth header.

---

## ECDC module

The README documents behavior, the payload and the upload guards. Code locations:

| Concern | Where |
| --- | --- |
| Screens (list, class, student card) | `dashboard/EcdcScreenRenderer`, screen constants `ecd`, `ecd_class`, `ecd_student` |
| Domain sync (`Sync ECCD`) | `DashboardActivity` (`ECDC_DOMAINS_SYNC_PATH`), replaced in one transaction |
| JSON building | `dashboard/EcdcUploadPayloadBuilder` |
| Single and mass upload, guards | `DashboardActivity` (search for `OMR_ECDC_UPLOAD`) |
| Storage | `ecdc_responses`, `ecdc_student_dates` via `EcdcResponseDao`, `EcdcStudentDateDao` |

Notes for maintainers: marks are keyed by `(class_id, lrn, period, competency_id)` and dates by `(class_id, lrn, period)`. Only classes whose grade starts with "Kinder" get the module. `last_ticked_at` is built in `EcdcUploadPayloadBuilder`. Its comment mentions GMT-8, but the README records that the code uses the device timezone. Confirm the intent with the backend.

---

## Backup and restore

`BackupManager` writes and reads a `.zip` built with `java.util.zip`. The archive holds a `backup.json` manifest (`BACKUP_FORMAT_VERSION = 1`) plus the scan images and profile photo. The manifest carries assessments, scans, answers, answer keys, quizzes, quiz scans and answers, ECDC responses and ECDC dates.

It deliberately **excludes** classes, rosters and account info, which come back from the server. Restore remaps data to the signed-in teacher's classes by `classroomId` and skips anything whose class is not synced yet. After restoring it rebuilds `Downloads/OMRScanner` so uploads work. It only exports and restores the active teacher's data.

If you change what is stored per scan or per assessment, update both directions in `BackupManager` and consider bumping `BACKUP_FORMAT_VERSION`. Restore is not atomic, and a failure part-way can leave a partial restore.

---

## Tuning hotspots

Change these only with real printed sheets and real phones.

| Goal | File and constants |
| --- | --- |
| Guide-square layout and locking | `CameraActivity`: `GUIDE_SQUARE_SIZE_FRACTION` (0.10), `GUIDE_CENTER_*_FRACTION_*` (compact and wide, portrait and landscape), `GUIDE_REQUIRED_CONSECUTIVE_HITS` (8), `GUIDE_LOCK_GRACE_PERIOD_MS` (1000) |
| Tilt gate | `CameraActivity`: `REQUIRED_TILT_ROTATION` (90), `FIXED_LABEL_ROTATION_STEPS` (1). The code says these were not verified on a physical device. Flip them if the warning shows when the phone is correctly tilted. |
| ArUco locking | `CameraActivity`: `ARUCO_LOCK_REQUIRED_CONSECUTIVE_HITS` (8), `ARUCO_LOCK_GRACE_PERIOD_MS` (1000), `REQUIRED_CONSECUTIVE_DETECTIONS_TILT_AGNOSTIC` (5). `ArucoAnchorDetector.buildHandheldTunedParameters` for decoding under blur. |
| Contour anchor detection | `AnchorDetector`: the five `DetectionProfile` constants |
| Fixed-mount zoom (currently unreachable) | `CameraActivity`: `FIXED_MOUNT_ZOOM_STEPS`, `FIXED_MOUNT_MISS_THRESHOLD` (6), `FIXED_MOUNT_ZOOM_COOLDOWN_MS` (250) |
| Question bubble sensitivity | `BubbleScanner.QUESTION_FILL_THRESHOLD` (0.18), `DEFAULT_INNER_MASK_RADIUS_FACTOR` (0.60) |
| LRN reading | `BubbleScanner`: `DEFAULT_LNR_PROFILE`, `ZPH60_LNR_PROFILE` (threshold, mask radius, winner margin, double-shade thresholds) |
| Block alignment | `GridAligner`: `SEARCH_MARGIN` (40), `MIN_MATCH_SCORE` (0.35), `MAX_MARGIN_SPACING_FRACTION` (0.4) |
| Orientation and sheet detection | `TemplateManager`: `DETECTION_MIN_SCORE` (0.35), `ROTATION_TIE_MARGIN` (0.015), `MIN_ORIENTATION_CONFIDENCE` (0.28), `REQUIRED_PORTRAIT_ROTATION`, `ARUCO_RESOLVED_CONTENT_ROTATION` |
| Export image size | `ClassExporter`: `EXPORT_IMAGE_TARGET_BYTES` (100 KB), `EXPORT_IMAGE_MIN_EDGE_PX` (900) |
| Beta gate | `BetaExpiryChecker`: `FORCE_EXPIRED`, expiry date |

---

## Debugging

Useful Logcat tags:

| Tag | What it shows |
| --- | --- |
| `OMR_ECDC_UPLOAD` | ECDC JSON payload and server response |
| `OMR_ASSESSMENT_UPLOAD` | Assessment upload request and the CSV contents |
| `OMR_ASSESSMENT_SYNC` | Assessment sync response |
| `ARUCO_ORIENTATION_CANDIDATES` | CW and CCW scores for an ArUco capture (use to verify `ARUCO_RESOLVED_CONTENT_ROTATION`) |
| `ARUCO_TOP_EDGE_ANGLE`, `ARUCO_TL_LANDS_IN_QUADRANT`, `ARUCO_WINDING_CHECK` | Orientation diagnostics in `ResultActivity` |
| `CameraActivity`, `TemplateManager`, `BubbleScanner`, `GridAligner` | Frame info, block offsets and scores, fill ratios |
| `QR_SCANNER` | Raw QR, envelope and decrypted payload (see Known issues) |

`CameraActivity` logs the analysis frame size once as `ANALYSIS FRAME INFO`. That is the resolution `AnchorDetector` thresholds are actually evaluated against, so compare it across devices when detection differs.

The FAB "test" row on the dashboard runs `DataInspector.printAll()` and `DataExporter.exportAll()` to dump database contents. It is reachable from the normal UI, so decide whether teachers should see it. `DashboardActivity` also contains a commented-out `DB_TEST` block that would insert a user with a hard-coded passkey. It is inactive, but worth deleting.

---

## Known issues and risks

Found while auditing the code. Roughly ordered by how likely they are to bite.

1. **Camera-mode labels are wrong.** "Fixed Mount" actually launches guide-square mode, and "Handheld" launches ArUco mode. The distance-compensation code (far profile, zoom stepping) is never reached. The README and `FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md` describe the old meaning. Either rename the dialog options or restore the intended behavior.
2. **Developer Options guard blocks developers and some teachers.** Any device with Developer Options on cannot get past the splash. Teachers who enabled it once will see this too.
3. **QR contents are logged.** `QrScannerActivity` writes the raw QR value, the encrypted envelope and the **decrypted plaintext** (which includes the passkey) to Logcat under `QR_SCANNER`. The decryption key is derived inside the app. Remove the logging and review key handling before wider distribution.
4. **No authentication on requests.** The client sends a self-declared `user_id` or class ID. Confirm the server verifies ownership. The stored `passKey` is unused.
5. **Beta gate disabled.** `MainActivity` always opens the dashboard. The checker's comment says April 5 but the code sets April 30, 2026. Decide whether the gate should return.
6. **Release builds need HTTPS.** QR hosts without a scheme become `http://`, which a release build blocks.
7. **Unverified orientation constants.** `ARUCO_RESOLVED_CONTENT_ROTATION` (marked `TODO verify`), `MIN_ORIENTATION_CONFIDENCE` (two samples per template), `REQUIRED_TILT_ROTATION` (not verified on device), and the wide guide-square layout for ZPH50/ZPH60.
8. **Scans need a synced roster.** A scan is rejected if its LRN is not in `student_lrn` for the class. Offline-first use before the first student sync will fail at save time.
9. **Callbacks run off the main thread**, and each `OMRRepository` instance has its own executor.
10. **Restore is not atomic.** An error mid-restore can leave partial data.
11. **Release hygiene.** Minification is off and `allowBackup` is `true`.
12. **Dead code and unused dependency.** See [What is live and what is legacy](#what-is-live-and-what-is-legacy). Zip4j adds size for nothing.
13. **Stale database header comment.** Migration history in `AppDatabase.java` is incomplete.
14. **`ECDC` marks can be orphaned** if a re-sync changes competency IDs. They still upload, with `null` domain and competency names (see the README).

---

## Testing

There are only the two Android template tests (`ExampleUnitTest`, `ExampleInstrumentedTest`). Everything else is manual. The best candidates for new unit tests are pure logic with no Android dependencies:

- `ActivityFolder.parseBaseTemplateId` and `parseItemCountFromSheetType`
- `TemplateManager.buildTruncatedTemplate`
- `EcdcUploadPayloadBuilder` and the ECDC upload guards
- score calculation in `DashboardActivity.saveScanResult` (extract it first)
- `BubbleScanner` LRN column classification (`classifyLnrColumn`) with fixed fill-ratio inputs
- Room migrations (use `MigrationTestHelper`, which needs `exportSchema = true`)

### Manual regression checklist

Run after any change to the scan pipeline, templates, database or export code.

1. Scan a ZPH40 and a ZPH60 sheet in **both** camera modes. Capture should trigger without a manual tap, and the overlay boxes should match the visible anchors.
2. Scan a sheet upside down in "Handheld" mode and confirm the result is still aligned.
3. Try a truncated assessment (for example ZPH40 with 30 items) and confirm trailing blocks are not read.
4. LRN: confirm undetected and double-shaded digits are flagged, and that a wrong LRN is blocked with the "not recognized" dialog.
5. Mark two bubbles on one question. Save must stay blocked until you fix or defer it, and upload must refuse while it is unresolved.
6. Score appears when a key is linked before saving, and re-grades when linked afterward.
7. Replace an existing scan for the same LRN.
8. Retake from `ResultActivity` keeps the chosen mode.
9. Check the files under `Downloads/OMRScanner` (images, per-student CSV, aggregate CSV).
10. Upload an assessment and an ECDC class against a test server.
11. Back up, wipe app data, sign in, sync, restore. Check scans, keys, quizzes and ECDC marks.
12. Sign in as a second teacher on the same phone. The first teacher's data must stay intact and separate.
13. Upgrade from an older installed version to verify migrations.

---

## Suggested refactors

1. Fix the camera-mode labels and decide whether fixed-mount distance compensation stays. Remove it if not.
2. Remove dead code after confirming: `PreviewActivity` (keep the constants), `BasicCamera`/`FlatScan` activities, `CSVFileActivity`, `FullImageViewerActivity`, `LrnErrorActivity`, `SplashActivity`, `StorageManager`, `BubbleDetector`, `GuideRegion`, the Zip4j dependency.
3. Break `DashboardActivity` apart: networking (sync, upload) and the ECDC flow are the clearest candidates.
4. Put networking behind one client class with timeouts, error mapping and a place for auth.
5. Dispatch repository callbacks on the main thread, or share one executor. At minimum document the contract.
6. Turn on `exportSchema` and add migration tests.
7. Remove sensitive Logcat output, and gate debug helpers (the FAB "test" row, `DataInspector`, `DataExporter`) behind `BuildConfig.DEBUG`.
8. Extract scoring and CSV building into testable classes.

---

## File map

If you have ten minutes to find the right file (all under `app/src/main/java/com/example/omrscanner/`):

| I need to change... | Look in |
| --- | --- |
| Camera modes, guide squares, tilt gate, auto-capture | `camera/CameraActivity.java` |
| Mode dialog and scan launch | `DashboardActivity.showCameraModeDialog`, `launchCamera` |
| ArUco detection | `omr/ArucoAnchorDetector.java` |
| Contour anchor detection | `omr/AnchorDetector.java` |
| Perspective warp | `omr/PerspectiveAligner.java` |
| Orientation, templates, overrides | `omr/TemplateManager.java` |
| Bubble and LRN reading | `omr/BubbleScanner.java` |
| Result screen, LRN checks, save gates | `ui/ResultActivity.java` |
| Scoring and saving a scan | `DashboardActivity.saveScanResult` |
| Editing a saved scan | `ui/ScanDetailActivity.java` |
| Files in Downloads | `dashboard/ClassExporter.java` |
| Assessment, quiz and answer key dialogs | `dashboard/DashboardDialogs.java` |
| ECDC screens and payload | `dashboard/EcdcScreenRenderer.java`, `dashboard/EcdcUploadPayloadBuilder.java` |
| Sync and upload code | `DashboardActivity.java` (search `_PATH`) |
| QR sign-in | `camera/QrScannerActivity.java` |
| Room schema and migrations | `database/AppDatabase.java` |
| Data access | `database/OMRRepository.java` |
| Entity to UI model mapping | `database/DataMapper.java` |
| Backup and restore | `BackupManager.java` |
| Calibration | `ui/ProModeCalibrationActivity.java`, `omr/TemplateCalibrator.java` |

---

## Keeping this guide current

Update this file when you change any of: camera modes or their dialog, the scan pipeline order, save gates, the database version, server paths or fields, export layout, or the list of live screens. When you delete something from the legacy table, delete its row here too.
