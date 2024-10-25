package com.zeotap_assignment_2.weather.service;

import com.zeotap_assignment_2.weather.entity.Weather;
import com.zeotap_assignment_2.weather.entity.WeatherSummary;
import com.zeotap_assignment_2.weather.entity.WeatherThreshold;
import com.zeotap_assignment_2.weather.repository.WeatherRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeatherService {

    @Value("${openWeatherMap.api.key}")
    private String apiKey;

    private final WeatherRepository weatherRepository;
    private final List<WeatherThreshold> thresholds = new ArrayList<>();

    private final Map<String, Integer> consecutiveBreaches = new HashMap<>();
    @Autowired
    public WeatherService(WeatherRepository weatherRepository) {
        this.weatherRepository = weatherRepository;
    }


    public void setThreshold(WeatherThreshold threshold) {
        thresholds.add(threshold);
    }


    public String checkAlerts(Weather weather) {
        for (WeatherThreshold threshold : thresholds) {
            if ("temperature".equals(threshold.getCondition())) {
                double thresholdTemp = threshold.getThreshold();
                if (weather.getTemperature() > thresholdTemp) {
                    consecutiveBreaches.put(weather.getCity(), consecutiveBreaches.getOrDefault(weather.getCity(), 0) + 1);
                    if (consecutiveBreaches.get(weather.getCity()) >= 2) {
                        System.out.println("Breach counts: " + consecutiveBreaches);
                        return threshold.getAlertMessage();
                    }
                } else {
                    consecutiveBreaches.put(weather.getCity(), 0);
                }
            }
        }
        return "Temperature is within normal range.";
    }

    @Scheduled(fixedRate = 300000)
    public void fetchWeatherForAllCities() {
        String[] cities = {"Delhi", "Mumbai", "Chennai", "Bangalore", "Kolkata", "Hyderabad"};
        for (String city : cities) {
            try {
                fetchWeatherData(city);
                System.out.println("Fetched weather data for " + city);
            } catch (Exception e) {
                System.err.println("Failed to fetch weather for " + city + ": " + e.getMessage());
            }
        }
    }


    public Weather fetchWeatherData(String city) throws Exception {
        String apiUrl = String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s", city, apiKey);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(apiUrl, String.class);
        } catch (Exception e) {
            throw new Exception("Error connecting to OpenWeatherMap API: " + e.getMessage());
        }
        if (response.getStatusCode() == HttpStatus.OK) {
            JSONObject weatherJson = new JSONObject(response.getBody());
            double tempKelvin = weatherJson.getJSONObject("main").getDouble("temp");
            double feelsLikeKelvin = weatherJson.getJSONObject("main").getDouble("feels_like");
            String weatherCondition = weatherJson.getJSONArray("weather").getJSONObject(0).getString("main");
            double humidity = weatherJson.getJSONObject("main").getDouble("humidity");
            double windSpeed = weatherJson.getJSONObject("wind").getDouble("speed");
            LocalDateTime timestamp = LocalDateTime.now().withSecond(0).withNano(0);
            Weather existingWeather = weatherRepository.findByCityAndTimestamp(city, timestamp);
            Weather newWeather = existingWeather != null ? existingWeather : new Weather();
            newWeather.setCity(city);
            newWeather.setTemperature(roundToTwoDecimalPlaces(kelvinToCelsius(tempKelvin)));
            newWeather.setFeelsLike(roundToTwoDecimalPlaces(kelvinToCelsius(feelsLikeKelvin)));
            newWeather.setWeatherCondition(weatherCondition);
            newWeather.setHumidity(humidity);
            newWeather.setWindSpeed(windSpeed);
            newWeather.setTimestamp(timestamp);
            weatherRepository.save(newWeather);
            return newWeather;
        }  else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            throw new Exception("API rate limit exceeded. Please try again later.");
        } else {
            throw new Exception("Failed to fetch weather data. API responded with status: " + response.getStatusCode());
        }
    }

    public List<Weather> fetchWeatherForecast(String city) throws Exception {
        String apiUrl = String.format("https://api.openweathermap.org/data/2.5/forecast?q=%s&appid=%s", city, apiKey);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);
        List<Weather> forecastList = new ArrayList<>();
        if (response.getStatusCode() == HttpStatus.OK) {
            JSONObject forecastJson = new JSONObject(response.getBody());
            JSONArray list = forecastJson.getJSONArray("list");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < list.length(); i++) {
                JSONObject forecastData = list.getJSONObject(i);
                JSONObject main = forecastData.getJSONObject("main");
                JSONObject wind = forecastData.getJSONObject("wind");
                double tempKelvin = main.getDouble("temp");
                double feelsLikeKelvin = main.getDouble("feels_like");
                double humidity = main.getDouble("humidity");
                double windSpeed = wind.getDouble("speed");
                String weatherCondition = forecastData.getJSONArray("weather").getJSONObject(0).getString("main");
                LocalDateTime timestamp = LocalDateTime.parse(forecastData.getString("dt_txt"), formatter);
                Weather forecast = new Weather();
                forecast.setCity(city);
                forecast.setTemperature(roundToTwoDecimalPlaces(kelvinToCelsius(tempKelvin)));
                forecast.setFeelsLike(roundToTwoDecimalPlaces(kelvinToCelsius(feelsLikeKelvin)));
                forecast.setHumidity(humidity);
                forecast.setWindSpeed(windSpeed);
                forecast.setWeatherCondition(weatherCondition);
                forecast.setTimestamp(timestamp);
                forecast.setId(Long.valueOf(i));
                forecastList.add(forecast);
            }
        } else {
            throw new Exception("Failed to fetch weather forecast. API responded with status: " + response.getStatusCode());
        }
        return forecastList;
    }

    public List<Weather> getWeatherDataForCity(String city, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return weatherRepository.findByCityAndTimestampBetween(city, startOfDay, endOfDay);
    }
    public double kelvinToCelsius(double kelvin) {
        return kelvin - 273.15;
    }
    private double roundToTwoDecimalPlaces(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public WeatherSummary calculateSummary(List<Weather> weatherData) {
        double averageTemp = weatherData.stream().mapToDouble(Weather::getTemperature).average().orElse(0);
        double maxTemp = weatherData.stream().mapToDouble(Weather::getTemperature).max().orElse(0);
        double minTemp = weatherData.stream().mapToDouble(Weather::getTemperature).min().orElse(0);
        Map<String, Long> conditionCount = weatherData.stream()
                .collect(Collectors.groupingBy(Weather::getWeatherCondition, Collectors.counting()));
        String dominantCondition = conditionCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();

        double averageHumidity = weatherData.stream().mapToDouble(Weather::getHumidity).average().orElse(0);
        double averageWindSpeed = weatherData.stream().mapToDouble(Weather::getWindSpeed).average().orElse(0);

        return new WeatherSummary(averageTemp, maxTemp, minTemp, dominantCondition, averageHumidity, averageWindSpeed);
    }
    public Weather simulateCurrentWeatherData(String city) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + city + "&appid=" + apiKey + "&units=metric";

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        Map<String, Object> main = (Map<String, Object>) response.get("main");
        Map<String, Object> wind = (Map<String, Object>) response.get("wind");
        String weatherCondition = ((Map<String, Object>) ((List<Object>) response.get("weather")).get(0)).get("description").toString();

        double temperature = Double.parseDouble(main.get("temp").toString());
        double feelsLike = Double.parseDouble(main.get("feels_like").toString());
        double humidity = Double.parseDouble(main.get("humidity").toString());
        double windSpeed = Double.parseDouble(wind.get("speed").toString());
        Weather newWeather = new Weather();
        newWeather.setCity(city);
        newWeather.setTemperature(temperature);
        newWeather.setFeelsLike(feelsLike);
        newWeather.setHumidity(humidity);
        newWeather.setWindSpeed(windSpeed);
        newWeather.setWeatherCondition(weatherCondition);
        newWeather.setTimestamp(LocalDateTime.now());
        weatherRepository.save(newWeather);
        return newWeather;
    }


    public Weather fetchCurrentWeather(String city) throws Exception {
        String apiUrl = String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s", city, apiKey);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            JSONObject weatherJson = new JSONObject(response.getBody());
            double tempKelvin = weatherJson.getJSONObject("main").getDouble("temp");
            double feelsLikeKelvin = weatherJson.getJSONObject("main").optDouble("feels_like", 0);
            String weatherCondition = weatherJson.getJSONArray("weather").getJSONObject(0).getString("main");
            double humidity = weatherJson.getJSONObject("main").getDouble("humidity");
            double windSpeed = weatherJson.getJSONObject("wind").getDouble("speed");
            Weather currentWeather = new Weather();
            currentWeather.setCity(city);
            currentWeather.setTemperature(roundToTwoDecimalPlaces(kelvinToCelsius(tempKelvin)));
            currentWeather.setFeelsLike(roundToTwoDecimalPlaces(kelvinToCelsius(feelsLikeKelvin)));
            currentWeather.setWeatherCondition(weatherCondition);
            currentWeather.setHumidity(humidity);
            currentWeather.setWindSpeed(windSpeed);
            currentWeather.setTimestamp(LocalDateTime.now().withSecond(0).withNano(0));
            weatherRepository.save(currentWeather);
            return currentWeather;
        } else {
            throw new Exception("Failed to fetch current weather. API responded with status: " + response.getStatusCode());
        }
    }

    public String generateWeatherTrends(String city) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);
        List<Weather> weatherHistory = weatherRepository.findByCityAndTimestampBetween(city, start, end);
        StringBuilder trends = new StringBuilder();
        trends.append("Weather trends for ").append(city).append(":\n");
        trends.append("Date\tTemperature\tCondition\n");
        for (Weather weather : weatherHistory) {
            trends.append(weather.getTimestamp().toLocalDate()).append("\t")
                    .append(weather.getTemperature()).append("°C\t")
                    .append(weather.getWeatherCondition()).append("\n");
        }
        return trends.toString();
    }
}
