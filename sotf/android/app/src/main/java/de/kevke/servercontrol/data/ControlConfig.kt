package de.kevke.servercontrol.data

import kotlinx.serialization.Serializable

/**
 * Everything the app needs: the control function's URL and its token.
 *
 * Actions ride along as query parameters (`&action=start|stop|status`), so
 * the same URL that works in a browser works here. No second service, no
 * database, nothing installed on the VM.
 */
@Serializable
data class ControlConfig(
    val label: String = "SotF",
    val baseUrl: String = "",
    val token: String = "",
) {
    val isUsable get() = baseUrl.isNotBlank() && token.isNotBlank()

    /** Shown in settings so you can copy a link into a browser or chat. */
    fun link(action: String) = "$baseUrl?token=$token&action=$action"
}
