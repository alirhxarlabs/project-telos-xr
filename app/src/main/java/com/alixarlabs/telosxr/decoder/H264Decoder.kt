package com.alixarlabs.telosxr.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.alixarlabs.telosxr.fec.NALUnit
import java.nio.ByteBuffer

/**
 * H.264 video decoder using Android MediaCodec.
 *
 * Configures codec with SPS/PPS and decodes NAL units to a Surface.
 */
class H264Decoder(private var surface: Surface) {
    private var mediaCodec: MediaCodec? = null
    private var isConfigured = false
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var waitingForIDR = true
    private var framesDecoded = 0L

    fun updateSurface(newSurface: Surface) {
        if (newSurface != surface) {
            Log.i(TAG, "Updating surface: old.isValid=${surface.isValid}, new.isValid=${newSurface.isValid}")
            surface = newSurface
            // Reset configuration to force reconfiguration with new surface
            if (!newSurface.isValid) {
                Log.w(TAG, "New surface is invalid, will retry when valid")
            } else {
                Log.i(TAG, "New surface is valid, will reconfigure if needed")
                if (sps != null && pps != null && !isConfigured) {
                    tryConfigureCodec()
                }
            }
        }
    }

    fun processNALUnit(nalUnit: NALUnit) {
        when {
            nalUnit.isSPS -> {
                Log.d(TAG, "Received SPS, size=${nalUnit.data.size}")
                sps = nalUnit.data
                tryConfigureCodec()
            }

            nalUnit.isPPS -> {
                Log.d(TAG, "Received PPS, size=${nalUnit.data.size}")
                pps = nalUnit.data
                tryConfigureCodec()
            }

            nalUnit.isIDR || nalUnit.isPFrame -> {
                if (!isConfigured) {
                    Log.w(TAG, "Received frame before codec configured, dropping")
                    return
                }

                if (nalUnit.isIDR) {
                    Log.d(TAG, "Received IDR frame, size=${nalUnit.data.size}")
                }

                decodeFrame(nalUnit)
            }

            else -> {
                Log.d(TAG, "Ignoring NAL type ${nalUnit.nalType}")
            }
        }
    }

    private fun tryConfigureCodec() {
        Log.d(TAG, "tryConfigureCodec called: sps=${sps != null}, pps=${pps != null}, isConfigured=$isConfigured")
        val spsData = sps ?: run {
            Log.d(TAG, "SPS not available yet")
            return
        }
        val ppsData = pps ?: run {
            Log.d(TAG, "PPS not available yet")
            return
        }

        if (isConfigured) {
            Log.d(TAG, "Already configured, skipping")
            return // Already configured
        }

        // Check if surface is valid before attempting configuration
        if (surface == null || !surface!!.isValid) {
            Log.w(TAG, "Surface is null or invalid, will retry when surface is ready")
            return
        }

        try {
            Log.i(TAG, "Configuring MediaCodec with SPS/PPS (surface is valid)")

            // Create codec
            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

            // Create format
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                1920,  // Width from plan
                1080   // Height from plan
            )

            // Add SPS/PPS as CSD (Codec Specific Data)
            // Format: [0x00 0x00 0x00 0x01][SPS][0x00 0x00 0x00 0x01][PPS]
            val csd0 = ByteBuffer.allocate(spsData.size + ppsData.size + 8)
            csd0.put(byteArrayOf(0x00, 0x00, 0x00, 0x01))
            csd0.put(spsData)
            csd0.put(byteArrayOf(0x00, 0x00, 0x00, 0x01))
            csd0.put(ppsData)
            csd0.flip()

            format.setByteBuffer("csd-0", csd0)

            // Configure codec
            codec.configure(format, surface, null, 0)
            codec.start()

            mediaCodec = codec
            isConfigured = true
            waitingForIDR = true

            Log.i(TAG, "MediaCodec configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure MediaCodec", e)
            mediaCodec?.release()
            mediaCodec = null
            isConfigured = false
        }
    }

    private fun decodeFrame(nalUnit: NALUnit) {
        val codec = mediaCodec ?: return

        try {
            // Get input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()

                    // Write NAL unit with start code
                    inputBuffer.put(START_CODE)
                    inputBuffer.put(nalUnit.data)

                    val size = START_CODE.size + nalUnit.data.size
                    val presentationTimeUs = System.nanoTime() / 1000

                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        size,
                        presentationTimeUs,
                        0
                    )

                    framesDecoded++
                }
            }

            // Release output buffers - use small timeout to ensure we catch decoded frames
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000) // 1ms timeout
            var renderedCount = 0
            while (outputBufferIndex >= 0) {
                // Render to surface
                codec.releaseOutputBuffer(outputBufferIndex, true)
                renderedCount++
                // Continue draining with 0 timeout after first frame
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (renderedCount > 0) {
                Log.v(TAG, "Rendered $renderedCount output buffers")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding frame", e)
        }
    }

    fun getFramesDecoded(): Long = framesDecoded

    fun release() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing codec", e)
        }
        mediaCodec = null
        isConfigured = false
        sps = null
        pps = null
        waitingForIDR = true
        framesDecoded = 0
    }

    companion object {
        private const val TAG = "H264Decoder"
        private const val TIMEOUT_US = 10000L

        // H.264 Annex B start code
        private val START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    }
}
