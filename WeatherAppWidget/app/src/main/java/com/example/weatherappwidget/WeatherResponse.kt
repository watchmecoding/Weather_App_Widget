package com.example.weatherappwidget

data class WeatherResponse(
    val name: String,
    val main: Main,
    val weather: List<WeatherDescription>
)

data class WeatherDescription(
    val icon: String
)