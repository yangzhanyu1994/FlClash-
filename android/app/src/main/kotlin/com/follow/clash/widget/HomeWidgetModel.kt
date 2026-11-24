package com.follow.clash.widget

import com.follow.clash.RunState
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

// shared_preferences stores Dart's `config` key as flutter.config.
internal const val WIDGET_CONFIG_PREFERENCE = "FlutterSharedPreferences"
internal const val WIDGET_CONFIG_KEY = "flutter.config"

private val WIDGET_LANGUAGES = setOf("en", "zh", "ja", "ru", "fr", "es", "ko")

internal enum class WidgetStatus {
    STOPPED,
    STARTING,
    STOPPING,
    CONNECTED,
}

internal enum class WidgetTone {
    NEUTRAL,
    PENDING,
    ACTIVE,
}

internal enum class WidgetAction {
    START,
    STOP,
}

internal enum class WidgetNightMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

internal data class WidgetAppearance(
    val languageTag: String? = null,
    val nightMode: WidgetNightMode = WidgetNightMode.FOLLOW_SYSTEM,
)

internal data class WidgetPresentation(
    val status: WidgetStatus,
    val tone: WidgetTone,
    val button: WidgetAction,
    val buttonEnabled: Boolean,
)

internal fun widgetPresentation(state: RunState): WidgetPresentation = when (state) {
    RunState.STOPPED -> WidgetPresentation(
        status = WidgetStatus.STOPPED,
        tone = WidgetTone.NEUTRAL,
        button = WidgetAction.START,
        buttonEnabled = true,
    )
    RunState.STARTING -> WidgetPresentation(
        status = WidgetStatus.STARTING,
        tone = WidgetTone.PENDING,
        button = WidgetAction.START,
        buttonEnabled = false,
    )
    RunState.STOPPING -> WidgetPresentation(
        status = WidgetStatus.STOPPING,
        tone = WidgetTone.PENDING,
        button = WidgetAction.STOP,
        buttonEnabled = false,
    )
    RunState.STARTED -> WidgetPresentation(
        status = WidgetStatus.CONNECTED,
        tone = WidgetTone.ACTIVE,
        button = WidgetAction.STOP,
        buttonEnabled = true,
    )
}

internal fun widgetClickAction(presentation: WidgetPresentation): WidgetAction? =
    if (presentation.buttonEnabled) presentation.button else null

internal fun resolvesNight(systemNight: Boolean, mode: WidgetNightMode): Boolean = when (mode) {
    WidgetNightMode.LIGHT -> false
    WidgetNightMode.DARK -> true
    WidgetNightMode.FOLLOW_SYSTEM -> systemNight
}

internal fun widgetLanguageTag(raw: String?): String? {
    val trimmed = raw?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
    val language = trimmed.substringBefore('-').lowercase()
    if (language !in WIDGET_LANGUAGES) return null
    val rest = trimmed.substringAfter('-', "")
    return if (rest.isEmpty()) language else "$language-$rest"
}

internal fun widgetAppearance(configJson: String?): WidgetAppearance {
    if (configJson.isNullOrBlank()) return WidgetAppearance()
    val root = try {
        JsonParser.parseString(configJson).takeIf { it.isJsonObject }?.asJsonObject
    } catch (_: Exception) {
        null
    } ?: return WidgetAppearance()
    return WidgetAppearance(
        languageTag = widgetLanguageTag(root.objectOrNull("appSettingProps").stringOrNull("locale")),
        nightMode = nightMode(root.objectOrNull("themeProps").stringOrNull("themeMode")),
    )
}

private fun nightMode(raw: String?): WidgetNightMode = when (raw) {
    "light" -> WidgetNightMode.LIGHT
    "dark" -> WidgetNightMode.DARK
    else -> WidgetNightMode.FOLLOW_SYSTEM
}

private fun JsonObject?.objectOrNull(name: String): JsonObject? =
    this?.get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject?.stringOrNull(name: String): String? {
    val value: JsonElement = this?.get(name) ?: return null
    return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
