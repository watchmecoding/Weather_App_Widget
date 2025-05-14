package com.example.weatherappwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException

class WeatherWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var countrySpinner: Spinner
    private lateinit var citySpinner: Spinner
    private var selectedCountry: String? = null
    private lateinit var countriesMap: Map<String, List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_configure)

        countrySpinner = findViewById(R.id.spinner_country)
        citySpinner = findViewById(R.id.spinner_city)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnLocation = findViewById<Button>(R.id.btn_get_location)

        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        fetchCountriesAndCities()

        btnSave.setOnClickListener {
            val selectedCity = citySpinner.selectedItem as? String ?: return@setOnClickListener
            fetchWeatherByCity(selectedCity)
        }

        btnLocation.setOnClickListener {
            getCurrentLocation()
        }
    }

    private fun fetchCountriesAndCities() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://countriesnow.space/api/v0.1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(CountriesApi::class.java)
        api.getCountries(mapOf()).enqueue(object : Callback<CountryResponse> {
            override fun onResponse(call: Call<CountryResponse>, response: Response<CountryResponse>) {
                if (response.isSuccessful && response.body()?.data != null) {
                    val data = response.body()!!.data
                    countriesMap = data.associate { it.country to it.cities }
                    Log.d("WeatherConfig", "Країни успішно отримані з API")
                    setupCountrySpinner()
                } else {
                    Log.e("WeatherConfig", "API вернуло порожні дані або помилку. Перехід до резервного JSON")
                    loadFallbackCountries()
                }
            }

            override fun onFailure(call: Call<CountryResponse>, t: Throwable) {
                Log.e("WeatherConfig", "Помилка при завантаженні країн з API: ${t.message}")
                loadFallbackCountries()
            }
        })
    }

    private fun loadFallbackCountries() {
        try {
            val inputStream = assets.open("countries_cities.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            countriesMap = Gson().fromJson(json, type)
            Log.d("WeatherConfig", "Завантажено з локального JSON")
            setupCountrySpinner()
        } catch (e: IOException) {
            Log.e("WeatherConfig", "Не вдалося завантажити локальний JSON: ${e.message}")
            showToast("Не вдалося завантажити список країн")
        }
    }

    private fun setupCountrySpinner() {
        val countryList = countriesMap.keys.toList()
        countrySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countryList)

        val prefs = getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val savedCountry = prefs.getString("selected_country", null)
        val savedCity = prefs.getString("selected_city", null)

        if (savedCountry != null) {
            val countryIndex = countryList.indexOf(savedCountry)
            if (countryIndex != -1) countrySpinner.setSelection(countryIndex)
        }

        countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCountry = countryList[position]
                val cities = countriesMap[selectedCountry] ?: emptyList()
                citySpinner.adapter = ArrayAdapter(this@WeatherWidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, cities)

                if (selectedCountry == savedCountry) {
                    val cityIndex = cities.indexOf(savedCity)
                    if (cityIndex != -1) citySpinner.setSelection(cityIndex)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun fetchWeatherByCity(city: String) {
        val apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY
        WeatherService.api.getWeatherByCity(city, apiKey).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                val weather = response.body()
                if (weather != null) {
                    saveToPrefs(selectedCountry ?: "", weather.name, weather.main.temp)
                    updateWidgetUI(weather.name, weather.main.temp)
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                showToast("Помилка запиту")
            }
        })
    }

    private fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        val apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY
        WeatherService.api.getWeatherByCoordinates(lat, lon, apiKey).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                val weather = response.body()
                if (weather != null) {
                    val prefs = getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("city", weather.name)
                        .putString("temp", weather.main.temp.toString())
                        .putFloat("lat", lat.toFloat())
                        .putFloat("lon", lon.toFloat())
                        .apply()

                    updateWidgetUI(weather.name, weather.main.temp)
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                showToast("Помилка запиту")
            }
        })
    }


    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                fetchWeatherByCoordinates(location.latitude, location.longitude)
            } else {
                showToast("Не вдалося отримати локацію")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            showToast("Потрібен дозвіл на геолокацію")
        }
    }

    private fun updateWidgetUI(city: String, temp: Double) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            WeatherAppWidget.updateAppWidget(this, appWidgetManager, appWidgetId)

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
        }
        finish()
    }

    private fun saveToPrefs(country: String, city: String, temp: Double) {
        val prefs = getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("selected_country", country)
            .putString("selected_city", city)
            .putString("city", city)
            .putString("temp", temp.toString())
            .apply()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

interface CountriesApi {
    @POST("countries/cities")
    fun getCountries(@Body body: Map<String, String>): Call<CountryResponse>
}

data class CountryResponse(
    val error: Boolean,
    val msg: String,
    val data: List<CountryData>
)

data class CountryData(
    val country: String,
    val cities: List<String>
)
