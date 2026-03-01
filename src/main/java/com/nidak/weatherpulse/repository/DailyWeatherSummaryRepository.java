package com.nidak.weatherpulse.repository;

import com.nidak.weatherpulse.entity.DailyWeatherSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyWeatherSummaryRepository extends JpaRepository<DailyWeatherSummaryEntity, Long> {

    Optional<DailyWeatherSummaryEntity> findByCityAndSummaryDate(String city, LocalDate summaryDate);

    List<DailyWeatherSummaryEntity> findBySummaryDate(LocalDate summaryDate);
}
