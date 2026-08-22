package de.kevke.servercontrol.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kevke.servercontrol.AppViewModel
import de.kevke.servercontrol.data.ConfigField
import de.kevke.servercontrol.ui.components.*
import de.kevke.servercontrol.ui.theme.CornerRadius
import de.kevke.servercontrol.ui.theme.LineIcons
import de.kevke.servercontrol.ui.theme.LocalAppColors

// --------------------------------------------------------------------------
// Worlds
// --------------------------------------------------------------------------

@Composable
fun WorldsScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val worlds by vm.worlds.collectAsState()
    var pendingSlot by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { vm.refreshWorlds() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(
            "Aktiver Slot: ${worlds.activeSlot ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onMuted,
        )
        Spacer(Modifier.height(16.dp))

        if (worlds.slots.isEmpty()) {
            EmptyHint("Keine Welten gefunden")
        } else {
            worlds.slots.forEach { slot ->
                val active = slot.slot == worlds.activeSlot
                Panel(Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(
                            LineIcons.World,
                            tint = if (active) c.ok else c.onMuted,
                            size = 22.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                slot.worldName ?: "Slot ${slot.slot}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.onBackground,
                            )
                            Text(
                                buildString {
                                    append("Slot ${slot.slot}")
                                    slot.gameDays?.let { append(" · Tag $it") }
                                    slot.sizeBytes?.let { append(" · ${it / 1024} KB") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onMuted,
                            )
                        }
                        if (active) {
                            Text(
                                "AKTIV",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.ok,
                            )
                        } else {
                            LineButton(
                                label = "Laden",
                                onClick = { pendingSlot = slot.slot },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LineButton(
            label = "Welt hochladen",
            icon = LineIcons.Upload,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.notify("Upload: ZIP ueber die Cloud Console in den Bucket legen, dann hier laden.") },
        )
        Spacer(Modifier.height(32.dp))
    }

    pendingSlot?.let { slot ->
        ConfirmDialog(
            title = "Welt wechseln?",
            message = "Der Server startet neu und laedt Slot $slot. " +
                "Vorher wird automatisch gesichert.",
            confirmLabel = "Wechseln",
            onConfirm = { vm.activateWorld(slot); pendingSlot = null },
            onDismiss = { pendingSlot = null },
        )
    }
}

// --------------------------------------------------------------------------
// Give item
// --------------------------------------------------------------------------

@Composable
fun GiveItemScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val players by vm.players.collectAsState()
    val items by vm.items.collectAsState()
    val lastCommand by vm.lastCommand.collectAsState()

    var selectedSteamId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshPlayers(); vm.loadItems(context) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("SPIELER", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(8.dp))

        if (players.isEmpty()) {
            EmptyHint("Niemand online")
        } else {
            players.forEach { player ->
                val selected = player.steamId == selectedSteamId
                Panel(Modifier.padding(bottom = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickableNoRipple {
                            selectedSteamId = player.steamId
                        },
                    ) {
                        LineIcon(
                            LineIcons.Player,
                            tint = if (selected) c.accent else c.onMuted,
                            size = 20.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                player.name ?: "Unbekannt",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) c.accent else c.onBackground,
                            )
                            Text(
                                player.steamId ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("ITEM", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(8.dp))

        SearchField(query, { query = it }, "Item suchen")
        Spacer(Modifier.height(12.dp))

        val filtered = remember(query, items) {
            if (query.isBlank()) items
            else items.filter { item ->
                item.name.contains(query, true) ||
                    item.id.toString() == query ||
                    item.tags.any { it.contains(query, true) }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(filtered) { item ->
                Panel(Modifier.padding(bottom = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickableNoRipple {
                            selectedSteamId?.let { vm.giveItem(it, item.id, 1) }
                                ?: vm.notify("Erst einen Spieler auswaehlen")
                        },
                    ) {
                        LineIcon(LineIcons.Item, tint = c.accent, size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.onBackground,
                            )
                            Text(
                                "#${item.id} · ${item.tags.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onMuted,
                            )
                        }
                    }
                }
            }
        }

        lastCommand?.let { cmd -> CommandStrip(cmd, context) }
    }
}

// --------------------------------------------------------------------------
// Teleport
// --------------------------------------------------------------------------

@Composable
fun TeleportScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val players by vm.players.collectAsState()
    val lastCommand by vm.lastCommand.collectAsState()

    var mode by remember { mutableIntStateOf(0) }
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var z by remember { mutableStateOf("") }
    var poi by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshPlayers() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SegmentedRow(
            options = listOf("Zu Spieler", "Zu XYZ", "Zu POI"),
            selectedIndex = mode,
            onSelect = { mode = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Text("WER", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(8.dp))
        PlayerPicker(players.map { it.steamId to (it.name ?: "?") }, fromId) { fromId = it }

        Spacer(Modifier.height(20.dp))
        Text("WOHIN", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(8.dp))

        when (mode) {
            0 -> PlayerPicker(players.map { it.steamId to (it.name ?: "?") }, toId) { toId = it }
            1 -> Row {
                CoordField("X", x, { x = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CoordField("Y", y, { y = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CoordField("Z", z, { z = it }, Modifier.weight(1f))
            }
            2 -> SearchField(poi, { poi = it }, "POI-Name, z.B. cave-1")
        }

        Spacer(Modifier.height(24.dp))
        LineButton(
            label = "Teleport-Befehl erzeugen",
            icon = LineIcons.Teleport,
            modifier = Modifier.fillMaxWidth(),
            enabled = fromId != null,
            onClick = {
                val from = fromId ?: return@LineButton
                when (mode) {
                    0 -> toId?.let { vm.teleportToPlayer(from, it) }
                    1 -> vm.teleportToXyz(
                        from,
                        x.toFloatOrNull() ?: 0f,
                        y.toFloatOrNull() ?: 0f,
                        z.toFloatOrNull() ?: 0f,
                    )
                    2 -> vm.teleportToPoi(from, poi)
                }
            },
        )

        Spacer(Modifier.height(16.dp))
        lastCommand?.let { cmd -> CommandStrip(cmd, context) }
        Spacer(Modifier.height(32.dp))
    }
}

// --------------------------------------------------------------------------
// Config
// --------------------------------------------------------------------------

@Composable
fun ConfigScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val schema by vm.configSchema.collectAsState()
    val values by vm.configValues.collectAsState()
    var dirty by remember { mutableStateOf(mapOf<String, Any>()) }
    var confirmApply by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshConfig() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        if (schema.isEmpty()) {
            EmptyHint("Konfiguration wird geladen…")
        }

        schema.forEach { field ->
            val current = dirty[field.key] ?: values[field.key]
            ConfigRow(field, current) { newValue ->
                dirty = dirty + (field.key to newValue)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (dirty.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LineButton(
                label = "${dirty.size} Aenderung(en) speichern",
                icon = LineIcons.Save,
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmApply = true },
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmApply) {
        val needsNewWorld = dirty.keys.any { key ->
            schema.firstOrNull { it.key == key }?.appliesToExistingWorld == false
        }
        ConfirmDialog(
            title = "Uebernehmen?",
            message = buildString {
                append("Der Server startet neu. Savegames bleiben erhalten.")
                if (needsNewWorld) {
                    append("\n\nAchtung: Mindestens eine Einstellung greift laut Spiel ")
                    append("erst bei einer neu erstellten Welt.")
                }
            },
            confirmLabel = "Speichern",
            onConfirm = {
                vm.applyConfig(dirty)
                dirty = emptyMap()
                confirmApply = false
            },
            onDismiss = { confirmApply = false },
        )
    }
}

@Composable
private fun ConfigRow(field: ConfigField, current: Any?, onChange: (Any) -> Unit) {
    val c = LocalAppColors.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(field.label, style = MaterialTheme.typography.bodyLarge,
                     color = c.onBackground)
                field.hint?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onMuted)
                }
                if (!field.appliesToExistingWorld) {
                    Text(
                        "greift erst bei neuer Welt",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.warn,
                    )
                }
            }
            when (field.type) {
                "bool" -> Switch(
                    checked = current as? Boolean ?: false,
                    onCheckedChange = { onChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.accent,
                        checkedTrackColor = c.accent.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        if (field.type == "enum") {
            Spacer(Modifier.height(10.dp))
            SegmentedRow(
                options = field.values,
                selectedIndex = field.values.indexOf(current as? String ?: ""),
                onSelect = { onChange(field.values[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (field.type == "int" || field.type == "string") {
            Spacer(Modifier.height(10.dp))
            SearchField(
                value = current?.toString() ?: "",
                onChange = { text ->
                    if (field.type == "int") text.toIntOrNull()?.let(onChange)
                    else onChange(text)
                },
                placeholder = field.label,
                numeric = field.type == "int",
            )
        }
    }
}

// --------------------------------------------------------------------------
// shared bits
// --------------------------------------------------------------------------

@Composable
private fun PlayerPicker(
    entries: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val c = LocalAppColors.current
    if (entries.isEmpty()) {
        EmptyHint("Niemand online")
        return
    }
    entries.forEach { (steamId, name) ->
        val isSelected = steamId == selected
        Panel(Modifier.padding(bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableNoRipple { steamId?.let(onSelect) },
            ) {
                LineIcon(
                    LineIcons.Player,
                    tint = if (isSelected) c.accent else c.onMuted,
                    size = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) c.accent else c.onBackground,
                )
            }
        }
    }
}

@Composable
private fun CoordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.onMuted,
        )
        Spacer(Modifier.height(4.dp))
        SearchField(value, onChange, label, numeric = true)
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean = false,
) {
    val c = LocalAppColors.current
    androidx.compose.foundation.layout.Box(
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
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shows a console command with a copy button — the fallback while the
 *  dedicated server has no remote console channel. */
@Composable
private fun CommandStrip(command: String, context: Context) {
    val c = LocalAppColors.current
    Panel {
        Text("KONSOLENBEFEHL", style = MaterialTheme.typography.labelSmall, color = c.onMuted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                command,
                style = MaterialTheme.typography.bodyLarge,
                color = c.accent,
                modifier = Modifier.weight(1f),
            )
            LineIcon(
                LineIcons.Copy,
                tint = c.onMuted,
                size = 20.dp,
                modifier = Modifier.clickableNoRipple {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("command", command))
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Im Spiel F1 druecken und einfuegen.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onMuted,
        )
    }
}
