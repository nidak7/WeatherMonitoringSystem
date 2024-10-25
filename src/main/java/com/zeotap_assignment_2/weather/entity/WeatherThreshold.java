package com.zeotap_assignment_2.weather.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WeatherThreshold {
    private String condition;
    private double threshold;
    private String alertMessage;

}
