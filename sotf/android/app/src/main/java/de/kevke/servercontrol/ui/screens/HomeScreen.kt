package de.kevke.servercontrol.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import de.kevke.servercontrol.AppViewModel
import de.kevke.servercontrol.ui.components.*
import de.kevke.servercontrol.ui.theme.LineIcons
import de.kevke.servercontrol.ui.theme.LocalAppColors

@Composable
fun HomeScreen(vm: AppViewModel, onOpenMenu: () -> Unit) {
    val c = LocalAppColors.current
    val status by vm.status.collectAsState()
    val backups by vm.backups.collectAsState()
    val busyMessage by vm.busyMessage.collectAsState()

    var backupsExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    vm.activeServerName,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                StatusLamp(status.state)
            }
            LineIcon(
                LineIcons.Menu,
                tint = c.onBackground,
                modifier = Modifier
                    .size(44.dp)
                    .padding(10.dp)
                    .then(Modifier)
                    .clickableNoRipple(onOpenMenu),
            )
        }

        Spacer(Modifier.height(36.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PowerButton(
                running = status.isRunning,
                busy = status.isBusy || busyMessage != null,
                enabled = true,
                onClick = {
                    if (status.isRunning) confirmStop = true else vm.startServer()
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        busyMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = c.warn,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        InfoStrip(status.externalIp, status.playersOnline, status.idleShutdownIn)

        Spacer(Modifier.height(24.dp))

        CollapsibleSection(
            title = "Backups",
            icon = LineIcons.Save,
            expanded = backupsExpanded,
            onToggle = {
                backupsExpanded = !backupsExpanded
                if (backupsExpanded) vm.refreshBackups()
            },
        ) {
            LineButton(
                label = "Jetzt speichern",
                icon = LineIcons.Save,
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.createBackup() },
            )
            Spacer(Modifier.height(16.dp))

            if (backups.isEmpty()) {
                EmptyHint("Noch keine Backups")
            } else {
                backups.forEach { backup ->
                    BackupRow(
                        name = backup.name,
                        subtitle = buildString {
                            backup.worldName?.let { append(it); append(" · ") }
                            backup.gameDays?.let { append("Tag $it"); append(" · ") }
                            backup.sizeBytes?.let { append("${it / 1024} KB") }
                        }.trimEnd(' ', '·'),
                        onRestore = { pendingRestore = backup.name },
                        onDelete = { pendingDelete = backup.name },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Server ausschalten?",
            message = if (status.playersOnline > 0)
                "Es sind noch ${status.playersOnline} Spieler online. Trotzdem herunterfahren?"
            else
                "Die VM wird gestoppt. Savegames bleiben erhalten.",
            confirmLabel = "Ausschalten",
            onConfirm = {
                confirmStop = false
                vm.stopServer(force = status.playersOnline > 0)
            },
            onDismiss = { confirmStop = false },
        )
    }

    pendingRestore?.let { name ->
        ConfirmDialog(
            title = "Backup einspielen?",
            message = "„$name“ ersetzt den aktuellen Spielstand. " +
                "Vorher wird automatisch ein Sicherungs-Backup angelegt.",
            confirmLabel = "Einspielen",
            onConfirm = { vm.restoreBackup(name); pendingRestore = null },
            onDismiss = { pendingRestore = null },
        )
    }

    pendingDelete?.let { name ->
        ConfirmDialog(
            title = "Sicher?",
            message = "Backup „$name“ wird endgueltig geloescht.",
            onConfirm = { vm.deleteBackup(name); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun InfoStrip(ip: String?, players: Int, idleIn: Long?) {
    val c = LocalAppColors.current
    Panel {
        InfoRow("Adresse", ip ?: "—")
        Spacer(Modifier.height(10.dp))
        InfoRow("Spieler online", players.toString())
        idleIn?.let {
            Spacer(Modifier.height(10.dp))
            InfoRow("Auto-Aus in", "${it / 60} min")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.onMuted,
             modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.onBackground)
    }
}

@Composable
private fun BackupRow(
    name: String,
    subtitle: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = c.onBackground)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onMuted)
            }
        }
        LineIcon(
            LineIcons.Restore, tint = c.accent, size = 22.dp,
            modifier = Modifier.padding(horizontal = 10.dp).clickableNoRipple(onRestore),
        )
        LineIcon(
            LineIcons.Delete, tint = c.danger, size = 22.dp,
            modifier = Modifier.padding(start = 6.dp).clickableNoRipple(onDelete),
        )
    }
}

/** Tap target without the Material ripple — keeps the line-art look clean. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    androidx.compose.foundation.clickable(
        interactionSource = remember {
            androidx.compose.foundation.interaction.MutableInteractionSource()
        },
        indication = null,
        onClick = onClick,
    )
}
