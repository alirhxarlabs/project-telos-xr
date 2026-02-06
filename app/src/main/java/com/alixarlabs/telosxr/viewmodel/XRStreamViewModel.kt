package com.alixarlabs.telosxr.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.alixarlabs.telosxr.model.ConnectionMode
import com.alixarlabs.telosxr.model.ConnectionState
import com.alixarlabs.telosxr.model.StreamStats
import com.alixarlabs.telosxr.network.FECReceiverService

class XRStreamViewModel : ViewModel() {
    private val tag = "XRStreamViewModel"

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats

    // Stereo mode toggle (default: true for 3D stereo)
    private val _isStereoMode = MutableStateFlow(true)
    val isStereoMode: StateFlow<Boolean> = _isStereoMode

    // Connection mode (default: WiFi)
    private val _connectionMode = MutableStateFlow(ConnectionMode.WIFI)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode

    private var receiverService: FECReceiverService? = null
    private var pendingSurface: Surface? = null
    private var isBound = false

    var onFrameUpdate: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FECReceiverService.LocalBinder
            receiverService = binder.getService()
            isBound = true

            // Setup state observers
            receiverService?.connectionState?.let { stateFlow ->
                viewModelScope.launch {
                    stateFlow.collect { state ->
                        _connectionState.value = state
                    }
                }
            }

            receiverService?.stats?.let { statsFlow ->
                viewModelScope.launch {
                    statsFlow.collect { stats ->
                        _stats.value = stats
                    }
                }
            }

            // Auto-start if surface is set (surface callbacks will retry if needed)
            pendingSurface?.let { surface ->
                Log.d(tag, "Service connected, attempting to start (surface valid=${surface.isValid})")
                startStreaming(surface)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            receiverService = null
            isBound = false
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, FECReceiverService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    fun setSurface(surface: Surface) {
        Log.d(tag, "setSurface called: surface.isValid=${surface.isValid}")
        pendingSurface = surface
    }

    fun startStreaming(surface: Surface? = null) {
        val surfaceToUse = surface ?: pendingSurface
        Log.d(tag, "startStreaming called: surface=${surfaceToUse}, isValid=${surfaceToUse?.isValid}")
        if (surfaceToUse != null) {
            // Start streaming even if surface is currently invalid
            // H264Decoder will wait for surface to become valid
            Log.d(tag, "Starting service (surface valid=${surfaceToUse.isValid}) in ${_connectionMode.value} mode")
            receiverService?.start(surfaceToUse, _connectionMode.value)
        } else {
            Log.w(tag, "Cannot start streaming: surface is null")
        }
    }

    fun stopStreaming() {
        receiverService?.stop()
    }

    fun toggleStereoMode() {
        _isStereoMode.value = !_isStereoMode.value
        Log.d(tag, "Stereo mode toggled: ${_isStereoMode.value}")
    }

    fun setConnectionMode(mode: ConnectionMode) {
        if (_connectionMode.value != mode) {
            _connectionMode.value = mode
            Log.d(tag, "Connection mode changed to: $mode")

            // If currently streaming, restart with new connection mode
            if (receiverService != null && _connectionState.value is ConnectionState.Connected) {
                Log.d(tag, "Restarting stream with new connection mode")
                stopStreaming()
                pendingSurface?.let { surface ->
                    startStreaming(surface)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        receiverService = null
    }
}
