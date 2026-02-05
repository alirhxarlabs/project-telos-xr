package com.alixarlabs.telosxr.ui

import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.xr.compose.platform.LocalSession
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.scenecore.SurfaceEntity
import com.alixarlabs.telosxr.model.ConnectionState
import com.alixarlabs.telosxr.viewmodel.XRStreamViewModel
import com.alixarlabs.telosxr.voice.OrbeyeCommandClient
import com.alixarlabs.telosxr.voice.VoiceCommandManager

/**
 * Main XR Content View matching Vision Pro Project Taris UI
 *
 * Layout:
 * - 800x450dp video surface at top (16:9 aspect ratio)
 * - Status bar below showing FPS, voice status, controls
 * - Auto-connects on launch
 * - Always-on voice listening
 */
@Composable
fun XRContentView(viewModel: XRStreamViewModel = viewModel()) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // Voice command setup
    val commandClient = remember { OrbeyeCommandClient(thorIp = "192.168.0.225") }
    val voiceManager = remember {
        VoiceCommandManager(context, viewModel.viewModelScope, commandClient)
    }
    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val lastTranscript by voiceManager.lastTranscript.collectAsStateWithLifecycle()
    val isVoiceAvailable by voiceManager.isAvailable.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.bindService(context)
        // Auto-start voice listening
        if (isVoiceAvailable) {
            voiceManager.startListening()
        }
        onDispose {
            viewModel.stopStreaming()
            viewModel.unbindService(context)
            voiceManager.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Video Player Area (800x450 matching Vision Pro)
        Box(
            modifier = Modifier
                .size(800.dp, 450.dp)
                .background(
                    if (connectionState is ConnectionState.Connected) Color.Black
                    else Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    2.dp,
                    if (connectionState is ConnectionState.Connected) Color.Green else Color.Gray,
                    RoundedCornerShape(12.dp)
                )
        ) {
            // ========== STEREO VIDEO RENDERING ==========
            // FORCED STEREO MODE: Always use SurfaceEntity with SIDE_BY_SIDE
            // Removed spatial UI check - directly create SurfaceEntity if XR session exists
            //
            // This bypasses the isSpatialUiEnabled check which returns false on Galaxy XR alpha10
            android.util.Log.d("XRContentView", "Rendering video in FORCED STEREO mode")

            // Get XR session
            val xrSession = LocalSession.current
            android.util.Log.d("XRContentView", "XR Session: ${xrSession != null}")

            var surfaceEntity by remember { mutableStateOf<SurfaceEntity?>(null) }

            // Always use SurfaceEntity with SIDE_BY_SIDE stereo mode
            if (xrSession != null) {
                LaunchedEffect(Unit) {
                    surfaceEntity = null

                    try {
                        android.util.Log.d("XRContentView", "Creating SurfaceEntity with SIDE_BY_SIDE stereo mode")

                        val newEntity = SurfaceEntity.create(
                            session = xrSession,
                            stereoMode = SurfaceEntity.StereoMode.SIDE_BY_SIDE,
                            pose = Pose(Vector3(0.0f, 0.0f, -2.0f)),
                            shape = SurfaceEntity.Shape.Quad(FloatSize2d(1.6f, 0.9f))
                        )

                        android.util.Log.d("XRContentView", "SurfaceEntity created, getting Surface...")
                        surfaceEntity = newEntity

                        val surface = newEntity.getSurface()
                        android.util.Log.d("XRContentView", "Got surface: ${if (surface != null) "isValid=${surface.isValid}" else "null"}")

                        if (surface != null && surface.isValid) {
                            android.util.Log.d("XRContentView", "Surface ready! Passing to decoder...")
                            viewModel.setSurface(surface)
                            viewModel.startStreaming()
                        } else {
                            android.util.Log.e("XRContentView", "Surface is invalid/null")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("XRContentView", "Failed to create SurfaceEntity", e)
                    }
                }
            } else {
                // Fallback for non-XR devices - use GLSurfaceView with stereo renderer
                android.util.Log.d("XRContentView", "No XR session - using GLSurfaceView stereo fallback")

                var stereoRenderer by remember { mutableStateOf<com.alixarlabs.telosxr.rendering.StereoGLRenderer?>(null) }

                DisposableEffect(Unit) {
                    onDispose {
                        stereoRenderer?.release()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        android.util.Log.d("XRContentView", "Creating GLSurfaceView for stereo rendering")
                        android.opengl.GLSurfaceView(ctx).apply {
                            setEGLContextClientVersion(2)
                            preserveEGLContextOnPause = true
                            val renderer = com.alixarlabs.telosxr.rendering.StereoGLRenderer { surface ->
                                android.util.Log.d("XRContentView", "Surface ready from StereoGLRenderer")
                                viewModel.setSurface(surface)
                                viewModel.startStreaming()
                            }
                            stereoRenderer = renderer
                            setRenderer(renderer)
                            renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            android.util.Log.d("XRContentView", "GLSurfaceView configured with RENDERMODE_CONTINUOUSLY")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        android.util.Log.v("XRContentView", "GLSurfaceView update - calling onResume()")
                        view.onResume()
                        view.requestRender()
                    }
                )
            }

            // Show connection overlay on top when not connected
            if (connectionState !is ConnectionState.Connected) {
                ConnectionOverlay(
                    connectionState = connectionState,
                    onConnect = { /* Auto-connects */ }
                )
            }
        }

        // Status bar (matches Vision Pro layout) - stereo toggle removed
        if (connectionState is ConnectionState.Connected) {
            StatusOverlay(
                fps = stats.currentFps,
                isVoiceListening = isListening,
                lastCommand = lastTranscript
            )
        }
    }
}
