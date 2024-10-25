package com.zeotap_assignment_2.weather.repository;

import com.zeotap_assignment_2.weather.entity.Weather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WeatherRepository extends JpaRepository<Weather, Long> {
    List<Weather> findByCityAndTimestampBetween(String city, LocalDateTime start, LocalDateTime end);
    Weather findByCityAndTimestamp(String city, LocalDateTime timestamp);


}