<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://github.com/user-attachments/assets/0aa67016-6eaf-458a-adb2-6e31a0763ed6" />
</div>

# VToLive – Video to Live Photo Converter

Android application that allows users to select a video clip and
export a segment as an Apple‑style **Live Photo** (JPEG with motion
metadata).  The new multi‑page workflow is:

1. **Home** – pick a video or view stats on recent exports.
2. **Interval selection** – choose 1.5‑3 s segment with a timeline view.
3. **Preview** – scrub the segment and optionally select a custom
   key frame for the still image.
4. **Export** – generate the Live Photo, save to gallery, and share it.

Under the hood the app extracts key frames and motion frames using
`MediaMetadataRetriever` and writes a JPEG with appended XMP metadata.

> Legacy activities (single‑page `MainActivity` and `modules.ui
> IntervalSelectorActivity`) have been removed during refactor.

## Building the Android project

Use Android Studio or Gradle as usual:

```bash
./gradlew assembleDebug
```

Make sure to grant storage permissions on recent Android versions
and run the app on a device/emulator with video files available.
