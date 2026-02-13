package com.evo.operator.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telecom.TelecomManager
import android.util.Log
import com.evo.operator.tts.HinglishTTS

/**
 * Service that handles call initiation.
 * Supports two modes:
 *
 * 1. Native Cellular Call — uses ACTION_CALL intent
 *    - Direct phone call via the dialer
 *    - No voice injection possible
 *    - Simple and reliable
 *
 * 2. VoIP Call — initiated via backend (Twilio/WebRTC)
 *    - AI-generated TTS can be streamed into the call
 *    - Recording possible (with consent)
 *    - Requires backend coordination
 */
class CallService : Service() {

    companion object {
        private const val TAG = "CallService"

        /**
         * Initiates a native cellular call.
         * Requires CALL_PHONE permission.
         */
        fun makeNativeCall(context: Context, phoneNumber: String) {
            Log.i(TAG, "Initiating native call to $phoneNumber")
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: SecurityException) {
                Log.e(TAG, "CALL_PHONE permission not granted", e)
            }
        }

        /**
         * Requests VoIP call initiation via the backend.
         * The backend will use Twilio to place the call and inject TTS.
         *
         * @param phone E.164 formatted number
         * @param script What the AI should say (Hinglish)
         * @param recordConsent Whether user consented to recording
         */
        fun requestVoipCall(
            phone: String,
            script: String,
            recordConsent: Boolean = false
        ): Map<String, Any> {
            // This returns a payload to send to the backend via WebSocket/REST
            return mapOf(
                "type" to "call_initiate",
                "data" to mapOf(
                    "phone" to phone,
                    "script" to script,
                    "type" to "voip",
                    "record_consent" to recordConsent
                )
            )
        }

        /**
         * Plays a local TTS preview of what the AI will say on the call.
         * Useful for user to hear the script before confirming.
         */
        fun previewCallScript(context: Context, script: String) {
            val tts = HinglishTTS(context)
            tts.speak(script)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
