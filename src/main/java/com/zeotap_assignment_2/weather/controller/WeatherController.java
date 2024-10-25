package com.zeotap_assignment_2.weather.controller;

import com.zeotap_assignment_2.weather.entity.Weather;
import com.zeotap_assignment_2.weather.entity.WeatherSummary;
import com.zeotap_assignment_2.weather.entity.WeatherThreshold;
import com.zeotap_assignment_2.weather.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    @Autowired
    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/fetch/{city}")
    public ResponseEntity<Object> fetchWeather(@PathVariable String city) {
        try {
            Weather weatherData = weatherService.fetchWeatherData(city);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Weather data fetched and stored successfully for " + city);
            response.put("weatherData", weatherData);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching weather data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/current/{city}")
    public ResponseEntity<Map<String, Object>> getCurrentWeather(@PathVariable String city) {
        try {
            Weather currentWeather = weatherService.fetchCurrentWeather(city);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Weather data fetched successfully for " + city);
            response.put("data", currentWeather);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/forecast/{city}")
    public ResponseEntity<Map<String, Object>> getWeatherForecast(@PathVariable String city) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Weather> forecast = weatherService.fetchWeatherForecast(city);
            response.put("data", forecast);
            response.put("message", "Weather forecast fetched successfully for " + city);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("data", null);
            response.put("message", "Error fetching weather forecast: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/history/{city}")
    public ResponseEntity<List<Weather>> getWeatherHistory(@PathVariable String city, @RequestParam String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalDateTime startOfDay = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(localDate, LocalTime.MAX);
            List<Weather> weatherHistory = weatherService.getWeatherDataForCity(city, startOfDay, endOfDay);
            return ResponseEntity.ok(weatherHistory);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/summary/{city}")
    public ResponseEntity<WeatherSummary> getWeatherSummary(@PathVariable String city, @RequestParam String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalDateTime startOfDay = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(localDate, LocalTime.MAX);
            List<Weather> weatherHistory = weatherService.getWeatherDataForCity(city, startOfDay, endOfDay);
            WeatherSummary summary = weatherService.calculateSummary(weatherHistory);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/threshold")
    public ResponseEntity<String> setWeatherThreshold(@RequestBody WeatherThreshold threshold) {
        try {
            weatherService.setThreshold(threshold);
            return ResponseEntity.ok("Weather threshold set successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error setting threshold: " + e.getMessage());
        }
    }

    @PostMapping("/check")
    public ResponseEntity<String> checkWeatherAlerts(@RequestBody Weather currentWeather) {
        try {
            String alertMessage = weatherService.checkAlerts(currentWeather);
            return ResponseEntity.ok(alertMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error checking alerts: " + e.getMessage());
        }
    }

    @PostMapping("/simulate/{city}")
    public ResponseEntity<Weather> simulateWeather(@PathVariable String city) {
        try {
            Weather simulatedWeather = weatherService.simulateCurrentWeatherData(city);
            return ResponseEntity.ok(simulatedWeather);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/trends/{city}")
    public ResponseEntity<String> getWeatherTrends(@PathVariable String city) {
        try {
            String trends = weatherService.generateWeatherTrends(city);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating trends: " + e.getMessage());
        }
    }
}
