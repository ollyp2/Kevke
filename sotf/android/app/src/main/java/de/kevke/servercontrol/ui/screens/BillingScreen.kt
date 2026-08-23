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
import de.kevke.servercontrol.ui.components.Panel
import de.kevke.servercontrol.ui.components.SegmentedRow
import de.kevke.servercontrol.ui.theme.LocalAppColors
import java.util.Locale

private val RANGE_KEYS = listOf("day", "week", "month", "quarter", "year")
private val RANGE_LABELS = listOf("Tag", "Woche", "Monat", "Quartal", "Jahr")

@Composable
fun BillingScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val billing by vm.billing.collectAsState()
    var rangeIndex by remember { mutableIntStateOf(2) }

    LaunchedEffect(rangeIndex) { vm.refreshBilling(RANGE_KEYS[rangeIndex]) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
            selectedIndex = rangeIndex - 3,
            onSelect = { rangeIndex = it + 3 },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Panel {
            Text("KOSTEN", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
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
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Laufzeit", style = MaterialTheme.typography.bodyMedium,
                     color = c.onMuted, modifier = Modifier.weight(1f))
                Text(duration(billing.uptimeSeconds),
                     style = MaterialTheme.typography.bodyMedium, color = c.onBackground)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Stundensatz", style = MaterialTheme.typography.bodyMedium,
                     color = c.onMuted, modifier = Modifier.weight(1f))
                Text("${money(billing.hourlyRateEur)} EUR/h",
                     style = MaterialTheme.typography.bodyMedium, color = c.onBackground)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("PRO SPIELER", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(12.dp))

        if (billing.perPlayer.isEmpty()) {
            Panel {
                Text(
                    "Noch keine Messwerte. Die Aufteilung braucht Stichproben, " +
                        "die alle paar Minuten festhalten, wer online ist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onMuted,
                )
            }
        } else {
            billing.perPlayer.forEach { player ->
                Panel(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(player.name, style = MaterialTheme.typography.bodyLarge,
                                 color = c.onBackground)
                            Text(duration(player.seconds),
                                 style = MaterialTheme.typography.bodyMedium,
                                 color = c.onMuted)
                        }
                        Text("${money(player.eur)} EUR",
                             style = MaterialTheme.typography.titleMedium, color = c.accent)
                    }
                    if (player.buckets.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        // One line per crowd size, so it is visible where a
                        // share came from: alone costs full rate, two split it.
                        player.buckets.entries
                            .sortedBy { it.key.toIntOrNull() ?: 0 }
                            .forEach { (crowd, seconds) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text(
                                        when (crowd) {
                                            "1" -> "allein"
                                            "2" -> "zu zweit"
                                            "3" -> "zu dritt"
                                            else -> "zu $crowd"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.onMuted,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(duration(seconds),
                                         style = MaterialTheme.typography.bodyMedium,
                                         color = c.onMuted)
                                }
                            }
                    }
                }
            }

            if (billing.unattributedEur > 0.005) {
                Panel(Modifier.padding(bottom = 10.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Niemand drauf",
                                 style = MaterialTheme.typography.bodyLarge,
                                 color = c.onBackground)
                            Text("Hochfahren und Leerlauf bis zum Auto-Aus",
                                 style = MaterialTheme.typography.bodyMedium,
                                 color = c.onMuted)
                        }
                        Text("${money(billing.unattributedEur)} EUR",
                             style = MaterialTheme.typography.titleMedium, color = c.onMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Panel {
            Text(
                "Die Laufzeit ist von Google gemessen, nicht geschaetzt. " +
                    "Der Betrag ist Laufzeit mal Stundensatz — die Platte " +
                    "kostet zusaetzlich rund 1,60 EUR im Monat, auch wenn " +
                    "der Server aus ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onMuted,
            )
            if (RANGE_KEYS[rangeIndex] in listOf("quarter", "year")) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fuer lange Zeitraeume faellt die Zahl zu niedrig aus: " +
                        "Messwerte werden nur sechs Wochen vorgehalten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.warn,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun money(value: Double) = String.format(Locale.GERMANY, "%.2f", value)

private fun duration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
