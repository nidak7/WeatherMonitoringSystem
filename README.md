# WeatherPulse

A simple weather monitoring project I built with Spring Boot + React.
It shows live weather for any city, keeps weather history in a database, and has a responsive frontend for quick city checks.

## Live Project

- Frontend (GitHub Pages): **https://nidak7.github.io/WeatherMonitoringSystem/**

## What this app does

- Search weather by city
- View current weather (temperature, feels like, humidity, wind speed)
- View short-term forecast cards
- Click forecast cards to inspect details
- Auto-refresh weather updates
- Store weather entries through backend APIs
- Fall back to Open-Meteo in frontend when backend is not reachable

## Tech stack

- Java 17
- Spring Boot 3
- Spring Data JPA
- H2 (default local DB), MySQL supported via env vars
- React + Vite
- GitHub Actions + GitHub Pages (frontend deployment)
- Docker + Render blueprint (backend deployment template)

## Project structure

```text
weather/
  src/                      # Spring Boot backend
  frontend/                 # React frontend
  .github/workflows/        # CI/CD workflow for GitHub Pages
  Dockerfile                # Backend container build
  render.yaml               # Backend deployment blueprint
```

## Local run

### Backend

1. Set `OPENWEATHER_API_KEY`
2. Run:

```bash
./mvnw spring-boot:run
```

Backend starts at `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts at `http://localhost:5173`.

## Environment variables

### Backend

- `OPENWEATHER_API_KEY` (required for OpenWeather calls)
- `PORT` (default: `8080`)
- `APP_CORS_ALLOWED_ORIGINS`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME`

### Frontend

- `VITE_API_BASE_URL` (default: `http://localhost:8080`)

## API endpoints

- `GET /api/weather/current/{city}`
- `GET /api/weather/forecast/{city}`
- `GET /api/weather/fetch/{city}`
- `GET /api/weather/history/{city}?date=yyyy-MM-dd`
- `GET /api/weather/summary/{city}?date=yyyy-MM-dd`
- `GET /api/weather/trends/{city}`
- `GET /api/weather/cities`
- `POST /api/weather/threshold`
- `POST /api/weather/check`
- `POST /api/weather/simulate/{city}`

## How to check full project flow

1. Open the live link: `https://nidak7.github.io/WeatherMonitoringSystem/`
2. Search a city name (for example: `Bangalore`, `Pune`, `London`).
3. Check top-right source label:
   - `Backend API` means hosted backend responded.
   - `Open-Meteo fallback` means frontend switched to direct weather source.
4. Click different forecast cards to verify interactive detail updates.
5. Toggle `C/F` and verify temperature conversion.
6. If you want to test backend APIs directly, run backend locally and hit:
   - `http://localhost:8080/api/weather/current/Bangalore`
   - `http://localhost:8080/api/weather/forecast/Bangalore`

## Notes

- Frontend deployment is automatic from `master` through `.github/workflows/deploy-pages.yml`.
- `render.yaml` and `Dockerfile` are included for backend hosting on Render or any Docker host.
