# PhotoFrame Studio

**Professional Batch Image Preparation** — Android-native Kotlin + Jetpack Compose project.

## Implemented
- Android Storage Access Framework import of multiple image files.
- Recursive scanning of selected folders and nested subfolders (JPG/JPEG, PNG, WEBP, BMP).
- Persisted URI permissions where Android grants them.
- Separate output folder selection.
- Independent crop-in and extend-out values for all four edges.
- PNG export and adjustable JPEG quality.
- Sequential batch processing, progress, pause/resume and stop.
- Failure report and retry-only-failed action.
- Collision-safe output names; partial outputs are deleted after a processing error when possible.
- Processing settings saved between app launches.
- Source images are read-only and never overwritten.

## Image engine notes
Values are interpreted in source-image pixels and scaled to the decoded bitmap. Very large images are downsampled to cap memory use. This is not yet a full-resolution tiled print pipeline. PNG encoding is lossless; JPEG is lossy. EXIF orientation and ICC profile handling remain to be added.

## Remaining work
Reference-frame import/detection, editable frame coordinates, before/after preview, three test-size variants, TIFF support, color management, project/template persistence, device stress tests and build validation.

## Build
JDK 17 and Android SDK 35:
```bash
./gradlew assembleDebug
```
GitHub Actions is deferred until feature completion and verified build/test results.
