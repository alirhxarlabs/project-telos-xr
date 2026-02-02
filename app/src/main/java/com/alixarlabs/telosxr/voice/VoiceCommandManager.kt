package com.alixarlabs.telosxr.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Manages voice command recognition and processing.
 * Matches Vision Pro voice command patterns.
 *
 * STATUS: WORKING after device reboot (cleared system service connection leak)
 *
 * IMPLEMENTATION NOTES:
 * - Uses standard SpeechRecognizer (not on-device variant)
 * - On-device recognizer (createOnDeviceSpeechRecognizer) has binding issues on Galaxy XR alpha
 * - Continuous listening with auto-restart on results/errors
 * - 100ms delay before restart to allow proper cleanup (prevents service connection leaks)
 * - Requires RECORD_AUDIO permission granted at runtime
 *
 * KNOWN ISSUE RESOLVED:
 * - System service connection exhaustion required device reboot to clear
 * - After reboot, voice recognition works reliably
 */
class VoiceCommandManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val commandClient: OrbeyeCommandClient
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent
    private val handler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    init {
        _isAvailable.value = SpeechRecognizer.isRecognitionAvailable(context)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    fun startListening() {
        if (!_isAvailable.value) {
            Log.w(TAG, "Speech recognition not available")
            return
        }

        stopListening()

        // Use standard SpeechRecognizer (on-device API has binding issues on Galaxy XR alpha)
        Log.i(TAG, "Using standard speech recognizer")
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    Log.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    Log.w(TAG, "Recognition error: $error")
                    // Restart listening after error with delay to allow cleanup
                    if (error != SpeechRecognizer.ERROR_CLIENT) {
                        handler.postDelayed({ startListening() }, 100)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { transcript ->
                        _lastTranscript.value = transcript
                        processCommand(transcript)
                    }
                    // Restart listening for continuous recognition with delay to allow cleanup
                    handler.postDelayed({ startListening() }, 100)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { transcript ->
                        _lastTranscript.value = transcript
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechRecognizer?.startListening(recognizerIntent)
        Log.i(TAG, "Started listening")
    }

    fun stopListening() {
        handler.removeCallbacksAndMessages(null) // Cancel any pending restarts
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
        _lastTranscript.value = ""
        Log.i(TAG, "Stopped listening")
    }

    private fun processCommand(transcript: String) {
        val lowerTranscript = transcript.lowercase()
        Log.d(TAG, "Processing: $lowerTranscript")

        scope.launch {
            when {
                // Movement commands
                lowerTranscript.contains("move up") || lowerTranscript.contains("pan up") -> {
                    commandClient.sendDirection("up")
                }
                lowerTranscript.contains("move down") || lowerTranscript.contains("pan down") -> {
                    commandClient.sendDirection("down")
                }
                lowerTranscript.contains("move left") || lowerTranscript.contains("pan left") -> {
                    commandClient.sendDirection("left")
                }
                lowerTranscript.contains("move right") || lowerTranscript.contains("pan right") -> {
                    commandClient.sendDirection("right")
                }

                // Zoom commands
                lowerTranscript.contains("zoom in") -> {
                    commandClient.sendZoom("in")
                }
                lowerTranscript.contains("zoom out") -> {
                    commandClient.sendZoom("out")
                }

                // Focus commands
                lowerTranscript.contains("focus in") || lowerTranscript.contains("focus near") -> {
                    commandClient.sendFocus("in")
                }
                lowerTranscript.contains("focus out") || lowerTranscript.contains("focus far") -> {
                    commandClient.sendFocus("out")
                }

                // Stop
                lowerTranscript.contains("stop") -> {
                    commandClient.sendStop()
                }

                // Mode changes
                lowerTranscript.contains("gaze control") || lowerTranscript.contains("voice control") -> {
                    commandClient.setMode("headset")
                }
                lowerTranscript.contains("manual control") -> {
                    commandClient.setMode("gui")
                }
                lowerTranscript.contains("tool control") || lowerTranscript.contains("to control") -> {
                    commandClient.setMode("tool")
                }
                lowerTranscript.contains("gamepad control") -> {
                    commandClient.setMode("gamepad")
                }

                else -> {
                    Log.d(TAG, "No command matched for: $lowerTranscript")
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoiceCommandManager"
    }
}
