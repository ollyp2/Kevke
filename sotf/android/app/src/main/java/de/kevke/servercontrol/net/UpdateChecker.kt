package de.kevke.servercontrol.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FilterOutputStream

/**
 * Self-update straight from the repo's GitHub releases.
 *
 * The repo is public, so the release JSON and the APK asset are plain
 * unauthenticated HTTPS — no token to embed, no bucket to provision. The
 * app compares the release's build number against its own and, when the
 * user agrees, downloads the APK and hands it to the system installer.
 */
class UpdateChecker(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(Android) {
        engine {
            connectTimeout = 15_000
            socketTimeout = 120_000   // the APK is ~11 MB
        }
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    )

    data class Available(
        val versionName: String,
        val buildNumber: Long,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    /** Null when already current, or when the check could not be made. */
    suspend fun check(currentBuild: Long): Available? = withContext(Dispatchers.IO) {
        runCatching {
            val body = http.get(RELEASES_URL) {
                header("Accept", "application/vnd.github+json")
            }.bodyAsText()
            val release = json.decodeFromString<Release>(body)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@runCatching null

            // Tags look like "app-v0.2.0-17"; the trailing number is the
            // build, which is what actually orders two releases.
            val build = TAG_BUILD.find(release.tagName)?.groupValues?.get(1)?.toLongOrNull()
                ?: return@runCatching null
            if (build <= currentBuild) return@runCatching null

            Available(
                versionName = release.tagName.removePrefix("app-v"),
                buildNumber = build,
                notes = release.body.trim(),
                downloadUrl = apk.downloadUrl,
                sizeBytes = apk.size,
            )
        }.getOrNull()
    }

    /**
     * Download to the app's own cache and open the package installer.
     * Progress is reported 0f..1f so the UI can show a bar.
     */
    suspend fun downloadAndInstall(
        update: Available,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Keep only the file we are about to write; stale APKs would
            // otherwise pile up in the cache forever.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "server-control-${update.buildNumber}.apk")

            val response = http.get(update.downloadUrl)
            val total = update.sizeBytes.takeIf { it > 0 } ?: -1L

            // copyTo drives the transfer; a counting wrapper turns the bytes
            // it writes into progress without touching the channel API,
            // which changed shape between Ktor versions.
            target.outputStream().use { file ->
                val counting = object : FilterOutputStream(file) {
                    private var written = 0L
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        out.write(b, off, len)
                        written += len
                        if (total > 0) {
                            onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                response.bodyAsChannel().copyTo(counting)
                counting.flush()
            }
            onProgress(1f)

            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.updates", target)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun close() = http.close()

    private companion object {
        const val RELEASES_URL =
            "https://api.github.com/repos/ollyp2/Kevke/releases/latest"
        val TAG_BUILD = Regex("""-(\d+)$""")
    }
}
