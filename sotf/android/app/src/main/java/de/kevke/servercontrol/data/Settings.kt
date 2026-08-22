package de.kevke.servercontrol.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.kevke.servercontrol.ui.theme.AccentPreset
import de.kevke.servercontrol.ui.theme.SurfacePreset
import kotlinx.serialization.json.Json

/**
 * The config holds a token, so it lives in EncryptedSharedPreferences.
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

    var controlConfig: ControlConfig
        get() = prefs.getString(KEY_CONFIG, null)
            ?.let { runCatching { json.decodeFromString<ControlConfig>(it) }.getOrNull() }
            ?: ControlConfig()
        set(value) {
            // Name the serializer explicitly — without it the compiler picks
            // the member overload that wants a SerializationStrategy first.
            val text = json.encodeToString(ControlConfig.serializer(), value)
            prefs.edit().putString(KEY_CONFIG, text).apply()
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
        const val KEY_CONFIG = "control_config"
        const val KEY_SURFACE = "surface_preset"
        const val KEY_ACCENT = "accent_preset"
    }
}
