package de.kevke.servercontrol.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kevke.servercontrol.AppViewModel
import de.kevke.servercontrol.ui.components.MetricTile
import de.kevke.servercontrol.ui.components.SegmentedRow

private val RANGES = listOf("15m", "1h", "6h", "24h", "7d")

@Composable
fun ResourcesScreen(vm: AppViewModel) {
    val metrics by vm.metrics.collectAsState()
    var rangeIndex by remember { mutableIntStateOf(1) }

    LaunchedEffect(rangeIndex) { vm.refreshMetrics(RANGES[rangeIndex]) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SegmentedRow(
            options = RANGES,
            selectedIndex = rangeIndex,
            onSelect = { rangeIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        MetricTile(
            label = "CPU",
            percent = metrics.current.cpuPercent,
            detail = "Container-Auslastung",
            history = metrics.series.map { it.cpu },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        MetricTile(
            label = "Arbeitsspeicher",
            percent = metrics.current.memPercent,
            detail = metrics.current.let { cur ->
                if (cur.memUsedMb != null && cur.memTotalMb != null)
                    "${cur.memUsedMb} / ${cur.memTotalMb} MB" else null
            },
            history = metrics.series.map { it.mem },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(32.dp))
    }
}
