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
import de.kevke.servercontrol.data.Endpoint
import de.kevke.servercontrol.ui.components.*
import de.kevke.servercontrol.ui.theme.AccentPreset
import de.kevke.servercontrol.ui.theme.LineIcons
import de.kevke.servercontrol.ui.theme.LocalAppColors
import de.kevke.servercontrol.ui.theme.SurfacePreset

/** First run: point the app at a backend. Also reachable later from the menu. */
@Composable
fun SetupScreen(vm: AppViewModel, onDone: () -> Unit) {
    val c = LocalAppColors.current
    var label by remember { mutableStateOf("Kevkes SotF") }
    var baseUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val error by vm.setupError.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        LineIcon(LineIcons.Server, tint = c.accent, size = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text("Server Control", style = MaterialTheme.typography.titleLarge, color = c.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            "Backend verbinden",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onMuted,
        )

        Spacer(Modifier.height(40.dp))

        FieldLabel("NAME")
        SearchField(label, { label = it }, "z.B. Kevkes SotF")
        Spacer(Modifier.height(16.dp))

        FieldLabel("BACKEND-URL")
        SearchField(baseUrl, { baseUrl = it }, "https://…cloudfunctions.net/sotf-control")
        Spacer(Modifier.height(16.dp))

        FieldLabel("API-TOKEN")
        SearchField(token, { token = it }, "Bearer-Token")

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger)
        }

        Spacer(Modifier.height(28.dp))
        LineButton(
            label = "Verbinden",
            modifier = Modifier.fillMaxWidth(),
            enabled = baseUrl.isNotBlank() && token.isNotBlank(),
            onClick = {
                vm.connect(
                    Endpoint(label.ifBlank { "Server" }, baseUrl.trim(), token.trim()),
                    onDone,
                )
            },
        )
        Spacer(Modifier.height(40.dp))
    }
}

/** Server picker shown when more than one endpoint is stored. */
@Composable
fun ServerPickerScreen(vm: AppViewModel, onPicked: () -> Unit, onAddNew: () -> Unit) {
    val c = LocalAppColors.current
    val servers by vm.servers.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Server waehlen", style = MaterialTheme.typography.titleLarge, color = c.onBackground)
        Spacer(Modifier.height(24.dp))

        servers.forEach { server ->
            Panel(Modifier.padding(bottom = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickableNoRipple {
                        vm.selectServer(server.id)
                        onPicked()
                    },
                ) {
                    LineIcon(
                        if (server.game == "sons-of-the-forest") LineIcons.Forest
                        else LineIcons.Server,
                        tint = c.accent,
                        size = 28.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.bodyLarge,
                             color = c.onBackground)
                        Text(server.game, style = MaterialTheme.typography.bodyMedium,
                             color = c.onMuted)
                    }
                    LineIcon(LineIcons.ChevronRight, tint = c.onMuted, size = 20.dp)
                }
            }
        }

        if (servers.isEmpty()) EmptyHint("Keine Server im Backend registriert")

        Spacer(Modifier.height(16.dp))
        LineButton(
            label = "Anderes Backend",
            icon = LineIcons.Plus,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAddNew,
        )
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val surface by vm.surfacePreset.collectAsState()
    val accent by vm.accentPreset.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("HINTERGRUND", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(10.dp))
        SegmentedRow(
            options = SurfacePreset.entries.map { it.label },
            selectedIndex = SurfacePreset.entries.indexOf(surface),
            onSelect = { vm.setSurface(SurfacePreset.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Text("AKZENT", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(10.dp))
        AccentPreset.entries.chunked(3).forEach { row ->
            SegmentedRow(
                options = row.map { it.label },
                selectedIndex = row.indexOf(accent),
                onSelect = { vm.setAccent(row[it]) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("VERBINDUNG", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(10.dp))
        Panel {
            Text(vm.activeEndpointLabel, style = MaterialTheme.typography.bodyLarge,
                 color = c.onBackground)
            Text(vm.activeEndpointUrl, style = MaterialTheme.typography.bodyMedium,
                 color = c.onMuted)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.onMuted,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}
