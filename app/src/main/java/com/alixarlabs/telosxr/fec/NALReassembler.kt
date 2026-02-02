package com.alixarlabs.telosxr.fec

import android.util.Log
import com.alixarlabs.telosxr.network.NetworkConfig
import java.io.ByteArrayOutputStream

/**
 * Reassembles fragmented NAL units from FEC packets.
 *
 * Handles:
 * - Fragmentation (is_start, is_end flags)
 * - Sequence number reordering
 * - NAL type detection (SPS=7, PPS=8, IDR=5, P-frame=1)
 * - Interleaved packets (separate buffer per NAL type)
 */
class NALReassembler {
    // Per-NAL-type fragment buffers to support interleaved packets
    private val fragments: MutableMap<Int, ByteArrayOutputStream> = mutableMapOf()
    private var lastSequenceNumber: Int = -1
    private var nalUnitsReassembled = 0L

    // Deduplication: Track recent sequence numbers (Thor sends SPS/PPS/IDR 3x)
    private val recentSequences: MutableSet<Int> = mutableSetOf()
    private var packetsDeduped = 0L

    /**
     * Process a media packet and return complete NAL units if ready.
     */
    fun processPacket(packet: FECPacket): List<NALUnit> {
        if (packet.isFec) {
            return emptyList() // FEC packets don't contain NAL data
        }

        val results = mutableListOf<NALUnit>()

        // Deduplication: Skip if we've seen this EXACT sequence very recently (within last 50 packets)
        // This handles Thor's 3x redundancy for SPS/PPS/IDR START packets
        // But allows sequence number reuse across different frames
        if (recentSequences.contains(packet.sequenceNumber)) {
            packetsDeduped++
            Log.v(TAG, "Skipping duplicate packet seq=${packet.sequenceNumber}")
            return emptyList()
        }

        // Track this sequence (keep only last 50 for deduplication window)
        recentSequences.add(packet.sequenceNumber)
        if (recentSequences.size > 50) {
            // Remove oldest half to keep window small
            val toRemove = recentSequences.take(25)
            recentSequences.removeAll(toRemove.toSet())
        }

        // Check for sequence discontinuity
        if (lastSequenceNumber >= 0) {
            val expectedSeq = (lastSequenceNumber + 1) % NetworkConfig.MAX_SEQUENCE_NUMBER
            if (packet.sequenceNumber != expectedSeq) {
                Log.v(TAG, "Sequence discontinuity: expected=$expectedSeq, got=${packet.sequenceNumber}")
                // DON'T clear fragment buffers - let them continue assembling
                // FEC recovery or packet reordering may still deliver missing packets
            }
        }
        lastSequenceNumber = packet.sequenceNumber

        when {
            packet.isStart && packet.isEnd -> {
                // Complete NAL unit in single packet
                results.add(createNALUnit(packet.nalType, packet.payload))
                nalUnitsReassembled++
            }

            packet.isStart -> {
                // Start of fragmented NAL unit - create new buffer for this NAL type
                val buffer = ByteArrayOutputStream(NetworkConfig.MAX_NAL_BUFFER_SIZE)
                buffer.write(packet.payload)
                fragments[packet.nalType] = buffer
                Log.v(TAG, "Started fragment for NAL type ${packet.nalType}, seq=${packet.sequenceNumber}")
            }

            packet.isEnd -> {
                // End of fragmented NAL unit - complete this NAL type
                val buffer = fragments[packet.nalType]
                if (buffer != null) {
                    buffer.write(packet.payload)
                    results.add(createNALUnit(packet.nalType, buffer.toByteArray()))
                    nalUnitsReassembled++
                    fragments.remove(packet.nalType)
                    Log.v(TAG, "Completed fragment for NAL type ${packet.nalType}, seq=${packet.sequenceNumber}")
                } else {
                    Log.w(TAG, "Received end packet for NAL type ${packet.nalType} without start")
                }
            }

            else -> {
                // Middle fragment - append to buffer for this NAL type
                val buffer = fragments[packet.nalType]
                if (buffer != null) {
                    buffer.write(packet.payload)
                } else {
                    Log.w(TAG, "Received middle packet for NAL type ${packet.nalType} without start")
                }
            }
        }

        return results
    }

    private fun createNALUnit(nalType: Int, data: ByteArray): NALUnit {
        // Also check the NAL type from the payload itself (first byte & 0x1F)
        val payloadNalType = if (data.isNotEmpty()) data[0].toInt() and 0x1F else 0
        Log.d(TAG, "NAL unit complete: headerType=$nalType (${getNALTypeName(nalType)}), payloadType=$payloadNalType (${getNALTypeName(payloadNalType)}), size=${data.size}")

        // Use the payload NAL type if different
        val actualType = if (payloadNalType != 0 && payloadNalType != nalType) {
            Log.w(TAG, "NAL type mismatch! Using payload type=$payloadNalType instead of header type=$nalType")
            payloadNalType
        } else {
            nalType
        }

        return NALUnit(actualType, data)
    }

    private fun getNALTypeName(nalType: Int): String {
        return when (nalType) {
            1 -> "P-frame"
            5 -> "IDR"
            7 -> "SPS"
            8 -> "PPS"
            else -> "Unknown"
        }
    }

    fun getNALUnitsReassembled(): Long = nalUnitsReassembled
    fun getPacketsDeduped(): Long = packetsDeduped

    fun reset() {
        fragments.clear()
        recentSequences.clear()
        lastSequenceNumber = -1
        nalUnitsReassembled = 0
        packetsDeduped = 0
    }

    companion object {
        private const val TAG = "NALReassembler"
    }
}

/**
 * Represents a complete NAL unit ready for decoding.
 */
data class NALUnit(
    val nalType: Int,
    val data: ByteArray
) {
    val isSPS: Boolean get() = nalType == 7
    val isPPS: Boolean get() = nalType == 8
    val isIDR: Boolean get() = nalType == 5
    val isPFrame: Boolean get() = nalType == 1

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NALUnit

        if (nalType != other.nalType) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nalType
        result = 31 * result + data.contentHashCode()
        return result
    }
}
