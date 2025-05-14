package com.example.weatherappwidget

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("weather")
    fun getWeatherByCity(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ua"
    ): Call<WeatherResponse>

    @GET("weather")
    fun getWeatherByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ua"
    ): Call<WeatherResponse>

    @GET("countries")
    fun getCountries(): Call<List<String>>

    @GET("cities")
    fun getCities(@Query("country") country: String): Call<List<String>>
}

