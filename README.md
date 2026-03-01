# WeatherPulse

WeatherPulse is a personal full-stack weather monitoring project built for portfolio-quality presentation.
It provides live city weather data, forecast visualization, historical persistence, and a polished React dashboard.

## Stack

- Backend: Java 17, Spring Boot 3, Spring Data JPA
- Database: H2 (default), MySQL-compatible via environment variables
- Weather provider: OpenWeatherMap API
- Frontend: React + Vite
- Deployment-ready: Docker (backend), Vercel config (frontend), Render blueprint (backend)

## Features

- Live weather for any city (`/api/weather/current/{city}`)
- 5-day forecast (`/api/weather/forecast/{city}`)
- Historical weather by date (`/api/weather/history/{city}?date=yyyy-MM-dd`)
- Daily weather summary (`/api/weather/summary/{city}?date=yyyy-MM-dd`)
- Threshold-based alert checks
- Auto-poll scheduler for tracked cities
- Recruiter-ready responsive frontend with live refresh

## Project Structure

```text
weather/
  src/                      # Spring Boot API
  frontend/                 # React app
  Dockerfile                # Backend container
  render.yaml               # Render backend deployment template
  .env.example              # Backend env reference
```

## Backend Setup

1. Create an OpenWeatherMap API key.
2. Copy `.env.example` values into your environment.
3. Run:

```bash
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080` by default.

## Frontend Setup

1. Go to frontend folder:

```bash
cd frontend
```

2. Set API base URL:

```bash
cp .env.example .env
```

3. Install and run:

```bash
npm install
npm run dev
```

Frontend starts on `http://localhost:5173`.

## Important Environment Variables

### Backend

- `OPENWEATHER_API_KEY` (required)
- `PORT` (optional, default `8080`)
- `APP_CORS_ALLOWED_ORIGINS` (optional)
- `SPRING_DATASOURCE_*` (optional for external DB)

### Frontend

- `VITE_API_BASE_URL` (API URL, default `http://localhost:8080`)

## API Endpoints

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

## Deployment

### Backend (Render or any Docker host)

- Build/deploy using `Dockerfile`
- Add env vars from `.env.example`
- Use `render.yaml` directly on Render for quick setup

### Frontend (Vercel)

- Import `frontend/` as project root
- Add `VITE_API_BASE_URL=https://your-backend-domain`
- `vercel.json` is already configured for Vite SPA routing

## Notes

- The app defaults to H2 file DB for local simplicity.
- Switch to MySQL by setting `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and driver class.
- If `OPENWEATHER_API_KEY` is missing, API returns a clear error response.
