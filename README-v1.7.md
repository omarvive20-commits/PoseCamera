# Pose Camera v1.8 — Real Pose Tracking

Implemented with Google ML Kit Pose Detection:
- Detects a reference pose from a Gallery image.
- Runs live pose detection on CameraX frames.
- Shows a live skeleton overlay.
- Calculates a live Pose Match percentage from detected landmarks.
- Track / Pause and Skeleton / Clean controls.
- Reference image remains movable/zoomable and can be locked.
- The tracking skeleton and reference overlay are UI only; they are not part of captured output.

Note:
The match score is a lightweight landmark-distance score, not a professional biomechanics score. It is intended as a visual posing aid.
