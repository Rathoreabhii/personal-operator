package com.evo.operator.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evo.operator.model.AuditEntry

/**
 * Main dashboard screen — shows connection status, system controls,
 * recent activity, and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isConnected: Boolean,
    killSwitchActive: Boolean,
    commandModeEnabled: Boolean,
    autoSuggestEnabled: Boolean,
    recentActions: List<AuditEntry>,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleCommandMode: (Boolean) -> Unit,
    onToggleAutoSuggest: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val gradientBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Personal Operator",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Assistant Control Center",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // ── Connection Status ──
            item {
                ConnectionStatusCard(isConnected = isConnected)
            }

            // ── Kill Switch ──
            item {
                KillSwitchCard(
                    active = killSwitchActive,
                    onToggle = onToggleKillSwitch
                )
            }

            // ── Mode Toggles ──
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Operating Modes",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        ModeToggleRow(
                            icon = Icons.Filled.Terminal,
                            title = "Command Mode",
                            subtitle = "Self-message instructions",
                            enabled = commandModeEnabled,
                            onToggle = onToggleCommandMode
                        )

                        Divider(
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        ModeToggleRow(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Auto Suggest",
                            subtitle = "Reply suggestions for incoming messages",
                            enabled = autoSuggestEnabled,
                            onToggle = onToggleAutoSuggest
                        )
                    }
                }
            }

            // ── Recent Activity ──
            item {
                Text(
                    text = "Recent Activity",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (recentActions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Inbox,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No activity yet",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            items(recentActions.takeLast(20).reversed()) { entry ->
                AuditEntryCard(entry = entry)
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(isConnected: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF064E3B) else Color(0xFF7F1D1D)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444))
            )
            Column {
                Text(
                    text = if (isConnected) "Connected" else "Disconnected",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isConnected) "Backend server is reachable" else "Check server and network",
                    color = Color(0xFFA7F3D0).copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun KillSwitchCard(active: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color(0xFF991B1B) else Color(0xFF1E293B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (active) Icons.Filled.Block else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = if (active) Color(0xFFFCA5A5) else Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Emergency Kill Switch",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (active) "ALL operations stopped" else "System is operational",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
            Switch(
                checked = active,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFFEF4444),
                    uncheckedTrackColor = Color(0xFF374151)
                )
            )
        }
    }
}

@Composable
fun ModeToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color(0xFF818CF8) else Color(0xFF475569),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF6366F1),
                uncheckedTrackColor = Color(0xFF374151)
            )
        )
    }
}

@Composable
fun AuditEntryCard(entry: AuditEntry) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            val icon = when (entry.eventType) {
                "notification" -> Icons.Filled.Notifications
                "action_confirmed" -> Icons.Filled.CheckCircle
                "action_rejected" -> Icons.Filled.Cancel
                "error" -> Icons.Filled.Error
                else -> Icons.Filled.Info
            }
            val iconColor = when (entry.eventType) {
                "action_confirmed" -> Color(0xFF10B981)
                "action_rejected" -> Color(0xFFEF4444)
                "error" -> Color(0xFFF59E0B)
                else -> Color(0xFF818CF8)
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.eventType.replace("_", " ").replaceFirstChar { it.uppercase() },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (entry.details.isNotBlank()) {
                    Text(
                        text = entry.details,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }

            Text(
                text = formatTimestamp(entry.timestamp),
                color = Color(0xFF475569),
                fontSize = 10.sp
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
