package de.kevke.servercontrol

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kevke.servercontrol.data.*
import de.kevke.servercontrol.net.ApiClient
import de.kevke.servercontrol.net.ApiException
import de.kevke.servercontrol.ui.theme.AccentPreset
import de.kevke.servercontrol.ui.theme.SurfacePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private var api: ApiClient? = null
    private var pollJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val _servers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val servers: StateFlow<List<ServerInfo>> = _servers.asStateFlow()

    private val _status = MutableStateFlow(ServerStatus())
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupEntry>>(emptyList())
    val backups: StateFlow<List<BackupEntry>> = _backups.asStateFlow()

    private val _metrics = MutableStateFlow(Metrics())
    val metrics: StateFlow<Metrics> = _metrics.asStateFlow()

    private val _billing = MutableStateFlow(Billing())
    val billing: StateFlow<Billing> = _billing.asStateFlow()

    private val _players = MutableStateFlow<List<OnlinePlayer>>(emptyList())
    val players: StateFlow<List<OnlinePlayer>> = _players.asStateFlow()

    private val _worlds = MutableStateFlow(WorldList())
    val worlds: StateFlow<WorldList> = _worlds.asStateFlow()

    private val _items = MutableStateFlow<List<GameItem>>(emptyList())
    val items: StateFlow<List<GameItem>> = _items.asStateFlow()

    private val _configSchema = MutableStateFlow<List<ConfigField>>(emptyList())
    val configSchema: StateFlow<List<ConfigField>> = _configSchema.asStateFlow()

    private val _configValues = MutableStateFlow<Map<String, Any>>(emptyMap())
    val configValues: StateFlow<Map<String, Any>> = _configValues.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError.asStateFlow()

    private val _surfacePreset = MutableStateFlow(settings.surfacePreset)
    val surfacePreset: StateFlow<SurfacePreset> = _surfacePreset.asStateFlow()

    private val _accentPreset = MutableStateFlow(settings.accentPreset)
    val accentPreset: StateFlow<AccentPreset> = _accentPreset.asStateFlow()

    private var activeServerId: String? = null

    val hasEndpoint get() = settings.activeEndpoint != null
    val activeEndpointLabel get() = settings.activeEndpoint?.label ?: "—"
    val activeEndpointUrl get() = settings.activeEndpoint?.baseUrl ?: "—"
    val activeServerName: String
        get() = _servers.value.firstOrNull { it.id == activeServerId }?.name
            ?: settings.activeEndpoint?.label ?: "Server"

    init {
        settings.activeEndpoint?.let { openClient(it) }
    }

    private fun openClient(endpoint: Endpoint) {
        api?.close()
        api = ApiClient(endpoint)
    }

    fun connect(endpoint: Endpoint, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _setupError.value = null
            openClient(endpoint)
            try {
                val list = api!!.servers()
                settings.addEndpoint(endpoint)
                _servers.value = list.servers
                if (list.servers.size == 1) selectServer(list.servers.first().id)
                onSuccess()
            } catch (e: Exception) {
                _setupError.value = friendly(e)
                api?.close()
                api = null
            }
        }
    }

    fun loadServers() {
        val client = api ?: return
        viewModelScope.launch {
            runCatching { client.servers() }
                .onSuccess { _servers.value = it.servers }
                .onFailure { notify(friendly(it)) }
        }
    }

    fun selectServer(id: String) {
        activeServerId = id
        startPolling()
    }

    /** Home screen keeps a light poll running so the lamp stays honest. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                refreshStatus()
                delay(if (_status.value.isBusy) 5_000 else 15_000)
            }
        }
    }

    fun refreshStatus() {
        val client = api ?: return
        val id = activeServerId ?: return
        viewModelScope.launch {
            runCatching { client.status(id) }
                .onSuccess { _status.value = it }
                .onFailure { /* transient network blips must not clear the UI */ }
        }
    }

    fun startServer() = withServer { client, id ->
        _busyMessage.value = "Server faehrt hoch…"
        runCatching { client.start(id) }
            .onSuccess { notifyAfter("Startet. In ca. 3 Minuten spielbar.") }
            .onFailure { _busyMessage.value = null; notify(friendly(it)) }
        refreshStatus()
    }

    fun stopServer(force: Boolean) = withServer { client, id ->
        _busyMessage.value = "Server faehrt herunter…"
        runCatching { client.stop(id, force) }
            .onSuccess { notifyAfter("Wird gestoppt.") }
            .onFailure { _busyMessage.value = null; notify(friendly(it)) }
        refreshStatus()
    }

    fun refreshBackups() = withServer { client, id ->
        runCatching { client.backups(id) }
            .onSuccess { _backups.value = it.backups }
            .onFailure { notify(friendly(it)) }
    }

    fun createBackup(label: String? = null) = withServer { client, id ->
        _busyMessage.value = "Backup laeuft…"
        runCatching { client.backupCreate(id, label) }
            .onSuccess { awaitJob(client, id, it.job, "Backup erstellt") }
            .onFailure { _busyMessage.value = null; notify(friendly(it)) }
    }

    fun restoreBackup(name: String) = withServer { client, id ->
        _busyMessage.value = "Backup wird eingespielt…"
        runCatching { client.backupRestore(id, name) }
            .onSuccess { awaitJob(client, id, it.job, "Backup eingespielt") }
            .onFailure { _busyMessage.value = null; notify(friendly(it)) }
    }

    fun deleteBackup(name: String) = withServer { client, id ->
        runCatching { client.backupDelete(id, name) }
            .onSuccess { awaitJob(client, id, it.job, "Backup geloescht") }
            .onFailure { notify(friendly(it)) }
    }

    fun refreshMetrics(range: String) = withServer { client, id ->
        runCatching { client.metrics(id, range) }
            .onSuccess { _metrics.value = it }
            .onFailure { notify(friendly(it)) }
    }

    fun refreshBilling(range: String) = withServer { client, id ->
        runCatching { client.billing(id, range) }
            .onSuccess { _billing.value = it }
            .onFailure { notify(friendly(it)) }
    }

    fun refreshPlayers() = withServer { client, id ->
        runCatching { client.players(id) }
            .onSuccess { _players.value = it.online }
            .onFailure { notify(friendly(it)) }
    }

    fun refreshWorlds() = withServer { client, id ->
        runCatching { client.worlds(id) }
            .onSuccess { _worlds.value = it }
            .onFailure { notify(friendly(it)) }
    }

    fun activateWorld(slot: Int) = withServer { client, id ->
        _busyMessage.value = "Welt wird gewechselt…"
        runCatching { client.activateWorld(id, slot) }
            .onSuccess { awaitJob(client, id, it.job, "Welt gewechselt"); refreshWorlds() }
            .onFailure { _busyMessage.value = null; notify(friendly(it)) }
    }

    fun refreshConfig() = withServer { client, id ->
        runCatching { client.config(id) }
            .onSuccess { payload ->
                _configSchema.value = payload.schema
                _configValues.value = flattenConfig(payload.config)
            }
            .onFailure { notify(friendly(it)) }
    }

    fun applyConfig(changes: Map<String, Any>) = withServer { client, id ->
        _busyMessage.value = "Konfiguration wird uebernommen…"
        val payload = buildJsonObject {
            changes.forEach { (key, value) ->
                when (value) {
                    is Boolean -> put(key, value)
                    is Int -> put(key, value)
                    is Double -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        runCatching { client.patchConfig(id, payload, restart = true) }
            .onSuccess { awaitJob(client, id, it.job, "Konfiguration gespeichert"); refreshConfig() }
            .onFailure { _busyMessage.value = null; notify(friendly(it)) }
    }

    fun giveItem(steamId: String, itemId: Int, count: Int) = withServer { client, id ->
        runCatching { client.give(id, steamId, itemId, count) }
            .onSuccess { _lastCommand.value = it.command }
            .onFailure { notify(friendly(it)) }
    }

    fun teleportToPlayer(from: String, to: String) = teleport(from) {
        put("kind", "player"); put("steamId", to)
    }

    fun teleportToXyz(from: String, x: Float, y: Float, z: Float) = teleport(from) {
        put("kind", "xyz"); put("x", x); put("y", y); put("z", z)
    }

    fun teleportToPoi(from: String, poi: String) = teleport(from) {
        put("kind", "poi"); put("poi", poi)
    }

    private fun teleport(from: String, build: JsonObjectBuilder.() -> Unit) =
        withServer { client, id ->
            runCatching { client.teleport(id, from, buildJsonObject(build)) }
                .onSuccess { _lastCommand.value = it.command }
                .onFailure { notify(friendly(it)) }
        }

    /** Item catalogue ships with the app so it works before the server is up. */
    fun loadItems(context: Context) {
        if (_items.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching {
                val text = context.assets.open("sotf_items.json")
                    .bufferedReader().use { it.readText() }
                json.decodeFromString<ItemCatalog>(text).items
            }.onSuccess { _items.value = it }
        }
    }

    fun setSurface(preset: SurfacePreset) {
        settings.surfacePreset = preset
        _surfacePreset.value = preset
    }

    fun setAccent(preset: AccentPreset) {
        settings.accentPreset = preset
        _accentPreset.value = preset
    }

    fun notify(message: String) {
        _busyMessage.value = message
        viewModelScope.launch {
            delay(4000)
            if (_busyMessage.value == message) _busyMessage.value = null
        }
    }

    private fun notifyAfter(message: String) = notify(message)

    private suspend fun awaitJob(
        client: ApiClient,
        serverId: String,
        jobId: String,
        successMessage: String,
    ) {
        repeat(60) {
            delay(2000)
            val job = runCatching { client.job(serverId, jobId) }.getOrNull() ?: return@repeat
            if (job.isTerminal) {
                _busyMessage.value = null
                notify(if (job.state == "done") successMessage else "Fehlgeschlagen: ${job.error}")
                refreshBackups()
                return
            }
        }
        _busyMessage.value = null
        notify("Zeitueberschreitung — bitte Status pruefen")
    }

    private fun withServer(block: suspend (ApiClient, String) -> Unit) {
        val client = api ?: return notify("Kein Backend verbunden")
        val id = activeServerId ?: return notify("Kein Server ausgewaehlt")
        viewModelScope.launch { block(client, id) }
    }

    /** Turn nested cfg JSON into the dotted keys the schema speaks. */
    private fun flattenConfig(element: JsonElement?): Map<String, Any> {
        val obj = element as? JsonObject ?: return emptyMap()
        val out = mutableMapOf<String, Any>()
        obj.forEach { (key, value) ->
            when (value) {
                is JsonPrimitive -> out[key] = value.toKotlin()
                is JsonObject -> value.forEach { (inner, innerValue) ->
                    (innerValue as? JsonPrimitive)?.let { out["$key.$inner"] = it.toKotlin() }
                }
                else -> Unit
            }
        }
        return out
    }

    private fun JsonPrimitive.toKotlin(): Any =
        booleanOrNull ?: intOrNull ?: doubleOrNull ?: content

    private fun friendly(e: Throwable): String = when {
        e is ApiException && e.slug == "unauthorized" -> "Token abgelehnt"
        e is ApiException && e.slug == "players_online" -> e.detail
        e is ApiException -> "${e.slug}: ${e.detail}"
        else -> e.message ?: "Netzwerkfehler"
    }

    override fun onCleared() {
        api?.close()
        super.onCleared()
    }
}
