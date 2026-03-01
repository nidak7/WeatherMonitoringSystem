# WeatherPulse

This is my weather monitoring project built with Spring Boot + React.
I wanted it to feel like a small real product, not just an API demo.

Live frontend:
- https://nidak7.github.io/WeatherMonitoringSystem/

## What it does

- Pulls weather updates for Indian metro cities on a fixed interval
- Stores weather records in the database
- Builds daily rollups (avg/max/min temp, dominant condition, humidity, wind)
- Tracks threshold alerts (temperature or weather condition with consecutive breaches)
- Shows current weather, forecast, daily summary, and recent alerts in UI

## Stack

- Java 17
- Spring Boot 3
- Spring Data JPA
- H2 (default), MySQL supported
- React + Vite

## Run locally

### 1. Backend

Set env vars:

```env
OPENWEATHER_API_KEY=your_key
PORT=8080
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Run:

```bash
./mvnw spring-boot:run
```

Backend URL: `http://localhost:8080`

### 2. Frontend

```bash
cd frontend
npm install
```

Create `.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Run:

```bash
npm run dev
```

Frontend URL: `http://localhost:5173`

## Useful APIs

- `GET /api/weather/current/{city}`
- `GET /api/weather/current/tracked`
- `GET /api/weather/forecast/{city}`
- `GET /api/weather/daily-summary/{city}`
- `GET /api/weather/daily-summary/tracked`
- `GET /api/weather/alerts`
- `GET /api/weather/alerts?city={city}`
- `POST /api/weather/threshold`

## Threshold payload examples

Temperature:

```json
{
  "condition": "temperature",
  "threshold": 35,
  "consecutiveUpdates": 2,
  "alertMessage": "High temperature breach"
}
```

Weather condition:

```json
{
  "condition": "weatherCondition",
  "weatherCondition": "Rain",
  "consecutiveUpdates": 2,
  "alertMessage": "Continuous rain detected"
}
```

## Quick flow check

1. Start backend.
2. Open frontend.
3. Search a city like `Bangalore` or `Hyderabad`.
4. Confirm current weather and forecast load.
5. Check daily summary + alerts section.
6. Hit APIs directly in browser/Postman for verification.

## Notes

- Polling interval is configurable with `WEATHER_POLLING_FIXED_RATE_MS`.
- Tracked cities are configurable with `WEATHER_TRACKED_CITIES`.
- Data freshness window is configurable with `WEATHER_DATA_STALE_MINUTES`.
