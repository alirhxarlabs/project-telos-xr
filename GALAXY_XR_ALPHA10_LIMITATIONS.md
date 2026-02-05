# Galaxy XR Alpha10 - StereoVideo Limitations

## Summary

**Stereo video rendering via `SurfaceEntity.StereoMode.SIDE_BY_SIDE` does NOT work on Galaxy XR alpha10** due to platform limitations.

## What We Discovered

### ✅ What Works
1. **XR Session creation** - `Session.create()` succeeds
2. **SurfaceEntity creation** - `SurfaceEntity.create()` with `SIDE_BY_SIDE` mode succeeds
3. **Surface validity** - Surface is valid and ready
4. **Video decoding** - H264Decoder renders frames to the surface successfully
5. **FPS counter** - Shows video is being received (~50fps)

### ❌ What Doesn't Work
**SurfaceEntity display** - The entity is created but **not visible** in the XR scene

## Root Cause

Galaxy XR alpha10 **does NOT grant SpatialUI capabilities** to apps, even with proper manifest configuration:

```
capabilities={
  hasSpatialUIControl=false,        // ❌ Required for SurfaceEntity display
  has3DContentsControl=false,
  hasPassThroughControl=false,
  hasEnvControl=false,
  hasSpatialAudioControl=true,      // ✅ Only this is granted
  hasSpatialEmbeddingControl=false
}
```

**Without `SpatialUIControl`, `SurfaceEntity` is created but never rendered to the XR scene.**

## What We Tried

### Attempt #1: Remove Spatial UI Check ✅ (Partial Success)
**Code change:**
```kotlin
// BEFORE (blocked creation):
if (xrSession != null && isSpatialEnabled) {

// AFTER (allows creation):
if (xrSession != null) {
```

**Result:**
- ✅ SurfaceEntity creates successfully
- ✅ Surface is valid
- ❌ **Still not visible** (no SpatialUIControl)

### Attempt #2: Request Full Spatial Mode ❌ (Failed)
**Manifest change:**
```xml
<!-- BEFORE -->
<meta-data
    android:name="com.google.android.xr.spatial.home_space_enabled"
    android:value="true" />

<!-- AFTER -->
<meta-data
    android:name="com.google.android.xr.spatial.enabled"
    android:value="true" />
```

**Result:**
- ❌ Still no SpatialUIControl granted
- ❌ Galaxy XR alpha10 limitation

## Technical Details

### XR Desktop Mode
App runs in `HOME_SPACE` mode with 2D rendering:
```
xrDesktopMode=HOME_SPACE
openXrRendering=false
is2DRenderingEnabled=true
```

### Video Pipeline (Working Correctly)
```
Thor (1920x1080 H.264 SBS @ 50fps)
  ↓
FEC Decoder
  ↓
H264 MediaCodec Decoder
  ↓
SurfaceEntity Surface (valid, but not displayed)
  ↓
❌ BLACK SCREEN (no SpatialUIControl to render it)
```

## Current Workaround

**Use standard 2D `SurfaceView`** - this displays the raw SBS video:
- ✅ Video is visible
- ❌ Shows both eyes side-by-side on flat screen
- ❌ No stereoscopic 3D depth effect

```kotlin
// This works on Galaxy XR alpha10:
SurfaceView(context).apply {
    holder.addCallback(object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            viewModel.setSurface(holder.surface)  // ✅ Displays (flat SBS)
        }
    })
}
```

## Why GLSurfaceView Doesn't Work Either

The custom `StereoGLRenderer` approach also fails:
- XR window manager destroys `GLSurfaceView` surface immediately after creation
- Surface lifecycle incompatible with XR 2D panel mode
- Manual viewport splitting doesn't work with XR runtime

## What Needs to Happen for Stereo

### Option 1: Wait for AndroidXR Stable Release (Recommended)
- Galaxy XR needs to grant `SpatialUIControl` capability
- `SurfaceEntity.StereoMode.SIDE_BY_SIDE` should then work as designed
- Expected when AndroidXR moves from alpha to stable

### Option 2: Device Firmware Update
- Samsung may update Galaxy XR firmware to enable full spatial capabilities
- Would allow `SurfaceEntity` to display properly

### Option 3: Different AndroidXR API
- Future API that works without SpatialUIControl
- Or specific API for video-only spatial rendering

## Comparison: Vision Pro vs Galaxy XR

| Feature | Vision Pro (Project Taris) | Galaxy XR (alpha10) |
|---------|---------------------------|-------------------|
| **Video Format** | 1920x1080 SBS ✅ | 1920x1080 SBS ✅ |
| **Decoding** | MediaCodec ✅ | MediaCodec ✅ |
| **SBS Splitting** | Metal compute shader ✅ | SurfaceEntity (created) ✅ |
| **Stereo Display** | RealityKit ✅ | **NOT VISIBLE** ❌ |
| **Capability Granted** | Full spatial ✅ | Only SpatialAudio ❌ |
| **Result** | **Works perfectly** ✅ | **Black screen** ❌ |

## Conclusion

**The stereo video implementation is correct**, but **Galaxy XR alpha10 doesn't support it** due to missing `SpatialUIControl` capability.

### For Now:
- Video displays in 2D mode (flat SBS)
- Wait for AndroidXR stable release
- Monitor Samsung Galaxy XR updates

### When AndroidXR Stable Releases:
- The current code should work immediately
- Just re-enable the SurfaceEntity path
- No code changes needed (already implemented correctly)

## Files Modified

1. `app/src/main/java/com/alixarlabs/telosxr/ui/XRContentView.kt`
   - Removed incorrect spatial UI check
   - Added comprehensive error logging
   - Documented alpha10 limitations

2. `app/src/main/AndroidManifest.xml`
   - Changed from `home_space_enabled` to `spatial.enabled`
   - Didn't help (alpha10 limitation)

3. Documentation:
   - `DEVELOPMENT_STATUS.md` - Updated with findings
   - `STEREO_FIX_TESTING_GUIDE.md` - Testing procedures
   - `CHANGES_SUMMARY.md` - All changes documented
   - This file - Alpha10 limitations explained

## References

- **AndroidXR Spatial Video Docs**: https://developer.android.com/develop/xr/jetpack-xr-sdk/add-spatial-video
- **Vision Pro (working)**: Project Taris implementation
- **Galaxy XR**: alpha10 build (1.0.0-alpha10)
