package de.kevke.servercontrol.net

import de.kevke.servercontrol.data.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApiException(val slug: String, val detail: String, val code: Int) :
    Exception("$slug: $detail")

/**
 * Thin wrapper over the sotf-control function. One instance per endpoint —
 * swapping servers means swapping the client, which keeps base URL and token
 * from ever drifting apart.
 */
class ApiClient(private val endpoint: Endpoint) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val http = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        engine {
            connectTimeout = 15_000
            socketTimeout = 30_000
        }
    }

    private fun url(path: String) =
        endpoint.baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private suspend fun check(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        val raw = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString<ApiError>(raw) }.getOrNull()
        throw ApiException(
            parsed?.error ?: "http_${response.status.value}",
            parsed?.detail ?: raw.take(300),
            response.status.value,
        )
    }

    private suspend inline fun <reified T> getJson(path: String): T =
        check(http.get(url(path)) {
            header("Authorization", "Bearer ${endpoint.token}")
        }).body()

    private suspend inline fun <reified T> postJson(path: String, payload: JsonObject): T =
        check(http.post(url(path)) {
            header("Authorization", "Bearer ${endpoint.token}")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }).body()

    // ---- server ----------------------------------------------------------

    suspend fun servers(): ServerList = getJson("/servers")

    suspend fun status(id: String): ServerStatus = getJson("/server/status?id=$id")

    suspend fun start(id: String): ServerStatus =
        postJson("/server/start", buildJsonObject { put("id", id) })

    suspend fun stop(id: String, force: Boolean = false): ServerStatus =
        postJson("/server/stop", buildJsonObject {
            put("id", id); put("force", force)
        })

    // ---- backups ---------------------------------------------------------

    suspend fun backups(id: String): BackupList = getJson("/backups?id=$id")

    suspend fun backupCreate(id: String, label: String?): JobRef =
        postJson("/backups/create", buildJsonObject {
            put("id", id); label?.let { put("label", it) }
        })

    suspend fun backupRestore(id: String, name: String): JobRef =
        postJson("/backups/restore", buildJsonObject {
            put("id", id); put("name", name)
        })

    suspend fun backupDelete(id: String, name: String): JobRef =
        postJson("/backups/delete", buildJsonObject {
            put("id", id); put("name", name); put("confirm", true)
        })

    // ---- metrics & billing ----------------------------------------------

    suspend fun metrics(id: String, range: String): Metrics =
        getJson("/metrics?id=$id&range=$range")

    suspend fun billing(id: String, range: String, from: String? = null, to: String? = null):
        Billing {
        val extra = if (range == "custom" && from != null && to != null)
            "&from=$from&to=$to" else ""
        return getJson("/billing?id=$id&range=$range$extra")
    }

    // ---- jobs ------------------------------------------------------------

    suspend fun job(id: String, jobId: String): JobStatus =
        getJson("/jobs/$jobId?id=$id")

    // ---- sons of the forest ---------------------------------------------

    suspend fun worlds(id: String): WorldList = getJson("/sotf/worlds?id=$id")

    suspend fun activateWorld(id: String, slot: Int): JobRef =
        postJson("/sotf/worlds/activate", buildJsonObject {
            put("id", id); put("slot", slot)
        })

    suspend fun players(id: String): PlayerList = getJson("/sotf/players?id=$id")

    suspend fun config(id: String): ConfigPayload = getJson("/sotf/config?id=$id")

    suspend fun patchConfig(id: String, changes: JsonObject, restart: Boolean): JobRef =
        postJson("/sotf/config", buildJsonObject {
            put("id", id)
            put("changes", changes)
            put("restart", restart)
        })

    suspend fun give(id: String, steamId: String, itemId: Int, count: Int): ManualCommand =
        postJson("/sotf/give", buildJsonObject {
            put("id", id); put("steamId", steamId)
            put("itemId", itemId); put("count", count)
        })

    suspend fun teleport(id: String, steamId: String, target: JsonObject): ManualCommand =
        postJson("/sotf/teleport", buildJsonObject {
            put("id", id); put("steamId", steamId); put("target", target)
        })

    fun close() = http.close()
}
