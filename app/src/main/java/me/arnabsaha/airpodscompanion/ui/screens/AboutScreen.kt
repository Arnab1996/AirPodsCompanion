package me.arnabsaha.airpodscompanion.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.arnabsaha.airpodscompanion.BuildConfig
import me.arnabsaha.airpodscompanion.ui.components.DisconnectConfirmDialog
import me.arnabsaha.airpodscompanion.ui.components.ForgetConfirmDialog
import me.arnabsaha.airpodscompanion.ui.components.InfoRow
import me.arnabsaha.airpodscompanion.ui.components.NavRow
import me.arnabsaha.airpodscompanion.ui.components.RenameDialog
import me.arnabsaha.airpodscompanion.ui.components.RowDivider
import me.arnabsaha.airpodscompanion.ui.components.SectionCard
import me.arnabsaha.airpodscompanion.ui.components.SectionHeader
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleRed
import me.arnabsaha.airpodscompanion.ui.theme.Radius
import me.arnabsaha.airpodscompanion.ui.theme.Spacing
import me.arnabsaha.airpodscompanion.ui.theme.TextAlpha
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel

/**
 * Device identity, app info and the destructive actions. Off the main surface so Home and
 * Settings stay about what the user changes day to day.
 */
@Composable
fun AboutScreen(vm: AirPodsViewModel, onBack: () -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
    ) {
        Spacer(Modifier.height(Spacing.s))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                "About",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(Spacing.l))

        SectionHeader("Device")
        DeviceCard(vm)

        Spacer(Modifier.height(Spacing.m))

        SectionHeader("App")
        AppCard()

        Spacer(Modifier.height(Spacing.xxl))

        DestructiveActions(vm)

        Spacer(Modifier.height(bottomInset + Spacing.section))
    }
}

@Composable
private fun DeviceCard(vm: AirPodsViewModel) {
    val deviceName by vm.bondedDeviceName.collectAsStateWithLifecycle()
    val nearest by vm.nearestAirPods.collectAsStateWithLifecycle()
    val deviceInfo by vm.deviceInfo.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf(false) }

    SectionCard {
        NavRow("Name", onClick = { showRenameDialog = true }, value = deviceName ?: "AirPods Pro")
        RowDivider()
        InfoRow(
            "Model",
            nearest?.modelName?.takeIf { it.isNotBlank() && !it.startsWith("Unknown") }
                ?: deviceInfo?.modelNumber?.ifBlank { null } ?: "—",
            dense = true
        )
        RowDivider()
        InfoRow("Firmware", deviceInfo?.firmwareVersion?.ifBlank { null } ?: "—", dense = true)
        RowDivider()
        InfoRow("Serial", deviceInfo?.serialNumber?.ifBlank { null } ?: "—", dense = true)
        RowDivider()
        InfoRow("Protocol", "AACP / L2CAP", dense = true)
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = deviceName ?: "AirPods Pro",
            onDismiss = { showRenameDialog = false },
            onRename = { vm.renameAirPods(it); showRenameDialog = false }
        )
    }
}

@Composable
private fun AppCard() {
    val context = LocalContext.current
    val hasOverlay = rememberCanDrawOverlays()

    SectionCard {
        InfoRow("Version", BuildConfig.VERSION_NAME, dense = true)
        RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
                .padding(vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Popup Overlay",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.m))
            Text(
                if (hasOverlay) "Granted" else "Tap to enable",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasOverlay) AppleGreen else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DestructiveActions(vm: AirPodsViewModel) {
    val deviceName by vm.bondedDeviceName.collectAsStateWithLifecycle()

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDisconnectDialog = true },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(Radius.control),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleRed)
    ) {
        Text("Disconnect", style = MaterialTheme.typography.labelLarge)
    }

    Spacer(Modifier.height(Spacing.s))

    TextButton(
        onClick = { showForgetDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Forget This Device",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.secondary)
        )
    }

    if (showDisconnectDialog) {
        DisconnectConfirmDialog(
            deviceName = deviceName ?: "AirPods",
            onDismiss = { showDisconnectDialog = false },
            onConfirm = { vm.disconnect(); showDisconnectDialog = false }
        )
    }
    if (showForgetDialog) {
        ForgetConfirmDialog(
            deviceName = deviceName ?: "AirPods",
            onDismiss = { showForgetDialog = false },
            onConfirm = { vm.forgetDevice(); showForgetDialog = false }
        )
    }
}
