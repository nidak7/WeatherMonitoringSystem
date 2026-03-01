package com.nidak.weatherpulse.repository;

import com.nidak.weatherpulse.entity.Weather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WeatherRepository extends JpaRepository<Weather, Long> {
    List<Weather> findByCityAndTimestampBetween(String city, LocalDateTime start, LocalDateTime end);
    Weather findByCityAndTimestamp(String city, LocalDateTime timestamp);
    Weather findTopByCityOrderByTimestampDesc(String city);

}
