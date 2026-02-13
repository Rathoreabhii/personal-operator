package com.evo.operator.ui.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evo.operator.model.ActionPlan
import com.evo.operator.model.RiskLevel

/**
 * Slide-in confirmation panel that appears from the bottom.
 * Shows the AI's action plan with:
 * - Human-readable summary (Hinglish)
 * - Risk level badge (color-coded)
 * - Execution steps
 * - Confirm / Reject buttons
 * - Double confirmation for high-risk actions
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConfirmationPanel(
    plan: ActionPlan?,
    visible: Boolean,
    onConfirm: (doubleConfirmed: Boolean) -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDoubleConfirm by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible && plan != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        plan?.let { currentPlan ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E2E)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // ── Header ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 Operator",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        RiskBadge(riskLevel = currentPlan.riskLevel)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Intent chip ──
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2D2D3F),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = currentPlan.intent.name.lowercase().replace("_", " "),
                            color = Color(0xFF818CF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // ── Human text (Hinglish) ──
                    Text(
                        text = currentPlan.humanText,
                        color = Color(0xFFE2E8F0),
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // ── Execution steps ──
                    if (currentPlan.executionPlan.isNotEmpty()) {
                        Text(
                            text = "Steps:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        currentPlan.executionPlan.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. ",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = step,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ── Confidence bar ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Confidence: ",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        LinearProgressIndicator(
                            progress = { currentPlan.confidence.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                currentPlan.confidence >= 0.8 -> Color(0xFF10B981)
                                currentPlan.confidence >= 0.5 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            },
                            trackColor = Color(0xFF374151),
                        )
                        Text(
                            text = " ${(currentPlan.confidence * 100).toInt()}%",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // ── Action buttons ──
                    if (!showDoubleConfirm) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Reject
                            OutlinedButton(
                                onClick = {
                                    showDoubleConfirm = false
                                    onReject()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFEF4444)
                                )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject")
                            }

                            // Confirm
                            Button(
                                onClick = {
                                    if (currentPlan.requiresDoubleConfirm) {
                                        showDoubleConfirm = true
                                    } else {
                                        onConfirm(false)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981)
                                )
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Confirm")
                            }
                        }
                    } else {
                        // ── Double confirmation for high-risk ──
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠️ High-risk action! Are you sure?",
                                color = Color(0xFFFBBF24),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showDoubleConfirm = false
                                        onReject()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFEF4444)
                                    )
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        showDoubleConfirm = false
                                        onConfirm(true)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626)
                                    )
                                ) {
                                    Text("Yes, Execute")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Color-coded risk level badge.
 */
@Composable
fun RiskBadge(riskLevel: RiskLevel) {
    val (color, icon) = when (riskLevel) {
        RiskLevel.LOW -> Color(0xFF10B981) to Icons.Filled.CheckCircle
        RiskLevel.MEDIUM -> Color(0xFFF59E0B) to Icons.Filled.Info
        RiskLevel.HIGH -> Color(0xFFEF4444) to Icons.Filled.Warning
        RiskLevel.CRITICAL -> Color(0xFFDC2626) to Icons.Filled.Error
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = riskLevel.name,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
