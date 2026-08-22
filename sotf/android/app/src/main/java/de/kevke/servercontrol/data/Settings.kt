package de.kevke.servercontrol.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.kevke.servercontrol.ui.theme.AccentPreset
import de.kevke.servercontrol.ui.theme.SurfacePreset
import kotlinx.serialization.json.Json

/**
 * Endpoints carry API tokens, so they live in EncryptedSharedPreferences.
 * Theme choices are not sensitive but ride along for simplicity.
 */
class Settings(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "server_control_secure",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var endpoints: List<Endpoint>
        get() = prefs.getString(KEY_ENDPOINTS, null)
            ?.let { runCatching { json.decodeFromString<List<Endpoint>>(it) }.getOrNull() }
            ?: emptyList()
        set(value) {
            prefs.edit().putString(KEY_ENDPOINTS, json.encodeToString(value)).apply()
        }

    var activeEndpointLabel: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) { prefs.edit().putString(KEY_ACTIVE, value).apply() }

    val activeEndpoint: Endpoint?
        get() {
            val list = endpoints
            val label = activeEndpointLabel
            return list.firstOrNull { it.label == label } ?: list.firstOrNull()
        }

    fun addEndpoint(endpoint: Endpoint) {
        endpoints = endpoints.filterNot { it.label == endpoint.label } + endpoint
        activeEndpointLabel = endpoint.label
    }

    fun removeEndpoint(label: String) {
        endpoints = endpoints.filterNot { it.label == label }
        if (activeEndpointLabel == label) activeEndpointLabel = endpoints.firstOrNull()?.label
    }

    var surfacePreset: SurfacePreset
        get() = prefs.getString(KEY_SURFACE, null)
            ?.let { name -> SurfacePreset.entries.firstOrNull { it.name == name } }
            ?: SurfacePreset.Ink
        set(value) { prefs.edit().putString(KEY_SURFACE, value.name).apply() }

    var accentPreset: AccentPreset
        get() = prefs.getString(KEY_ACCENT, null)
            ?.let { name -> AccentPreset.entries.firstOrNull { it.name == name } }
            ?: AccentPreset.Sage
        set(value) { prefs.edit().putString(KEY_ACCENT, value.name).apply() }

    private companion object {
        const val KEY_ENDPOINTS = "endpoints"
        const val KEY_ACTIVE = "active_endpoint"
        const val KEY_SURFACE = "surface_preset"
        const val KEY_ACCENT = "accent_preset"
    }
}
