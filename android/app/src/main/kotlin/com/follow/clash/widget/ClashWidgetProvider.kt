package com.follow.clash.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.follow.clash.R
import com.follow.clash.common.Components
import com.follow.clash.common.QuickAction
import com.follow.clash.common.intent
import com.follow.clash.common.quickIntent
import com.follow.clash.common.toPendingIntent

class ClashWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        scheduleTick(context)
    }

    override fun onEnabled(context: Context) {
        scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                refreshAll(context)
                scheduleTick(context)
            }
            ACTION_REFRESH -> refreshAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_TICK = "com.follow.clash.widget.ACTION_TICK"
        const val ACTION_REFRESH = "com.follow.clash.widget.ACTION_REFRESH"
        private const val TICK_MS = 3000L

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ClashWidgetProvider::class.java),
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val snapshot = WidgetSnapshot.load(context)
            manager.updateAppWidget(
                appWidgetId,
                buildViews(context, snapshot, WidgetSnapshot.flashOn(snapshot.abnormal)),
            )
        }

        private fun buildViews(
            context: Context,
            snapshot: WidgetSnapshot,
            flashOn: Boolean,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_4x2)
            val accent = ContextCompat.getColor(context, R.color.widget_accent)
            val primary = ContextCompat.getColor(context, R.color.widget_text_primary)
            val secondary = ContextCompat.getColor(context, R.color.widget_text_secondary)
            val alert = ContextCompat.getColor(context, R.color.widget_alert_text)
            val statusColor = when {
                flashOn -> alert
                snapshot.running -> accent
                else -> secondary
            }
            views.setTextViewText(
                R.id.widget_updated_at,
                context.getString(
                    R.string.widget_last_updated,
                    WidgetSnapshot.formatUpdatedAt(snapshot.updatedAt),
                ),
            )
            views.setInt(
                R.id.widget_status_row,
                "setBackgroundResource",
                if (flashOn) R.drawable.widget_card_alert_bg else R.drawable.widget_card_bg,
            )
            views.setInt(R.id.widget_shield, "setColorFilter", statusColor)
            views.setTextViewText(
                R.id.widget_status_title,
                context.getString(
                    if (snapshot.running) {
                        R.string.widget_status_running
                    } else {
                        R.string.widget_status_stopped
                    },
                ),
            )
            views.setTextColor(R.id.widget_status_title, statusColor)
            views.setTextViewText(
                R.id.widget_vpn_state,
                context.getString(
                    if (snapshot.vpnConnected) {
                        R.string.widget_vpn_connected
                    } else {
                        R.string.widget_vpn_disconnected
                    },
                ),
            )
            views.setTextColor(R.id.widget_vpn_state, if (flashOn) alert else secondary)
            views.setTextViewText(
                R.id.widget_download,
                context.getString(
                    R.string.widget_download,
                    snapshot.downloadLabel.ifEmpty { "--" },
                ),
            )
            views.setTextViewText(
                R.id.widget_upload,
                context.getString(
                    R.string.widget_upload,
                    snapshot.uploadLabel.ifEmpty { "--" },
                ),
            )
            views.setInt(
                R.id.widget_power,
                "setBackgroundResource",
                if (snapshot.running) {
                    R.drawable.widget_power_bg
                } else {
                    R.drawable.widget_power_bg_off
                },
            )
            views.setTextViewText(
                R.id.widget_node_name,
                snapshot.nodeName.ifEmpty { "--" },
            )
            views.setTextViewText(
                R.id.widget_line_type,
                context.getString(
                    R.string.widget_line_type,
                    snapshot.lineType.ifEmpty { "--" },
                ),
            )
            views.setTextViewText(
                R.id.widget_ip_quality,
                context.getString(
                    R.string.widget_ip_quality,
                    snapshot.ipQualityLabel.ifEmpty { "--" },
                ),
            )
            views.setTextViewText(
                R.id.widget_latency_value,
                snapshot.latencyMs?.let { "$it ms" } ?: "--",
            )
            views.setTextViewText(
                R.id.widget_tile_vpn_value,
                context.getString(
                    if (snapshot.vpnConnected) {
                        R.string.widget_connected
                    } else {
                        R.string.widget_disconnected
                    },
                ),
            )
            views.setTextViewText(
                R.id.widget_tile_connections_value,
                snapshot.connections.toString(),
            )
            views.setTextViewText(
                R.id.widget_tile_core_value,
                context.getString(
                    if (snapshot.coreRunning) {
                        R.string.widget_core_running
                    } else {
                        R.string.widget_core_stopped
                    },
                ),
            )
            views.setTextViewText(
                R.id.widget_tile_requests_value,
                snapshot.requests.toString(),
            )
            views.setTextViewText(
                R.id.widget_tile_mode_value,
                snapshot.mode.ifEmpty { "--" },
            )
            views.setTextColor(R.id.widget_tile_vpn_value, statusColor)
            views.setOnClickPendingIntent(
                R.id.widget_power,
                QuickAction.TOGGLE.quickIntent.toPendingIntent,
            )
            views.setOnClickPendingIntent(
                R.id.widget_refresh,
                refreshPendingIntent(context),
            )
            val openApp = openAppPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_node_row, openApp)
            return views
        }

        private fun refreshPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ClashWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun openAppPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                2,
                Components.mainActivity.intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun tickPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ClashWidgetProvider::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun scheduleTick(context: Context) {
            context.getSystemService<AlarmManager>()?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TICK_MS,
                tickPendingIntent(context),
            )
        }

        private fun cancelTick(context: Context) {
            context.getSystemService<AlarmManager>()?.cancel(tickPendingIntent(context))
        }
    }
}
