import { useCallback, useEffect, useMemo, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const defaultCities = ["Delhi", "Mumbai", "Bangalore", "Chennai", "Kolkata", "Hyderabad"];

function formatDateTime(value) {
  if (!value) return "N/A";
  const date = new Date(value);
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function formatTime(value) {
  if (!value) return "";
  const date = new Date(value);
  return new Intl.DateTimeFormat("en-IN", { hour: "2-digit", minute: "2-digit" }).format(date);
}

async function fetchJson(url) {
  const response = await fetch(url);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload?.message || payload?.data || "Unable to process request.");
  }
  return payload;
}

function App() {
  const [cityInput, setCityInput] = useState("Bangalore");
  const [activeCity, setActiveCity] = useState("Bangalore");
  const [cities, setCities] = useState(defaultCities);
  const [currentWeather, setCurrentWeather] = useState(null);
  const [forecast, setForecast] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadWeather = useCallback(async (city) => {
    setLoading(true);
    setError("");
    try {
      const normalizedCity = city.trim();
      const [currentPayload, forecastPayload] = await Promise.all([
        fetchJson(`${API_BASE_URL}/api/weather/current/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/forecast/${encodeURIComponent(normalizedCity)}`)
      ]);
      setCurrentWeather(currentPayload.data || null);
      setForecast(Array.isArray(forecastPayload.data) ? forecastPayload.data : []);
    } catch (requestError) {
      setError(requestError.message);
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
    }, 60000);
    return () => clearInterval(intervalId);
  }, [activeCity, loadWeather]);

  const nextForecast = useMemo(() => forecast.slice(0, 8), [forecast]);

  const handleSearchSubmit = (event) => {
    event.preventDefault();
    const normalizedCity = cityInput.trim();
    if (!normalizedCity) {
      return;
    }
    setActiveCity(normalizedCity);
  };

  return (
    <div className="app-shell">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <header className="topbar">
        <div>
          <p className="eyebrow">Personal Project</p>
          <h1>WeatherPulse</h1>
        </div>
        <p className="status-chip">{loading ? "Refreshing..." : "Live feed active"}</p>
      </header>

      <main className="layout">
        <section className="panel hero">
          <form className="search-row" onSubmit={handleSearchSubmit}>
            <input
              value={cityInput}
              onChange={(event) => setCityInput(event.target.value)}
              placeholder="Search a city"
              aria-label="City"
            />
            <button type="submit">Check Weather</button>
          </form>

          <div className="city-quick-picks">
            {cities.slice(0, 6).map((city) => (
              <button
                key={city}
                className={`ghost-btn ${city === activeCity ? "active" : ""}`}
                onClick={() => {
                  setCityInput(city);
                  setActiveCity(city);
                }}
                type="button"
              >
                {city}
              </button>
            ))}
          </div>

          {error ? <p className="error-text">{error}</p> : null}

          <div className="hero-grid">
            <div className="main-weather-card">
              <p className="city-name">{activeCity}</p>
              <p className="temperature">{currentWeather ? `${currentWeather.temperature} C` : "--"}</p>
              <p className="condition">{currentWeather?.weatherCondition || "Waiting for data"}</p>
              <p className="timestamp">Updated: {formatDateTime(currentWeather?.timestamp)}</p>
            </div>
            <div className="metrics-grid">
              <article className="metric-card">
                <h3>Feels Like</h3>
                <p>{currentWeather ? `${currentWeather.feelsLike} C` : "--"}</p>
              </article>
              <article className="metric-card">
                <h3>Humidity</h3>
                <p>{currentWeather ? `${currentWeather.humidity}%` : "--"}</p>
              </article>
              <article className="metric-card">
                <h3>Wind</h3>
                <p>{currentWeather ? `${currentWeather.windSpeed} m/s` : "--"}</p>
              </article>
            </div>
          </div>
        </section>

        <section className="panel forecast">
          <div className="panel-head">
            <h2>Forecast Timeline</h2>
            <p>Next 24 hours (3-hour intervals)</p>
          </div>

          <div className="forecast-grid">
            {nextForecast.map((item, index) => (
              <article className="forecast-card" key={`${item.timestamp}-${index}`}>
                <p className="forecast-time">{formatTime(item.timestamp)}</p>
                <p className="forecast-temp">{item.temperature} C</p>
                <p className="forecast-condition">{item.weatherCondition}</p>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
