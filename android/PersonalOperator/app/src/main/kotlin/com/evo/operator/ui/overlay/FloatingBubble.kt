package com.evo.operator.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Floating bubble that appears over other apps.
 * Tapping opens the confirmation panel.
 * Draggable to any edge of the screen.
 *
 * Implementation uses WindowManager with TYPE_APPLICATION_OVERLAY
 * and Jetpack Compose for rendering.
 */
object FloatingBubble {

    private const val TAG = "FloatingBubble"
    private var isShowing = false

    /**
     * Creates the floating bubble overlay parameters.
     * Call this from a Service context with SYSTEM_ALERT_WINDOW permission.
     */
    fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
        }
    }

    /**
     * Composable content for the floating bubble.
     */
    @Composable
    fun BubbleContent(
        hasNotification: Boolean = false,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6366F1),  // Indigo
                            Color(0xFF8B5CF6)   // Violet
                        )
                    )
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "Operator",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )

            // Notification badge
            if (hasNotification) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))  // Red badge
                )
            }
        }
    }
}
