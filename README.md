# PhotoFrame Studio

**Professional Batch Image Preparation** — Android-native project using Kotlin and Jetpack Compose.

## Implemented in development branch
- Native Android project structure with Compose and RTL Persian dashboard.
- Select multiple images with Android Storage Access Framework.
- Select a folder and recursively enumerate supported images in nested folders.
- Persist SAF read/write URI permissions where Android grants them.
- Select an independent output folder.
- Per-edge crop-in and extend-out values for top, bottom, left and right.
- PNG export and configurable JPEG quality.
- Sequential processing, progress reporting, pause/resume and stop.
- Per-file error report and collision-safe output names.
- Original source documents are never overwritten.

## Current image engine behavior
The bitmap engine applies crop and extension values in source-image pixel units. To reduce memory pressure, exceptionally large images are decoded at a lower sample size. The output is a newly encoded bitmap. JPEG is lossy by definition; choose PNG when lossless encoding is needed. This is not yet a color-managed print pipeline.

## Still to implement and verify
- Reference-image import, frame detection and editable manual frame coordinates.
- Before/after preview and three test-scale variants.
- Project/template persistence and re-opening previous sessions.
- TIFF decoding, EXIF orientation normalization, ICC profile preservation and tiled full-resolution processing.
- Automated device tests, stress tests and final build verification.

## Build
JDK 17 and Android SDK 35:
```bash
./gradlew assembleDebug
```

GitHub Actions is intentionally deferred until feature completion and build/test validation. No release APK is claimed at this stage.
