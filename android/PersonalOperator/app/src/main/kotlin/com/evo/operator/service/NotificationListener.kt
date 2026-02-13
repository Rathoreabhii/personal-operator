package com.evo.operator.service

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.evo.operator.model.NotificationData
import com.evo.operator.security.SecureStorage

/**
 * Listens to WhatsApp and Telegram notifications.
 * Extracts sender and message text, determines if it's a self-message
 * (command mode) or external message (suggestion mode), and routes
 * to the WebSocket client for AI processing.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"

        // Packages we monitor
        private const val PKG_WHATSAPP = "com.whatsapp"
        private const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val PKG_TELEGRAM = "org.telegram.messenger"

        private val MONITORED_PACKAGES = setOf(
            PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS, PKG_TELEGRAM
        )

        // Listener for forwarding to the foreground service
        var onNotificationReceived: ((NotificationData) -> Unit)? = null
    }

    private lateinit var secureStorage: SecureStorage

    override fun onCreate() {
        super.onCreate()
        secureStorage = SecureStorage(this)
        Log.i(TAG, "NotificationListener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // Check kill switch
        if (secureStorage.killSwitchActive) return

        val packageName = sbn.packageName
        if (packageName !in MONITORED_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract text
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Skip group summary notifications
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            // For group messages, title = group name, we still process
        }

        // Determine sender name
        val sender = extractSenderName(title, packageName)

        // Determine mode: command vs suggestion
        val mode = determineMode(sender, title, text)

        // Skip if mode is disabled
        if (mode == "command" && !secureStorage.commandModeEnabled) return
        if (mode == "suggestion" && !secureStorage.autoSuggestEnabled) return

        val notifData = NotificationData(
            mode = mode,
            sender = sender,
            message = text,
            timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssZ",
                java.util.Locale.US
            ).format(java.util.Date(sbn.postTime)),
            packageName = packageName
        )

        Log.i(TAG, "Captured [$mode] from $sender via $packageName: ${text.take(50)}...")

        // Forward to the foreground service
        onNotificationReceived?.invoke(notifData)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed on removal
    }

    /**
     * Extracts the sender name from the notification title.
     * WhatsApp: title = sender name (or "Contact Name" for DMs)
     * Telegram: title = sender name
     */
    private fun extractSenderName(title: String, packageName: String): String {
        // WhatsApp group format: "Sender @ Group" or "Group: N messages"
        return when {
            title.contains(" @ ") -> title.substringBefore(" @ ").trim()
            title.contains(": ") && title.endsWith("messages") -> title.substringBefore(":").trim()
            else -> title.trim()
        }
    }

    /**
     * Determines if this is a self-message (command mode) or external (suggestion mode).
     *
     * Detection strategies:
     * 1. Compare sender name against user's configured phone/name
     * 2. WhatsApp "Message yourself" feature sends with user's own name
     * 3. If sender matches user's name → command mode
     */
    private fun determineMode(sender: String, title: String, text: String): String {
        val userPhone = secureStorage.userPhoneNumber

        // Strategy 1: Check if sender matches configured user identity
        if (userPhone.isNotBlank()) {
            val senderLower = sender.lowercase()
            val userLower = userPhone.lowercase()

            // Direct match or contains
            if (senderLower == userLower ||
                senderLower.contains(userLower) ||
                senderLower == "you" ||
                senderLower == "me"
            ) {
                return "command"
            }
        }

        // Strategy 2: WhatsApp "You" indicator in notifications
        if (title.equals("You", ignoreCase = true) ||
            title.startsWith("You:", ignoreCase = true)) {
            return "command"
        }

        // Default: suggestion mode
        return "suggestion"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}
