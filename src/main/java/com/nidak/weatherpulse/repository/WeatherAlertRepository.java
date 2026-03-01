package com.nidak.weatherpulse.repository;

import com.nidak.weatherpulse.entity.WeatherAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeatherAlertRepository extends JpaRepository<WeatherAlert, Long> {

    List<WeatherAlert> findTop50ByOrderByCreatedAtDesc();

    List<WeatherAlert> findTop20ByCityOrderByCreatedAtDesc(String city);

    WeatherAlert findTopByCityAndAlertTypeOrderByCreatedAtDesc(String city, String alertType);
}
