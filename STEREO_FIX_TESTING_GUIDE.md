# Stereo Rendering Fix - Testing Guide

## What Was Fixed

### Problem
Side-by-side stereo video (1920x1080 from Thor) was not displaying correctly on Galaxy XR because the code incorrectly required `isSpatialUiEnabled = true` to create the stereo SurfaceEntity.

### Solution
1. **Removed the spatial UI check** - `StereoMode.SIDE_BY_SIDE` doesn't actually require spatial UI
2. **Added comprehensive logging** - Now shows detailed error information
3. **Confirmed video format** - Verified 1920x1080 SBS matches Vision Pro implementation

## Build and Deploy

### 1. Build the APK
```bash
cd ~/AndroidStudioProjects/project-telos-xr
./gradlew assembleDebug
```

### 2. Install on Galaxy XR
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Launch App
The app will auto-connect to Thor and start streaming.

## Testing Steps

### Step 1: Check Stereo Mode is Enabled
- Stereo mode should be ON by default (toggle at bottom of UI)
- Green "3D" indicator should be highlighted

### Step 2: Monitor Logs
Open a terminal and run:
```bash
adb logcat | grep "XRContentView"
```

### Expected Success Logs
```
I XRContentView: === XR Environment Check ===
I XRContentView:   XR Session: true
I XRContentView:   Stereo Mode: true
I XRContentView:   Video Resolution: 1920x1080 SBS (960x1080 per eye)
I XRContentView: === Creating SurfaceEntity ===
I XRContentView:   Stereo Mode: SIDE_BY_SIDE
I XRContentView:   Expected: Auto-split 1920x1080 → 960x1080 per eye
I XRContentView: ✅ SurfaceEntity created successfully
I XRContentView:   Surface obtained: true
I XRContentView:   Surface valid: true
I XRContentView: ✅ Surface ready! Starting video stream...
```

### Step 3: Verify Stereo Display

#### What You Should See:
- **Video displays in 3D with depth** (not flat)
- **Each eye sees a different perspective**
- **No double image** (properly separated to each eye)

#### What You Should NOT See:
- Two side-by-side images on a flat screen
- Double vision or overlapping images
- Black screen or error

### Step 4: Toggle Stereo Mode
- Click the stereo toggle button
- Switch to "2D" mode
- Video should display as flat 2D (no depth)
- Switch back to "3D" mode
- Video should have depth again

## Troubleshooting

### If You See Error Logs

The enhanced logging will show exactly what failed. Common scenarios:

#### SecurityException
```
E XRContentView: ❌ SecurityException creating SurfaceEntity
E XRContentView:    Check AndroidManifest.xml permissions
```
**Fix:** Verify AndroidManifest has XR permissions (should already be there)

#### IllegalStateException
```
E XRContentView: ❌ IllegalStateException creating SurfaceEntity
E XRContentView:    XR Session may not be fully initialized
```
**Possible causes:**
- XR runtime not ready yet (try again after a few seconds)
- Galaxy XR firmware issue
- AndroidXR alpha10 bug

#### Surface Invalid
```
E XRContentView: ❌ Surface is invalid or null
E XRContentView:    This may indicate XR runtime issues
```
**Possible causes:**
- XR runtime bug
- Timing issue (surface created before XR ready)

### If Stereo Still Doesn't Work

1. **Capture full logs:**
   ```bash
   adb logcat > stereo_test_logs.txt
   ```
   Look for any errors around SurfaceEntity creation

2. **Check XR Session:**
   ```bash
   adb logcat | grep "MainActivity"
   ```
   Should see: "XR Session created successfully"

3. **Try rebooting Galaxy XR** - Sometimes XR runtime needs reset

4. **Verify Thor is streaming:**
   ```bash
   adb logcat | grep "FEC\|H264Decoder"
   ```
   Should see packets being received and frames decoded

## Video Format Reference

### Thor Streaming Format (confirmed from Project Taris)
- **Resolution:** 1920x1080@50fps
- **Codec:** H.264
- **Format:** Side-by-side stereo
- **Left eye:** Pixels x: 0-959 (left half)
- **Right eye:** Pixels x: 960-1919 (right half)

### How AndroidXR Should Handle It
1. MediaCodec decodes 1920x1080 H.264 to a Surface
2. `SurfaceEntity` with `StereoMode.SIDE_BY_SIDE` automatically:
   - Splits the surface (left half → left eye, right half → right eye)
   - Stretches each half to full display resolution
   - Renders to the corresponding eye display

This is equivalent to Vision Pro's Metal shader approach, but native to AndroidXR.

## Success Criteria

✅ **Stereo working if:**
- Logs show "✅ SurfaceEntity created successfully"
- Video has 3D depth effect
- No double vision
- Smooth 50fps playback

❌ **Stereo not working if:**
- Falls back to GLSurfaceView mode
- See two side-by-side images on flat screen
- Black screen or crash

## Comparison: Before vs After

### Before Fix
```kotlin
if (xrSession != null && isSpatialEnabled) {  // ❌
```
- On Galaxy XR: `isSpatialEnabled = false`
- Result: Never created SurfaceEntity
- Fell back to GLSurfaceView (wrong approach)

### After Fix
```kotlin
if (xrSession != null) {  // ✅
```
- On Galaxy XR: `xrSession != null` is true
- Result: Creates SurfaceEntity with SIDE_BY_SIDE
- Should display proper stereo

## Report Results

After testing, please report:

1. **What you see:**
   - Proper 3D stereo? ✅
   - Flat SBS (two images)? ❌
   - Black screen? ❌

2. **Relevant log snippets:**
   - SurfaceEntity creation logs
   - Any errors

3. **Video quality:**
   - Smooth playback?
   - Correct depth?
   - Any artifacts?

This will help determine if additional fixes are needed or if AndroidXR alpha10 has other limitations.
