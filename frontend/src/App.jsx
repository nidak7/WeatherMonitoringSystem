import { useCallback, useEffect, useMemo, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const defaultCities = ["Bangalore", "Mumbai", "Delhi", "Chennai", "Hyderabad", "Kolkata"];

const weatherCodeMap = {
  0: "Clear",
  1: "Mostly clear",
  2: "Partly cloudy",
  3: "Cloudy",
  45: "Fog",
  48: "Fog",
  51: "Drizzle",
  53: "Drizzle",
  55: "Drizzle",
  61: "Rain",
  63: "Rain",
  65: "Heavy rain",
  71: "Snow",
  73: "Snow",
  75: "Snow",
  80: "Rain showers",
  81: "Rain showers",
  82: "Heavy showers",
  95: "Thunderstorm"
};

function toDisplayTemp(value, unit) {
  if (value === null || value === undefined) return "--";
  if (unit === "F") {
    return ((value * 9) / 5 + 32).toFixed(1);
  }
  return Number(value).toFixed(1);
}

function formatDateTime(value) {
  if (!value) return "N/A";
  const date = new Date(value);
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function formatTime(value) {
  if (!value) return "--";
  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

async function fetchJson(url) {
  const response = await fetch(url);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload?.message || payload?.data || "Request failed.");
  }
  return payload;
}

async function fetchFromOpenMeteo(city) {
  const geocodeUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(
    city
  )}&count=1&language=en&format=json`;
  const geoResponse = await fetch(geocodeUrl);
  const geoJson = await geoResponse.json();

  if (!geoJson?.results?.length) {
    throw new Error(`City not found: ${city}`);
  }

  const place = geoJson.results[0];
  const forecastUrl =
    `https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}` +
    `&longitude=${place.longitude}` +
    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code" +
    "&hourly=temperature_2m,weather_code,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
    "&forecast_days=2&timezone=auto";

  const weatherResponse = await fetch(forecastUrl);
  const weatherJson = await weatherResponse.json();

  const current = weatherJson.current;
  const hourly = weatherJson.hourly;
  const forecast = [];

  for (let i = 0; i < hourly.time.length && forecast.length < 12; i += 3) {
    const weatherCode = hourly.weather_code[i];
    forecast.push({
      city: place.name,
      temperature: hourly.temperature_2m[i],
      feelsLike: hourly.apparent_temperature[i],
      humidity: hourly.relative_humidity_2m[i],
      windSpeed: hourly.wind_speed_10m[i],
      weatherCondition: weatherCodeMap[weatherCode] || "Weather update",
      timestamp: hourly.time[i]
    });
  }

  return {
    currentWeather: {
      city: place.name,
      temperature: current.temperature_2m,
      feelsLike: current.apparent_temperature,
      humidity: current.relative_humidity_2m,
      windSpeed: current.wind_speed_10m,
      weatherCondition: weatherCodeMap[current.weather_code] || "Weather update",
      timestamp: current.time
    },
    forecast
  };
}

function App() {
  const [cityInput, setCityInput] = useState("Bangalore");
  const [activeCity, setActiveCity] = useState("Bangalore");
  const [cities, setCities] = useState(defaultCities);
  const [currentWeather, setCurrentWeather] = useState(null);
  const [forecast, setForecast] = useState([]);
  const [selectedForecastIndex, setSelectedForecastIndex] = useState(0);
  const [unit, setUnit] = useState("C");
  const [source, setSource] = useState("Backend API");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadWeather = useCallback(async (city) => {
    const normalizedCity = city.trim();
    if (!normalizedCity) return;

    setLoading(true);
    setError("");
    setSelectedForecastIndex(0);

    try {
      const [currentPayload, forecastPayload] = await Promise.all([
        fetchJson(`${API_BASE_URL}/api/weather/current/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/forecast/${encodeURIComponent(normalizedCity)}`)
      ]);

      setCurrentWeather(currentPayload.data || null);
      setForecast(Array.isArray(forecastPayload.data) ? forecastPayload.data.slice(0, 12) : []);
      setSource("Backend API");
    } catch (backendError) {
      try {
        const fallbackData = await fetchFromOpenMeteo(normalizedCity);
        setCurrentWeather(fallbackData.currentWeather);
        setForecast(fallbackData.forecast);
        setSource("Open-Meteo fallback");
      } catch (fallbackError) {
        setError(fallbackError.message || backendError.message);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchJson(`${API_BASE_URL}/api/weather/cities`)
      .then((payload) => {
        if (Array.isArray(payload.data) && payload.data.length > 0) {
          setCities(payload.data);
        }
      })
      .catch(() => {
        setCities(defaultCities);
      });
  }, []);

  useEffect(() => {
    loadWeather(activeCity);
  }, [activeCity, loadWeather]);

  useEffect(() => {
    const intervalId = setInterval(() => {
      loadWeather(activeCity);
    }, 120000);
    return () => clearInterval(intervalId);
  }, [activeCity, loadWeather]);

  const selectedForecast = useMemo(() => {
    if (!forecast.length) return null;
    return forecast[Math.min(selectedForecastIndex, forecast.length - 1)];
  }, [forecast, selectedForecastIndex]);

  const handleSearchSubmit = (event) => {
    event.preventDefault();
    const normalizedCity = cityInput.trim();
    if (!normalizedCity) return;
    setActiveCity(normalizedCity);
  };

  return (
    <div className="app-shell">
      <div className="bg-shape shape-one" />
      <div className="bg-shape shape-two" />
      <header className="topbar">
        <div>
          <p className="eyebrow">Live Weather Dashboard</p>
          <h1>WeatherPulse</h1>
          <p className="subtext">Current conditions and short-term forecast for your city.</p>
        </div>
        <div className="header-controls">
          <p className="source-pill">{source}</p>
          <div className="unit-toggle" role="group" aria-label="Temperature unit">
            <button
              type="button"
              className={unit === "C" ? "active" : ""}
              onClick={() => setUnit("C")}
            >
              C
            </button>
            <button
              type="button"
              className={unit === "F" ? "active" : ""}
              onClick={() => setUnit("F")}
            >
              F
            </button>
          </div>
        </div>
      </header>

      <main className="layout">
        <section className="panel">
          <form className="search-row" onSubmit={handleSearchSubmit}>
            <input
              value={cityInput}
              onChange={(event) => setCityInput(event.target.value)}
              placeholder="Search city (for example: Pune, London, Tokyo)"
              aria-label="City"
            />
            <button type="submit">Search</button>
          </form>

          <div className="city-chips">
            {cities.slice(0, 6).map((city) => (
              <button
                key={city}
                type="button"
                className={`chip ${city === activeCity ? "active" : ""}`}
                onClick={() => {
                  setCityInput(city);
                  setActiveCity(city);
                }}
              >
                {city}
              </button>
            ))}
          </div>

          {error ? <p className="error-text">{error}</p> : null}

          <div className="weather-grid">
            <article className="hero-card">
              <p className="city-name">{currentWeather?.city || activeCity}</p>
              <h2 className="temp-value">
                {currentWeather ? toDisplayTemp(currentWeather.temperature, unit) : "--"}
                <span>{unit}</span>
              </h2>
              <p className="condition">{currentWeather?.weatherCondition || "Waiting for data"}</p>
              <p className="timestamp">Updated {formatDateTime(currentWeather?.timestamp)}</p>
            </article>

            <div className="metrics">
              <article className="metric-card">
                <h3>Feels like</h3>
                <p>{currentWeather ? `${toDisplayTemp(currentWeather.feelsLike, unit)} ${unit}` : "--"}</p>
              </article>
              <article className="metric-card">
                <h3>Humidity</h3>
                <p>{currentWeather?.humidity ?? "--"}%</p>
              </article>
              <article className="metric-card">
                <h3>Wind speed</h3>
                <p>{currentWeather?.windSpeed ?? "--"} m/s</p>
              </article>
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Forecast</h2>
            <p>Tap any card to inspect details</p>
          </div>

          <div className="forecast-grid">
            {forecast.map((item, index) => (
              <button
                key={`${item.timestamp}-${index}`}
                type="button"
                className={`forecast-card ${selectedForecastIndex === index ? "active" : ""}`}
                onClick={() => setSelectedForecastIndex(index)}
              >
                <p>{formatTime(item.timestamp)}</p>
                <strong>{toDisplayTemp(item.temperature, unit)} {unit}</strong>
                <span>{item.weatherCondition}</span>
              </button>
            ))}
          </div>

          <article className="detail-card">
            <h3>Selected forecast slot</h3>
            {selectedForecast ? (
              <div className="detail-grid">
                <p>Time: <strong>{formatDateTime(selectedForecast.timestamp)}</strong></p>
                <p>Condition: <strong>{selectedForecast.weatherCondition}</strong></p>
                <p>Feels like: <strong>{toDisplayTemp(selectedForecast.feelsLike, unit)} {unit}</strong></p>
                <p>Humidity: <strong>{selectedForecast.humidity}%</strong></p>
                <p>Wind: <strong>{selectedForecast.windSpeed} m/s</strong></p>
              </div>
            ) : (
              <p className="empty-msg">{loading ? "Loading forecast..." : "No forecast data yet."}</p>
            )}
          </article>
        </section>
      </main>
    </div>
  );
}

export default App;
