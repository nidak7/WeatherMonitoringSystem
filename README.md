# WeatherPulse

This is my personal weather monitoring project.
I built it to practice a full flow: fetch live weather, store it, generate daily summaries, and show it in a clean dashboard.

Live project:
- https://nidak7.github.io/WeatherMonitoringSystem/

## What it does

- Current weather + short forecast for searched cities
- Daily rollup (avg/max/min temp, dominant condition, humidity, wind)
- Alerts for heat, wind, snow, dust, etc.
- Auto-refresh every 2 minutes

The backend uses OpenWeather as the main source.
If backend APIs are not reachable, the frontend can still show data using Open-Meteo fallback.

## Stack

- Spring Boot (Java 17)
- Spring Data JPA
- H2 by default (MySQL can be configured)
- React + Vite

## Run locally

### Backend

Set env vars:

```env
OPENWEATHER_API_KEY=your_key
PORT=8080
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Start server:

```bash
./mvnw spring-boot:run
```

Backend URL: `http://localhost:8080`

### Frontend

```bash
cd frontend
npm install
```

Create `frontend/.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Start UI:

```bash
npm run dev
```

Frontend URL: `http://localhost:5173`

## Quick flow check

1. Start backend, then frontend.
2. Open the dashboard and search any city.
3. Check that current weather, forecast cards, and risk badge are visible.
4. Scroll to `Daily Summary And Alerts` and confirm values are filled.
5. If weather is hot/severe, alerts should show in plain words.
6. Wait 2 minutes and verify the update time changes.

## Config you can tweak

- `WEATHER_TRACKED_CITIES`: Comma-separated city list the backend keeps updating automatically.
- `WEATHER_POLLING_FIXED_RATE_MS`: How often the backend fetches new data for tracked cities (in milliseconds).
- `WEATHER_DATA_STALE_MINUTES`: After this many minutes, saved data is treated as old and refreshed on the next request.
