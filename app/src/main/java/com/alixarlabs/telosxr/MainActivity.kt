package com.alixarlabs.telosxr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateResult
import com.alixarlabs.telosxr.ui.XRContentView
import com.alixarlabs.telosxr.ui.theme.TelosXRTheme

/**
 * MainActivity for Project Telos XR
 *
 * AndroidXR application for Samsung Galaxy XR headset
 * Streams H.264 video from Thor device with FEC error correction
 *
 * CURRENT STATUS (AndroidXR alpha10):
 * ✅ XR Session creation: WORKING
 * ✅ Video streaming (FEC + H.264): WORKING
 * ✅ Voice commands: WORKING (after device reboot to clear system service connections)
 * ❌ Stereo 3D rendering: NOT YET WORKING (spatial UI APIs incomplete in alpha10)
 *
 * ARCHITECTURE:
 * - XR Session created with reflection (SessionCreateResult handling)
 * - Falls back to 2D panel mode when spatial UI unavailable
 * - Three rendering modes: SurfaceEntity (XR spatial), GLSurfaceView (stereo), SurfaceView (2D)
 * - Auto-connects to Thor on startup
 * - Continuous voice listening for hands-free control
 *
 * NEXT STEPS FOR STEREO:
 * - Wait for AndroidXR stable release with working SurfaceEntity.StereoMode.SIDE_BY_SIDE
 * - Or implement workaround for GLSurfaceView surface lifecycle in XR
 */
class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"
    private var xrSession: Session? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            // Handle permission denial
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request microphone permission for voice commands
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Try to create XR Session
        try {
            val result: SessionCreateResult = Session.create(
                activity = this,
                lifecycleOwner = this
            )

            // Try to extract session from result using reflection
            // SessionCreateResult is a sealed class with different success types
            try {
                val sessionField = result.javaClass.getDeclaredField("session")
                sessionField.isAccessible = true
                xrSession = sessionField.get(result) as? Session
                Log.d(tag, "XR Session created successfully")
            } catch (e: NoSuchFieldException) {
                Log.w(tag, "Session creation returned unexpected result: $result, running in 2D mode")
                xrSession = null
            }
        } catch (e: SecurityException) {
            Log.w(tag, "XR permissions not granted, running in 2D mode: ${e.message}")
            xrSession = null
        } catch (e: Exception) {
            Log.w(tag, "Failed to create XR Session, running in 2D mode: ${e.message}")
            xrSession = null
        }

        setContent {
            TelosXRTheme {
                // Simple approach: Just display the content
                // Spatial mode will work on XR headsets when APIs are stable
                XRContentView()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Session lifecycle is managed by the Activity
        xrSession = null
        Log.d(tag, "XR Session released")
    }
}
