package com.alixarlabs.telosxr.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends voice commands to Thor device via UDP port 9000.
 * Commands are sent as JSON formatted messages.
 */
class OrbeyeCommandClient(
    private val thorIp: String = "192.168.50.1",
    private val commandPort: Int = 9000
) {
    suspend fun sendDirection(direction: String) {
        val command = JSONObject().apply {
            put("type", "direction")
            put("direction", direction)
        }
        sendCommand(command)
    }

    suspend fun sendZoom(zoom: String) {
        val command = JSONObject().apply {
            put("type", "zoom_focus")
            put("zoom", zoom)
        }
        sendCommand(command)
    }

    suspend fun sendFocus(focus: String) {
        val command = JSONObject().apply {
            put("type", "zoom_focus")
            put("focus", focus)
        }
        sendCommand(command)
    }

    suspend fun sendStop() {
        val command = JSONObject().apply {
            put("type", "stop")
        }
        sendCommand(command)
    }

    suspend fun setMode(mode: String) {
        val command = JSONObject().apply {
            put("type", "mode_change")
            put("mode", mode)
        }
        sendCommand(command)
    }

    private suspend fun sendCommand(command: JSONObject) {
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val data = command.toString().toByteArray()
                val address = InetAddress.getByName(thorIp)
                val packet = DatagramPacket(data, data.size, address, commandPort)

                socket.send(packet)
                socket.close()

                Log.d(TAG, "Sent command: $command")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
            }
        }
    }

    companion object {
        private const val TAG = "OrbeyeCommandClient"
    }
}
