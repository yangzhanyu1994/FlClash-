package com.follow.clash.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.follow.clash.MainActivity
import com.follow.clash.R

const val HOME_WIDGET_PREFS = "home_widget"

const val HOME_WIDGET_SNAPSHOT = "snapshot"

fun homeWidgetRefreshAction(context: Context): String {
    return "${context.packageName}.action.HOME_WIDGET_REFRESH"
}

fun loadHomeWidgetSnapshot(context: Context): HomeWidgetSnapshot {
    val json = context
        .getSharedPreferences(HOME_WIDGET_PREFS, Context.MODE_PRIVATE)
        .getString(HOME_WIDGET_SNAPSHOT, null)
    if (json.isNullOrEmpty()) return HomeWidgetSnapshot()
    return parseHomeWidgetSnapshot(json)
}

fun saveHomeWidgetSnapshot(context: Context, json: String) {
    context
        .getSharedPreferences(HOME_WIDGET_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(HOME_WIDGET_SNAPSHOT, json)
        .apply()
}

class HomeWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (homeWidgetStartsService(intent.action)) return
        val refresh = intent.action == homeWidgetRefreshAction(context) ||
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (refresh) {
            renderHomeWidgets(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        renderHomeWidgets(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        renderHomeWidgets(context, intArrayOf(appWidgetId))
    }
}

fun renderHomeWidgets(context: Context, ids: IntArray? = null) {
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, HomeWidgetProvider::class.java)
    val widgetIds = ids ?: manager.getAppWidgetIds(component)
    val now = System.currentTimeMillis()
    val presented = presentHomeWidget(loadHomeWidgetSnapshot(context), now)
    for (id in widgetIds) {
        val options = manager.getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val size = homeWidgetSize(width, height)
        val views = RemoteViews(context.packageName, layoutFor(size))
        bindHomeWidget(context, views, size, presented)
        views.setOnClickPendingIntent(R.id.home_widget_root, openHomeWidget(context))
        manager.updateAppWidget(id, views)
    }
    scheduleHomeWidgetRefresh(context, presented, now)
}

fun bindHomeWidget(
    context: Context,
    views: RemoteViews,
    size: HomeWidgetSize,
    snapshot: HomeWidgetSnapshot,
) {
    val fields = homeWidgetFields(size)
    views.setTextViewText(R.id.home_widget_status, snapshot.status)
    views.setTextColor(R.id.home_widget_status, toneColor(context, snapshot.statusTone))
    views.setTextViewText(R.id.home_widget_node, snapshot.node)
    if (fields.contains("rates")) {
        views.setTextViewText(R.id.home_widget_up, snapshot.upRate)
        views.setTextViewText(R.id.home_widget_down, snapshot.downRate)
    }
    if (fields.contains("mode")) {
        views.setTextViewText(R.id.home_widget_mode, snapshot.mode)
    }
    if (fields.contains("ip")) {
        views.setTextViewText(R.id.home_widget_ip_type, snapshot.ipType)
        views.setTextViewText(R.id.home_widget_ip_level, snapshot.ipLevel)
        views.setTextColor(
            R.id.home_widget_ip_level,
            toneColor(context, snapshot.ipLevelTone),
        )
        views.setTextViewText(R.id.home_widget_ip_state, snapshot.ipState)
        views.setTextViewText(R.id.home_widget_ip_meta, snapshot.ipMeta)
    }
    if (fields.contains("session")) {
        views.setTextViewText(R.id.home_widget_session_up, snapshot.sessionUp)
        views.setTextViewText(R.id.home_widget_session_down, snapshot.sessionDown)
    }
    if (fields.contains("connections")) {
        views.setTextViewText(R.id.home_widget_connections, snapshot.connections)
    }
    if (fields.contains("profile")) {
        views.setTextViewText(R.id.home_widget_profile, snapshot.profile)
    }
    if (fields.contains("takeover")) {
        views.setTextViewText(R.id.home_widget_takeover, snapshot.takeover)
    }
    if (fields.contains("core")) {
        views.setTextViewText(R.id.home_widget_core, snapshot.core)
        views.setTextColor(R.id.home_widget_core, toneColor(context, snapshot.coreTone))
    }
    if (size != HomeWidgetSize.THIN) {
        views.setTextViewText(R.id.home_widget_readonly, snapshot.readOnly)
        views.setViewVisibility(
            R.id.home_widget_detail,
            if (snapshot.detail.isEmpty()) View.GONE else View.VISIBLE,
        )
        views.setTextViewText(R.id.home_widget_detail, snapshot.detail)
    }
}

fun openHomeWidget(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = HOME_WIDGET_OPEN_ACTION
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

fun scheduleHomeWidgetRefresh(
    context: Context,
    snapshot: HomeWidgetSnapshot,
    nowMillis: Long,
) {
    val alarm = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = refreshPendingIntent(context)
    val delay = homeWidgetRefreshDelay(snapshot, nowMillis)
    if (delay == null) {
        alarm.cancel(pending)
        return
    }
    alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pending)
}

private fun refreshPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, HomeWidgetProvider::class.java).apply {
        action = homeWidgetRefreshAction(context)
    }
    return PendingIntent.getBroadcast(
        context,
        1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun layoutFor(size: HomeWidgetSize): Int = when (size) {
    HomeWidgetSize.THIN -> R.layout.home_widget_thin
    HomeWidgetSize.COMPACT -> R.layout.home_widget_compact
    HomeWidgetSize.WIDE -> R.layout.home_widget_wide
    HomeWidgetSize.LARGE -> R.layout.home_widget_large
}

private fun toneColor(context: Context, tone: String): Int {
    val id = when (tone) {
        "ok" -> R.color.home_widget_ok
        "warn" -> R.color.home_widget_warn
        "error" -> R.color.home_widget_error
        else -> R.color.home_widget_muted
    }
    return context.getColor(id)
}
