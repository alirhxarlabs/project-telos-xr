# TODO - Next Session

## Current Status ✅
- **Stereo video WORKING** on Samsung Galaxy XR
- GL_OVR_multiview2 implementation functional
- Side-by-side video correctly split to left/right eyes
- 60 FPS rendering stable

## Next Steps

### 1. Add Back 2D/3D Mode Toggle
- **Default to 3D stereo mode** on launch
- Toggle button in StatusOverlay.kt
- Switch between:
  - **3D Stereo**: Current GL_OVR_multiview2 implementation
  - **2D Side-by-Side**: Original non-stereo mode (both eyes see full SBS video)

**Files to modify:**
- `StatusOverlay.kt` - Add toggle UI
- `XRStreamViewModel.kt` - Add `isStereoMode` state back
- `XRContentView.kt` - Conditional rendering based on mode

### 2. Improve Stereo Resolution Quality
**Problem:** Stereo mode has lower visual quality compared to:
- 2D mode on same device
- Apple Vision Pro (project-tarus) stereo

**Investigation needed:**
- Check if texture resolution being halved unnecessarily
- Compare viewport sizing between 2D and stereo modes
- Review project-tarus Metal shader for quality hints
- May need higher bitrate or different MediaCodec settings for stereo

**Files to check:**
- `StereoGLRenderer.kt` - Texture and viewport configuration
- Video decoder settings in streaming service

### 3. Add Tethered Support
**Goal:** Support wired USB connection like project-telos Android

**Reference implementation:**
- Check project-telos Android repo for tethered code
- Likely involves ADB port forwarding or USB networking

**Files to modify:**
- Network/connection layer
- Add tethered option to connection UI
- May need new service or connection manager

## Technical Context

### Current Stereo Implementation
- **Renderer**: `StereoGLRenderer.kt`
- **Extension**: GL_OVR_multiview2
- **Shader**: Fragment shader splits SBS at x=0.5
- **OpenGL**: ES 3.0
- **Format**: Side-by-side (SBS) H.264 video input

### Key Files
```
app/src/main/java/com/alixarlabs/telosxr/
├── rendering/
│   └── StereoGLRenderer.kt        # Multiview stereo renderer
├── ui/
│   ├── XRContentView.kt           # Main UI with GLSurfaceView
│   └── StatusOverlay.kt           # Status bar overlay
└── viewmodel/
    └── XRStreamViewModel.kt       # Streaming state management
```

## Questions to Answer Next Time
1. Why is stereo resolution lower? Is it inherent to multiview or fixable?
2. What was the 2D mode implementation we started with?
3. How does tethered work in project-telos?
