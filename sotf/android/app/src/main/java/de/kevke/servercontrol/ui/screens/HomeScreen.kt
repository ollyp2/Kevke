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
    val config by vm.config.collectAsState()
    val message by vm.message.collectAsState()
    val working by vm.working.collectAsState()

    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshStatus() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    config.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                StatusLamp(status.lampState)
            }
            LineIcon(
                LineIcons.Menu,
                tint = c.onBackground,
                size = 26.dp,
                modifier = Modifier.clickableNoRipple(onOpenMenu),
            )
        }

        Spacer(Modifier.height(40.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PowerButton(
                running = status.gameReady,
                busy = working || status.isBusy || (status.isRunning && !status.gameReady),
                enabled = !working,
                onClick = { if (status.isRunning) confirmStop = true else vm.start() },
            )
        }

        Spacer(Modifier.height(24.dp))

        message?.let {
            Panel {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = c.warn)
            }
            Spacer(Modifier.height(16.dp))
        }

        Panel {
            InfoRow("Adresse", status.externalIp ?: "—")
            Spacer(Modifier.height(10.dp))
            InfoRow(
                "Spieler",
                if (status.maxPlayers > 0) "${status.players} / ${status.maxPlayers}"
                else status.players.toString(),
            )
            status.serverName?.let {
                Spacer(Modifier.height(10.dp))
                InfoRow("Servername", it)
            }
            status.uptimeSeconds?.let {
                Spacer(Modifier.height(10.dp))
                InfoRow("Laufzeit", formatUptime(it))
            }

            // RUNNING with a silent game port means the world is still loading;
            // saying "online" there would just invite a failed join.
            if (status.isRunning && !status.gameReady) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "VM laeuft, Welt laedt noch. Etwa 3 Minuten ab Start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.warn,
                )
            }

            Spacer(Modifier.height(16.dp))
            LineButton(
                label = "Status neu pruefen",
                icon = LineIcons.Restore,
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.refreshStatus() },
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Server ausschalten?",
            message = if (status.players > 0)
                "Es sind ${status.players} Spieler online. Trotzdem herunterfahren?"
            else
                "Die VM wird gestoppt. Savegames bleiben erhalten.",
            confirmLabel = "Ausschalten",
            onConfirm = {
                confirmStop = false
                vm.stop(force = status.players > 0)
            },
            onDismiss = { confirmStop = false },
        )
    }
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h} h ${m} min" else "${m} min"
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.onMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.onBackground)
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
