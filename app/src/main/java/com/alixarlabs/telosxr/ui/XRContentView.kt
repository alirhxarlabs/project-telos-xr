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
    val isStereoMode by viewModel.isStereoMode.collectAsStateWithLifecycle()

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
            // ========== VIDEO RENDERING - THREE MODES ==========
            // STATUS: Video streaming works, voice works, stereo rendering NOT YET WORKING
            //
            // STEREO ISSUE: AndroidXR alpha10 spatial APIs are incomplete/unstable
            // - SurfaceEntity.create() with StereoMode.SIDE_BY_SIDE is the correct API
            // - But requires spatial UI enabled (isSpatialUiEnabled = true)
            // - Galaxy XR currently returns isSpatialUiEnabled = false even in XR mode
            // - GLSurfaceView stereo renderer works but surface gets destroyed by XR lifecycle
            //
            // THREE RENDERING MODES:
            // 1. XR Headset + Spatial UI: Use SurfaceEntity with native stereo (PREFERRED, NOT WORKING YET)
            // 2. Non-XR + Stereo: Use OpenGL renderer with custom shader (PARTIAL - surface lifecycle issues)
            // 3. Non-XR + 2D: Use regular SurfaceView (WORKS)
            //
            // TODO FOR STEREO:
            // - Wait for AndroidXR stable release with working spatial APIs
            // - Or find workaround to enable spatial UI on Galaxy XR
            // - Or fix GLSurfaceView surface lifecycle in XR environment
            android.util.Log.d("XRContentView", "Rendering video view: isStereoMode=$isStereoMode")

            // Check if we're in spatial environment
            val xrSession = LocalSession.current
            val spatialCapabilities = androidx.xr.compose.platform.LocalSpatialCapabilities.current
            val isSpatialEnabled = spatialCapabilities?.isSpatialUiEnabled == true

            android.util.Log.d("XRContentView", "XR Session: ${xrSession != null}, Spatial UI enabled: $isSpatialEnabled")

            var surfaceEntity by remember { mutableStateOf<SurfaceEntity?>(null) }

            // MODE 1: XR Headset - Use SurfaceEntity with native stereo
            if (xrSession != null && isSpatialEnabled) {
                LaunchedEffect(isStereoMode) {
                    surfaceEntity = null

                    try {
                        android.util.Log.d("XRContentView", "Creating SurfaceEntity with stereoMode=${if (isStereoMode) "SIDE_BY_SIDE" else "MONO"}")

                        val newEntity = SurfaceEntity.create(
                            session = xrSession,
                            stereoMode = if (isStereoMode) {
                                SurfaceEntity.StereoMode.SIDE_BY_SIDE
                            } else {
                                SurfaceEntity.StereoMode.MONO
                            },
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
            }
            // MODE 2: Non-XR + Stereo Mode - Use custom OpenGL renderer
            else if (isStereoMode) {
                android.util.Log.d("XRContentView", "Using GLSurfaceView for stereo mode (non-XR)")

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
            // MODE 3: Non-XR + 2D Mode - Use regular SurfaceView
            else {
                android.util.Log.d("XRContentView", "Using SurfaceView for 2D mode")
                AndroidView(
                    factory = { ctx ->
                        android.view.SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    android.util.Log.d("XRContentView", "surfaceCreated: isValid=${holder.surface.isValid}")
                                    viewModel.setSurface(holder.surface)
                                    viewModel.startStreaming()
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                    android.util.Log.d("XRContentView", "surfaceChanged: ${width}x${height}, isValid=${holder.surface.isValid}")
                                    viewModel.startStreaming()
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    android.util.Log.d("XRContentView", "surfaceDestroyed")
                                    viewModel.stopStreaming()
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
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

        // Status bar (matches Vision Pro layout)
        if (connectionState is ConnectionState.Connected) {
            StatusOverlay(
                fps = stats.currentFps,
                isVoiceListening = isListening,
                lastCommand = lastTranscript,
                isStereoMode = isStereoMode,
                onToggleStereo = {
                    android.util.Log.d("XRContentView", "Toggling stereo mode from $isStereoMode")
                    viewModel.toggleStereoMode()
                }
            )
        }
    }
}
