package com.alixarlabs.telosxr.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.alixarlabs.telosxr.model.ConnectionMode
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
    val connectionMode by viewModel.connectionMode.collectAsStateWithLifecycle()

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
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Connection Mode Selector (WiFi / USB)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = connectionMode == ConnectionMode.WIFI,
                onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI) },
                label = { Text("WiFi") },
                leadingIcon = {
                    if (connectionMode == ConnectionMode.WIFI) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilterChip(
                selected = connectionMode == ConnectionMode.USB_TETHERED,
                onClick = { viewModel.setConnectionMode(ConnectionMode.USB_TETHERED) },
                label = { Text("USB") },
                leadingIcon = {
                    if (connectionMode == ConnectionMode.USB_TETHERED) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }

        // Video Player Area - fullscreen
        Box(
            modifier = Modifier.weight(1f)
        ) {
            // ========== VIDEO RENDERING (2D/3D SWITCHABLE) ==========
            // 3D Mode: GL_OVR_multiview2 extension for stereo splitting
            // 2D Mode: Simple renderer showing full SBS frame
            val rendererMode = if (isStereoMode) "3D Stereo" else "2D Mono"
            android.util.Log.d("XRContentView", "Renderer mode: $rendererMode")

            var currentRenderer by remember { mutableStateOf<Any?>(null) }

            DisposableEffect(isStereoMode) {
                onDispose {
                    android.util.Log.d("XRContentView", "Disposing renderer, stopping stream")
                    // Stop streaming before releasing renderer
                    viewModel.stopStreaming()
                    when (val renderer = currentRenderer) {
                        is com.alixarlabs.telosxr.rendering.StereoGLRenderer -> renderer.release()
                        is com.alixarlabs.telosxr.rendering.MonoGLRenderer -> renderer.release()
                    }
                }
            }

            // Use key to force recreation when stereo mode changes
            key(isStereoMode) {
                AndroidView(
                    factory = { ctx ->
                        android.util.Log.d("XRContentView", "Creating GLSurfaceView ($rendererMode)")
                        android.opengl.GLSurfaceView(ctx).apply {
                            setEGLContextClientVersion(3)
                            preserveEGLContextOnPause = true

                            val renderer = if (isStereoMode) {
                                com.alixarlabs.telosxr.rendering.StereoGLRenderer { surface ->
                                    android.util.Log.d("XRContentView", "Stereo surface ready")
                                    viewModel.setSurface(surface)
                                    viewModel.startStreaming()
                                }
                            } else {
                                com.alixarlabs.telosxr.rendering.MonoGLRenderer { surface ->
                                    android.util.Log.d("XRContentView", "Mono surface ready")
                                    viewModel.setSurface(surface)
                                    viewModel.startStreaming()
                                }
                            }

                            currentRenderer = renderer
                            setRenderer(renderer as GLSurfaceView.Renderer)
                            renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            android.util.Log.d("XRContentView", "GLSurfaceView configured ($rendererMode)")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        android.util.Log.v("XRContentView", "GLSurfaceView update ($rendererMode)")
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

        // Status bar (matches Vision Pro layout)
        if (connectionState is ConnectionState.Connected) {
            StatusOverlay(
                fps = stats.currentFps,
                isVoiceListening = isListening,
                lastCommand = lastTranscript,
                isStereoMode = isStereoMode,
                onToggleStereo = { viewModel.toggleStereoMode() }
            )
        }
    }
}
