package com.alixarlabs.telosxr.network

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends periodic heartbeat packets to Thor device.
 *
 * Thor uses the source IP from heartbeat packets to determine where to send video stream.
 */
class HeartbeatSender {
    private var job: Job? = null
    private var socket: DatagramSocket? = null

    fun start(scope: CoroutineScope) {
        stop()

        job = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket()
                val thorAddress = InetAddress.getByName(NetworkConfig.THOR_IP)

                Log.i(TAG, "Starting heartbeat to ${NetworkConfig.THOR_IP}:${NetworkConfig.HEARTBEAT_PORT}")

                while (isActive) {
                    try {
                        // Match Vision Pro format: "HB" + 4-byte big-endian timestamp (6 bytes total)
                        val timestamp = (System.currentTimeMillis() and 0xFFFFFFFF).toInt()
                        val heartbeatData = ByteArray(6)
                        heartbeatData[0] = 'H'.code.toByte()
                        heartbeatData[1] = 'B'.code.toByte()
                        heartbeatData[2] = (timestamp shr 24).toByte()  // Big-endian
                        heartbeatData[3] = (timestamp shr 16).toByte()
                        heartbeatData[4] = (timestamp shr 8).toByte()
                        heartbeatData[5] = timestamp.toByte()

                        val packet = DatagramPacket(
                            heartbeatData,
                            heartbeatData.size,
                            thorAddress,
                            NetworkConfig.HEARTBEAT_PORT
                        )
                        socket?.send(packet)
                        Log.v(TAG, "Heartbeat sent")
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error sending heartbeat", e)
                        }
                    }

                    delay(NetworkConfig.HEARTBEAT_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat sender failed", e)
            } finally {
                socket?.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket?.close()
        socket = null
        Log.i(TAG, "Heartbeat stopped")
    }

    companion object {
        private const val TAG = "HeartbeatSender"
    }
}
