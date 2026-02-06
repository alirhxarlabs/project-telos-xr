# TODO - Next Session

## Current Status ✅
- **Stereo video WORKING** on Samsung Galaxy XR
- GL_OVR_multiview2 implementation functional
- Side-by-side video correctly split to left/right eyes
- 60 FPS rendering stable

## Completed This Session ✅

### 1. Add Back 2D/3D Mode Toggle ✅
- **DONE**: Default to 3D stereo mode on launch
- **DONE**: Toggle button in StatusOverlay.kt
- **DONE**: Switch between:
  - **3D Stereo**: GL_OVR_multiview2 implementation
  - **2D Side-by-Side**: MonoGLRenderer showing full SBS video
- **DONE**: Fixed white border issue - video now fullscreen
- **DONE**: Video renderer switching works correctly

**Files modified:**
- `StatusOverlay.kt` - Added toggle UI
- `XRStreamViewModel.kt` - Added `isStereoMode` state
- `XRContentView.kt` - Conditional rendering, removed borders
- `MonoGLRenderer.kt` - New 2D renderer (created)

### 2. Add Tethered Support ✅
- **DONE**: WiFi/USB connection mode selector
- **DONE**: Network interface detection (wlan0 for WiFi, usb0/rndis0/eth0 for USB)
- **DONE**: Socket binding to specific interface
- **DONE**: UI with FilterChips for mode selection

**Files created:**
- `ConnectionMode.kt` - WIFI/USB_TETHERED enum
- `NetworkInterfaceManager.kt` - Interface detection

**Files modified:**
- `FECReceiverService.kt` - Interface-specific binding
- `HeartbeatSender.kt` - Source address binding
- `XRStreamViewModel.kt` - Connection mode management
- `XRContentView.kt` - WiFi/USB selector UI

**Note:** USB tethering code complete but untested on Galaxy XR alpha (USB host support unclear)

## Next Steps

### 1. Improve Stereo Resolution Quality
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

### 2. Test USB Tethering (if Galaxy XR supports it)
**Status:** Code implemented but unverified on Galaxy XR alpha

**To test:**
- Connect USB-C to Ethernet adapter to Galaxy XR
- Verify interface appears: `adb shell ip addr show`
- Check for usb0/rndis0/eth0 interface
- Test connection in app with USB mode selected

**If not supported:** Consider removing UI button or documenting as "future feature"

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
