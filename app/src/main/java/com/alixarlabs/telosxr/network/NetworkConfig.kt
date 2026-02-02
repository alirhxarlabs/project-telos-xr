package com.alixarlabs.telosxr.network

object NetworkConfig {
    // Thor device IP address
    const val THOR_IP = "192.168.0.225"

    // Port configuration
    const val DATA_PORT = 5700      // UDP port for receiving FEC video data
    const val HEARTBEAT_PORT = 5701 // UDP port for sending heartbeats to Thor

    // Network parameters
    const val MTU_SIZE = 1400                    // Maximum packet payload size
    const val RECEIVE_BUFFER_SIZE = 16 * 1024 * 1024  // 16MB UDP receive buffer

    // Heartbeat configuration (matches Vision Pro)
    const val HEARTBEAT_INTERVAL_MS = 200L  // Send heartbeat every 200ms (Vision Pro frequency)
    const val CONNECTION_TIMEOUT_MS = 3000L  // Consider disconnected after 3s without response

    // FEC configuration
    const val FEC_GROUP_SIZE = 4        // 4 media packets per FEC group
    const val MAX_PARALLEL_GROUPS = 4   // Support 4 interleaved FEC groups

    // NAL unit reassembly
    const val MAX_NAL_BUFFER_SIZE = 200 * 1024  // 200KB for large IDR frames

    // Sequence number handling
    const val MAX_SEQUENCE_NUMBER = 65536  // 16-bit sequence number wraps here
}
