# Project Telos XR

AndroidXR version of Project Telos - FEC UDP video receiver with voice commands for Thor device.

## Overview

This app displays live video from the Thor surgical device in an XR spatial panel, matching the Vision Pro (Project Taris) UI/UX. When running on XR devices, the video appears as a floating 1.6m wide panel 2 meters in front of the user. On standard Android devices, it displays as regular 2D UI.

### Features

- ✅ **Spatial Video Display**: 800x450 video panel in XR space (or fullscreen on regular Android)
- ✅ **FEC UDP Streaming**: Custom FEC protocol with XOR error correction
- ✅ **H.264 Video Decoding**: Hardware-accelerated MediaCodec
- ✅ **Voice Commands**: Always-on speech recognition for Thor control
- ✅ **Auto-Connect**: Automatically connects to Thor on launch
- ✅ **Real-time Stats**: FPS counter, voice status indicator
- ✅ **2D/3D Toggle**: Switch between mono and stereo modes (stereo not yet implemented)

## Requirements

### For XR Mode
- AndroidXR-compatible device (Samsung/Google XR headset)
- Android API 34+ runtime
- AndroidXR system services

### For Standard Android Mode
- Android device with API 24+
- Microphone for voice commands
- WiFi connection to Thor device (192.168.0.225)

## Architecture

### Shared Code (from Android Project Telos)
- `network/` - FEC UDP receiver, heartbeat sender
- `fec/` - FEC decoder, NAL unit reassembler
- `decoder/` - H.264 MediaCodec decoder
- `voice/` - Voice command manager
- `model/` - Connection state, streaming stats

### XR-Specific Code
- `MainActivity.kt` - XR activity with spatial panel
- `ui/XRContentView.kt` - Main UI matching Vision Pro
- `ui/StatusOverlay.kt` - FPS/voice/controls
- `ui/ConnectionOverlay.kt` - Connection status
- `viewmodel/XRStreamViewModel.kt` - Streaming state management

## Build Instructions

### Prerequisites
1. Install Android Studio (latest version)
2. Install Android SDK API 34+
3. Enable AndroidXR emulator or connect XR device

### Build & Install
```bash
cd ~/AndroidStudioProjects/project-telos-xr

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or build and install in one step
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run on Emulator
```bash
# Launch AndroidXR emulator (requires Android Emulator 35.6.7+)
# Then install and run the app
```

## Network Configuration

The app connects to Thor device at:
- **Thor IP**: `192.168.0.225`
- **Video Port**: `5700` (FEC UDP packets)
- **Heartbeat Port**: `5701` (connection keepalive)
- **Command Port**: `9000` (voice control JSON/UDP)

To change Thor IP, edit:
- `ui/XRContentView.kt`: Line with `OrbeyeCommandClient(thorIp = "192.168.0.225")`

## Voice Commands

The app supports the same voice commands as Vision Pro:

### Movement
- "Move up" / "Pan up"
- "Move down" / "Pan down"
- "Move left" / "Pan left"
- "Move right" / "Pan right"
- "Stop"

### Zoom & Focus
- "Zoom in"
- "Zoom out"
- "Focus in" / "Focus near"
- "Focus out" / "Focus far"

### Control Modes
- "Gaze control" / "Voice control"
- "Manual control"
- "Tool control"
- "Gamepad control"

## UI Layout (Matches Vision Pro)

```
┌─────────────────────────────────────┐
│                                     │
│        800x450 Video Surface        │
│        (Green border when           │
│         connected)                  │
│                                     │
└─────────────────────────────────────┘

Status Bar:
[●] FEC  |  FPS: 50.0  |  🎤 Last Command  |  [🎤 Voice]
               [2D/3D Toggle]  [Disconnect]
```

## XR vs Standard Android Mode

The app detects XR capabilities at runtime and adapts:

| Feature | XR Mode | Standard Android |
|---------|---------|------------------|
| **Display** | Floating spatial panel | Fullscreen 2D |
| **Position** | 2m in front, eye level | Screen coordinates |
| **Size** | 1.6m × 0.9m (physical) | 800dp × 450dp |
| **Input** | Gaze + Voice | Touch + Voice |
| **Navigation** | Spatial movement | Standard nav |

## Known Limitations

1. **Stereo Mode**: 2D/3D toggle exists but stereo rendering not yet implemented
2. **AndroidXR Alpha**: SDK is in alpha (v1.0.0-alpha10), APIs may change
3. **XR Device Required**: XR features only work on compatible hardware
4. **WiFi Only**: Requires local network connection to Thor

## Troubleshooting

### App Won't Connect
- Check Thor is powered on and connected to network
- Verify Android device on same WiFi (192.168.0.x)
- Check firewall not blocking UDP ports 5700, 5701, 9000

### Black Screen
- Wait for IDR frame (initial connection takes 1-2 seconds)
- Check logcat: `adb logcat | grep -E "FEC|NAL|H264"`
- Ensure SPS/PPS packets received before video starts

### Voice Not Working
- Grant microphone permission in Settings
- Check voice indicator shows red (listening)
- Verify commands in English with clear pronunciation

### XR Mode Not Activating
- Ensure running on AndroidXR-compatible device
- Check `LocalSpatialCapabilities.current.isSpatialUiEnabled` returns true
- Verify AndroidXR system services are running

## Debugging

### View Logs
```bash
# All logs
adb logcat

# Filter XR-specific
adb logcat | grep -E "Telos|XR|Spatial"

# Filter video streaming
adb logcat | grep -E "FEC|NAL|H264|MediaCodec"

# Filter voice commands
adb logcat | grep -E "Voice|Speech|Orbeye"
```

### Monitor Network
```bash
# Check Thor connectivity
ping 192.168.0.225

# Monitor Thor logs
ssh thor@192.168.0.225
journalctl -f | grep -i "headset\|command"
```

## Development

### Project Structure
```
app/src/main/java/com/alixarlabs/telosxr/
├── MainActivity.kt
├── decoder/
│   └── H264Decoder.kt
├── fec/
│   ├── FECDecoder.kt
│   ├── FECPacket.kt
│   └── NALReassembler.kt
├── model/
│   ├── ConnectionState.kt
│   └── StreamStats.kt
├── network/
│   ├── FECReceiverService.kt
│   ├── HeartbeatSender.kt
│   └── NetworkConfig.kt
├── ui/
│   ├── ConnectionOverlay.kt
│   ├── StatusOverlay.kt
│   ├── XRContentView.kt
│   └── theme/
├── viewmodel/
│   └── XRStreamViewModel.kt
└── voice/
    ├── OrbeyeCommandClient.kt
    └── VoiceCommandManager.kt
```

### Adding Features

**Add Stereo Rendering**:
1. Create `StereoGLRenderer.kt` with OpenGL ES compute shaders
2. Split side-by-side video into left/right eye textures
3. Render to dual viewports
4. Update `XRContentView.kt` to use stereo renderer when toggled

**Add Spatial Audio**:
1. Use `SubspaceModifier.onPointSourceParams`
2. Position audio source at video panel location
3. Enable spatialized audio for immersion

## License

Copyright © 2026 AliXR Labs. All rights reserved.

## Related Projects

- **Project Telos (Android)**: Standard Android version
- **Project Taris (Vision Pro)**: Apple Vision Pro version
- **Thor Device**: Surgical camera streaming server

## Support

For issues or questions:
- GitHub: https://github.com/alixarlabs/project-telos-xr
- Documentation: See IMPLEMENTATION_GUIDE.md
