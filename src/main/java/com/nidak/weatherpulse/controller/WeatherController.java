package com.nidak.weatherpulse.controller;

import com.nidak.weatherpulse.dto.ApiResponse;
import com.nidak.weatherpulse.entity.Weather;
import com.nidak.weatherpulse.entity.WeatherSummary;
import com.nidak.weatherpulse.entity.WeatherThreshold;
import com.nidak.weatherpulse.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/fetch/{city}")
    public ResponseEntity<ApiResponse<Weather>> fetchWeather(@PathVariable String city) {
        Weather weatherData = weatherService.fetchWeatherData(city);
        return ResponseEntity.ok(ApiResponse.of("Weather data fetched for " + city, weatherData));
    }

    @GetMapping("/current/{city}")
    public ResponseEntity<ApiResponse<Weather>> getCurrentWeather(@PathVariable String city) {
        Weather currentWeather = weatherService.fetchCurrentWeather(city);
        return ResponseEntity.ok(ApiResponse.of("Live weather fetched for " + city, currentWeather));
    }

    @GetMapping("/forecast/{city}")
    public ResponseEntity<ApiResponse<List<Weather>>> getWeatherForecast(@PathVariable String city) {
        List<Weather> forecast = weatherService.fetchWeatherForecast(city);
        return ResponseEntity.ok(ApiResponse.of("5-day forecast fetched for " + city, forecast));
    }

    @GetMapping("/history/{city}")
    public ResponseEntity<ApiResponse<List<Weather>>> getWeatherHistory(
            @PathVariable String city,
            @RequestParam String date
    ) {
        LocalDate localDate = LocalDate.parse(date);
        LocalDateTime startOfDay = LocalDateTime.of(localDate, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(localDate, LocalTime.MAX);
        List<Weather> weatherHistory = weatherService.getWeatherDataForCity(city, startOfDay, endOfDay);
        return ResponseEntity.ok(ApiResponse.of("Historical weather fetched for " + city, weatherHistory));
    }

    @GetMapping("/summary/{city}")
    public ResponseEntity<ApiResponse<WeatherSummary>> getWeatherSummary(
            @PathVariable String city,
            @RequestParam String date
    ) {
        LocalDate localDate = LocalDate.parse(date);
        LocalDateTime startOfDay = LocalDateTime.of(localDate, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(localDate, LocalTime.MAX);
        List<Weather> weatherHistory = weatherService.getWeatherDataForCity(city, startOfDay, endOfDay);
        WeatherSummary summary = weatherService.calculateSummary(weatherHistory);
        return ResponseEntity.ok(ApiResponse.of("Weather summary generated for " + city, summary));
    }

    @PostMapping("/threshold")
    public ResponseEntity<ApiResponse<String>> setWeatherThreshold(@RequestBody WeatherThreshold threshold) {
        weatherService.setThreshold(threshold);
        return ResponseEntity.ok(ApiResponse.of("Weather threshold saved", "Success"));
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<String>> checkWeatherAlerts(@RequestBody Weather currentWeather) {
        String alertMessage = weatherService.checkAlerts(currentWeather);
        return ResponseEntity.ok(ApiResponse.of("Threshold check complete", alertMessage));
    }

    @PostMapping("/simulate/{city}")
    public ResponseEntity<ApiResponse<Weather>> simulateWeather(@PathVariable String city) {
        Weather simulatedWeather = weatherService.simulateCurrentWeatherData(city);
        return ResponseEntity.ok(ApiResponse.of("Simulated weather generated for " + city, simulatedWeather));
    }

    @GetMapping("/trends/{city}")
    public ResponseEntity<ApiResponse<String>> getWeatherTrends(@PathVariable String city) {
        String trends = weatherService.generateWeatherTrends(city);
        return ResponseEntity.ok(ApiResponse.of("Weather trend report generated for " + city, trends));
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<String>>> getTrackedCities() {
        return ResponseEntity.ok(ApiResponse.of("Tracked city list", weatherService.getTrackedCities()));
    }
}
