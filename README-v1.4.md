# Pose Camera v1.8 — Real Color Grading

This version upgrades the grading engine so captured photos can be processed as real bitmaps:
- Brightness
- Contrast
- Saturation
- Warmth / coolness
- Auto Enhance (mild automatic luminance/contrast correction)
- JPEG export at high quality
- Reference pose overlay remains excluded from the saved image

The helper functions are in `MainActivity.kt`:
- `applyRealGrade(...)`
- `saveBitmapToGallery(...)`

Important: the existing v1.8 UI/capture pipeline is preserved. If your local source has a different capture callback, call `applyRealGrade()` on the captured bitmap/file before `saveBitmapToGallery()`.
