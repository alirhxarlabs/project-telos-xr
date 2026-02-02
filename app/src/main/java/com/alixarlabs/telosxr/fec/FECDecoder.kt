package com.alixarlabs.telosxr.fec

import android.util.Log
import com.alixarlabs.telosxr.network.NetworkConfig

/**
 * FEC Decoder implementing XOR-based forward error correction.
 *
 * Groups 4 media packets with 1 FEC packet (25% overhead).
 * Supports up to 4 parallel interleaved FEC groups for burst loss recovery.
 */
class FECDecoder {
    private val fecGroups = mutableMapOf<Int, FECGroup>()
    private var recoveredPackets = 0L

    data class FECGroup(
        val groupId: Int,
        val mediaPackets: MutableMap<Int, FECPacket> = mutableMapOf(),
        var fecPacket: FECPacket? = null,
        var expectedPacketCount: Int = NetworkConfig.FEC_GROUP_SIZE
    )

    /**
     * Process a received packet and attempt FEC recovery if needed.
     * Returns the list of media packets (original + recovered) ready for reassembly.
     */
    fun processPacket(packet: FECPacket): List<FECPacket> {
        val groupId = packet.fecGroupId
        val group = fecGroups.getOrPut(groupId) { FECGroup(groupId) }

        if (packet.isFec) {
            group.fecPacket = packet
            group.expectedPacketCount = packet.packetCount
        } else {
            group.mediaPackets[packet.sequenceNumber] = packet
        }

        // Try to recover missing packets
        val recoveredPackets = tryRecover(group)

        // Cleanup if group is complete
        if (group.mediaPackets.size >= group.expectedPacketCount) {
            fecGroups.remove(groupId)
        }

        // Cleanup old groups (keep max parallel groups)
        if (fecGroups.size > NetworkConfig.MAX_PARALLEL_GROUPS * 2) {
            cleanupOldGroups()
        }

        return recoveredPackets
    }

    private fun tryRecover(group: FECGroup): List<FECPacket> {
        val fecPacket = group.fecPacket ?: return emptyList()
        val mediaPackets = group.mediaPackets.values.toList()

        // Can only recover if exactly one packet is missing
        if (mediaPackets.size != group.expectedPacketCount - 1) {
            return emptyList()
        }

        // Find missing sequence number
        val receivedSeqs = mediaPackets.map { it.sequenceNumber }.toSet()
        val allSeqs = findGroupSequenceNumbers(fecPacket.sequenceNumber, group.expectedPacketCount)
        val missingSeq = allSeqs.firstOrNull { it !in receivedSeqs } ?: return emptyList()

        // XOR all received media packets with FEC packet to recover missing packet
        val recoveredPayload = xorRecover(mediaPackets.map { it.payload }, fecPacket.payload)

        // Reconstruct the missing packet
        // Note: We need to reconstruct flags from context
        val recoveredPacket = FECPacket(
            sequenceNumber = missingSeq,
            isFec = false,
            isStart = false, // Will be determined by NAL reassembler
            isEnd = false,
            nalTypeOrPacketCount = 0, // Will be determined by NAL reassembler
            fecGroupId = group.groupId,
            payload = recoveredPayload
        )

        group.mediaPackets[missingSeq] = recoveredPacket
        recoveredPackets++

        Log.d(TAG, "FEC recovered packet seq=$missingSeq in group=${group.groupId}")

        return listOf(recoveredPacket)
    }

    private fun xorRecover(mediaPayloads: List<ByteArray>, fecPayload: ByteArray): ByteArray {
        // Start with FEC payload
        val result = fecPayload.copyOf()

        // XOR with all received media packets
        for (payload in mediaPayloads) {
            val minSize = minOf(result.size, payload.size)
            for (i in 0 until minSize) {
                result[i] = (result[i].toInt() xor payload[i].toInt()).toByte()
            }
        }

        return result
    }

    private fun findGroupSequenceNumbers(fecSeq: Int, packetCount: Int): List<Int> {
        // FEC packet comes after media packets in the group
        // Media packets are: fecSeq - packetCount, fecSeq - packetCount + 1, ..., fecSeq - 1
        val seqs = mutableListOf<Int>()
        for (i in 0 until packetCount) {
            var seq = fecSeq - packetCount + i
            // Handle sequence number wraparound
            if (seq < 0) {
                seq += NetworkConfig.MAX_SEQUENCE_NUMBER
            }
            seqs.add(seq)
        }
        return seqs
    }

    private fun cleanupOldGroups() {
        // Remove oldest groups, keep only recent ones
        val sortedGroups = fecGroups.entries.sortedBy { it.value.groupId }
        val toRemove = sortedGroups.take(fecGroups.size - NetworkConfig.MAX_PARALLEL_GROUPS)
        toRemove.forEach { fecGroups.remove(it.key) }
    }

    fun getRecoveredPacketCount(): Long = recoveredPackets

    fun reset() {
        fecGroups.clear()
        recoveredPackets = 0
    }

    companion object {
        private const val TAG = "FECDecoder"
    }
}
