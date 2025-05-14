package com.example.weatherappwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import retrofit2.Call
import retrofit2.Response
import retrofit2.Callback


class WeatherAppWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH_WIDGET = "com.example.weatherappwidget.ACTION_REFRESH_WIDGET"
        private const val ACTION_AUTO_UPDATE = "com.example.weatherappwidget.ACTION_AUTO_UPDATE"

        private fun fetchWeatherAndUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val lat = prefs.getFloat("lat", Float.NaN)
            val lon = prefs.getFloat("lon", Float.NaN)
            val city = prefs.getString("city", "Київ") ?: "Київ"
            val apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY

            val callback = object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    val weather = response.body()
                    if (weather != null) {
                        prefs.edit()
                            .putString("city", weather.name)
                            .putString("temp", weather.main.temp.toString())
                            .apply()

                        val views = RemoteViews(context.packageName, R.layout.weather_app_widget)
                        views.setTextViewText(R.id.txt_city, weather.name)
                        views.setTextViewText(R.id.txt_temp, "${weather.main.temp}°C")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Toast.makeText(context, "Не вдалося оновити погоду", Toast.LENGTH_SHORT).show()
                }
            }

            // Якщо координати є — використовуй їх
            if (!lat.isNaN() && !lon.isNaN()) {
                WeatherService.api.getWeatherByCoordinates(lat.toDouble(), lon.toDouble(), apiKey).enqueue(callback)
            } else {
                WeatherService.api.getWeatherByCity(city, apiKey).enqueue(callback)
            }
        }


        private fun scheduleNextUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WeatherAppWidget::class.java).apply {
                action = ACTION_AUTO_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val intervalMillis = AlarmManager.INTERVAL_HOUR
            val triggerAtMillis = System.currentTimeMillis() + intervalMillis
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtMillis, intervalMillis, pendingIntent)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val city = prefs.getString("city", "Київ") ?: "Київ"
            val temp = prefs.getString("temp", "--") ?: "--"

            val views = RemoteViews(context.packageName, R.layout.weather_app_widget)
            views.setTextViewText(R.id.txt_city, city)
            views.setTextViewText(R.id.txt_temp, "$temp°C")

            // refresh button
            val intent = Intent(context, WeatherAppWidget::class.java).apply {
                action = ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("city", city)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, pendingIntent)

            // settings button
            val configIntent = Intent(context, WeatherWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_settings, configPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)

            // 👉 Додано:
            fetchWeatherAndUpdate(context, appWidgetManager, appWidgetId)
        }

    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleNextUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val manager = AppWidgetManager.getInstance(context)
        val appWidgetIds = manager.getAppWidgetIds(ComponentName(context, WeatherAppWidget::class.java))

        when (intent.action) {
            ACTION_REFRESH_WIDGET, ACTION_AUTO_UPDATE -> {
                for (appWidgetId in appWidgetIds) {
                    val city = intent.getStringExtra("city")
                    if (city != null) {
                        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("city", city).apply()
                    }
                    fetchWeatherAndUpdate(context, manager, appWidgetId)
                }
            }
        }
    }
}