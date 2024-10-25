# Real-Time Data Processing System for Weather Monitoring with Rollups and Aggregates

## Project Overview

This project aims to develop a real-time data processing system that monitors weather conditions and provides summarized insights using rollups and aggregates. The system retrieves weather data from the OpenWeatherMap API and focuses on key weather parameters such as temperature and main weather conditions.

## Table of Contents

- [Features](#features)
- [Technologies Used](#technologies-used)
- [Installation](#installation)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Usage](#usage)


## Features
- Fetch current weather data for a specific city.
- Retrieve weather forecasts.
- Get historical weather data for a specific date.
- Generate a summary of weather data.
- Set weather thresholds for alerts.
- Check for weather alerts based on current conditions.
- Simulate weather data for a specific city.
- Generate weather trends for analysis.

## Technologies Used
- Java 17+
- Spring Boot
- Hibernate
- MySQL
- OpenWeatherMap API
- Postman for testing

## Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/nidak7/WeatherMonitoringSystem.git
   cd WeatherMonitoringSystem

### Configuration

Before running the application, you need to configure the `application.properties` file located in `src/main/resources`. Below are the required properties and how to set them up.

#### Step 1: Database Configuration
You need to provide your MySQL database connection details in the `application.properties` file.

1. Open the `application.properties` file.
2. Set the following properties:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/weather_db
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password

# OpenWeatherMap API Configuration
openWeatherMap.api.key=your_api_key
 ```
## Usage

Once the application is running, you can use **Postman** to test the following endpoints.

### API Endpoints

### 1. **Fetch Weather Data**
- **Method**: `GET`
- **Endpoint**: `/api/weather/fetch/{city}`
- **Description**: Fetches and stores the current weather data for the specified city.
- **Path Parameters**:
   - `city`: Name of the city to fetch weather data for.


### 2. **Get Weather Summary**
- **Endpoint**: `GET /api/weather/summary/{city}`
- **Description**: Retrieves current weather data for a specified city.
- **Query Parameters**:
    - `city`: Name of the city


### 3. **Set Threshold**
- **Endpoint**: `POST /api/weather/threshold`
- **Description**: Sets a weather threshold for alerts.
- **Request Body Example**:
    ```json
    {
      "attribute": "age",
      "operator": ">",
      "value": "18"
    }
    ```
- **Expected Response**:
  ```
  Weather threshold set successfully!
    
  ```

### 4. **Fetch Current Weather**
- **Endpoint**: `GET /api/weather/current/{city}`
- **Description**: Retrieves the current weather data for the specified city.
- **Path Parameters**:
    `city`: Name of the city
- **Expected Response**:
```json
    {
      "data": {
        "id": 142,
        "city": "Chennai",
        "temperature": 29.95,
        "feelsLike": 36.95,
        "weatherCondition": "Mist",
        "humidity": 83.0,
        "windSpeed": 3.09,
        "timestamp": "2024-10-25T20:47:00"
      },
      "message": "Weather data fetched successfully for Chennai"
    }
  ```

### 5. **Get Weather Forecast**
- **Endpoint**: `GET /api/weather/forecast/{city}`
- **Description**: Retrieves the weather forecast for the specified city.
- **Path Parameters**:
  `city`: Name of the city


### 6. **Fetch Weather History**
- **Endpoint**: `GET /api/weather/history/{city}`
- **Description**:  Retrieves historical weather data for the specified city on a given date.
- **Path Parameters**:
  `city`: Name of the city
- **Query  Parameters**:
    `date`: Date in yyyy-MM-dd format.

### 7. **Check Threshold**
- **Endpoint**: `POST /api/weather/check`
- **Description**:Checks for weather alerts based on current conditions.
- **Request Body Example**:
    ```json
    {
    "temperature": 50.0,
    "humidity": 60.0
    }
    ```
- **Expected Response**:
  ```
  ALERT: Temperature exceeds threshold of 25.0°C!
    
  ```

### 8. **Simulate Weather Data**
- **Endpoint**: `POST /api/weather/simulate/{city}`
- **Description**: Simulates weather data for a specific city.
- **Path Parameters**:
  `city`: Name of the city
- **Expected Response**:
```json
    {
      "id": 149,
      "city": "Rajasthan",
      "temperature": 27.83,
      "feelsLike": 26.78,
      "weatherCondition": "clear sky",
      "humidity": 25.0,
      "windSpeed": 3.1,
      "timestamp": "2024-10-25T20:55:53.4665173"
    } 
  ```

### 9. **Get Weather Trends**
- **Endpoint**: `GET /api/weather/trends/{city}`
- **Description**: Retrieves weather trends for analysis.
- **Path Parameters**:
  `city`: Name of the city
- **Expected Response**:
```Weather trends for Bangalore:

    Date	Temperature	Condition
    2024-10-23	22.27°C	Rain
    2024-10-24	22.1°C	Drizzle
    2024-10-24	24.16°C	Clouds
    2024-10-24	30.0°C	Haze
  ```