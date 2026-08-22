package de.kevke.servercontrol.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerInfo(
    val id: String,
    val name: String,
    val game: String = "unknown",
    val zone: String? = null,
    val instance: String? = null,
    val logo: String = "generic",
)

@Serializable
data class ServerList(val servers: List<ServerInfo> = emptyList())

@Serializable
data class ServerStatus(
    val id: String = "",
    val state: String = "UNKNOWN",
    val externalIp: String? = null,
    val lastStart: String? = null,
    val lastStop: String? = null,
    val uptimeSeconds: Long? = null,
    val gameReady: Boolean = false,
    val playersOnline: Int = 0,
    val idleShutdownIn: Long? = null,
) {
    val isRunning get() = state == "RUNNING"
    val isBusy get() = state == "STAGING" || state == "STOPPING"
}

@Serializable
data class BackupEntry(
    val name: String,
    val createdAt: String? = null,
    val sizeBytes: Long? = null,
    val slot: Int? = null,
    val worldName: String? = null,
    val gameDays: Int? = null,
    val source: String = "manual",
)

@Serializable
data class BackupList(val backups: List<BackupEntry> = emptyList())

@Serializable
data class MetricPoint(val t: String, val cpu: Float, val mem: Float)

@Serializable
data class MetricCurrent(
    val cpuPercent: Float? = null,
    val memPercent: Float? = null,
    val memUsedMb: Long? = null,
    val memTotalMb: Long? = null,
)

@Serializable
data class Metrics(
    val current: MetricCurrent = MetricCurrent(),
    val series: List<MetricPoint> = emptyList(),
)

@Serializable
data class PlayerBilling(
    val steamId: String? = null,
    val name: String? = null,
    val lastIp: String? = null,
    val sessionSeconds: Long = 0,
    val shareEur: Double = 0.0,
)

@Serializable
data class BillingRange(val from: String, val to: String)

@Serializable
data class Billing(
    val range: BillingRange? = null,
    val totalEur: Double = 0.0,
    val uptimeSeconds: Long = 0,
    val hourlyRateEur: Double = 0.0,
    val perPlayer: List<PlayerBilling> = emptyList(),
    val unattributedEur: Double = 0.0,
)

@Serializable
data class OnlinePlayer(
    val steamId: String? = null,
    val name: String? = null,
    val ip: String? = null,
    val clientId: Int? = null,
    val connectedAt: String? = null,
)

@Serializable
data class PlayerList(val online: List<OnlinePlayer> = emptyList())

@Serializable
data class WorldSlot(
    val slot: Int,
    val worldName: String? = null,
    val gameDays: Int? = null,
    val sizeBytes: Long? = null,
    val lastSaved: String? = null,
)

@Serializable
data class WorldList(
    val activeSlot: Int? = null,
    val slots: List<WorldSlot> = emptyList(),
)

@Serializable
data class ConfigField(
    val key: String,
    val type: String,
    val label: String,
    val hint: String? = null,
    val values: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val secret: Boolean = false,
    val appliesToExistingWorld: Boolean = true,
)

@Serializable
data class ConfigPayload(
    val config: JsonElement? = null,
    val schema: List<ConfigField> = emptyList(),
)

@Serializable
data class JobRef(val job: String)

@Serializable
data class JobStatus(
    val id: String = "",
    val kind: String? = null,
    val state: String = "queued",
    val createdAt: String? = null,
    val finishedAt: String? = null,
    val error: String? = null,
) {
    val isTerminal get() = state == "done" || state == "failed"
}

@Serializable
data class ManualCommand(
    val mode: String = "manual",
    val command: String = "",
    val hint: String = "",
)

@Serializable
data class GameItem(
    val id: Int,
    val name: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class ItemCatalog(val items: List<GameItem> = emptyList())

@Serializable
data class ApiError(
    val error: String = "unknown",
    val detail: String = "",
)

/** A remembered backend: base URL + token. Multiple allowed. */
@Serializable
data class Endpoint(
    @SerialName("label") val label: String,
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("token") val token: String,
)
