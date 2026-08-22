package de.kevke.servercontrol.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kevke.servercontrol.AppViewModel
import de.kevke.servercontrol.data.ControlConfig
import de.kevke.servercontrol.ui.components.*
import de.kevke.servercontrol.ui.theme.AccentPreset
import de.kevke.servercontrol.ui.theme.CornerRadius
import de.kevke.servercontrol.ui.theme.LineIcons
import de.kevke.servercontrol.ui.theme.LocalAppColors
import de.kevke.servercontrol.ui.theme.SurfacePreset

/**
 * Two fields and you are done: the function's URL and its token. The app
 * checks them by asking for status before it stores anything.
 */
@Composable
fun SetupScreen(vm: AppViewModel, onDone: () -> Unit) {
    val c = LocalAppColors.current
    val stored by vm.config.collectAsState()
    val error by vm.setupError.collectAsState()
    val working by vm.working.collectAsState()

    var label by remember { mutableStateOf(stored.label) }
    var baseUrl by remember { mutableStateOf(stored.baseUrl) }
    var token by remember { mutableStateOf(stored.token) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(LineIcons.Forest, tint = c.accent, size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Server verbinden",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onBackground,
                )
                Text(
                    "Function-URL und Token",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onMuted,
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        FieldLabel("NAME")
        TextField(label, { label = it }, "SotF")
        Spacer(Modifier.height(20.dp))

        FieldLabel("FUNCTION-URL")
        TextField(baseUrl, { baseUrl = it }, "https://europe-west3-….cloudfunctions.net/sotf-control")
        Hint("Ohne ?token=… — nur die Adresse bis zum Funktionsnamen.")
        Spacer(Modifier.height(20.dp))

        FieldLabel("TOKEN")
        TextField(token, { token = it }, "der Wert hinter ?token=")

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = c.danger)
        }

        Spacer(Modifier.height(28.dp))
        LineButton(
            label = if (working) "Pruefe…" else "Verbinden",
            modifier = Modifier.fillMaxWidth(),
            enabled = !working && baseUrl.isNotBlank() && token.isNotBlank(),
            onClick = {
                vm.connect(
                    ControlConfig(
                        label = label.ifBlank { "Server" },
                        baseUrl = baseUrl.trim().removeSuffix("?").trimEnd('&'),
                        token = token.trim(),
                    ),
                    onDone,
                )
            },
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val surface by vm.surfacePreset.collectAsState()
    val accent by vm.accentPreset.collectAsState()
    val config by vm.config.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("HINTERGRUND", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(10.dp))
        SurfacePreset.entries.chunked(3).forEach { row ->
            SegmentedRow(
                options = row.map { it.label },
                selectedIndex = row.indexOf(surface),
                onSelect = { vm.setSurface(row[it]) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
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
            Text(config.label, style = MaterialTheme.typography.bodyLarge,
                 color = c.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(config.baseUrl, style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalAppColors.current.onMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
fun TextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean = false,
) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius))
            .border(1.dp, c.outline, RoundedCornerShape(CornerRadius))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.onMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onBackground),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
