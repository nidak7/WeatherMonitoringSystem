# WeatherPulse

WeatherPulse is a full-stack weather monitoring system built with Spring Boot and React.
It continuously pulls live weather updates, stores records, computes daily rollups, and exposes alerts + summaries through APIs and UI.

## Live frontend

- https://nidak7.github.io/WeatherMonitoringSystem/

Important: this frontend now uses **backend-only** weather data.  
Set `VITE_API_BASE_URL` to your running backend URL before deploying frontend.

## Assignment 2 mapping (Weather Monitoring)

This project covers the expected requirements from the second assignment:

1. Real-time weather retrieval for Indian metros  
Cities: `Delhi, Mumbai, Chennai, Bangalore, Kolkata, Hyderabad`  
Implemented via scheduled polling (`weather.polling.fixed-rate-ms`, default 5 minutes).

2. Temperature conversion from Kelvin to Celsius  
OpenWeather responses are consumed in Kelvin and converted in backend service.

3. Daily rollups and aggregates  
For each city/day, backend stores:
- average temperature
- max temperature
- min temperature
- dominant weather condition
- dominant condition reason
- average humidity
- average wind speed
- sample count

4. Alerting thresholds  
Supports configurable thresholds for:
- temperature
- weather condition  
Alerts are triggered after consecutive breaches and persisted.

5. Visualizations  
Frontend shows:
- current weather
- forecast
- daily summary rollup
- recent alerts

## Tech stack

- Backend: Java 17, Spring Boot 3, Spring Data JPA
- Database: H2 (default), MySQL supported via env vars
- Frontend: React + Vite
- Deployment: GitHub Pages for frontend, Docker/Render blueprint for backend

## Backend setup

1. Configure env vars:
- `OPENWEATHER_API_KEY` (required)
- `PORT` (optional, default `8080`)
- `APP_CORS_ALLOWED_ORIGINS` (for frontend origins)

2. Run backend:

```bash
./mvnw spring-boot:run
```

Backend base URL: `http://localhost:8080`

## Frontend setup

1. Move to frontend:

```bash
cd frontend
```

2. Configure API base URL:

```bash
cp .env.example .env
```

Set in `.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

3. Run:

```bash
npm install
npm run dev
```

## Key backend endpoints

- `GET /api/weather/current/{city}`
- `GET /api/weather/current/tracked`
- `GET /api/weather/forecast/{city}`
- `GET /api/weather/daily-summary/{city}`
- `GET /api/weather/daily-summary/tracked`
- `GET /api/weather/alerts`
- `GET /api/weather/alerts?city={city}`
- `GET /api/weather/history/{city}?date=yyyy-MM-dd`
- `GET /api/weather/summary/{city}?date=yyyy-MM-dd`
- `GET /api/weather/trends/{city}`
- `POST /api/weather/threshold`

## Example threshold payloads

Temperature threshold:

```json
{
  "condition": "temperature",
  "threshold": 35,
  "consecutiveUpdates": 2,
  "alertMessage": "High temperature breach"
}
```

Weather condition threshold:

```json
{
  "condition": "weatherCondition",
  "weatherCondition": "Rain",
  "consecutiveUpdates": 2,
  "alertMessage": "Continuous rain detected"
}
```

## Notes

- Backend performs startup warm-up and then periodic polling.
- Daily summaries and alerts are persisted for analysis.
- Frontend is intentionally backend-driven for evaluator checks.
