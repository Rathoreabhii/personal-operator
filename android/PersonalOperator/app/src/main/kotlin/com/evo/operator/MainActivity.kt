package com.evo.operator

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.evo.operator.model.ActionPlan
import com.evo.operator.model.AuditEntry
import com.evo.operator.security.SecureStorage
import com.evo.operator.service.OperatorForegroundService
import com.evo.operator.ui.overlay.ConfirmationPanel
import com.evo.operator.ui.screens.DashboardScreen
import com.evo.operator.ui.screens.SettingsDialog
import com.google.gson.Gson

/**
 * Main entry point for the Personal Operator app.
 * Handles permission requests, service startup, and root navigation.
 */
class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage
    private val gson = Gson()

    // State
    private val _isConnected = mutableStateOf(false)
    private val _killSwitchActive = mutableStateOf(false)
    private val _commandModeEnabled = mutableStateOf(true)
    private val _autoSuggestEnabled = mutableStateOf(true)
    private val _recentActions = mutableStateListOf<AuditEntry>()
    private val _currentPlan = mutableStateOf<ActionPlan?>(null)
    private val _showConfirmation = mutableStateOf(false)
    private var _currentPlanJson: String = ""

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startOperatorService()
        } else {
            Toast.makeText(this, "Permissions required for operation", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        secureStorage = SecureStorage(this)

        // Load saved states
        _killSwitchActive.value = secureStorage.killSwitchActive
        _commandModeEnabled.value = secureStorage.commandModeEnabled
        _autoSuggestEnabled.value = secureStorage.autoSuggestEnabled

        // Request permissions and start service
        requestPermissions()

        // Setup callbacks from foreground service
        setupServiceCallbacks()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                var showSettings by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(
                        isConnected = _isConnected.value,
                        killSwitchActive = _killSwitchActive.value,
                        commandModeEnabled = _commandModeEnabled.value,
                        autoSuggestEnabled = _autoSuggestEnabled.value,
                        recentActions = _recentActions,
                        onToggleKillSwitch = { active ->
                            _killSwitchActive.value = active
                            if (active) {
                                OperatorForegroundService.instance?.activateKillSwitch()
                            } else {
                                OperatorForegroundService.instance?.deactivateKillSwitch()
                            }
                        },
                        onToggleCommandMode = { enabled ->
                            _commandModeEnabled.value = enabled
                            secureStorage.commandModeEnabled = enabled
                        },
                        onToggleAutoSuggest = { enabled ->
                            _autoSuggestEnabled.value = enabled
                            secureStorage.autoSuggestEnabled = enabled
                        },
                        onOpenSettings = {
                            showSettings = true
                        }
                    )

                    // Confirmation panel overlays on top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        ConfirmationPanel(
                            plan = _currentPlan.value,
                            visible = _showConfirmation.value,
                            onConfirm = { doubleConfirmed ->
                                OperatorForegroundService.instance?.confirmAction(
                                    _currentPlanJson,
                                    doubleConfirmed
                                )
                                _showConfirmation.value = false
                                addAuditEntry("action_confirmed", _currentPlan.value?.intent?.name ?: "")
                            },
                            onReject = {
                                OperatorForegroundService.instance?.rejectAction(
                                    _currentPlan.value?.intent?.name
                                )
                                _showConfirmation.value = false
                                addAuditEntry("action_rejected", _currentPlan.value?.intent?.name ?: "")
                            },
                            onDismiss = {
                                _showConfirmation.value = false
                            }
                        )
                    }

                    // Settings dialog
                    if (showSettings) {
                        SettingsDialog(
                            currentServerUrl = secureStorage.serverUrl,
                            currentApiKey = secureStorage.apiKey,
                            currentPhoneNumber = secureStorage.userPhoneNumber,
                            onSave = { serverUrl, apiKey, phoneNumber ->
                                secureStorage.serverUrl = serverUrl
                                secureStorage.apiKey = apiKey
                                secureStorage.userPhoneNumber = phoneNumber
                                showSettings = false
                                // Restart service to apply new settings
                                OperatorForegroundService.stop(this@MainActivity)
                                OperatorForegroundService.start(this@MainActivity)
                                Toast.makeText(this@MainActivity, "Settings saved! Reconnecting...", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startOperatorService()
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Check notification listener access
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(
                this,
                "Please enable Notification Access for Personal Operator",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, com.evo.operator.service.NotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    private fun startOperatorService() {
        OperatorForegroundService.start(this)
    }

    private fun setupServiceCallbacks() {
        OperatorForegroundService.onPlanReceived = { json ->
            runOnUiThread {
                try {
                    _currentPlanJson = json
                    val response = gson.fromJson(json, Map::class.java)
                    val planMap = response["plan"] as? Map<*, *>
                    if (planMap != null) {
                        val planJson = gson.toJson(planMap)
                        _currentPlan.value = gson.fromJson(planJson, ActionPlan::class.java)
                        _showConfirmation.value = true
                        addAuditEntry("notification", _currentPlan.value?.humanText ?: "Plan received")
                    }
                } catch (e: Exception) {
                    addAuditEntry("error", "Failed to parse plan: ${e.message}")
                }
            }
        }

        OperatorForegroundService.onConnectionChanged = { connected ->
            runOnUiThread {
                _isConnected.value = connected
            }
        }

        OperatorForegroundService.onErrorReceived = { error ->
            runOnUiThread {
                addAuditEntry("error", error)
            }
        }
    }

    private fun addAuditEntry(type: String, details: String) {
        _recentActions.add(
            AuditEntry(
                eventType = type,
                details = details
            )
        )
    }

    override fun onDestroy() {
        OperatorForegroundService.onPlanReceived = null
        OperatorForegroundService.onConnectionChanged = null
        OperatorForegroundService.onErrorReceived = null
        super.onDestroy()
    }
}
