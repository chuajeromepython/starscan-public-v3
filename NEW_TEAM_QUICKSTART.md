# New Team Quickstart

A short orientation for your first day. For depth, read the [README](README.md) (what the app does) and the [DEVELOPER_GUIDE](DEVELOPER_GUIDE.md) (how the code works, known issues, tuning constants).

## What this app is

STARS (code name **OMRScanner**) is an Android app for teachers. It scans printed OMR answer sheets with the phone camera, grades them, and syncs classes and results with the STARS web system. It also has an ECDC (Early Childhood Development Checklist) module for Kinder classes and local-only quizzes.

The package, Gradle project and many class names still say `OMRScanner`. The app label is **STARS**.

Core scan workflow:

1. Sign in by scanning the teacher's QR code from the STARS website (Profile tab).
2. Sync classes and the class's students.
3. Open a class, create an assessment, open it, and start a scan.
4. The camera locks onto the sheet's four corner markers and captures automatically.
5. The app aligns the sheet, reads the LRN and answers, and asks the teacher to confirm the LRN.
6. The scan is saved, graded if an answer key is linked, and written to `Downloads/OMRScanner`.
7. Results are uploaded to STARS from the assessment screen.

Sheet types the UI offers: `ZPH40` and `ZPH60` (quizzes: `ZPH40` only). `ZPH30` and `ZPH50` templates exist but cannot be selected.

## Day-one setup

1. Clone and open in Android Studio (a recent version that supports Gradle 9.1 and AGP 9.0):

   ```bash
   git clone https://github.com/chuajeromepython/starscan-public-v3.git
   cd starscan-public-v3
   ```

2. Let Gradle sync, then build:

   ```bash
   ./gradlew :app:assembleDebug           # macOS / Linux
   .\gradlew.bat :app:assembleDebug       # Windows
   ```

3. Run on a **physical phone** with a camera. Scanning is tuned on real printed sheets, and emulators are not useful for it.
4. **Turn off Developer Options on that phone** (or you will see a blocking dialog at launch). `MainActivity` refuses to start the app while it is on. If that blocks your debugging workflow, add a debug-build bypass.
5. You need a STARS server and a teacher QR code to get real classes and rosters. Debug builds allow plain `http://` addresses (for example a server on your LAN). Release builds only reach `https://` servers.
6. You also need printed ZPH40 or ZPH60 sheets. They must carry the four corner ArUco markers.

## What to read first

1. [README.md](README.md): features and user flows.
2. [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md): architecture, the **camera-mode caveat**, known issues.
3. [`DashboardActivity.java`](app/src/main/java/com/example/omrscanner/DashboardActivity.java): the main host (about 6,400 lines, skim it).
4. [`CameraActivity.java`](app/src/main/java/com/example/omrscanner/camera/CameraActivity.java): live detection and capture.
5. [`ResultActivity.java`](app/src/main/java/com/example/omrscanner/ui/ResultActivity.java): alignment, reading, LRN check, save.

## Mental model

The app has two halves:

- **Dashboard and data:** classes, assessments, scans, answer keys, quizzes, ECDC, sync, upload, backup. Room database plus screens built in Java code (`dashboard/` renderers).
- **Scanning:** live camera, corner detection, perspective alignment, template and orientation detection, bubble reading (`camera/`, `omr/`, `ui/ResultActivity`).

It is activity-driven Java. There are no ViewModels, and most screens are built programmatically rather than from XML.

## Main scan flow

```text
DashboardActivity (camera-mode dialog)
   -> CameraActivity    locks onto the 4 corner anchors, then captures
   -> ResultActivity    align -> detect orientation -> read bubbles -> verify LRN -> save
   -> Room + Downloads/OMRScanner
```

There is no gallery or manual-preview path anymore. `PreviewActivity` still exists but nothing launches it.

## Two things that will confuse you

**1. The camera-mode labels are misleading.** The dialog offers two options:

| Dialog label | What actually runs |
| --- | --- |
| "Fixed Mount" | Guide-square mode: four fixed on-screen squares, phone must be tilted right |
| "Handheld" | ArUco mode: finds the corner markers by ID, works in any orientation |

The old fixed-mount distance compensation (zoom stepping) is not reachable from the UI. Older notes, including `FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md`, describe the previous meaning. Details are in the [Developer Guide](DEVELOPER_GUIDE.md#camera-modes).

**2. Capture is quick, not instant.** Each corner has to be detected on 8 consecutive frames before capture triggers (with a one-second grace period if it drops out briefly). ArUco mode then needs 5 consecutive full detections. Do not expect "first valid frame wins".

## Rules the app enforces

A scan will not save unless:

- the LRN is exactly 12 digits and was confirmed by the teacher,
- the LRN is in the synced student roster for that class,
- any question with two or more marked bubbles has been fixed or deferred.

An assessment will not upload while any scan has an unresolved multi-mark question, or if its class was not synced from the server.

## Common tasks

| I want to... | Look at |
| --- | --- |
| Make live detection more or less sensitive | `omr/AnchorDetector`, `omr/ArucoAnchorDetector`, guide constants in `camera/CameraActivity` |
| Change how faint a pencil mark can be | `QUESTION_FILL_THRESHOLD` in `omr/BubbleScanner` (currently 0.18) |
| Add a sheet type | Steps in the [Developer Guide](DEVELOPER_GUIDE.md#adding-a-sheet-type) |
| Change what gets uploaded or exported | `dashboard/ClassExporter`, upload code in `DashboardActivity` |
| Change the database | `database/AppDatabase` (bump the version and add a `Migration`, there is no destructive fallback) |
| Debug a scan | Logcat tags `CameraActivity`, `TemplateManager`, `BubbleScanner`, `ARUCO_*` |
| Debug a server upload | Logcat tags `OMR_ASSESSMENT_UPLOAD`, `OMR_ASSESSMENT_SYNC`, `OMR_ECDC_UPLOAD` |

## Known gotchas

- `OMRRepository` callbacks run on a background thread. Wrap UI updates in `runOnUiThread`.
- Tune detection only with real sheets and real phones.
- Several classes are unused (`StorageManager`, `BubbleDetector`, `SplashActivity`, and others). The [Developer Guide](DEVELOPER_GUIDE.md#what-is-live-and-what-is-legacy) lists them. Do not build on them.
- The QR scanner logs the decrypted QR payload, including the passkey, to Logcat. Do not share logs from a real account.
- The only tests are the Android template ones. Use the [manual regression checklist](DEVELOPER_GUIDE.md#manual-regression-checklist) before merging scan or database changes.
- `NEW_TEAM_QUICKSTART.md` is the only older doc rewritten so far. `FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md` and `answer_key_implementation_plan.md` have **not** been audited.

## If you only remember three things

1. Test on a real phone with printed sheets and Developer Options off.
2. The camera-mode dialog labels do not mean what they say. Check the Developer Guide before touching "fixed mount" code.
3. `DashboardActivity`, `CameraActivity` and `ResultActivity` are where most behavior lives.
