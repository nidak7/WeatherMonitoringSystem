package com.nidak.weatherpulse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "weather_data")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Weather {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private double temperature;

    @Column(nullable = false)
    private double feelsLike;

    @Column(nullable = false)
    private String weatherCondition;

    @Column
    private String weatherDescription;

    @Column
    private Integer weatherCode;

    @Column
    private String weatherRisk;

    @Column(nullable = false)
    private double humidity;

    @Column(nullable = false)
    private double windSpeed;

    @Column(nullable = false)
    private LocalDateTime timestamp;


}
