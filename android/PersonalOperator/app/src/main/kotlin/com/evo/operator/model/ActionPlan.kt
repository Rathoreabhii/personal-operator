package com.evo.operator.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the AI-generated action plan from DeepSeek.
 * Maps directly to the strict JSON schema defined in the system prompt.
 */
data class ActionPlan(
    @SerializedName("intent")
    val intent: ActionIntent,

    @SerializedName("confidence")
    val confidence: Double,

    @SerializedName("risk_level")
    val riskLevel: RiskLevel,

    @SerializedName("human_text")
    val humanText: String,

    @SerializedName("params")
    val params: Map<String, String>,

    @SerializedName("execution_plan")
    val executionPlan: List<String>,

    @SerializedName("confirmation_required")
    val confirmationRequired: Boolean = true,

    @SerializedName("requires_double_confirm")
    val requiresDoubleConfirm: Boolean = false,

    @SerializedName("is_passive")
    val isPassive: Boolean = false
)

/**
 * Allowed action intents — matches backend whitelist.
 */
enum class ActionIntent {
    @SerializedName("send_message") SEND_MESSAGE,
    @SerializedName("call_number") CALL_NUMBER,
    @SerializedName("summarize_call") SUMMARIZE_CALL,
    @SerializedName("open_app") OPEN_APP,
    @SerializedName("info_response") INFO_RESPONSE
}

/**
 * Risk levels for action classification.
 */
enum class RiskLevel {
    @SerializedName("low") LOW,
    @SerializedName("medium") MEDIUM,
    @SerializedName("high") HIGH,
    @SerializedName("critical") CRITICAL
}

/**
 * WebSocket message wrapper for outgoing messages to backend.
 */
data class WsOutgoingMessage(
    val type: String,
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val data: Any? = null,
    val apiKey: String? = null,
    val clientId: String? = null
)

/**
 * Notification data extracted from WhatsApp/Telegram.
 */
data class NotificationData(
    val mode: String,           // "command" or "suggestion"
    val sender: String,
    val message: String,
    val timestamp: String,
    val packageName: String
)

/**
 * Represents a captured notification for the audit log.
 */
data class AuditEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val intent: String? = null,
    val riskLevel: String? = null,
    val details: String = ""
)

/**
 * Call summary returned from the backend.
 */
data class CallSummary(
    val summary: String,
    @SerializedName("key_points")
    val keyPoints: List<String>,
    @SerializedName("decisions_made")
    val decisionsMade: List<String>?,
    @SerializedName("action_items")
    val actionItems: List<ActionItem>?,
    val tone: String?,
    @SerializedName("follow_up_needed")
    val followUpNeeded: Boolean = false
)

data class ActionItem(
    val task: String,
    val owner: String?,
    val deadline: String?
)
