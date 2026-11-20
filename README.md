# PhotoFrame Studio

**Professional Batch Image Preparation** — native Android application using Kotlin, Jetpack Compose, Android Storage Access Framework, Android Bitmap and OpenCV.

## Implemented on development
- Multi-image import and recursive image-folder scanning (JPG/JPEG, PNG, WEBP, BMP).
- Persisted SAF permissions where supported by Android document providers.
- Named projects saved locally, with reopening of selected files, output folder, reference image and processing settings.
- Independent output directory and collision-safe output filenames.
- Four independent edge controls: crop inward and extend outward for top, bottom, left and right.
- PNG and JPEG export with configurable JPEG quality.
- Sequential batch processing, progress, pause/resume, stop, failure report and retry-only-failed.
- Settings and project data persisted between launches.
- OpenCV candidate rectangle detection from a selected reference image.
- Reference-derived crop coordinates stored as normalized proportions and applied to images of different dimensions.
- Before/after preview for the first selected image; recalculated when settings change.
- Original source files are never overwritten.

## Known limitations / remaining work
- OpenCV detection is heuristic; it may select a shadow, artwork border or unrelated rectangle. Review candidates before applying.
- The current preview is downscaled for responsiveness and is not a print-resolution proof.
- Draggable manual coordinate overlay, three comparison/test-size variants and project deletion/rename remain.
- TIFF decoding, EXIF orientation normalization, ICC profile preservation and tiled full-resolution processing remain.
- Very large images may be downsampled to limit memory; this is not yet a full-resolution tiled print pipeline.

## Build
Use JDK 17 and Android SDK 35:
```bash
./gradlew assembleDebug
```
GitHub Actions will be added after feature completion and build/test verification.
