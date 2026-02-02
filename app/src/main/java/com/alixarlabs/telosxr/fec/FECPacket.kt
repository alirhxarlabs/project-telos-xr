package com.alixarlabs.telosxr.fec

import java.nio.ByteBuffer

/**
 * Represents a parsed FEC UDP packet.
 *
 * Packet format: [2-byte seq#][1-byte flags][1-byte fec_group_id][payload]
 *
 * Flags byte:
 * - Bit 7: is_fec (1=FEC packet, 0=media packet)
 * - Bit 6: is_start (start of NAL unit)
 * - Bit 5: is_end (end of NAL unit)
 * - Bits 4-0: nal_type (for media) OR packet_count (for FEC)
 */
data class FECPacket(
    val sequenceNumber: Int,
    val isFec: Boolean,
    val isStart: Boolean,
    val isEnd: Boolean,
    val nalTypeOrPacketCount: Int,
    val fecGroupId: Int,
    val payload: ByteArray
) {
    companion object {
        private const val HEADER_SIZE = 4

        private const val FLAG_IS_FEC = 0x80
        private const val FLAG_IS_START = 0x40
        private const val FLAG_IS_END = 0x20
        private const val MASK_NAL_TYPE = 0x1F

        fun parse(data: ByteArray): FECPacket? {
            if (data.size < HEADER_SIZE) {
                return null
            }

            val buffer = ByteBuffer.wrap(data)

            // Parse 2-byte sequence number (big-endian)
            val sequenceNumber = buffer.short.toInt() and 0xFFFF

            // Parse flags byte
            val flags = buffer.get().toInt() and 0xFF
            val isFec = (flags and FLAG_IS_FEC) != 0
            val isStart = (flags and FLAG_IS_START) != 0
            val isEnd = (flags and FLAG_IS_END) != 0
            val nalTypeOrPacketCount = flags and MASK_NAL_TYPE

            // Parse FEC group ID
            val fecGroupId = buffer.get().toInt() and 0xFF

            // Extract payload
            val payloadSize = data.size - HEADER_SIZE
            val payload = ByteArray(payloadSize)
            buffer.get(payload)

            return FECPacket(
                sequenceNumber = sequenceNumber,
                isFec = isFec,
                isStart = isStart,
                isEnd = isEnd,
                nalTypeOrPacketCount = nalTypeOrPacketCount,
                fecGroupId = fecGroupId,
                payload = payload
            )
        }
    }

    val nalType: Int
        get() = if (!isFec) nalTypeOrPacketCount else 0

    val packetCount: Int
        get() = if (isFec) nalTypeOrPacketCount else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FECPacket

        if (sequenceNumber != other.sequenceNumber) return false
        if (isFec != other.isFec) return false
        if (isStart != other.isStart) return false
        if (isEnd != other.isEnd) return false
        if (nalTypeOrPacketCount != other.nalTypeOrPacketCount) return false
        if (fecGroupId != other.fecGroupId) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + isFec.hashCode()
        result = 31 * result + isStart.hashCode()
        result = 31 * result + isEnd.hashCode()
        result = 31 * result + nalTypeOrPacketCount
        result = 31 * result + fecGroupId
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
