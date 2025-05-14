package com.example.weatherappwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class WeatherUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WeatherAppWidget::class.java))

        for (appWidgetId in appWidgetIds) {
            WeatherAppWidget.updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
