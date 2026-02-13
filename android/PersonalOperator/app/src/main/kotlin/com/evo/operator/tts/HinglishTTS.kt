package com.evo.operator.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

/**
 * Text-to-Speech engine configured for Hinglish output.
 * Uses Android's built-in TTS with Hindi (India) locale.
 *
 * Supports:
 * - Speaking Hinglish text aloud
 * - Queueing multiple utterances
 * - Callback on completion
 */
class HinglishTTS(
    context: Context,
    private val onReady: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "HinglishTTS"
    }

    private val tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Try Hindi (India) locale first
            val hindiLocale = Locale("hi", "IN")
            val result = tts.setLanguage(hindiLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Fallback to English (India) which handles Hinglish reasonably
                Log.w(TAG, "Hindi locale not available, falling back to en-IN")
                tts.setLanguage(Locale("en", "IN"))
            }

            // Set speech rate — slightly slower for clarity
            tts.setSpeechRate(0.9f)
            tts.setPitch(1.0f)

            isReady = true
            onReady?.invoke()
            Log.i(TAG, "TTS initialized with locale: ${tts.voice?.locale}")
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    /**
     * Speaks the given text in Hinglish.
     * @param text The text to speak
     * @param onDone Optional callback when speaking finishes
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            Log.w(TAG, "TTS not ready yet, queuing text")
            // Retry after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speak(text, onDone)
            }, 500)
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        if (onDone != null) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) onDone()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {}
            })
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.i(TAG, "Speaking: ${text.take(50)}...")
    }

    /**
     * Adds text to the speaking queue without interrupting current speech.
     */
    fun queue(text: String) {
        if (!isReady) return
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    /**
     * Stops any ongoing speech.
     */
    fun stop() {
        tts.stop()
    }

    /**
     * Releases TTS resources. Must be called when done.
     */
    fun shutdown() {
        tts.stop()
        tts.shutdown()
        isReady = false
    }
}
