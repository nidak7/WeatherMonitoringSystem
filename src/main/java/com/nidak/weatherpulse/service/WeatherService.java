package com.nidak.weatherpulse.service;

import com.nidak.weatherpulse.entity.Weather;
import com.nidak.weatherpulse.entity.WeatherSummary;
import com.nidak.weatherpulse.entity.WeatherThreshold;
import com.nidak.weatherpulse.exception.WeatherServiceException;
import com.nidak.weatherpulse.repository.WeatherRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WeatherService {

    private static final String CURRENT_WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";
    private static final DateTimeFormatter FORECAST_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${openweather.api.key:}")
    private String apiKey;

    @Value("${weather.tracked-cities:Delhi,Mumbai,Chennai,Bangalore,Kolkata,Hyderabad}")
    private String trackedCitiesConfig;

    private final WeatherRepository weatherRepository;
    private final List<WeatherThreshold> thresholds = new ArrayList<>();
    private final Map<String, Integer> consecutiveBreaches = new HashMap<>();

    public WeatherService(WeatherRepository weatherRepository) {
        this.weatherRepository = weatherRepository;
    }

    public List<String> getTrackedCities() {
        return Arrays.stream(trackedCitiesConfig.split(","))
                .map(String::trim)
                .filter(city -> !city.isBlank())
                .toList();
    }

    public void setThreshold(WeatherThreshold threshold) {
        if (threshold == null || threshold.getCondition() == null || threshold.getCondition().isBlank()) {
            throw new WeatherServiceException(HttpStatus.BAD_REQUEST, "Threshold condition is required.");
        }
        thresholds.add(threshold);
    }

    public String checkAlerts(Weather weather) {
        if (weather == null) {
            throw new WeatherServiceException(HttpStatus.BAD_REQUEST, "Weather payload is required.");
        }
        String city = normalizeCity(weather.getCity());
        for (WeatherThreshold threshold : thresholds) {
            if ("temperature".equalsIgnoreCase(threshold.getCondition())) {
                double thresholdTemp = threshold.getThreshold();
                if (weather.getTemperature() > thresholdTemp) {
                    consecutiveBreaches.put(city, consecutiveBreaches.getOrDefault(city, 0) + 1);
                    if (consecutiveBreaches.get(city) >= 2) {
                        return threshold.getAlertMessage() == null || threshold.getAlertMessage().isBlank()
                                ? "Temperature threshold breached for " + city
                                : threshold.getAlertMessage();
                    }
                } else {
                    consecutiveBreaches.put(city, 0);
                }
            }
        }
        return "Temperature is within the configured threshold.";
    }

    @Scheduled(fixedRateString = "${weather.polling.fixed-rate-ms:300000}")
    public void fetchWeatherForAllCities() {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        for (String city : getTrackedCities()) {
            try {
                fetchWeatherData(city);
            } catch (RuntimeException ignored) {
                // Keep polling resilient even if one city fails.
            }
        }
    }

    public Weather fetchWeatherData(String city) {
        String normalizedCity = normalizeCity(city);
        JSONObject weatherJson = getApiResponse(buildCurrentWeatherUrl(normalizedCity), normalizedCity);
        Weather mappedWeather = mapCurrentWeather(weatherJson, normalizedCity);
        LocalDateTime timestamp = mappedWeather.getTimestamp();

        Weather existingWeather = weatherRepository.findByCityAndTimestamp(normalizedCity, timestamp);
        Weather weatherRecord = existingWeather == null ? new Weather() : existingWeather;
        weatherRecord.setCity(mappedWeather.getCity());
        weatherRecord.setTemperature(mappedWeather.getTemperature());
        weatherRecord.setFeelsLike(mappedWeather.getFeelsLike());
        weatherRecord.setWeatherCondition(mappedWeather.getWeatherCondition());
        weatherRecord.setHumidity(mappedWeather.getHumidity());
        weatherRecord.setWindSpeed(mappedWeather.getWindSpeed());
        weatherRecord.setTimestamp(mappedWeather.getTimestamp());

        return weatherRepository.save(weatherRecord);
    }

    public Weather fetchCurrentWeather(String city) {
        return fetchWeatherData(city);
    }

    public List<Weather> fetchWeatherForecast(String city) {
        String normalizedCity = normalizeCity(city);
        JSONObject forecastJson = getApiResponse(buildForecastUrl(normalizedCity), normalizedCity);
        JSONArray list = forecastJson.getJSONArray("list");
        List<Weather> forecastList = new ArrayList<>();

        for (int i = 0; i < list.length(); i++) {
            JSONObject forecastData = list.getJSONObject(i);
            JSONObject main = forecastData.getJSONObject("main");
            JSONObject wind = forecastData.optJSONObject("wind");
            String weatherCondition = forecastData.getJSONArray("weather").getJSONObject(0).getString("main");

            Weather forecast = new Weather();
            forecast.setCity(normalizedCity);
            forecast.setTemperature(roundToTwoDecimalPlaces(main.getDouble("temp")));
            forecast.setFeelsLike(roundToTwoDecimalPlaces(main.optDouble("feels_like", 0)));
            forecast.setHumidity(main.optDouble("humidity", 0));
            forecast.setWindSpeed(wind == null ? 0 : wind.optDouble("speed", 0));
            forecast.setWeatherCondition(weatherCondition);
            forecast.setTimestamp(LocalDateTime.parse(forecastData.getString("dt_txt"), FORECAST_TIMESTAMP_FORMATTER));
            forecastList.add(forecast);
        }
        return forecastList;
    }

    public List<Weather> getWeatherDataForCity(String city, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return weatherRepository.findByCityAndTimestampBetween(normalizeCity(city), startOfDay, endOfDay);
    }

    public WeatherSummary calculateSummary(List<Weather> weatherData) {
        if (weatherData == null || weatherData.isEmpty()) {
            return new WeatherSummary(0, 0, 0, "N/A", 0, 0);
        }

        double averageTemp = weatherData.stream().mapToDouble(Weather::getTemperature).average().orElse(0);
        double maxTemp = weatherData.stream().mapToDouble(Weather::getTemperature).max().orElse(0);
        double minTemp = weatherData.stream().mapToDouble(Weather::getTemperature).min().orElse(0);
        Map<String, Long> conditionCount = weatherData.stream()
                .collect(Collectors.groupingBy(Weather::getWeatherCondition, Collectors.counting()));
        String dominantCondition = conditionCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        double averageHumidity = weatherData.stream().mapToDouble(Weather::getHumidity).average().orElse(0);
        double averageWindSpeed = weatherData.stream().mapToDouble(Weather::getWindSpeed).average().orElse(0);

        return new WeatherSummary(
                roundToTwoDecimalPlaces(averageTemp),
                roundToTwoDecimalPlaces(maxTemp),
                roundToTwoDecimalPlaces(minTemp),
                dominantCondition,
                roundToTwoDecimalPlaces(averageHumidity),
                roundToTwoDecimalPlaces(averageWindSpeed)
        );
    }

    public Weather simulateCurrentWeatherData(String city) {
        return fetchWeatherData(city);
    }

    public String generateWeatherTrends(String city) {
        String normalizedCity = normalizeCity(city);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);
        List<Weather> weatherHistory = weatherRepository.findByCityAndTimestampBetween(normalizedCity, start, end);

        StringBuilder trends = new StringBuilder();
        trends.append("Weather trends for ").append(normalizedCity).append(":\n");
        trends.append("Date\tTemperature\tCondition\n");
        for (Weather weather : weatherHistory) {
            trends.append(weather.getTimestamp().toLocalDate()).append("\t")
                    .append(weather.getTemperature()).append(" C\t")
                    .append(weather.getWeatherCondition()).append("\n");
        }
        return trends.toString();
    }

    private Weather mapCurrentWeather(JSONObject weatherJson, String city) {
        JSONObject main = weatherJson.getJSONObject("main");
        JSONObject wind = weatherJson.optJSONObject("wind");
        String weatherCondition = weatherJson.getJSONArray("weather").getJSONObject(0).getString("main");

        Weather currentWeather = new Weather();
        currentWeather.setCity(city);
        currentWeather.setTemperature(roundToTwoDecimalPlaces(main.getDouble("temp")));
        currentWeather.setFeelsLike(roundToTwoDecimalPlaces(main.optDouble("feels_like", 0)));
        currentWeather.setWeatherCondition(weatherCondition);
        currentWeather.setHumidity(main.optDouble("humidity", 0));
        currentWeather.setWindSpeed(wind == null ? 0 : wind.optDouble("speed", 0));
        currentWeather.setTimestamp(LocalDateTime.now().withSecond(0).withNano(0));
        return currentWeather;
    }

    private JSONObject getApiResponse(String url, String city) {
        ensureApiKey();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response;

        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (RestClientException exception) {
            throw new WeatherServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to connect to weather provider for " + city + "."
            );
        }

        if (response.getStatusCode().is2xxSuccessful()) {
            return new JSONObject(response.getBody());
        }
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new WeatherServiceException(HttpStatus.NOT_FOUND, "City not found: " + city);
        }
        if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            throw new WeatherServiceException(HttpStatus.TOO_MANY_REQUESTS, "OpenWeather API rate limit reached.");
        }
        throw new WeatherServiceException(
                HttpStatus.BAD_GATEWAY,
                "Weather provider error: " + response.getStatusCode()
        );
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new WeatherServiceException(
                    HttpStatus.BAD_REQUEST,
                    "OpenWeather API key is missing. Set OPENWEATHER_API_KEY."
            );
        }
    }

    private String buildCurrentWeatherUrl(String city) {
        return UriComponentsBuilder.fromHttpUrl(CURRENT_WEATHER_URL)
                .queryParam("q", city)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private String buildForecastUrl(String city) {
        return UriComponentsBuilder.fromHttpUrl(FORECAST_URL)
                .queryParam("q", city)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            throw new WeatherServiceException(HttpStatus.BAD_REQUEST, "City is required.");
        }
        String normalized = city.trim();
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private double roundToTwoDecimalPlaces(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        return decimal.doubleValue();
    }
}
