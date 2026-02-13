package com.evo.operator.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.evo.operator.MainActivity
import com.evo.operator.model.NotificationData
import com.evo.operator.network.WebSocketClient
import com.evo.operator.security.SecureStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Foreground service that keeps the app alive, manages the WebSocket
 * connection lifecycle, and coordinates between NotificationListener,
 * overlay UI, and the backend.
 */
class OperatorForegroundService : Service() {

    companion object {
        private const val TAG = "OperatorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "operator_foreground"

        var instance: OperatorForegroundService? = null
            private set

        // Event callbacks for UI
        var onPlanReceived: ((String) -> Unit)? = null
        var onExecuteReceived: ((String) -> Unit)? = null
        var onErrorReceived: ((String) -> Unit)? = null
        var onConnectionChanged: ((Boolean) -> Unit)? = null

        fun start(context: Context) {
            val intent = Intent(context, OperatorForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OperatorForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var secureStorage: SecureStorage
    private var wsClient: WebSocketClient? = null
    private val gson = Gson()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        secureStorage = SecureStorage(this)
        createNotificationChannel()
        Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        // Acquire wake lock to keep WebSocket alive
        acquireWakeLock()

        // Connect WebSocket
        connectWebSocket()

        // Register notification listener callback
        NotificationListener.onNotificationReceived = { data ->
            handleNotification(data)
        }

        return START_STICKY
    }

    private fun connectWebSocket() {
        val serverUrl = secureStorage.serverUrl
        val apiKey = secureStorage.apiKey

        if (serverUrl.isBlank() || apiKey.isBlank()) {
            Log.e(TAG, "Server URL or API key not configured")
            updateNotification("⚠️ Setup required")
            return
        }

        wsClient?.disconnect()

        wsClient = WebSocketClient(
            serverUrl = serverUrl,
            apiKey = apiKey,
            clientId = "android_${Build.MODEL}_${System.currentTimeMillis()}",
            onPlanReceived = { json ->
                Log.i(TAG, "Plan received from backend")
                onPlanReceived?.invoke(json)
            },
            onExecute = { json ->
                Log.i(TAG, "Execute command received")
                onExecuteReceived?.invoke(json)
            },
            onError = { error ->
                Log.e(TAG, "WS error: $error")
                onErrorReceived?.invoke(error)
            },
            onConnectionChanged = { connected ->
                val status = if (connected) "🟢 Connected" else "🔴 Disconnected"
                updateNotification(status)
                onConnectionChanged?.invoke(connected)
            }
        )

        wsClient?.connect()
    }

    /**
     * Handles an incoming notification from NotificationListener.
     */
    private fun handleNotification(data: NotificationData) {
        if (secureStorage.killSwitchActive) {
            Log.w(TAG, "Kill switch active — ignoring notification")
            return
        }

        Log.i(TAG, "Processing notification: [${data.mode}] ${data.sender}: ${data.message.take(40)}")
        wsClient?.sendNotification(data)
    }

    /**
     * Confirms an action plan via WebSocket.
     */
    fun confirmAction(planJson: String, doubleConfirmed: Boolean = false) {
        val planType = object : TypeToken<Map<String, Any>>() {}.type
        val plan: Map<String, Any> = gson.fromJson(planJson, planType)
        wsClient?.confirmAction(plan, doubleConfirmed)
    }

    /**
     * Rejects an action plan via WebSocket.
     */
    fun rejectAction(intent: String?) {
        wsClient?.rejectAction(intent)
    }

    /**
     * Emergency kill switch — disconnects everything.
     */
    fun activateKillSwitch() {
        secureStorage.killSwitchActive = true
        wsClient?.disconnect()
        updateNotification("🛑 Kill Switch Active")
        Log.w(TAG, "KILL SWITCH ACTIVATED")
    }

    /**
     * Deactivates kill switch and reconnects.
     */
    fun deactivateKillSwitch() {
        secureStorage.killSwitchActive = false
        connectWebSocket()
        Log.i(TAG, "Kill switch deactivated, reconnecting")
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PersonalOperator::ForegroundService"
        ).apply {
            acquire(60 * 60 * 1000L) // 1 hour, will re-acquire on restart
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Personal Operator",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the AI assistant running"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Personal Operator")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        wsClient?.disconnect()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        NotificationListener.onNotificationReceived = null
        instance = null
        super.onDestroy()
        Log.i(TAG, "Foreground service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
