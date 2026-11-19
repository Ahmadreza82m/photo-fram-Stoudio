# PhotoFrame Studio

**Professional Batch Image Preparation** — native Android project using Kotlin and Jetpack Compose.

## Project status
This is the initial Android application foundation. It contains the Gradle project, Android manifest, launcher activity, and an RTL dark-glass Compose dashboard with working Android file/folder pickers.

## Planned implementation
- Recursive SAF image-folder scanning and persistent URI permissions
- Reference frame import, assisted edge detection and manual correction
- Independent crop-in / extend-out controls for all four edges
- Accurate preview, guides and before/after comparison
- Sequential batch processing with pause/resume/cancel and retry reporting
- PNG/JPEG export, collision-safe filenames and output validation
- Saved projects and reusable templates
- Device testing and GitHub Actions APK build after implementation and review

## Build environment
Android Studio, JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug
```

No APK workflow is added yet intentionally. This repository is not yet a feature-complete release.
