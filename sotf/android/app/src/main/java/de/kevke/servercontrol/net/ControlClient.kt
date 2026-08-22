package de.kevke.servercontrol.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to the sotf-control function the same way a browser would: one base
 * URL, a token in the query string, an `action` parameter. Nothing else is
 * needed to run the server from the phone.
 */
class ControlClient(private val baseUrl: String, private val token: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(Android) {
        engine {
            connectTimeout = 15_000
            socketTimeout = 30_000
        }
    }

    @Serializable
    data class Reply(
        val ok: Boolean = false,
        val state: String = "UNKNOWN",
        val message: String = "",
        val externalIp: String? = null,
        val lastStart: String? = null,
        val lastStop: String? = null,
        val uptimeSeconds: Long? = null,
        val gameReady: Boolean = false,
        val players: Int = 0,
        val maxPlayers: Int = 0,
        val serverName: String? = null,
    ) {
        val isRunning get() = state == "RUNNING"
        val isBusy get() = state == "STAGING" || state == "STOPPING"

        /** RUNNING but not yet joinable is still "starting" to a player. */
        val lampState: String
            get() = if (state == "RUNNING" && !gameReady) "STAGING" else state
    }

    private fun url(action: String, extra: String = "") =
        baseUrl.trimEnd('/') + "?token=$token&action=$action&format=json$extra"

    private suspend fun call(action: String, extra: String = ""): Reply =
        withContext(Dispatchers.IO) {
            val response = http.get(url(action, extra)) {
                header("Accept", "application/json")
            }
            val body = response.bodyAsText()
            runCatching { json.decodeFromString<Reply>(body) }.getOrElse {
                Reply(ok = false, message = body.take(200).ifBlank { "Leere Antwort" })
            }
        }

    suspend fun status(): Reply = call("status")

    suspend fun start(): Reply = call("start")

    suspend fun stop(force: Boolean = false): Reply =
        call("stop", if (force) "&force=1" else "")

    // ---- backups ---------------------------------------------------------

    @Serializable
    data class Backup(
        val name: String,
        val createdAt: String? = null,
        val status: String? = null,
        val sizeGb: Int = 0,
        val diskSizeGb: Int = 0,
        val label: String = "",
    ) {
        val isReady get() = status == "READY"
    }

    @Serializable
    data class BackupList(
        val ok: Boolean = false,
        val message: String = "",
        val backups: List<Backup> = emptyList(),
    )

    suspend fun backups(): BackupList = withContext(Dispatchers.IO) {
        val body = http.get(url("backups")) { header("Accept", "application/json") }
            .bodyAsText()
        runCatching { json.decodeFromString<BackupList>(body) }
            .getOrElse { BackupList(message = body.take(200)) }
    }

    suspend fun createBackup(label: String?): Reply =
        call("backup", label?.takeIf { it.isNotBlank() }?.let { "&label=$it" } ?: "")

    suspend fun restoreBackup(name: String): Reply = call("restore", "&name=$name")

    suspend fun deleteBackup(name: String): Reply =
        call("delete_backup", "&name=$name")

    // ---- billing ---------------------------------------------------------

    @Serializable
    data class Billing(
        val ok: Boolean = false,
        val message: String = "",
        val range: String = "month",
        val from: String? = null,
        val to: String? = null,
        val uptimeSeconds: Long = 0,
        val hourlyRateEur: Double = 0.0,
        val totalEur: Double = 0.0,
        val events: Int = 0,
    )

    suspend fun billing(range: String): Billing = withContext(Dispatchers.IO) {
        val body = http.get(url("billing", "&range=$range")) {
            header("Accept", "application/json")
        }.bodyAsText()
        runCatching { json.decodeFromString<Billing>(body) }
            .getOrElse { Billing(message = body.take(200)) }
    }

    fun close() = http.close()
}
