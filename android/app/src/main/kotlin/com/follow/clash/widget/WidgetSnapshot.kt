package com.follow.clash.widget

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WidgetSnapshot(
    @SerializedName("running") val running: Boolean = false,
    @SerializedName("vpnConnected") val vpnConnected: Boolean = false,
    @SerializedName("downloadSpeed") val downloadSpeed: Double = 0.0,
    @SerializedName("uploadSpeed") val uploadSpeed: Double = 0.0,
    @SerializedName("downloadLabel") val downloadLabel: String = "",
    @SerializedName("uploadLabel") val uploadLabel: String = "",
    @SerializedName("nodeName") val nodeName: String = "",
    @SerializedName("country") val country: String = "",
    @SerializedName("countryCode") val countryCode: String = "",
    @SerializedName("ipAddress") val ipAddress: String = "",
    @SerializedName("organization") val organization: String = "",
    @SerializedName("runtimeLabel") val runtimeLabel: String = "",
    @SerializedName("todayTrafficLabel") val todayTrafficLabel: String = "",
    @SerializedName("lineType") val lineType: String = "",
    @SerializedName("ipQualityLabel") val ipQualityLabel: String = "",
    @SerializedName("latencyMs") val latencyMs: Int? = null,
    @SerializedName("connections") val connections: Int = 0,
    @SerializedName("requests") val requests: Int = 0,
    @SerializedName("coreRunning") val coreRunning: Boolean = false,
    @SerializedName("mode") val mode: String = "",
    @SerializedName("updatedAt") val updatedAt: Long = 0L,
    @SerializedName("abnormal") val abnormal: Boolean = false,
) {
    companion object {
        private const val PREFS_FILE = "FlutterSharedPreferences"
        private const val PREFS_KEY = "flutter.widgetSnapshot"

        fun load(context: Context): WidgetSnapshot {
            val json = context
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .getString(PREFS_KEY, null) ?: return WidgetSnapshot()
            return runCatching { Gson().fromJson(json, WidgetSnapshot::class.java) }
                .getOrNull() ?: WidgetSnapshot()
        }

        fun flashOn(abnormal: Boolean): Boolean =
            abnormal && (System.currentTimeMillis() / FLASH_HALF_PERIOD_MS) % 2L == 0L

        fun formatUpdatedAt(updatedAt: Long): String {
            if (updatedAt <= 0L) return ""
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(updatedAt))
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return if (dayFormat.format(Date(updatedAt)) == dayFormat.format(Date())) {
                "今天 $time"
            } else {
                "${dayFormat.format(Date(updatedAt))} $time"
            }
        }

        private const val FLASH_HALF_PERIOD_MS = 3000L
    }
}
