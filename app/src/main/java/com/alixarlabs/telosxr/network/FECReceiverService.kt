package com.alixarlabs.telosxr.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Surface
import com.alixarlabs.telosxr.decoder.H264Decoder
import com.alixarlabs.telosxr.fec.FECDecoder
import com.alixarlabs.telosxr.fec.FECPacket
import com.alixarlabs.telosxr.fec.NALReassembler
import com.alixarlabs.telosxr.model.ConnectionState
import com.alixarlabs.telosxr.model.StreamStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

/**
 * Background service for receiving FEC UDP video stream from Thor device.
 *
 * Pipeline: UDP → FEC Decoder → NAL Reassembler → H.264 Decoder → Surface
 */
class FECReceiverService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var socket: DatagramSocket? = null
    private var receiverJob: Job? = null
    private val heartbeatSender = HeartbeatSender()

    private val fecDecoder = FECDecoder()
    private val nalReassembler = NALReassembler()
    private var h264Decoder: H264Decoder? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats

    private var packetsReceived = 0L
    private var lastStatsUpdate = System.currentTimeMillis()
    private var framesAtLastUpdate = 0L

    inner class LocalBinder : Binder() {
        fun getService(): FECReceiverService = this@FECReceiverService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.i(TAG, "FECReceiverService created")
    }

    override fun onDestroy() {
        stop()
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "FECReceiverService destroyed")
    }

    fun start(surface: Surface) {
        if (receiverJob?.isActive == true) {
            Log.w(TAG, "Receiver already running, updating surface instead")
            h264Decoder?.updateSurface(surface)
            return
        }

        Log.i(TAG, "Starting FEC receiver")
        _connectionState.value = ConnectionState.Connecting

        // Release any existing decoder first
        h264Decoder?.release()

        // Initialize new decoder
        h264Decoder = H264Decoder(surface)

        // Start heartbeat
        heartbeatSender.start(serviceScope)

        // Start receiver
        receiverJob = serviceScope.launch(Dispatchers.IO) {
            receiveLoop()
        }
    }

    fun updateSurface(surface: Surface) {
        Log.i(TAG, "Updating decoder surface")
        h264Decoder?.updateSurface(surface)
    }

    fun stop() {
        Log.i(TAG, "Stopping FEC receiver")

        receiverJob?.cancel()
        receiverJob = null

        heartbeatSender.stop()

        socket?.close()
        socket = null

        h264Decoder?.release()
        h264Decoder = null

        fecDecoder.reset()
        nalReassembler.reset()

        packetsReceived = 0
        lastStatsUpdate = System.currentTimeMillis()
        framesAtLastUpdate = 0

        _connectionState.value = ConnectionState.Disconnected
        _stats.value = StreamStats()
    }

    private suspend fun receiveLoop() {
        try {
            // Create socket
            socket = DatagramSocket(NetworkConfig.DATA_PORT).apply {
                receiveBufferSize = NetworkConfig.RECEIVE_BUFFER_SIZE
                soTimeout = 1000 // 1 second timeout for checking cancellation
            }

            Log.i(TAG, "Listening on port ${NetworkConfig.DATA_PORT}")

            val buffer = ByteArray(NetworkConfig.MTU_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)

            while (coroutineContext.isActive) {
                try {
                    socket?.receive(packet)

                    // Parse packet
                    val data = packet.data.copyOfRange(0, packet.length)
                    val fecPacket = FECPacket.parse(data)

                    if (fecPacket != null) {
                        processPacket(fecPacket)
                    } else {
                        Log.w(TAG, "Failed to parse packet")
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout is expected, just continue
                    continue
                } catch (e: Exception) {
                    if (coroutineContext.isActive) {
                        Log.e(TAG, "Error receiving packet", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receiver loop failed", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
        }
    }

    private fun processPacket(packet: FECPacket) {
        packetsReceived++

        // Update connection state
        if (_connectionState.value != ConnectionState.Connected) {
            _connectionState.value = ConnectionState.Connected
            Log.i(TAG, "Connection established")
        }

        // FEC decode
        val recoveredPackets = fecDecoder.processPacket(packet)

        // Process original packet
        if (!packet.isFec) {
            processMediaPacket(packet)
        }

        // Process recovered packets
        recoveredPackets.forEach { processMediaPacket(it) }

        // Update stats periodically
        val now = System.currentTimeMillis()
        if (now - lastStatsUpdate >= 1000) {
            updateStats()
            lastStatsUpdate = now
        }
    }

    private fun processMediaPacket(packet: FECPacket) {
        // Reassemble NAL units
        val nalUnits = nalReassembler.processPacket(packet)

        // Decode NAL units
        val decoder = h264Decoder
        if (decoder != null) {
            nalUnits.forEach { nalUnit ->
                decoder.processNALUnit(nalUnit)
            }
        } else {
            if (nalUnits.isNotEmpty()) {
                Log.w(TAG, "Decoder null, dropping ${nalUnits.size} NAL units")
            }
        }
    }

    private fun updateStats() {
        val decoder = h264Decoder ?: return
        val currentFrames = decoder.getFramesDecoded()
        val framesSinceLastUpdate = currentFrames - framesAtLastUpdate
        val fps = framesSinceLastUpdate.toFloat() // Already approximately 1 second

        _stats.value = StreamStats(
            packetsReceived = packetsReceived,
            packetsLost = 0, // TODO: Track from sequence numbers
            fecRecovered = fecDecoder.getRecoveredPacketCount(),
            framesDecoded = currentFrames,
            currentFps = fps,
            latencyMs = 0 // TODO: Calculate from timestamps
        )

        framesAtLastUpdate = currentFrames
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Video Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "FEC video streaming from Thor device"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("HoloScan Receiver")
            .setContentText("Receiving video stream")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    companion object {
        private const val TAG = "FECReceiverService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "video_streaming"
    }
}
