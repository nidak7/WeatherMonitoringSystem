package com.nidak.weatherpulse.service;

import com.nidak.weatherpulse.entity.DailyWeatherSummaryEntity;
import com.nidak.weatherpulse.entity.Weather;
import com.nidak.weatherpulse.entity.WeatherAlert;
import com.nidak.weatherpulse.entity.WeatherSummary;
import com.nidak.weatherpulse.entity.WeatherThreshold;
import com.nidak.weatherpulse.exception.WeatherServiceException;
import com.nidak.weatherpulse.repository.DailyWeatherSummaryRepository;
import com.nidak.weatherpulse.repository.WeatherAlertRepository;
import com.nidak.weatherpulse.repository.WeatherRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

    @Value("${weather.data-stale-minutes:3}")
    private long weatherDataStaleMinutes;

    private final WeatherRepository weatherRepository;
    private final DailyWeatherSummaryRepository dailyWeatherSummaryRepository;
    private final WeatherAlertRepository weatherAlertRepository;

    private final List<WeatherThreshold> thresholds = new ArrayList<>();
    private final Map<String, Integer> consecutiveBreaches = new HashMap<>();

    public WeatherService(
            WeatherRepository weatherRepository,
            DailyWeatherSummaryRepository dailyWeatherSummaryRepository,
            WeatherAlertRepository weatherAlertRepository
    ) {
        this.weatherRepository = weatherRepository;
        this.dailyWeatherSummaryRepository = dailyWeatherSummaryRepository;
        this.weatherAlertRepository = weatherAlertRepository;
    }

    public List<String> getTrackedCities() {
        return Arrays.stream(trackedCitiesConfig.split(","))
                .map(String::trim)
                .filter(city -> !city.isBlank())
                .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpTrackedCitiesOnStartup() {
        fetchWeatherForAllCities();
    }

    public void setThreshold(WeatherThreshold threshold) {
        if (threshold == null || threshold.getCondition() == null || threshold.getCondition().isBlank()) {
            throw new WeatherServiceException(HttpStatus.BAD_REQUEST, "Threshold condition is required.");
        }

        if ("weatherCondition".equalsIgnoreCase(threshold.getCondition())
                && (threshold.getWeatherCondition() == null || threshold.getWeatherCondition().isBlank())) {
            throw new WeatherServiceException(
                    HttpStatus.BAD_REQUEST,
                    "weatherCondition value is required when condition is weatherCondition."
            );
        }

        if (threshold.getConsecutiveUpdates() <= 0) {
            threshold.setConsecutiveUpdates(2);
        }
        thresholds.add(threshold);
    }

    public String checkAlerts(Weather weather) {
        if (weather == null) {
            throw new WeatherServiceException(HttpStatus.BAD_REQUEST, "Weather payload is required.");
        }
        return evaluateThresholds(weather, false);
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
        weatherRecord.setWeatherDescription(mappedWeather.getWeatherDescription());
        weatherRecord.setWeatherCode(mappedWeather.getWeatherCode());
        weatherRecord.setWeatherRisk(mappedWeather.getWeatherRisk());
        weatherRecord.setHumidity(mappedWeather.getHumidity());
        weatherRecord.setWindSpeed(mappedWeather.getWindSpeed());
        weatherRecord.setTimestamp(mappedWeather.getTimestamp());

        Weather saved = weatherRepository.save(weatherRecord);
        upsertDailySummary(saved.getCity(), saved.getTimestamp().toLocalDate());
        evaluateThresholds(saved, true);
        return saved;
    }

    public Weather fetchCurrentWeather(String city) {
        return getLatestWeatherForCity(city);
    }

    public Weather getLatestWeatherForCity(String city) {
        String normalizedCity = normalizeCity(city);
        Weather latestWeather = weatherRepository.findTopByCityOrderByTimestampDesc(normalizedCity);
        if (latestWeather == null || isStale(latestWeather.getTimestamp())) {
            return fetchWeatherData(normalizedCity);
        }
        return latestWeather;
    }

    public List<Weather> getLatestWeatherForTrackedCities() {
        List<Weather> latestWeatherList = new ArrayList<>();
        for (String city : getTrackedCities()) {
            try {
                latestWeatherList.add(getLatestWeatherForCity(city));
            } catch (RuntimeException ignored) {
                // Keep endpoint available even if one city fails.
            }
        }
        latestWeatherList.sort(Comparator.comparing(Weather::getCity));
        return latestWeatherList;
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
            JSONObject weatherObject = forecastData.getJSONArray("weather").getJSONObject(0);
            String weatherCondition = weatherObject.getString("main");
            String weatherDescription = weatherObject.optString("description", weatherCondition);
            int weatherCode = weatherObject.optInt("id", 0);
            double windSpeed = wind == null ? 0 : wind.optDouble("speed", 0);

            Weather forecast = new Weather();
            forecast.setCity(normalizedCity);
            forecast.setTemperature(roundToTwoDecimalPlaces(kelvinToCelsius(main.getDouble("temp"))));
            forecast.setFeelsLike(roundToTwoDecimalPlaces(kelvinToCelsius(main.optDouble("feels_like", 0))));
            forecast.setHumidity(main.optDouble("humidity", 0));
            forecast.setWindSpeed(windSpeed);
            forecast.setWeatherCondition(weatherCondition);
            forecast.setWeatherDescription(weatherDescription);
            forecast.setWeatherCode(weatherCode);
            forecast.setWeatherRisk(determineWeatherRisk(weatherCode, weatherCondition, weatherDescription, windSpeed));
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

    public DailyWeatherSummaryEntity getDailySummary(String city, LocalDate date) {
        String normalizedCity = normalizeCity(city);
        return dailyWeatherSummaryRepository.findByCityAndSummaryDate(normalizedCity, date)
                .orElseGet(() -> upsertDailySummary(normalizedCity, date));
    }

    public List<DailyWeatherSummaryEntity> getTrackedCityDailySummaries(LocalDate date) {
        List<DailyWeatherSummaryEntity> summaries = new ArrayList<>();
        for (String city : getTrackedCities()) {
            summaries.add(getDailySummary(city, date));
        }
        summaries.sort(Comparator.comparing(DailyWeatherSummaryEntity::getCity));
        return summaries;
    }

    public List<WeatherAlert> getRecentAlerts() {
        return weatherAlertRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public List<WeatherAlert> getRecentAlertsForCity(String city) {
        return weatherAlertRepository.findTop20ByCityOrderByCreatedAtDesc(normalizeCity(city));
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
        JSONObject weatherObject = weatherJson.getJSONArray("weather").getJSONObject(0);
        String weatherCondition = weatherObject.getString("main");
        String weatherDescription = weatherObject.optString("description", weatherCondition);
        int weatherCode = weatherObject.optInt("id", 0);
        double windSpeed = wind == null ? 0 : wind.optDouble("speed", 0);

        Weather currentWeather = new Weather();
        currentWeather.setCity(city);
        currentWeather.setTemperature(roundToTwoDecimalPlaces(kelvinToCelsius(main.getDouble("temp"))));
        currentWeather.setFeelsLike(roundToTwoDecimalPlaces(kelvinToCelsius(main.optDouble("feels_like", 0))));
        currentWeather.setWeatherCondition(weatherCondition);
        currentWeather.setWeatherDescription(weatherDescription);
        currentWeather.setWeatherCode(weatherCode);
        currentWeather.setWeatherRisk(determineWeatherRisk(weatherCode, weatherCondition, weatherDescription, windSpeed));
        currentWeather.setHumidity(main.optDouble("humidity", 0));
        currentWeather.setWindSpeed(windSpeed);
        currentWeather.setTimestamp(LocalDateTime.now().withSecond(0).withNano(0));
        return currentWeather;
    }

    private String evaluateThresholds(Weather weather, boolean persistAlert) {
        if (thresholds.isEmpty()) {
            return "No thresholds configured.";
        }

        String city = normalizeCity(weather.getCity());
        for (WeatherThreshold threshold : thresholds) {
            String thresholdCondition = threshold.getCondition() == null
                    ? ""
                    : threshold.getCondition().trim();

            if ("temperature".equalsIgnoreCase(thresholdCondition)) {
                String key = city + ":temperature:" + threshold.getThreshold();
                if (weather.getTemperature() > threshold.getThreshold()) {
                    int count = consecutiveBreaches.getOrDefault(key, 0) + 1;
                    consecutiveBreaches.put(key, count);
                    if (count == threshold.getConsecutiveUpdates()) {
                        String alertMessage = buildTemperatureAlertMessage(weather, threshold);
                        if (persistAlert) {
                            saveAlert(city, "temperature", alertMessage, String.valueOf(weather.getTemperature()),
                                    String.valueOf(threshold.getThreshold()));
                        }
                        return alertMessage;
                    }
                } else {
                    consecutiveBreaches.put(key, 0);
                }
            }

            if ("weatherCondition".equalsIgnoreCase(thresholdCondition)) {
                String expectedCondition = threshold.getWeatherCondition();
                if (expectedCondition == null || expectedCondition.isBlank()) {
                    continue;
                }

                String key = city + ":weatherCondition:" + expectedCondition.toLowerCase(Locale.ROOT);
                boolean matches = expectedCondition.equalsIgnoreCase(weather.getWeatherCondition());
                if (matches) {
                    int count = consecutiveBreaches.getOrDefault(key, 0) + 1;
                    consecutiveBreaches.put(key, count);
                    if (count == threshold.getConsecutiveUpdates()) {
                        String alertMessage = buildConditionAlertMessage(weather, threshold);
                        if (persistAlert) {
                            saveAlert(city, "weatherCondition", alertMessage, weather.getWeatherCondition(), expectedCondition);
                        }
                        return alertMessage;
                    }
                } else {
                    consecutiveBreaches.put(key, 0);
                }
            }
        }

        return "No alerts triggered.";
    }

    private String buildTemperatureAlertMessage(Weather weather, WeatherThreshold threshold) {
        if (threshold.getAlertMessage() != null && !threshold.getAlertMessage().isBlank()) {
            return threshold.getAlertMessage();
        }
        return "Temperature threshold breached for " + weather.getCity()
                + ": current=" + weather.getTemperature()
                + " C, threshold=" + threshold.getThreshold() + " C";
    }

    private String buildConditionAlertMessage(Weather weather, WeatherThreshold threshold) {
        if (threshold.getAlertMessage() != null && !threshold.getAlertMessage().isBlank()) {
            return threshold.getAlertMessage();
        }
        return "Weather condition threshold breached for " + weather.getCity()
                + ": condition=" + weather.getWeatherCondition();
    }

    private void saveAlert(String city, String alertType, String alertMessage, String observedValue, String thresholdValue) {
        WeatherAlert alert = new WeatherAlert();
        alert.setCity(city);
        alert.setAlertType(alertType);
        alert.setAlertMessage(alertMessage);
        alert.setObservedValue(observedValue);
        alert.setThresholdValue(thresholdValue);
        alert.setCreatedAt(LocalDateTime.now());
        weatherAlertRepository.save(alert);
    }

    private DailyWeatherSummaryEntity upsertDailySummary(String city, LocalDate summaryDate) {
        LocalDateTime start = LocalDateTime.of(summaryDate, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(summaryDate, LocalTime.MAX);
        List<Weather> weatherData = weatherRepository.findByCityAndTimestampBetween(city, start, end);
        WeatherSummary summary = calculateSummary(weatherData);

        Map<String, Long> conditionCount = weatherData.stream()
                .collect(Collectors.groupingBy(Weather::getWeatherCondition, Collectors.counting()));
        String dominantCondition = summary.getDominantWeatherCondition();
        long dominantCount = conditionCount.getOrDefault(dominantCondition, 0L);
        String dominantReason = "Most frequent condition based on " + dominantCount
                + " of " + weatherData.size() + " samples";

        DailyWeatherSummaryEntity entity = dailyWeatherSummaryRepository
                .findByCityAndSummaryDate(city, summaryDate)
                .orElseGet(DailyWeatherSummaryEntity::new);
        entity.setCity(city);
        entity.setSummaryDate(summaryDate);
        entity.setAverageTemperature(summary.getAverageTemperature());
        entity.setMaxTemperature(summary.getMaxTemperature());
        entity.setMinTemperature(summary.getMinTemperature());
        entity.setDominantWeatherCondition(summary.getDominantWeatherCondition());
        entity.setDominantWeatherConditionReason(dominantReason);
        entity.setAverageHumidity(summary.getAverageHumidity());
        entity.setAverageWindSpeed(summary.getAverageWindSpeed());
        entity.setTotalSamples(weatherData.size());
        entity.setUpdatedAt(LocalDateTime.now());
        return dailyWeatherSummaryRepository.save(entity);
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
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private String buildForecastUrl(String city) {
        return UriComponentsBuilder.fromHttpUrl(FORECAST_URL)
                .queryParam("q", city)
                .queryParam("appid", apiKey)
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

    private double kelvinToCelsius(double kelvin) {
        return kelvin - 273.15;
    }

    private double roundToTwoDecimalPlaces(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        return decimal.doubleValue();
    }

    private boolean isStale(LocalDateTime timestamp) {
        if (timestamp == null) {
            return true;
        }
        return timestamp.isBefore(LocalDateTime.now().minusMinutes(weatherDataStaleMinutes));
    }

    private String determineWeatherRisk(int weatherCode, String weatherCondition, String weatherDescription, double windSpeed) {
        String condition = weatherCondition == null ? "" : weatherCondition.toLowerCase(Locale.ROOT);
        String description = weatherDescription == null ? "" : weatherDescription.toLowerCase(Locale.ROOT);

        if (weatherCode >= 200 && weatherCode < 300) {
            if (windSpeed >= 20) {
                return "Cyclone/Severe Storm Risk";
            }
            return "Thunderstorm Risk";
        }

        if (weatherCode >= 600 && weatherCode < 700) {
            return "Snow Risk";
        }

        if (weatherCode >= 700 && weatherCode < 800) {
            if (description.contains("sand") || description.contains("dust")) {
                return "Sandstorm/Dust Risk";
            }
            if (description.contains("tornado")) {
                return "Cyclone/Tornado Risk";
            }
            if (description.contains("squall")) {
                return "High Wind Squall Risk";
            }
        }

        if (description.contains("hurricane") || description.contains("cyclone")) {
            return "Cyclone Risk";
        }

        if (windSpeed >= 24) {
            return "Cyclone-like Wind Risk";
        }
        if (windSpeed >= 15) {
            return "Strong Wind Risk";
        }

        if ("rain".equals(condition) || "drizzle".equals(condition)) {
            return "Rain Risk";
        }

        return "Low Risk";
    }
}
