package com.follow.clash.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.widget.RemoteViews
import com.follow.clash.R
import com.follow.clash.ServiceState
import com.follow.clash.common.Components
import com.follow.clash.common.GlobalState
import com.follow.clash.common.QuickAction
import com.follow.clash.common.quickIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class HomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HomeWidgetUpdater.refresh(context)
    }

    override fun onEnabled(context: Context) {
        HomeWidgetUpdater.refresh(context)
    }

    override fun onDisabled(context: Context) {
        HomeWidgetUpdater.stop()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        HomeWidgetUpdater.refresh(context)
    }
}

internal object HomeWidgetUpdater {
    private val lock = Any()
    private val publishLock = Mutex()
    private var observeJob: Job? = null
    private var preferences: SharedPreferences? = null
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WIDGET_CONFIG_KEY) {
                HomeWidgetUpdater.refresh(GlobalState.application)
            }
        }

    fun refresh(context: Context) {
        val app = context.applicationContext
        GlobalState.launch {
            ensureObserving(app)
            publishLocked(app)
        }
    }

    fun stop() {
        synchronized(lock) {
            observeJob?.cancel()
            observeJob = null
            preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            preferences = null
        }
    }

    private fun ensureObserving(app: Context) {
        synchronized(lock) {
            if (observeJob?.isActive == true) return
            val ids = runCatching { widgetIds(app) }.getOrDefault(intArrayOf())
            if (ids.isEmpty()) return
            observePreferences(app)
            observeJob = GlobalState.launch {
                ServiceState.runState.collect {
                    publishLocked(app)
                }
            }
        }
    }

    private fun observePreferences(app: Context) {
        if (preferences != null) return
        val prefs = app.getSharedPreferences(WIDGET_CONFIG_PREFERENCE, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        preferences = prefs
    }

    private suspend fun publishLocked(app: Context) {
        publishLock.withLock {
            runCatching { publish(app) }
                .onFailure { error -> GlobalState.log("Unable to update the home widget: $error") }
        }
    }

    private fun publish(app: Context) {
        val manager = AppWidgetManager.getInstance(app)
        val ids = widgetIds(app)
        if (ids.isEmpty()) return
        val presentation = widgetPresentation(ServiceState.runState.value)
        val appearance = appearanceOf(app)
        val night = resolvesNight(systemNight(app), appearance.nightMode)
        manager.updateAppWidget(ids, remoteViews(app, presentation, appearance, night))
    }

    private fun widgetIds(app: Context): IntArray =
        AppWidgetManager.getInstance(app).getAppWidgetIds(component(app))

    private fun component(app: Context) = ComponentName(
        app.packageName,
        HomeWidgetProvider::class.java.name,
    )

    private fun appearanceOf(app: Context): WidgetAppearance = runCatching {
        val prefs = app.getSharedPreferences(WIDGET_CONFIG_PREFERENCE, Context.MODE_PRIVATE)
        widgetAppearance(prefs.getString(WIDGET_CONFIG_KEY, null))
    }.getOrDefault(WidgetAppearance())

    private fun systemNight(app: Context): Boolean {
        val mode = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}

private fun remoteViews(
    app: Context,
    presentation: WidgetPresentation,
    appearance: WidgetAppearance,
    night: Boolean,
): RemoteViews {
    val strings = app.forLanguage(appearance.languageTag)
    val statusColor = app.getColor(presentation.tone.colorRes(night))
    return RemoteViews(app.packageName, R.layout.home_widget).apply {
        setTextViewText(
            R.id.widget_title,
            app.applicationInfo.loadLabel(app.packageManager).toString(),
        )
        setTextColor(
            R.id.widget_title,
            app.getColor(if (night) R.color.widget_title_dark else R.color.widget_title_light),
        )
        setTextViewText(R.id.widget_status, strings.getString(presentation.status.textRes()))
        setTextColor(R.id.widget_status, statusColor)
        setInt(R.id.widget_dot, "setColorFilter", statusColor)
        setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (night) R.drawable.widget_card_dark else R.drawable.widget_card_light,
        )
        setOnClickPendingIntent(R.id.widget_open, openIntent(app))
        bindAction(app, strings, presentation, night)
    }
}

private fun RemoteViews.bindAction(
    app: Context,
    strings: Context,
    presentation: WidgetPresentation,
    night: Boolean,
) {
    setTextViewText(R.id.widget_action, strings.getString(presentation.button.textRes()))
    setTextColor(
        R.id.widget_action,
        app.getColor(
            if (presentation.buttonEnabled) {
                R.color.widget_action_text
            } else if (night) {
                R.color.widget_action_text_disabled_dark
            } else {
                R.color.widget_action_text_disabled_light
            },
        ),
    )
    setInt(
        R.id.widget_action,
        "setBackgroundResource",
        if (presentation.buttonEnabled) {
            R.drawable.widget_action
        } else if (night) {
            R.drawable.widget_action_disabled_dark
        } else {
            R.drawable.widget_action_disabled_light
        },
    )
    setBoolean(R.id.widget_action, "setEnabled", presentation.buttonEnabled)
    // START and STOP match the label. TOGGLE would reverse a widget whose paint is stale.
    val action = widgetClickAction(presentation) ?: return
    setOnClickPendingIntent(R.id.widget_action, actionIntent(app, action))
}

private fun WidgetStatus.textRes(): Int = when (this) {
    WidgetStatus.STOPPED -> R.string.widget_status_stopped
    WidgetStatus.STARTING -> R.string.widget_status_starting
    WidgetStatus.STOPPING -> R.string.widget_status_stopping
    WidgetStatus.CONNECTED -> R.string.widget_status_connected
}

private fun WidgetAction.textRes(): Int = when (this) {
    WidgetAction.START -> R.string.widget_action_start
    WidgetAction.STOP -> R.string.widget_action_stop
}

private fun WidgetTone.colorRes(night: Boolean): Int = when (this) {
    WidgetTone.NEUTRAL ->
        if (night) R.color.widget_status_dark else R.color.widget_status_light
    WidgetTone.PENDING ->
        if (night) R.color.widget_status_pending_dark else R.color.widget_status_pending_light
    WidgetTone.ACTIVE ->
        if (night) R.color.widget_status_active_dark else R.color.widget_status_active_light
}

private fun Context.forLanguage(languageTag: String?): Context {
    if (languageTag.isNullOrEmpty()) return this
    val locale = Locale.forLanguageTag(languageTag)
    if (locale.language.isEmpty()) return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(LocaleList(locale))
    }
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}

private fun pendingActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private fun openIntent(context: Context): PendingIntent {
    val intent = Intent(Intent.ACTION_MAIN).setComponent(Components.mainActivity).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    return pendingActivity(context, REQUEST_OPEN, intent)
}

private fun actionIntent(context: Context, action: WidgetAction): PendingIntent {
    val quick = when (action) {
        WidgetAction.START -> QuickAction.START
        WidgetAction.STOP -> QuickAction.STOP
    }
    val request = if (action == WidgetAction.START) REQUEST_START else REQUEST_STOP
    return pendingActivity(context, request, quick.quickIntent)
}

private const val REQUEST_OPEN = 0x4663
private const val REQUEST_START = 0x4664
private const val REQUEST_STOP = 0x4665
