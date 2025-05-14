package com.example.weatherappwidget

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object WeatherService {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    val api: WeatherApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(WeatherApi::class.java)
    }
}
