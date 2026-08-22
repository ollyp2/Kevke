package de.kevke.servercontrol.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kevke.servercontrol.AppViewModel
import de.kevke.servercontrol.ui.components.*
import de.kevke.servercontrol.ui.theme.LineIcons
import de.kevke.servercontrol.ui.theme.LocalAppColors

@Composable
fun BackupsScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val backups by vm.backups.collectAsState()
    val status by vm.status.collectAsState()
    val working by vm.working.collectAsState()
    val message by vm.message.collectAsState()

    var label by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshBackups() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        message?.let {
            Panel(Modifier.padding(bottom = 16.dp)) {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = c.warn)
            }
        }

        Panel {
            Text("NEUES BACKUP", style = MaterialTheme.typography.labelSmall,
                 color = c.onMuted)
            Spacer(Modifier.height(10.dp))
            TextField(label, { label = it }, "Notiz, z.B. vor-dem-bunker")
            Spacer(Modifier.height(12.dp))
            LineButton(
                label = if (working) "Laeuft…" else "Jetzt sichern",
                icon = LineIcons.Save,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.createBackup(label); label = "" },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Sichert die komplette Platte per Snapshot — funktioniert " +
                    "auch, wenn der Server aus ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onMuted,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("VORHANDEN", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(12.dp))

        if (backups.isEmpty()) {
            EmptyHint("Noch keine Backups")
        } else {
            backups.forEach { backup ->
                Panel(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                backup.label.ifBlank { backup.name.removePrefix("sotf-server-backup-") },
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.onBackground,
                            )
                            Text(
                                buildString {
                                    append(formatStamp(backup.createdAt))
                                    if (backup.sizeGb > 0) append(" · ${backup.sizeGb} GB")
                                    if (!backup.isReady) append(" · ${backup.status}")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (backup.isReady) c.onMuted else c.warn,
                            )
                        }
                        if (backup.isReady) {
                            LineIcon(
                                LineIcons.Restore, tint = c.accent, size = 22.dp,
                                modifier = Modifier.padding(horizontal = 10.dp)
                                    .clickableNoRipple { pendingRestore = backup.name },
                            )
                        }
                        LineIcon(
                            LineIcons.Delete, tint = c.danger, size = 22.dp,
                            modifier = Modifier.padding(start = 6.dp)
                                .clickableNoRipple { pendingDelete = backup.name },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    pendingRestore?.let { name ->
        val canRestore = status.state == "TERMINATED"
        ConfirmDialog(
            title = if (canRestore) "Backup einspielen?" else "Server laeuft noch",
            message = if (canRestore)
                "Die Platte wird auf den Stand dieses Backups zurueckgesetzt. " +
                    "Die bisherige Platte bleibt als Rueckfallnetz liegen — sie " +
                    "kostet rund 1,60 EUR im Monat, bis du sie loeschst."
            else
                "Zum Einspielen muss der Server aus sein. Schalte ihn im " +
                    "Start-Bildschirm ab und versuch es dann nochmal.",
            confirmLabel = if (canRestore) "Einspielen" else "Verstanden",
            onConfirm = {
                if (canRestore) vm.restoreBackup(name)
                pendingRestore = null
            },
            onDismiss = { pendingRestore = null },
        )
    }

    pendingDelete?.let { name ->
        ConfirmDialog(
            title = "Sicher?",
            message = "Backup wird endgueltig geloescht:\n$name",
            onConfirm = { vm.deleteBackup(name); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** "20260822-2247" or an RFC3339 stamp -> "22.08.2026, 22:47". */
private fun formatStamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val digits = raw.filter { it.isDigit() }
    if (digits.length < 12) return raw
    return "${digits.substring(6, 8)}.${digits.substring(4, 6)}." +
        "${digits.substring(0, 4)}, ${digits.substring(8, 10)}:${digits.substring(10, 12)}"
}
