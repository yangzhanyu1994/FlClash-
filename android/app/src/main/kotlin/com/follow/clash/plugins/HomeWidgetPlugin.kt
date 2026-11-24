package com.follow.clash.plugins

import android.content.Context
import com.follow.clash.widget.renderHomeWidgets
import com.follow.clash.widget.saveHomeWidgetSnapshot
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class HomeWidgetPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "${context.packageName}/home_widget")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "publish" -> {
                val json = call.arguments as? String
                if (!json.isNullOrEmpty()) {
                    saveHomeWidgetSnapshot(context, json)
                    renderHomeWidgets(context)
                }
                result.success(null)
            }
            "installed" -> {
                val manager = android.appwidget.AppWidgetManager.getInstance(context)
                val component = android.content.ComponentName(
                    context,
                    com.follow.clash.widget.HomeWidgetProvider::class.java,
                )
                result.success(manager.getAppWidgetIds(component).size)
            }
            else -> result.notImplemented()
        }
    }
}
