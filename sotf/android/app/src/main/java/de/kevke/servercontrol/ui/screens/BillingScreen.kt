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
import java.util.Locale

private val RANGE_KEYS = listOf("day", "week", "month", "quarter", "year", "last-month")
private val RANGE_LABELS = listOf("Tag", "Woche", "Monat", "Quartal", "Jahr", "Vormonat")

@Composable
fun BillingScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val billing by vm.billing.collectAsState()
    var rangeIndex by remember { mutableIntStateOf(2) }

    LaunchedEffect(rangeIndex) { vm.refreshBilling(RANGE_KEYS[rangeIndex]) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SegmentedRow(
            options = RANGE_LABELS.take(3),
            selectedIndex = rangeIndex.coerceAtMost(2),
            onSelect = { rangeIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SegmentedRow(
            options = RANGE_LABELS.drop(3),
            selectedIndex = (rangeIndex - 3).coerceAtLeast(-1),
            onSelect = { rangeIndex = it + 3 },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Panel {
            Text("GESAMT", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    money(billing.totalEur),
                    style = MaterialTheme.typography.displayLarge,
                    color = c.onBackground,
                )
                Text(
                    " EUR",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${billing.uptimeSeconds / 3600} h Laufzeit · " +
                    "${money(billing.hourlyRateEur)} EUR/h",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onMuted,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "PRO SPIELER",
            style = MaterialTheme.typography.labelSmall,
            color = c.onMuted,
        )
        Spacer(Modifier.height(12.dp))

        if (billing.perPlayer.isEmpty()) {
            EmptyHint("Keine Sessions im Zeitraum")
        } else {
            billing.perPlayer.forEach { player ->
                Panel(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(LineIcons.Player, tint = c.accent, size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                player.name ?: player.steamId ?: "Unbekannt",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.onBackground,
                            )
                            Text(
                                "${player.sessionSeconds / 3600} h" +
                                    (player.lastIp?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onMuted,
                            )
                        }
                        Text(
                            "${money(player.shareEur)} EUR",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.accent,
                        )
                    }
                }
            }
        }

        if (billing.unattributedEur > 0.005) {
            Spacer(Modifier.height(8.dp))
            Panel {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Nicht zugeordnet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.onBackground,
                        )
                        Text(
                            "Laufzeit ohne Spieler (Boot, Idle)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onMuted,
                        )
                    }
                    Text(
                        "${money(billing.unattributedEur)} EUR",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun money(value: Double) = String.format(Locale.GERMANY, "%.2f", value)
