package de.kevke.servercontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kevke.servercontrol.data.ControlConfig
import de.kevke.servercontrol.data.Settings
import de.kevke.servercontrol.net.ControlClient
import de.kevke.servercontrol.net.UpdateChecker
import de.kevke.servercontrol.ui.theme.AccentPreset
import de.kevke.servercontrol.ui.theme.SurfacePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private var client: ControlClient? = null
    private var pollJob: Job? = null

    private val _config = MutableStateFlow(settings.controlConfig)
    val config: StateFlow<ControlConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow(ControlClient.Reply())
    val status: StateFlow<ControlClient.Reply> = _status.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError.asStateFlow()

    private val _backups = MutableStateFlow<List<ControlClient.Backup>>(emptyList())
    val backups: StateFlow<List<ControlClient.Backup>> = _backups.asStateFlow()

    private val _billing = MutableStateFlow(ControlClient.Billing())
    val billing: StateFlow<ControlClient.Billing> = _billing.asStateFlow()

    private val updates = UpdateChecker(app)

    private val _update = MutableStateFlow<UpdateChecker.Available?>(null)
    val update: StateFlow<UpdateChecker.Available?> = _update.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    val versionLabel: String = BuildConfig.VERSION_LABEL

    private val _surfacePreset = MutableStateFlow(settings.surfacePreset)
    val surfacePreset: StateFlow<SurfacePreset> = _surfacePreset.asStateFlow()

    private val _accentPreset = MutableStateFlow(settings.accentPreset)
    val accentPreset: StateFlow<AccentPreset> = _accentPreset.asStateFlow()

    val isConfigured get() = settings.controlConfig.isUsable

    init {
        if (isConfigured) {
            openClient(settings.controlConfig)
            startPolling()
        }
        checkForUpdate()
    }

    private fun openClient(config: ControlConfig) {
        client?.close()
        client = ControlClient(config.baseUrl, config.token)
    }

    /** Verifies the URL/token pair before storing it, so a typo fails here. */
    fun connect(config: ControlConfig, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _setupError.value = null
            _working.value = true
            openClient(config)
            val reply = runCatching { client!!.status() }.getOrNull()
            _working.value = false

            if (reply == null) {
                _setupError.value = "Keine Antwort — URL pruefen"
                client?.close(); client = null
                return@launch
            }
            if (!reply.ok && reply.message.contains("noe", true)) {
                _setupError.value = "Token abgelehnt"
                client?.close(); client = null
                return@launch
            }

            settings.controlConfig = config
            _config.value = config
            _status.value = reply
            startPolling()
            onSuccess()
        }
    }

    /**
     * Polls faster while something is in motion — a boot takes about three
     * minutes and you want the lamp to flip the moment it is joinable.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                refreshStatus()
                val s = _status.value
                delay(if (s.isBusy || (s.isRunning && !s.gameReady)) 10_000 else 30_000)
            }
        }
    }

    fun refreshStatus() {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { c.status() }
                .onSuccess { _status.value = it }
                // A dropped packet should not blank out a good reading.
                .onFailure { /* keep the last known state */ }
        }
    }

    fun start() {
        val c = client ?: return notify("Nicht eingerichtet")
        viewModelScope.launch {
            _working.value = true
            runCatching { c.start() }
                .onSuccess { notify(it.message); _status.value = it }
                .onFailure { notify(it.message ?: "Start fehlgeschlagen") }
            _working.value = false
            refreshStatus()
        }
    }

    fun stop(force: Boolean = false) {
        val c = client ?: return notify("Nicht eingerichtet")
        viewModelScope.launch {
            _working.value = true
            runCatching { c.stop(force) }
                .onSuccess { notify(it.message) }
                .onFailure { notify(it.message ?: "Stop fehlgeschlagen") }
            _working.value = false
            refreshStatus()
        }
    }

    // ---- backups ---------------------------------------------------------

    fun refreshBackups() {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { c.backups() }
                .onSuccess { _backups.value = it.backups }
                .onFailure { notify(it.message ?: "Backups nicht abrufbar") }
        }
    }

    fun createBackup(label: String?) {
        val c = client ?: return notify("Nicht eingerichtet")
        viewModelScope.launch {
            _working.value = true
            runCatching { c.createBackup(label) }
                .onSuccess { notify(it.message) }
                .onFailure { notify(it.message ?: "Backup fehlgeschlagen") }
            _working.value = false
            refreshBackups()
        }
    }

    fun restoreBackup(name: String) {
        val c = client ?: return notify("Nicht eingerichtet")
        viewModelScope.launch {
            _working.value = true
            notify("Backup wird eingespielt, das dauert ein bis zwei Minuten…")
            runCatching { c.restoreBackup(name) }
                .onSuccess { notify(it.message) }
                .onFailure { notify(it.message ?: "Restore fehlgeschlagen") }
            _working.value = false
            refreshStatus()
        }
    }

    fun deleteBackup(name: String) {
        val c = client ?: return notify("Nicht eingerichtet")
        viewModelScope.launch {
            runCatching { c.deleteBackup(name) }
                .onSuccess { notify(it.message) }
                .onFailure { notify(it.message ?: "Loeschen fehlgeschlagen") }
            refreshBackups()
        }
    }

    // ---- billing ---------------------------------------------------------

    fun refreshBilling(range: String) {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { c.billing(range) }
                .onSuccess { _billing.value = it }
                .onFailure { notify(it.message ?: "Kosten nicht abrufbar") }
        }
    }

    // ---- player aliases --------------------------------------------------

    private val _aliases = MutableStateFlow(settings.aliases)
    val aliases: StateFlow<Map<String, String>> = _aliases.asStateFlow()

    /** Blank clears the override and the log's own name shows again. */
    fun setAlias(steamId: String, name: String) {
        val trimmed = name.trim()
        settings.aliases = if (trimmed.isEmpty()) {
            settings.aliases - steamId
        } else {
            settings.aliases + (steamId to trimmed)
        }
        _aliases.value = settings.aliases
    }

    // ---- self-update -----------------------------------------------------

    fun checkForUpdate(announceWhenCurrent: Boolean = false) {
        viewModelScope.launch {
            val found = updates.check(BuildConfig.BUILD_NUMBER)
            _update.value = found
            if (found == null && announceWhenCurrent) notify("Die App ist aktuell.")
        }
    }

    fun installUpdate() {
        val target = _update.value ?: return
        viewModelScope.launch {
            _downloadProgress.value = 0f
            updates.downloadAndInstall(target) { _downloadProgress.value = it }
                .onFailure { notify(it.message ?: "Download fehlgeschlagen") }
            _downloadProgress.value = null
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    fun notify(text: String) {
        _message.value = text
        viewModelScope.launch {
            delay(5000)
            if (_message.value == text) _message.value = null
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

    override fun onCleared() {
        client?.close()
        updates.close()
        super.onCleared()
    }
}
