package com.zeotap_assignment_2.weather.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSummary {

    private double averageTemperature;
    private double maxTemperature;
    private double minTemperature;
    private String dominantWeatherCondition;
    private double averageHumidity;
    private double averageWindSpeed;

}

