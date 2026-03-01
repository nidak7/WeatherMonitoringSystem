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
  75: "Heavy snow",
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

function formatDateOnly(value) {
  if (!value) return "N/A";
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value;
  }
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium"
  }).format(new Date(value));
}

function deriveRisk(condition, description, windSpeed, temperature) {
  const text = `${condition || ""} ${description || ""}`.toLowerCase();
  if (text.includes("cyclone") || text.includes("hurricane") || text.includes("tornado")) return "Cyclone Risk";
  if (text.includes("sand") || text.includes("dust")) return "Sandstorm/Dust Risk";
  if (text.includes("snow")) return "Snow Risk";
  if (text.includes("thunder")) return "Thunderstorm Risk";
  if ((condition || "").toLowerCase().includes("clear") && (temperature || 0) >= 34) return "Sunny/Heat Risk";
  if ((temperature || 0) >= 40) return "Extreme Heat Risk";
  if ((windSpeed || 0) >= 20) return "Cyclone-like Wind Risk";
  if ((windSpeed || 0) >= 14) return "Strong Wind Risk";
  if (text.includes("rain")) return "Rain Risk";
  return "Low Risk";
}

function riskClass(risk) {
  if (!risk) return "risk-low";
  const label = risk.toLowerCase();
  if (label.includes("cyclone") || label.includes("sandstorm")) return "risk-high";
  if (label.includes("snow") || label.includes("thunder") || label.includes("strong") || label.includes("heat")) {
    return "risk-medium";
  }
  return "risk-low";
}

function isSunnyAndHot(weather) {
  if (!weather) return false;
  const condition = (weather.weatherCondition || "").toLowerCase();
  const description = (weather.weatherDescription || "").toLowerCase();
  return (condition.includes("clear") || description.includes("sun")) && Number(weather.temperature || 0) >= 34;
}

function buildClientSummary(city, currentWeather, forecast) {
  const records = [currentWeather, ...(forecast || [])].filter(Boolean);
  if (!records.length) return null;

  const temperatures = records
    .map((item) => Number(item.temperature))
    .filter((value) => !Number.isNaN(value));
  if (!temperatures.length) return null;

  const weatherCount = {};
  records.forEach((item) => {
    const key = item.weatherCondition || "Unknown";
    weatherCount[key] = (weatherCount[key] || 0) + 1;
  });
  const dominant = Object.entries(weatherCount).sort((a, b) => b[1] - a[1])[0]?.[0] || "Unknown";

  const avgHumidity =
    records.reduce((acc, item) => acc + Number(item.humidity || 0), 0) / Math.max(1, records.length);
  const avgWind =
    records.reduce((acc, item) => acc + Number(item.windSpeed || 0), 0) / Math.max(1, records.length);
  const timestamp = records[0]?.timestamp ? new Date(records[0].timestamp) : new Date();
  const summaryDate = timestamp.toISOString().slice(0, 10);

  return {
    city,
    summaryDate,
    averageTemperature: Number((temperatures.reduce((a, b) => a + b, 0) / temperatures.length).toFixed(2)),
    maxTemperature: Number(Math.max(...temperatures).toFixed(2)),
    minTemperature: Number(Math.min(...temperatures).toFixed(2)),
    dominantWeatherCondition: dominant,
    averageHumidity: Number(avgHumidity.toFixed(2)),
    averageWindSpeed: Number(avgWind.toFixed(2)),
    totalSamples: records.length
  };
}

function buildClientAlerts(currentWeather, forecast) {
  if (!currentWeather) return [];

  const alerts = [];
  const now = new Date().toISOString();

  if (isSunnyAndHot(currentWeather)) {
    alerts.push({
      id: `heat-${now}`,
      createdAt: now,
      alertType: "system.heat.sunny",
      alertMessage: "Sunny and hot conditions detected",
      observedValue: `${currentWeather.temperature} C`,
      thresholdValue: ">= 34 C with clear sky"
    });
  }

  const liveRisk = deriveRisk(
    currentWeather.weatherCondition,
    currentWeather.weatherDescription,
    currentWeather.windSpeed,
    currentWeather.temperature
  );
  if (liveRisk !== "Low Risk" && !liveRisk.includes("Rain")) {
    alerts.push({
      id: `risk-${now}`,
      createdAt: now,
      alertType: "system.risk.live",
      alertMessage: `${liveRisk} detected`,
      observedValue: currentWeather.weatherDescription || currentWeather.weatherCondition,
      thresholdValue: "Severe condition detected"
    });
  }

  const severeForecast = (forecast || []).find((item) => {
    const risk = deriveRisk(item.weatherCondition, item.weatherDescription, item.windSpeed, item.temperature);
    return risk !== "Low Risk" && !risk.includes("Rain");
  });

  if (severeForecast) {
    const futureRisk = deriveRisk(
      severeForecast.weatherCondition,
      severeForecast.weatherDescription,
      severeForecast.windSpeed,
      severeForecast.temperature
    );
    alerts.push({
      id: `forecast-${severeForecast.timestamp}`,
      createdAt: severeForecast.timestamp,
      alertType: "system.risk.forecast",
      alertMessage: `${futureRisk} likely in forecast`,
      observedValue: severeForecast.weatherDescription || severeForecast.weatherCondition,
      thresholdValue: formatTime(severeForecast.timestamp)
    });
  }

  return alerts.slice(0, 5);
}

async function fetchJson(url) {
  const response = await fetch(url);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload?.message || payload?.data || "Request failed.");
  }
  return payload;
}

async function searchPlaces(query) {
  const url = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(
    query
  )}&count=6&language=en&format=json`;
  const response = await fetch(url);
  const payload = await response.json();
  if (!payload?.results?.length) return [];
  return payload.results.map((place) => {
    const labelParts = [place.name, place.admin1, place.country].filter(Boolean);
    return {
      id: `${place.latitude}-${place.longitude}-${place.name}`,
      name: place.name,
      label: labelParts.join(", ")
    };
  });
}

async function fetchPublicWeather(city) {
  const suggestions = await searchPlaces(city);
  if (!suggestions.length) {
    throw new Error(`City not found: ${city}`);
  }

  const selected = suggestions[0];
  const geocodeUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(
    selected.name
  )}&count=1&language=en&format=json`;
  const geocodeResponse = await fetch(geocodeUrl);
  const geocodeJson = await geocodeResponse.json();
  const place = geocodeJson.results[0];

  const weatherUrl =
    `https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}` +
    `&longitude=${place.longitude}` +
    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code" +
    "&hourly=temperature_2m,weather_code,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
    "&forecast_days=2&timezone=auto";
  const weatherResponse = await fetch(weatherUrl);
  const weatherJson = await weatherResponse.json();
  const current = weatherJson.current;
  const hourly = weatherJson.hourly;

  const forecast = [];
  for (let i = 0; i < hourly.time.length && forecast.length < 12; i += 3) {
    const condition = weatherCodeMap[hourly.weather_code[i]] || "Weather update";
    forecast.push({
      city: place.name,
      temperature: hourly.temperature_2m[i],
      feelsLike: hourly.apparent_temperature[i],
      humidity: hourly.relative_humidity_2m[i],
      windSpeed: hourly.wind_speed_10m[i],
      weatherCondition: condition,
      weatherDescription: condition,
      weatherRisk: deriveRisk(condition, condition, hourly.wind_speed_10m[i]),
      timestamp: hourly.time[i]
    });
  }

  const currentCondition = weatherCodeMap[current.weather_code] || "Weather update";
  return {
    currentWeather: {
      city: place.name,
      temperature: current.temperature_2m,
      feelsLike: current.apparent_temperature,
      humidity: current.relative_humidity_2m,
      windSpeed: current.wind_speed_10m,
      weatherCondition: currentCondition,
      weatherDescription: currentCondition,
      weatherRisk: deriveRisk(currentCondition, currentCondition, current.wind_speed_10m),
      timestamp: current.time
    },
    forecast
  };
}

function App() {
  const [cityInput, setCityInput] = useState("Bangalore");
  const [activeCity, setActiveCity] = useState("Bangalore");
  const [cities, setCities] = useState(defaultCities);
  const [placeSuggestions, setPlaceSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [suggestionLoading, setSuggestionLoading] = useState(false);

  const [currentWeather, setCurrentWeather] = useState(null);
  const [forecast, setForecast] = useState([]);
  const [trackedWeather, setTrackedWeather] = useState([]);
  const [dailySummary, setDailySummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [analyticsAvailable, setAnalyticsAvailable] = useState(true);
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

    let currentData = null;
    let forecastData = [];

    try {
      const [currentPayload, forecastPayload] = await Promise.all([
        fetchJson(`${API_BASE_URL}/api/weather/current/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/forecast/${encodeURIComponent(normalizedCity)}`)
      ]);
      currentData = currentPayload.data || null;
      forecastData = Array.isArray(forecastPayload.data) ? forecastPayload.data.slice(0, 12) : [];
      setCurrentWeather(currentData);
      setForecast(forecastData);
      setError("");
    } catch (backendCoreError) {
      try {
        const fallback = await fetchPublicWeather(normalizedCity);
        currentData = fallback.currentWeather;
        forecastData = fallback.forecast;
        setCurrentWeather(currentData);
        setForecast(forecastData);
      } catch (fallbackError) {
        setCurrentWeather(null);
        setForecast([]);
        setDailySummary(null);
        setAlerts([]);
        setTrackedWeather([]);
        setAnalyticsAvailable(false);
        setError(fallbackError.message || backendCoreError.message || "Unable to load weather data.");
        setLoading(false);
        return;
      }
    }

    const computedSummary = buildClientSummary(normalizedCity, currentData, forecastData);
    const computedAlerts = buildClientAlerts(currentData, forecastData);

    try {
      const [summaryPayload, alertsPayload] = await Promise.all([
        fetchJson(`${API_BASE_URL}/api/weather/daily-summary/${encodeURIComponent(normalizedCity)}`),
        fetchJson(`${API_BASE_URL}/api/weather/alerts?city=${encodeURIComponent(normalizedCity)}`)
      ]);
      const serverSummary = summaryPayload.data || null;
      const serverAlerts = Array.isArray(alertsPayload.data) ? alertsPayload.data.slice(0, 5) : [];
      setDailySummary(serverSummary || computedSummary);
      setAlerts(serverAlerts.length ? serverAlerts : computedAlerts);
      setAnalyticsAvailable(true);
    } catch {
      setDailySummary(computedSummary);
      setAlerts(computedAlerts);
      setAnalyticsAvailable(false);
    }

    try {
      const trackedPayload = await fetchJson(`${API_BASE_URL}/api/weather/current/tracked`);
      setTrackedWeather(Array.isArray(trackedPayload.data) ? trackedPayload.data : []);
    } catch {
      setTrackedWeather(currentData ? [currentData] : []);
    }

    setLoading(false);
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
    const query = cityInput.trim();
    if (query.length < 2) {
      setPlaceSuggestions([]);
      return;
    }

    const timeoutId = setTimeout(async () => {
      try {
        setSuggestionLoading(true);
        const suggestions = await searchPlaces(query);
        setPlaceSuggestions(suggestions);
      } catch {
        setPlaceSuggestions([]);
      } finally {
        setSuggestionLoading(false);
      }
    }, 250);

    return () => clearTimeout(timeoutId);
  }, [cityInput]);

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
    setShowSuggestions(false);
  };

  return (
    <div className="app-shell">
      <div className="bg-shape shape-one" />
      <div className="bg-shape shape-two" />
      <header className="topbar">
        <div>
          <p className="eyebrow">Live Weather Dashboard</p>
          <h1>WeatherPulse</h1>
          <p className="subtext">Search cities worldwide, track risks, and monitor regional weather patterns.</p>
        </div>
        <div className="header-controls">
          <div className="unit-toggle" role="group" aria-label="Temperature unit">
            <button type="button" className={unit === "C" ? "active" : ""} onClick={() => setUnit("C")}>C</button>
            <button type="button" className={unit === "F" ? "active" : ""} onClick={() => setUnit("F")}>F</button>
          </div>
        </div>
      </header>

      <main className="layout">
        <section className="panel">
          <form className="search-row" onSubmit={handleSearchSubmit}>
            <div className="search-box-wrap">
              <input
                value={cityInput}
                onChange={(event) => setCityInput(event.target.value)}
                onFocus={() => setShowSuggestions(true)}
                onBlur={() => setTimeout(() => setShowSuggestions(false), 120)}
                placeholder="Search city, state, or country"
                aria-label="City"
              />
              {showSuggestions && (placeSuggestions.length > 0 || suggestionLoading) ? (
                <div className="suggestions-menu">
                  {suggestionLoading ? <p className="suggestion-item muted">Searching...</p> : null}
                  {placeSuggestions.map((place) => (
                    <button
                      key={place.id}
                      type="button"
                      className="suggestion-item"
                      onMouseDown={() => {
                        setCityInput(place.name);
                        setActiveCity(place.name);
                        setShowSuggestions(false);
                      }}
                    >
                      {place.label}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
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
              <p className="condition-sub">{currentWeather?.weatherDescription || "No detailed description yet"}</p>
              {isSunnyAndHot(currentWeather) ? <p className="heat-note">Sunny and hot, heat advisory in effect.</p> : null}
              <p className={`risk-pill ${riskClass(currentWeather?.weatherRisk || deriveRisk(currentWeather?.weatherCondition, currentWeather?.weatherDescription, currentWeather?.windSpeed, currentWeather?.temperature))}`}>
                {currentWeather?.weatherRisk || deriveRisk(currentWeather?.weatherCondition, currentWeather?.weatherDescription, currentWeather?.windSpeed, currentWeather?.temperature)}
              </p>
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
                <p>
                  Risk: <strong>
                    {selectedForecast.weatherRisk || deriveRisk(
                      selectedForecast.weatherCondition,
                      selectedForecast.weatherDescription,
                      selectedForecast.windSpeed,
                      selectedForecast.temperature
                    )}
                  </strong>
                </p>
              </div>
            ) : (
              <p className="empty-msg">{loading ? "Loading forecast..." : "No forecast data yet."}</p>
            )}
          </article>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Regional Watch</h2>
            <p>Multi-city risk view similar to operational weather dashboards</p>
          </div>
          <div className="watch-grid">
            {trackedWeather.map((item, index) => (
              <article className="watch-card" key={`${item.city}-${item.timestamp}-${index}`}>
                <p className="watch-city">{item.city}</p>
                <p className="watch-temp">{toDisplayTemp(item.temperature, unit)} {unit}</p>
                <p className="watch-condition">{item.weatherCondition}</p>
                <p className={`risk-pill ${riskClass(item.weatherRisk || deriveRisk(item.weatherCondition, item.weatherDescription, item.windSpeed, item.temperature))}`}>
                  {item.weatherRisk || deriveRisk(item.weatherCondition, item.weatherDescription, item.windSpeed, item.temperature)}
                </p>
              </article>
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Daily Summary And Alerts</h2>
            <p>Daily rollups and threshold tracking</p>
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
              <p className="empty-msg">
                {loading
                  ? "Loading daily summary..."
                  : analyticsAvailable
                    ? "No summary data yet."
                    : "Detailed summary appears when the analytics endpoint is active."}
              </p>
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
              <p className="empty-msg">
                {analyticsAvailable ? "No alerts triggered for this city." : "Alert history appears in full server mode."}
              </p>
            )}
          </article>
        </section>
      </main>
    </div>
  );
}

export default App;
