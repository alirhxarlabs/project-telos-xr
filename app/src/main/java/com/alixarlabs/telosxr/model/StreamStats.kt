package com.alixarlabs.telosxr.model

data class StreamStats(
    val packetsReceived: Long = 0,
    val packetsLost: Long = 0,
    val fecRecovered: Long = 0,
    val framesDecoded: Long = 0,
    val currentFps: Float = 0f,
    val latencyMs: Long = 0
) {
    val packetLossRate: Float
        get() = if (packetsReceived > 0) {
            (packetsLost.toFloat() / (packetsReceived + packetsLost)) * 100f
        } else 0f

    val fecRecoveryRate: Float
        get() = if (packetsLost > 0) {
            (fecRecovered.toFloat() / packetsLost) * 100f
        } else 0f
}
