# STARS (OMRScanner)

STARS is an Android app for teachers. It scans printed OMR (Optical Mark Recognition) answer sheets with the phone camera, grades them, and syncs classes and results with the **STARS** web system. It also includes an **ECDC** (Early Childhood Development Checklist) module for Kinder classes.

> The app label is **STARS**; the code, package (`com.example.omrscanner`) and Gradle project are still named **OMRScanner**.

---

## Table of contents

1. [What the app does](#what-the-app-does)
2. [App tabs](#app-tabs)
3. [Getting started (developers)](#getting-started-developers)
4. [Connecting to the STARS server](#connecting-to-the-stars-server)
5. [Scanning](#scanning)
6. [ECDC checklist](#ecdc-checklist)
7. [Quizzes](#quizzes)
8. [Backup and restore](#backup-and-restore)
9. [Architecture](#architecture)
10. [Database](#database)
11. [Server API used by the app](#server-api-used-by-the-app)
12. [Known limitations and things to verify](#known-limitations-and-things-to-verify)
13. [Branches](#branches)
14. [Other documents](#other-documents)

---

## What the app does

- **Connects to a teacher account** by scanning a QR code from the STARS website. The QR stores the teacher's name, school and the server address on the device.
- **Syncs** classes and student rosters (LRNs and names) from STARS.
- **Scans OMR sheets** live with the camera, reads the student's LRN and marked answers, and saves the scan.
- **Grades** scans against locally stored answer keys.
- **Uploads** assessment results (CSV) to STARS.
- **Runs quizzes** that are scanned and graded like assessments but stored only on the device.
- **Records ECDC checklists** for Kinder students (Present / Not present / Not tested, with a P/O/R type) and uploads them to STARS, one student or a whole class at a time.
- **Backs up and restores** a teacher's local data as a `.zip`.

Almost everything works offline. The network is only needed to sync classes/students/assessments/ECDC domains and to upload results.

---

## App tabs

The bottom bar scrolls sideways. The tabs are:

| Tab | Purpose |
| --- | --- |
| Home | Synced classes, class and student sync |
| Assessments | Assessments across classes |
| Scans | Flat, read-only list of every scan across all classes |
| Quizzes | Local-only quizzes |
| ECDCs | ECDC (Kinder) classes and student checklists |
| Answer Keys | Create and manage answer keys (local only) |
| Profile | Scan QR code, profile photo, backup/restore, Pro Mode calibration, Help & FAQ |

---

## Getting started (developers)

### Requirements

| Item | Value (from the Gradle files) |
| --- | --- |
| Android Studio | A recent version that supports Gradle 9.1 and AGP from `libs.versions.toml` |
| Gradle | 9.1.0 (wrapper) |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 24 (Android 7.0) |
| Java | 11 |
| Language | Java (no Kotlin in the app module) |
| Device | A physical Android phone with a camera is strongly recommended. Scanning is tuned on real printed sheets. |

### Clone and build

```bash
git clone https://github.com/chuajeromepython/starscan-public-v3.git
cd starscan-public-v3
git checkout feature/with-por
```

Open the folder in Android Studio, let Gradle sync, then run the `app` configuration on a device. From the command line:

```bash
./gradlew :app:assembleDebug        # macOS / Linux
.\gradlew.bat :app:assembleDebug    # Windows
```

### Modules

- `app`: the Android application.
- `sdk`: the imported OpenCV Android SDK (Java wrappers and native libraries).

### Main libraries

CameraX 1.3.2, OpenCV (via `:sdk`, including ArUco marker detection), Room 2.6.1, ML Kit barcode scanning 17.2.0 (QR login), Gson, Zip4j, Material Components 1.11, RecyclerView, ViewPager2, ExifInterface.

### Debug and release builds

The two build types have different network rules:

- **debug** (`app/src/debug/res/xml/network_security_config.xml`): plain HTTP is allowed. This is what lets the app talk to a server on a local network (for example `http://192.168.x.x:8000`).
- **release** (`app/src/release/res/xml/network_security_config.xml`): cleartext HTTP is **blocked**. A release build only works with a server that uses `https://` **(verify against your production server)**.

### Permissions

Camera, Internet, and storage access (`MANAGE_EXTERNAL_STORAGE`, plus `WRITE_EXTERNAL_STORAGE` up to API 28). The app is sideloaded, not published on the Play Store, which is why it uses broad storage access to write to `Downloads/OMRScanner`.

---

## Connecting to the STARS server

1. Open **Profile**, tap **Scan QR code**, and scan the QR code on the teacher's page of the STARS website.
2. The QR payload provides the account details and the server address. If the address has no scheme, the app prepends `http://` and strips trailing slashes (`QrScannerActivity.normalizeServerUrl`).
3. The server address is stored per user on the device. If the server's IP changes, scan the QR code again.

Each teacher's data (classes, assessments, answer keys, quizzes, ECDC marks) is kept separate on a shared device. Signing in as another teacher no longer erases the first teacher's data.

---

## Scanning

### Supported sheet types

Templates for `ZPH40` and `ZPH60` ship in `app/src/main/assets/templates/` as JSON.

**In the UI you can currently create assessments with `ZPH40` (40 items) and `ZPH60` (60 items). Quizzes are restricted to `ZPH40`.** The `ZPH30` and `ZPH50` templates exist but are not offered when creating an assessment.

### Camera modes

When you start a scan, you choose a camera mode:

- **Fixed Mount**: for an elevated or mounted phone with sheets slid underneath. It adds distance compensation (a more tolerant far-distance detection pass and bounded zoom stepping after repeated misses).
- **Handheld** (tilt-agnostic): the phone is held over the sheet. Corners are found by ArUco marker identity, so the sheet can be in any orientation.

`FlatScanCameraActivity` and `BasicCameraActivity` are still in the code but are not reachable from the current mode dialog.

### How a scan works

```text
DashboardActivity
      |
      v
CameraActivity  -- CameraX preview + ImageAnalysis
      |            ArucoAnchorDetector finds the 4 corner markers
      |            first valid detection triggers capture
      v
ResultActivity
      |-- PerspectiveAligner : warp the sheet to a fixed canonical rectangle
      |-- TemplateManager    : load templates, detect sheet type and orientation
      |-- GridAligner        : refine block alignment
      |-- BubbleScanner      : read the LRN and the marked answers
      v
LRN verification -> save to Room -> auto-export to Downloads/OMRScanner
```

Key classes are in `omr/` (`ArucoAnchorDetector`, `AnchorDetector`, `PerspectiveAligner`, `TemplateManager`, `GridAligner`, `BubbleScanner`, `BubbleDetector`) and `camera/` (`CameraActivity`, `AnchorOverlayView`, `QrScannerActivity`).

### Grading

An answer key is created locally in the **Answer Keys** tab and linked to an assessment (or quiz). A scan is graded automatically only if the key is already linked when the scan is saved. Linking a key afterwards re-grades that assessment's existing scans.

### Saved output

Every saved scan is written automatically to `Downloads/OMRScanner/...` (compressed images plus CSV). File names are based on the student's LRN, so re-scanning the same student overwrites the old file. If a student's LRN already exists in that assessment or quiz, the app asks whether to replace the earlier scan.

### Pro Mode calibration

Under **Profile**, Pro Mode calibration is a one-time setup per sheet type for sheets that don't line up with the default template (crumpled sheets, printer margin drift, photocopies). You mark two bubble centres per block on a raw photo and the app derives the block geometry (`TemplateCalibrator`). **Reset calibration** clears saved calibrations.

---

## ECDC checklist

ECDC = Early Childhood Development Checklist. It is available only for classes whose grade starts with "Kinder" (`isEcdcGrade`).

### Setup

1. Scan your QR code (Profile).
2. Sync your classes and the class's students.
3. Open the **ECDCs** tab and tap **Sync ECCD** to download the domains and competencies. This replaces the local reference data in a single transaction, so a failed sync keeps the old data.

### Marking a student

1. Open a Kinder class and pick a **period**: Beginning (`BOSY`), Middle (`MOSY`) or End (`EOSY`) of School Year.
2. Search for a student by name or LRN. Recently opened students are listed.
3. On the student's card, set the **assessment date** (it is saved immediately).
4. Choose a domain pill and mark every competency:
   - **Present**: also choose a type, **P**, **O** or **R** (the "Type" row expands under the card).
   - **Not present**
   - **Not tested**
   - Tapping a selected option again clears it.
5. Tap **Save**. Leaving with unsaved changes asks first. The eye button shows a per-domain summary of the marks on screen.

### Data model

Marks are stored in `ecdc_responses`, keyed by `(class_id, lrn, period, competency_id)`. The assessment date is in `ecdc_student_dates`, keyed by `(class_id, lrn, period)`. Neither table has foreign keys, on purpose: re-syncing the ECDC reference data or the class roster must never cascade-delete a teacher's saved work.

### Uploading

- **Single student:** the **Upload** button on the student card.
- **Mass Upload:** three-dot menu on the class card in the ECDCs tab, then choose a period.

Both send the same JSON to `POST /api/ecdc/upload`:

```json
{
  "classroom_id": 12,
  "user_id": 5,
  "period": "BOSY",
  "students": [
    {
      "lrn": "108357260002",
      "last_ticked_at": "2026-10-10",
      "responses": [
        {
          "domain_id": 1,
          "domain": "GROSS MOTOR DOMAIN",
          "competency_id": 1,
          "competency": "...",
          "status": "1",
          "present_type": "P"
        }
      ]
    }
  ]
}
```

`status` is `"1"` for Present, `"-"` for Not present and `"*"` for Not tested. `present_type` is `P`, `O` or `R` for Present marks and `null` otherwise. The payload is also printed to Logcat under the tag `OMR_ECDC_UPLOAD`, which is useful for debugging. Re-uploading the same period replaces that student's earlier answers on the server.

### Upload guards

An upload is blocked, with a dialog explaining why, when:

- the class has no `classroom_id` (it wasn't synced from the server) or no period is chosen;
- the single-student checklist has unsaved changes (the app offers to save first);
- any domain isn't fully marked (single student) or any student being uploaded has unmarked competencies (mass upload);
- any Present mark has no P/O/R type;
- any student being uploaded has no assessment date;
- there are no saved marks for that class and period.

In a mass upload, students who have marks but are no longer on the class roster are **skipped** (their marks stay in the database) and the toast shows how many were skipped. If the roster is empty, for example the students haven't been synced yet, this filter is skipped.

---

## Quizzes

Quizzes have their own tab and are scanned and graded like assessments (`ZPH40` only), but they are stored **only on the device**. There is no server copy, so a backup is the only way to recover them after uninstalling or changing phones. Quiz scans are kept in separate tables (`quiz_scans`, `quiz_scan_answers`).

---

## Backup and restore

**Profile → Back up my data** writes a `.zip` (named with the teacher's name and date/time) that contains: assessments, scans, answers, answer keys, scan images, quizzes, quiz scans, ECDC marks and assessment dates, and the profile photo.

It does **not** contain classes, student rosters or account info, since those come back from the server after scanning the QR code and syncing.

**Profile → Restore from backup** remaps everything onto the signed-in teacher's current classes using `classroomId`. Items that belong to a class that isn't synced yet are skipped; sync that class and restore again. Restoring overwrites matching local records, including ECDC marks, with the versions in the backup. Only the signed-in teacher's data is exported or restored.

---

## Architecture

```text
com.example.omrscanner
├── DashboardActivity       main screen host: tabs, sync, uploads, ECDC flow
├── MainActivity            splash, then DashboardActivity
├── BackupManager           backup .zip export and restore
├── camera/                 CameraActivity, QrScannerActivity, overlay views, other camera modes
├── dashboard/              screen renderers and helpers (Home, Class, Activity, Scans,
│                           Ecdc), dialogs, ClassExporter, EcdcUploadPayloadBuilder
├── database/               Room: AppDatabase, DAOs, entities, projections, OMRRepository
├── models/                 in-memory folder models (ClassFolder, ActivityFolder, ...)
├── omr/                    detection, alignment, template, bubble reading
├── ui/                     ResultActivity, PreviewActivity, ScanDetail, ProModeCalibration, ...
└── utils/                  CSV export, image utils, StorageManager, BetaExpiryChecker
```

Things to know:

- `DashboardActivity` is very large (about 6,400 lines) and holds most of the app's UI logic, including the ECDC flow. Renderers in `dashboard/` build views programmatically.
- Database access goes through `OMRRepository`, which runs queries on a background executor and returns results through callbacks.
- Network calls use `HttpURLConnection` on plain threads. There are no auth headers, and the server address comes from the QR code.

---

## Database

Room database `omrscanner.db`, currently **version 28**, with explicit migrations from 1 to 28 (`AppDatabase`). `exportSchema` is `false`, so no schema JSON is kept.

Main tables: `teachers`, `users`, `classes`, `assessments`, `scans`, `answers`, `answer_keys`, `student_lrn`, `quizzes`, `quiz_scans`, `quiz_scan_answers`, `ecdc_domains`, `ecdc_competencies`, `ecdc_responses`, `ecdc_student_dates`.

When you change an entity, bump the version and add a `Migration`. Room validates the schema at startup and will crash if the migration SQL doesn't match the entity. The version history is documented at the top of `AppDatabase.java`.

---

## Server API used by the app

Paths are appended to the server address from the QR code.

| Method | Path | Used for |
| --- | --- | --- |
| `POST` | `/api/classrooms/sync` | Pull the teacher's classes |
| `POST` | `/api/students/sync` | Pull a class's student roster |
| `POST` | `/api/assessment/sync` | Pull the teacher's assessments and answer keys |
| `GET` | `/api/ecdc/domains` | Pull ECDC domains and competencies |
| `POST` (multipart) | `/api/upload/assessment` | Upload an assessment's CSV |
| `POST` (JSON) | `/api/ecdc/upload` | Upload ECDC results (single or mass) |

The methods come from the client code. The server is not in this repository, so request and response shapes beyond the ECDC upload are not documented here.

---

## Known limitations and things to verify

- **Beta expiry gate is disabled.** `MainActivity` always opens `DashboardActivity`; the `BetaExpiryChecker` call is commented out. If the beta window (April 2026 in `BetaExpiryChecker`) is still meant to be enforced, restore it.
- **No authentication on requests.** Only a client-supplied `user_id` is sent in the ECDC upload body. Confirm the server verifies it.
- **Release builds need HTTPS.** QR addresses without a scheme become `http://`, which a release build blocks.
- **Timezone of `last_ticked_at`.** The comment in `EcdcUploadPayloadBuilder` says GMT-8, but the code uses the device timezone. Confirm with the backend which is intended.
- **ECDC marks can be orphaned** if a server re-sync changes competency IDs; they are still uploaded, with `null` domain and competency names.
- **Restore is not atomic.** An error part-way through can leave a partial restore.
- **Student dashboard is a placeholder.** `StudentDashboardActivity` is opened for Student-role QR codes but has no features yet.
- **Tests:** the project only contains the template unit and instrumented tests. The ECDC payload builder and the upload guards are good candidates for unit tests.
- **Release minification is off** (`minifyEnabled false`) and `allowBackup` is `true`.
- **Gallery import is not supported;** scanning is live-camera only.

---

## Branches

| Branch | Notes |
| --- | --- |
| `main` | Default branch |
| `feature/with-por` | This branch: ECDC checklist with P/O/R present types, assessment dates, mass upload, ECDC backup |
| `feature/with-quizzes` | Quizzes feature branch |

---

## Other documents

These files were written by the previous team and **have not been audited against the current code**. They may describe behaviour that has since changed (for example gallery import or the older scan flow). Prefer this README and the code when they disagree.

- [`NEW_TEAM_QUICKSTART.md`](NEW_TEAM_QUICKSTART.md): short onboarding guide
- [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md): maintainer handoff notes
- [`FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md`](FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md): notes on fixed-mount and auto-capture changes
- [`answer_key_implementation_plan.md`](answer_key_implementation_plan.md): answer key planning notes
