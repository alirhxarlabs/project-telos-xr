# Project Telos XR - Development Status

AndroidXR port of Project Telos for Samsung Galaxy XR headset

## Current Status (2026-02-02)

### ✅ Working Features
- **XR Session Creation**: XR runtime initializes successfully
- **Video Streaming**: H.264 video streams from Thor device at ~50fps
- **FEC Error Correction**: Forward Error Correction working reliably
- **Network Stack**: UDP multicast reception, packet reassembly, NAL unit decoding
- **Voice Commands**: Continuous speech recognition with auto-restart (requires device reboot to clear system service connections on first use)
- **Auto-connect**: Automatically connects to Thor on app startup
- **2D Video Display**: Video renders correctly in 2D mode

### ❌ Not Yet Working
- **Stereoscopic 3D Rendering**: Side-by-side stereo video not displaying correctly

## Stereo Rendering Investigation

### The Goal
Display side-by-side (SBS) stereoscopic video like Vision Pro Project Taris:
- Left eye sees left half of frame
- Right eye sees right half of frame
- Each eye gets full field-of-view (stretched from half-width source)

### Attempted Solutions

#### 1. SurfaceEntity with StereoMode.SIDE_BY_SIDE (Preferred)
**Status**: Not working - spatial UI unavailable

```kotlin
val entity = SurfaceEntity.create(
    session = xrSession,
    stereoMode = SurfaceEntity.StereoMode.SIDE_BY_SIDE,
    pose = Pose(Vector3(0.0f, 0.0f, -2.0f)),
    shape = SurfaceEntity.Shape.Quad(FloatSize2d(1.6f, 0.9f))
)
```

**Issue**:
- Requires `isSpatialUiEnabled = true`
- Galaxy XR returns `isSpatialUiEnabled = false` even in XR mode
- AndroidXR alpha10 spatial APIs incomplete/unstable
- ANR (Application Not Responding) when attempting to create SurfaceEntity

**This is the correct API** but needs AndroidXR stable release

#### 2. GLSurfaceView with Custom Stereo Shader
**Status**: Renderer works but surface lifecycle issues

```kotlin
// Fragment shader splits SBS texture into left/right eye views
if (uEyeIndex == 0) {
    texCoord.x = texCoord.x * 0.5;  // Left half
} else {
    texCoord.x = 0.5 + texCoord.x * 0.5;  // Right half
}

// Dual viewport rendering
glViewport(0, 0, width/2, height);  // Left eye
glViewport(width/2, 0, width/2, height);  // Right eye
```

**Issue**:
- OpenGL setup successful, shaders compile correctly
- Video frames ARE decoded and arriving at SurfaceTexture
- BUT: XR window manager destroys GLSurfaceView surface immediately after creation
- Logs show `surfaceDestroyed` right after `surfaceCreated`
- Surface lifecycle incompatible with XR 2D panel mode

**Renderer implementation is correct** but can't display due to surface destruction

#### 3. Regular SurfaceView (2D Mode)
**Status**: ✅ WORKING

Standard Android SurfaceView with MediaCodec output. No stereo splitting.

## Voice Recognition Fix

### Issue
SpeechRecognizer failed with error 10 (insufficient permissions)

### Root Cause
System process exceeded maximum service connections:
```
W ActivityManager: bindService exceeded max service connection number per process
```

### Solution
**Device reboot** to clear stale service connections

### Implementation
```kotlin
// Standard SpeechRecognizer (on-device API has binding issues on Galaxy XR alpha)
speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

// Auto-restart with 100ms delay to prevent service connection leaks
handler.postDelayed({ startListening() }, 100)
```

## Architecture

### Video Pipeline
```
Thor Device (H.264 encoder)
    ↓ UDP multicast
FECReceiverService (FEC decoder)
    ↓ NAL units
H264Decoder (MediaCodec)
    ↓ Surface
Rendering (SurfaceView/GLSurfaceView/SurfaceEntity)
    ↓
Display (2D panel or XR spatial)
```

### Voice Pipeline
```
SpeechRecognizer (standard Android API)
    ↓ text transcript
VoiceCommandManager (command parsing)
    ↓ HTTP requests
OrbeyeCommandClient → Thor device
```

### XR Mode Detection
```kotlin
val xrSession = Session.create(activity, lifecycleOwner)
val isSpatialEnabled = LocalSpatialCapabilities.current?.isSpatialUiEnabled == true

if (xrSession != null && isSpatialEnabled) {
    // MODE 1: Native XR stereo with SurfaceEntity
} else if (isStereoMode) {
    // MODE 2: Custom OpenGL stereo renderer
} else {
    // MODE 3: Standard 2D SurfaceView
}
```

## Technical Details

### Tested On
- Device: Samsung Galaxy XR (prototype)
- OS: AndroidXR alpha10
- SDK: androidx.xr.compose:1.0.0-alpha10

### Dependencies
```gradle
// AndroidXR
implementation("androidx.xr.compose:compose:1.0.0-alpha10")
implementation("androidx.xr.runtime:runtime:1.0.0-alpha10")
implementation("androidx.xr.scenecore:scenecore:1.0.0-alpha10")

// Jetpack Compose
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// Media & OpenGL
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.opengl:opengl:1.0.0")
```

### Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<uses-feature android:name="android.hardware.xr.headtracking" android:required="true" />
<uses-feature android:name="android.hardware.xr.spatial" android:required="true" />
```

## Next Steps

### Option 1: Wait for AndroidXR Stable (Recommended)
- AndroidXR is still in alpha (1.0.0-alpha10)
- Spatial APIs incomplete/unstable
- SurfaceEntity.StereoMode.SIDE_BY_SIDE should work in stable release
- Monitor: https://developer.android.com/develop/xr

### Option 2: Fix GLSurfaceView Surface Lifecycle
- Investigate why XR destroys GLSurfaceView surface immediately
- May need custom XR window handling
- Could be related to 2D panel vs spatial mode

### Option 3: Implement OpenXR Directly
- Bypass AndroidXR Jetpack library
- Use OpenXR standard API directly
- More control but more complex

## References

- **Vision Pro Implementation**: Project Taris (working stereo with Metal shaders)
- **AndroidXR Docs**: https://developer.android.com/develop/xr/jetpack-xr-sdk
- **Spatial Video Guide**: https://developer.android.com/develop/xr/jetpack-xr-sdk/add-spatial-video
- **Voice Recognition**: https://developer.android.com/develop/xr/jetpack-xr-sdk/asr

## Build & Deploy

```bash
cd ~/AndroidStudioProjects/project-telos-xr
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Known Issues

1. **Voice requires device reboot on first use** - System service connection leak
2. **Stereo not working** - AndroidXR alpha10 spatial APIs incomplete
3. **No disconnect button** - Removed per user request (auto-reconnects anyway)
4. **Defaults to stereo mode** - Even though stereo doesn't work yet (shows raw SBS)

## Testing Commands

```bash
# Monitor logs
adb logcat | grep -E "XRContentView|VoiceCommandManager|H264Decoder|FEC"

# Check XR session
adb logcat | grep "XR Session"

# Check voice recognition
adb logcat | grep -E "Ready for speech|Processing:"

# Check video streaming
adb logcat | grep -E "Packets received|Video rendering"
```
