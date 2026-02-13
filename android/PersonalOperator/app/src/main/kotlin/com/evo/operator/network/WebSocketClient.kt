package com.evo.operator.network

import android.util.Log
import com.evo.operator.model.NotificationData
import com.evo.operator.model.WsOutgoingMessage
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket client connecting to the backend server.
 * Features: auto-reconnect, heartbeat, TLS, authentication handshake.
 */
class WebSocketClient(
    private val serverUrl: String,
    private val apiKey: String,
    private val clientId: String,
    private val onPlanReceived: (String) -> Unit,      // raw JSON of action_proposed
    private val onExecute: (String) -> Unit,            // raw JSON of execute command
    private val onError: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "WSClient"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
    }

    private val gson = Gson()
    private val isConnected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private var webSocket: WebSocket? = null
    private var heartbeatThread: Thread? = null
    private val shouldReconnect = AtomicBoolean(true)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES)  // Keep alive
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Establishes WebSocket connection and authenticates.
     */
    fun connect() {
        shouldReconnect.set(true)
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                // Send auth message
                val authMsg = WsOutgoingMessage(
                    type = "auth",
                    apiKey = apiKey,
                    clientId = clientId
                )
                ws.send(gson.toJson(authMsg))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
                handleDisconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                handleDisconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                onError("Connection failed: ${t.message}")
                handleDisconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, Map::class.java)
            val type = json["type"] as? String ?: return

            when (type) {
                "auth_success" -> {
                    Log.i(TAG, "Authenticated successfully")
                    isConnected.set(true)
                    reconnectAttempts.set(0)
                    onConnectionChanged(true)
                    startHeartbeat()
                }
                "auth_failed" -> {
                    Log.e(TAG, "Authentication failed")
                    onError("Authentication failed — check API key")
                    shouldReconnect.set(false)
                }
                "action_proposed" -> {
                    onPlanReceived(text)
                }
                "execute" -> {
                    onExecute(text)
                }
                "double_confirm_required" -> {
                    onPlanReceived(text) // Treat as needing re-confirmation
                }
                "action_rejected" -> {
                    val reason = json["reason"] as? String ?: "Unknown reason"
                    val humanText = json["human_text"] as? String ?: reason
                    onError("Rejected: $humanText")
                }
                "action_cancelled" -> {
                    Log.i(TAG, "Action cancelled by user")
                }
                "pong" -> { /* heartbeat reply */ }
                "error" -> {
                    val msg = json["message"] as? String ?: "Unknown error"
                    onError(msg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WS message", e)
        }
    }

    /**
     * Sends a notification event to the backend for AI processing.
     */
    fun sendNotification(data: NotificationData) {
        if (!isConnected.get()) {
            onError("Not connected to server")
            return
        }
        val msg = WsOutgoingMessage(type = "notification", data = data)
        webSocket?.send(gson.toJson(msg))
    }

    /**
     * Sends confirmation for an action plan.
     */
    fun confirmAction(plan: Map<String, Any>, doubleConfirmed: Boolean = false) {
        val msg = WsOutgoingMessage(
            type = "action_confirm",
            data = mapOf("plan" to plan, "double_confirmed" to doubleConfirmed)
        )
        webSocket?.send(gson.toJson(msg))
    }

    /**
     * Sends rejection for an action plan.
     */
    fun rejectAction(intent: String?) {
        val msg = WsOutgoingMessage(
            type = "action_reject",
            data = mapOf("intent" to (intent ?: "unknown"))
        )
        webSocket?.send(gson.toJson(msg))
    }

    private fun startHeartbeat() {
        heartbeatThread?.interrupt()
        heartbeatThread = Thread {
            while (isConnected.get() && !Thread.interrupted()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                    val ping = WsOutgoingMessage(type = "ping")
                    webSocket?.send(gson.toJson(ping))
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun handleDisconnect() {
        isConnected.set(false)
        onConnectionChanged(false)
        heartbeatThread?.interrupt()

        if (shouldReconnect.get()) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val attempt = reconnectAttempts.incrementAndGet()
        val delay = minOf(1000L * (1 shl minOf(attempt, 5)), MAX_RECONNECT_DELAY_MS)
        Log.i(TAG, "Reconnecting in ${delay}ms (attempt $attempt)")
        Thread {
            try {
                Thread.sleep(delay)
                if (shouldReconnect.get()) connect()
            } catch (_: InterruptedException) {}
        }.also { it.isDaemon = true; it.start() }
    }

    /**
     * Disconnects and stops reconnection attempts.
     */
    fun disconnect() {
        shouldReconnect.set(false)
        isConnected.set(false)
        heartbeatThread?.interrupt()
        webSocket?.close(1000, "Client disconnect")
        onConnectionChanged(false)
    }

    fun isConnected(): Boolean = isConnected.get()
}
