# New Team Quickstart

## What This App Is

OMRScanner is an Android app for scanning paper-based OMR answer sheets using the phone camera.

Core workflow:

1. open a class and assessment
2. launch the scanner
3. detect the sheet anchors
4. auto-capture the image
5. align and read the sheet
6. verify the LRN
7. save results and export when needed

Supported sheet types:

- `ZPH30`
- `ZPH40`
- `ZPH50`
- `ZPH60`

## What To Read First

If you are new to the repo, start here:

1. [README.md](/C:/Users/maest/AndroidStudioProjects/OMRScanner/README.md)
2. [DEVELOPER_GUIDE.md](/C:/Users/maest/AndroidStudioProjects/OMRScanner/DEVELOPER_GUIDE.md)
3. [DashboardActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java)
4. [CameraActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/camera/CameraActivity.java)
5. [AnchorDetector.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/AnchorDetector.java)
6. [ResultActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/ui/ResultActivity.java)

## Mental Model

The app has two halves:

- dashboard/data side
  - classes
  - assessments
  - scans
  - answer keys
  - exports
- scanning side
  - live camera
  - anchor detection
  - perspective alignment
  - template detection
  - bubble reading

## Main Scan Flow

Normal camera flow:

1. [DashboardActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java) launches [CameraActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/camera/CameraActivity.java)
2. [CameraActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/camera/CameraActivity.java) runs live anchor detection
3. once 4 valid anchors are detected, capture happens immediately
4. [ResultActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/ui/ResultActivity.java) aligns the image and scans the answers
5. results are saved through the Room-backed repository

Gallery/manual path:

1. dashboard opens [PreviewActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/ui/PreviewActivity.java)
2. still-image anchor detection runs
3. user proceeds to [ResultActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/ui/ResultActivity.java)

## Current Important Behavior

### Immediate auto-capture

The app no longer waits for multiple stable frames before capturing.

Current behavior:

- first valid 4-anchor frame triggers capture
- overlay smoothing still exists
- overlay smoothing does not delay capture

Primary files:

- [CameraActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/camera/CameraActivity.java)
- [AnchorDetector.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/AnchorDetector.java)

### Fixed-mount mode

The app supports elevated/fixed phone setups.

Current behavior:

- dashboard lets the user choose `Handheld Scan` or `Fixed Mount Scan`
- fixed-mount mode is still immediate once anchors are detected
- any extra delay happens before detection succeeds
- that delay comes from fallback detection and zoom adjustment when anchors are too small

## Most Important Files

### Scan pipeline

- [CameraActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/camera/CameraActivity.java)
- [AnchorDetector.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/AnchorDetector.java)
- [PerspectiveAligner.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/PerspectiveAligner.java)
- [TemplateManager.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/TemplateManager.java)
- [GridAligner.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/GridAligner.java)
- [BubbleScanner.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/BubbleScanner.java)

### Data layer

- [AppDatabase.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/AppDatabase.java)
- [OMRRepository.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/OMRRepository.java)
- [DataMapper.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/DataMapper.java)

### Dashboard/UI

- [DashboardActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/DashboardActivity.java)
- [DashboardDialogs.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/DashboardDialogs.java)
- [ClassExporter.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/dashboard/ClassExporter.java)
- [ScanDetailActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/ui/ScanDetailActivity.java)

## Common Tasks

### Change live scan sensitivity

Edit [AnchorDetector.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/AnchorDetector.java).

### Change fixed-mount zoom behavior

Edit [CameraActivity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/camera/CameraActivity.java).

### Change bubble fill threshold

Edit [BubbleScanner.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/BubbleScanner.java).

### Add a new sheet type

Update:

- `app/src/main/assets/templates/`
- [TemplateManager.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/omr/TemplateManager.java)
- [AssessmentEntity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/entities/AssessmentEntity.java)
- [ActivityFolder.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/models/ActivityFolder.java)
- [AnswerKeyEntity.java](/C:/Users/maest/AndroidStudioProjects/OMRScanner/app/src/main/java/com/example/omrscanner/database/entities/AnswerKeyEntity.java)

## Known Gotchas

- `OMRRepository` callbacks are not automatically returned on the main thread.
- `PreviewActivity` still exists, but it is not the main camera flow anymore.
- `SplashActivity` exists, but the launcher activity is `MainActivity`.
- `StorageManager` looks legacy compared to the active Room-based data flow.
- Scan tuning must be tested with real sheets, not only screenshots.

## Build Command

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

## If You Only Remember Three Things

1. Live capture is immediate after the first valid 4-anchor detection.
2. Fixed-mount mode adds pre-detection compensation, not post-detection capture delay.
3. The safest files to inspect first are `DashboardActivity`, `CameraActivity`, `AnchorDetector`, and `ResultActivity`.