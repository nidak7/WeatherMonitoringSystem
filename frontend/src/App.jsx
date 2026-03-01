import { useCallback, useEffect, useMemo, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const defaultCities = ["Bangalore", "Mumbai", "Delhi", "Chennai", "Hyderabad", "Kolkata"];

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

function formatDateOnly(value) {
  if (!value) return "N/A";
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value;
  }
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium"
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

function App() {
  const [cityInput, setCityInput] = useState("Bangalore");
  const [activeCity, setActiveCity] = useState("Bangalore");
  const [cities, setCities] = useState(defaultCities);
  const [currentWeather, setCurrentWeather] = useState(null);
  const [forecast, setForecast] = useState([]);
  const [dailySummary, setDailySummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [selectedForecastIndex, setSelectedForecastIndex] = useState(0);
  const [unit, setUnit] = useState("C");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadWeather = useCallback(async (city) => {
    const normalizedCity = city.trim();
    if (!normalizedCity) return;

    setLoading(true);
    setError("");
    setSelectedForecastIndex(0);

    try {
      const [currentPayload, forecastPayload, summaryPayload, alertsPayload] = await Promise.all([
        fetchJson(`${API_BASE_URL}/api/weather/current/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/forecast/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/daily-summary/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/alerts?city=${encodeURIComponent(normalizedCity)}`)
      ]);

      setCurrentWeather(currentPayload.data || null);
      setForecast(Array.isArray(forecastPayload.data) ? forecastPayload.data.slice(0, 12) : []);
      setDailySummary(summaryPayload.data || null);
      setAlerts(Array.isArray(alertsPayload.data) ? alertsPayload.data.slice(0, 5) : []);
    } catch (backendError) {
      setCurrentWeather(null);
      setForecast([]);
      setDailySummary(null);
      setAlerts([]);
      setError(backendError.message || "Backend is unavailable.");
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
          <p className="source-pill">Backend API</p>
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

        <section className="panel">
          <div className="panel-head">
            <h2>Daily Summary And Alerts</h2>
            <p>Rollups and threshold monitoring from backend</p>
          </div>

          <article className="detail-card">
            <h3>Today summary ({dailySummary?.city || activeCity})</h3>
            {dailySummary ? (
              <div className="detail-grid">
                <p>Date: <strong>{formatDateOnly(dailySummary.summaryDate)}</strong></p>
                <p>Avg temp: <strong>{toDisplayTemp(dailySummary.averageTemperature, unit)} {unit}</strong></p>
                <p>Max temp: <strong>{toDisplayTemp(dailySummary.maxTemperature, unit)} {unit}</strong></p>
                <p>Min temp: <strong>{toDisplayTemp(dailySummary.minTemperature, unit)} {unit}</strong></p>
                <p>Dominant: <strong>{dailySummary.dominantWeatherCondition}</strong></p>
                <p>Samples: <strong>{dailySummary.totalSamples}</strong></p>
              </div>
            ) : (
              <p className="empty-msg">{loading ? "Loading daily summary..." : "No summary data yet."}</p>
            )}
          </article>

          <article className="detail-card">
            <h3>Recent alerts</h3>
            {alerts.length > 0 ? (
              <div className="alerts-list">
                {alerts.map((alert) => (
                  <div className="alert-item" key={`${alert.id}-${alert.createdAt}`}>
                    <p className="alert-title">{alert.alertMessage}</p>
                    <p className="alert-meta">
                      {alert.alertType} | observed: {alert.observedValue} | threshold: {alert.thresholdValue}
                    </p>
                    <p className="alert-meta">{formatDateTime(alert.createdAt)}</p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="empty-msg">No alerts triggered for this city.</p>
            )}
          </article>
        </section>
      </main>
    </div>
  );
}

export default App;
