# OMRScanner

OMRScanner is an Android application for scanning Optical Mark Recognition (OMR) answer sheets using the phone camera. It is built with Java, CameraX, OpenCV, Room, and Gson.

The app is designed for school-style paper assessments. It detects the 4 corner anchor squares of a supported OMR sheet, captures the image automatically, aligns the paper into a fixed reference frame, determines the sheet template, reads the learner reference number and marked answers, then stores and exports the results.

## Overview

OMRScanner combines a live camera scanner with a local class and assessment management system.

At a high level, it lets users:

- create and manage classes
- create assessments tied to supported OMR sheet types
- scan answer sheets from the camera
- import images from the gallery
- auto-detect sheet anchors and capture automatically
- align and read LRN and answers
- store scans locally
- assign answer keys and grade results
- export scan data and images

## Feature Summary

| Area | What It Does |
| --- | --- |
| Class management | Organizes scans under classes and assessments |
| Live scanning | Detects sheet anchors from CameraX preview frames |
| Auto-capture | Captures immediately after the first valid 4-anchor detection |
| Fixed-mount mode | Supports elevated or mounted phone setups with distance compensation |
| Template detection | Detects supported sheet type and orientation after alignment |
| Bubble reading | Extracts LRN and marked answers from warped sheet images |
| Grading | Supports reusable answer keys and per-assessment grading |
| Export | Produces CSV output and protected ZIP exports |

## Supported Sheet Types

- `ZPH30`
- `ZPH40`
- `ZPH50`
- `ZPH60`

## Main User Flow

1. create or open a class
2. create or open an assessment
3. choose scan mode
   - handheld
   - fixed mount
4. place a sheet under the camera
5. app detects the corner anchors
6. app captures automatically when anchors are valid
7. app aligns and scans the sheet
8. user verifies the LRN
9. app saves and can export the results

## Scan Modes

### Handheld Scan

Optimized for close-up scanning where the user holds the phone over the sheet.

### Fixed Mount Scan

Optimized for elevated or mounted devices where users slide sheets underneath the camera. This mode adds distance compensation using:

- more tolerant distant-anchor detection
- a far-distance fallback detection pass
- bounded zoom stepping when repeated misses occur

Important behavior:

- capture is still immediate once anchors are detected
- any extra delay happens only while the app is trying to make distant anchors detectable

## Current Capture Behavior

The live scanner has already been optimized for faster auto-capture:

- first valid 4-anchor detection triggers capture immediately
- overlay smoothing remains for visual stability only
- overlay smoothing does not delay capture
- live detection avoids the older expensive bitmap conversion path
- fixed-mount support exists for higher camera positions

## Architecture

The app has two main halves:

### 1. Dashboard and data management

Handles:

- teacher profile
- classes
- assessments
- scan history
- answer keys
- exports

Primary entry point:

- `DashboardActivity`

### 2. OMR scanning pipeline

Handles:

- live camera preview
- anchor detection
- perspective alignment
- template and orientation detection
- bubble scanning
- result verification and saving

Primary flow:

- `DashboardActivity -> CameraActivity -> ResultActivity`

## Scan Pipeline Diagram

```text
DashboardActivity
    |
    v
CameraActivity
    |
    |-- CameraX Preview
    |-- ImageAnalysis
    |      |
    |      v
    |   AnchorDetector
    |      |
    |      v
    |   4 valid anchors found
    |      |
    |      v
    |   immediate takePhoto()
    |
    v
ResultActivity
    |
    v
PerspectiveAligner
    |
    v
TemplateManager
    |
    v
BubbleScanner
    |
    v
LRN verification + save/export
```

## Project Structure

```text
OMRScanner/
├─ app/
│  ├─ src/main/java/com/example/omrscanner/
│  │  ├─ camera/
│  │  ├─ dashboard/
│  │  ├─ database/
│  │  ├─ models/
│  │  ├─ omr/
│  │  ├─ ui/
│  │  └─ utils/
│  ├─ src/main/assets/templates/
│  └─ src/main/res/
├─ sdk/
├─ README.md
├─ NEW_TEAM_QUICKSTART.md
└─ DEVELOPER_GUIDE.md
```

## Key Technical Components

### Camera and live detection

- CameraX preview, capture, and analysis live in `camera/`
- `CameraActivity` runs the live analyzer loop
- `AnchorOverlayView` draws the detected anchor quadrilateral

### OMR processing

- `AnchorDetector`
  - finds the 4 corner black squares
- `PerspectiveAligner`
  - warps the sheet into a fixed canonical rectangle
- `TemplateManager`
  - loads templates, detects sheet type, resolves orientation
- `GridAligner`
  - refines block alignment using template matching
- `BubbleScanner`
  - reads LRN and answers from the aligned image

### Persistence

- Room database stores:
  - teachers
  - classes
  - assessments
  - scans
  - answers
  - answer keys

## Tech Stack

- Java
- CameraX
- OpenCV
- Room
- Gson
- Material Components
- Zip4j

## Project Modules

- `app`
  - main Android application
- `sdk`
  - imported OpenCV Android SDK module

## Documents

- [NEW_TEAM_QUICKSTART.md](/C:/Users/maest/AndroidStudioProjects/OMRScanner/NEW_TEAM_QUICKSTART.md)
  - short onboarding guide
- [DEVELOPER_GUIDE.md](/C:/Users/maest/AndroidStudioProjects/OMRScanner/DEVELOPER_GUIDE.md)
  - detailed maintainer handoff
- [FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md](/C:/Users/maest/AndroidStudioProjects/OMRScanner/FIXED_MOUNT_AND_AUTO_CAPTURE_SUMMARY.md)
  - summary of recent scanning behavior changes

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

or:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

## Notes For Future Maintainers

- the main camera flow is `DashboardActivity -> CameraActivity -> ResultActivity`
- `PreviewActivity` still exists for still-image and gallery flows
- Room is the main persistence layer
- scan tuning should always be validated using real printed sheets and real devices
- fixed-mount mode adds pre-detection compensation, not post-detection capture delay