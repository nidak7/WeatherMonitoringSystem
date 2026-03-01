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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_weather_summary",
        indexes = {
                @Index(name = "idx_daily_summary_city_date", columnList = "city, summaryDate")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DailyWeatherSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private LocalDate summaryDate;

    @Column(nullable = false)
    private double averageTemperature;

    @Column(nullable = false)
    private double maxTemperature;

    @Column(nullable = false)
    private double minTemperature;

    @Column(nullable = false)
    private String dominantWeatherCondition;

    @Column(nullable = false)
    private String dominantWeatherConditionReason;

    @Column(nullable = false)
    private double averageHumidity;

    @Column(nullable = false)
    private double averageWindSpeed;

    @Column(nullable = false)
    private int totalSamples;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
