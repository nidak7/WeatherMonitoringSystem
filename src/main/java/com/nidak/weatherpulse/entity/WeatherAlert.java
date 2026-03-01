package com.nidak.weatherpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "weather_alert",
        indexes = {
                @Index(name = "idx_weather_alert_city_timestamp", columnList = "city, createdAt")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WeatherAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String alertMessage;

    @Column(nullable = false)
    private String observedValue;

    @Column(nullable = false)
    private String thresholdValue;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
