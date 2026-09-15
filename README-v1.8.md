# Pose Camera v1.8 — Real Video + Pose Tracking

New:
- Real CameraX VideoCapture recording to Movies/PoseCamera.
- Live ML Kit pose tracking continues while recording.
- Live Pose Match percentage.
- Reference pose overlay stays UI-only and is NOT burned into the recorded video.
- Skeleton can be toggled on/off.
- Track/Pause and overlay lock remain available.
- Microphone permission is requested only when starting video.

The recorded video is the camera feed itself; the reference image and tracking skeleton are Compose UI overlays, so they are excluded from the saved MP4.
