package com.follow.clash.widget

import com.google.gson.JsonObject
import com.google.gson.JsonParser

const val HOME_WIDGET_BLANK = "—"

const val HOME_WIDGET_OPEN_ACTION = "android.intent.action.MAIN"

const val HOME_WIDGET_RATE_STALE_MS = 20_000L

const val HOME_WIDGET_STATE_STALE_MS = 180_000L

const val HOME_WIDGET_WIDE_MIN_DP = 250

const val HOME_WIDGET_THIN_MAX_HEIGHT_DP = 90

const val HOME_WIDGET_LARGE_MIN_HEIGHT_DP = 280

enum class HomeWidgetSize { THIN, COMPACT, WIDE, LARGE }

fun homeWidgetSize(widthDp: Int, heightDp: Int): HomeWidgetSize {
    val wide = widthDp >= HOME_WIDGET_WIDE_MIN_DP
    if (wide && heightDp < HOME_WIDGET_THIN_MAX_HEIGHT_DP) return HomeWidgetSize.THIN
    if (!wide) return HomeWidgetSize.COMPACT
    if (heightDp < HOME_WIDGET_LARGE_MIN_HEIGHT_DP) return HomeWidgetSize.WIDE
    return HomeWidgetSize.LARGE
}

fun homeWidgetFields(size: HomeWidgetSize): Set<String> = when (size) {
    HomeWidgetSize.THIN -> setOf("status", "node", "rates")
    HomeWidgetSize.COMPACT -> setOf("status", "node")
    HomeWidgetSize.WIDE -> setOf("status", "node", "mode", "rates", "ip")
    HomeWidgetSize.LARGE ->
        setOf(
            "status",
            "node",
            "mode",
            "rates",
            "ip",
            "session",
            "connections",
            "profile",
            "takeover",
            "core",
        )
}

fun homeWidgetStartsService(action: String?): Boolean {
    if (action.isNullOrEmpty()) return false
    return action.endsWith(".action.START") ||
        action.endsWith(".action.STOP") ||
        action.endsWith(".action.TOGGLE") ||
        action.endsWith(".intent.action.VPN_START_REQUESTED") ||
        action.endsWith(".intent.action.VPN_REVOKED")
}

data class HomeWidgetSnapshot(
    val updatedAt: Long = 0,
    val run: String = "unknown",
    val status: String = HOME_WIDGET_BLANK,
    val statusTone: String = "idle",
    val staleLabel: String = HOME_WIDGET_BLANK,
    val staleNode: String = HOME_WIDGET_BLANK,
    val node: String = HOME_WIDGET_BLANK,
    val mode: String = HOME_WIDGET_BLANK,
    val upRate: String = HOME_WIDGET_BLANK,
    val downRate: String = HOME_WIDGET_BLANK,
    val ratesLive: Boolean = false,
    val ipType: String = HOME_WIDGET_BLANK,
    val ipLevel: String = HOME_WIDGET_BLANK,
    val ipLevelTone: String = "idle",
    val ipState: String = HOME_WIDGET_BLANK,
    val ipMeta: String = HOME_WIDGET_BLANK,
    val sessionUp: String = HOME_WIDGET_BLANK,
    val sessionDown: String = HOME_WIDGET_BLANK,
    val connections: String = HOME_WIDGET_BLANK,
    val profile: String = HOME_WIDGET_BLANK,
    val takeover: String = HOME_WIDGET_BLANK,
    val core: String = HOME_WIDGET_BLANK,
    val coreTone: String = "idle",
    val detail: String = "",
    val readOnly: String = "",
    val blank: String = HOME_WIDGET_BLANK,
) {
    fun displayedText(): String =
        listOf(
            status,
            node,
            mode,
            upRate,
            downRate,
            ipType,
            ipLevel,
            ipState,
            ipMeta,
            sessionUp,
            sessionDown,
            connections,
            profile,
            takeover,
            core,
            detail,
            readOnly,
            staleLabel,
            staleNode,
        ).joinToString("\n")
}

fun parseHomeWidgetSnapshot(json: String): HomeWidgetSnapshot {
    val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
        ?: return HomeWidgetSnapshot()
    return HomeWidgetSnapshot(
        updatedAt = root.long("updatedAt"),
        run = root.text("run", "unknown"),
        status = root.text("status"),
        statusTone = root.text("statusTone", "idle"),
        staleLabel = root.text("staleLabel"),
        staleNode = root.text("staleNode"),
        node = root.text("node"),
        mode = root.text("mode"),
        upRate = root.text("upRate"),
        downRate = root.text("downRate"),
        ratesLive = root.bool("ratesLive"),
        ipType = root.text("ipType"),
        ipLevel = root.text("ipLevel"),
        ipLevelTone = root.text("ipLevelTone", "idle"),
        ipState = root.text("ipState"),
        ipMeta = root.text("ipMeta"),
        sessionUp = root.text("sessionUp"),
        sessionDown = root.text("sessionDown"),
        connections = root.text("connections"),
        profile = root.text("profile"),
        takeover = root.text("takeover"),
        core = root.text("core"),
        coreTone = root.text("coreTone", "idle"),
        detail = root.text("detail", ""),
        readOnly = root.text("readOnly", ""),
        blank = root.text("blank"),
    )
}

fun presentHomeWidget(snapshot: HomeWidgetSnapshot, nowMillis: Long): HomeWidgetSnapshot {
    fun cleared() = snapshot.copy(
        upRate = snapshot.blank,
        downRate = snapshot.blank,
        ratesLive = false,
    )
    if (snapshot.run != "running") return cleared()
    val age = nowMillis - snapshot.updatedAt
    if (age >= HOME_WIDGET_STATE_STALE_MS) {
        return snapshot.copy(
            status = snapshot.staleLabel,
            statusTone = "warn",
            node = snapshot.staleNode,
            coreTone = "warn",
            upRate = snapshot.blank,
            downRate = snapshot.blank,
            ratesLive = false,
        )
    }
    if (age >= HOME_WIDGET_RATE_STALE_MS) return cleared()
    return snapshot
}

fun homeWidgetRefreshDelay(snapshot: HomeWidgetSnapshot, nowMillis: Long): Long? {
    if (snapshot.run != "running") return null
    val age = nowMillis - snapshot.updatedAt
    val deadline = when {
        age < HOME_WIDGET_RATE_STALE_MS -> snapshot.updatedAt + HOME_WIDGET_RATE_STALE_MS
        age < HOME_WIDGET_STATE_STALE_MS -> snapshot.updatedAt + HOME_WIDGET_STATE_STALE_MS
        else -> return null
    }
    return (deadline - nowMillis).coerceAtLeast(5_000)
}

private fun JsonObject.text(key: String, fallback: String = HOME_WIDGET_BLANK): String {
    val value = get(key) ?: return fallback
    if (value.isJsonNull) return fallback
    return value.asString
}

private fun JsonObject.long(key: String): Long {
    val value = get(key) ?: return 0
    if (value.isJsonNull) return 0
    return value.asLong
}

private fun JsonObject.bool(key: String): Boolean {
    val value = get(key) ?: return false
    if (value.isJsonNull) return false
    return value.asBoolean
}
