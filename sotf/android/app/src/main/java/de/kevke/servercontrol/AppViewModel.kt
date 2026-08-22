package de.kevke.servercontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kevke.servercontrol.data.ControlConfig
import de.kevke.servercontrol.data.Settings
import de.kevke.servercontrol.net.ControlClient
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
        super.onCleared()
    }
}
