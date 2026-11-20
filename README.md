# PhotoFrame Studio

**Professional Batch Image Preparation** — Android-native Kotlin + Jetpack Compose.

## Implemented on development
- Multi-image import and recursive image-folder scan (JPG/JPEG, PNG, WEBP, BMP).
- Android Storage Access Framework with persisted URI permissions where supported.
- Separate output folder and collision-safe output names.
- Independent crop-in and extend-out controls for top, bottom, left and right.
- PNG/JPEG output with adjustable JPEG quality.
- Sequential batch processing, progress, pause/resume, stop, error report and retry failed items.
- Processing settings saved between launches.
- OpenCV-based reference-frame candidate detection and transfer of crop coordinates.
- Reference-derived crop values can be stored as normalized proportions and scaled to images with different dimensions; manually editing a side switches that side back to fixed source pixels.
- Original source images are never overwritten.

## Current limitations
- OpenCV detection is heuristic, not guaranteed recognition; candidates must be reviewed.
- Before/after visual preview, draggable coordinate overlay, three comparison sizes and saved project/template library remain.
- TIFF decoding, EXIF orientation normalization, ICC profile preservation and tiled full-resolution processing remain.
- Very large images may be downsampled to limit memory, so this is not yet a full-resolution print pipeline.

## Build
Use JDK 17 and Android SDK 35:
```bash
./gradlew assembleDebug
```
GitHub Actions will be added after feature completion and build/test verification.
