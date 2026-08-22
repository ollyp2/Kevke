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

        Spacer(Modifier.height(20.dp))

        Panel {
            Text(
                "Gerechnet wird Laufzeit mal Stundensatz. Die Platte kostet " +
                    "zusaetzlich rund 1,60 EUR im Monat, auch wenn der Server aus ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onMuted,
            )
            if (RANGE_KEYS[rangeIndex] in listOf("quarter", "year")) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fuer lange Zeitraeume ist die Zahl zu niedrig: die " +
                        "Ereignisse kommen aus dem Log, das nur 30 Tage " +
                        "zurueckreicht.",
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
