(() => {
function initializeWeather() {
const weatherCard = document.querySelector('[data-weather]');

if (weatherCard) {
  const field = (name) => weatherCard.querySelector(`[data-weather-${name}]`);
  let loading = false;
  let loaded = false;
  let activeController;
  let disposed = false;
  let lastAttempt = 0;
  const interval = 15 * 60 * 1000;
  const rounded = (value, suffix = '') => Number.isFinite(value) ? `${Math.round(value)}${suffix}` : '—';
  const conditions = (code, day) => {
    const sky = day === 0 ? 'night' : 'day';
    if (code === 0) return [sky, day === 0 ? 'Clear night' : 'Clear sky'];
    if (code === 1) return [`cloudy-${sky}-1`, 'Mostly clear'];
    if (code === 2) return [`cloudy-${sky}-2`, 'Partly cloudy'];
    if (code === 3) return ['cloudy', 'Overcast'];
    if ([45, 48].includes(code)) return ['cloudy', 'Foggy'];
    if ([51, 53, 55, 56, 57].includes(code)) return ['rainy-4', 'Drizzle'];
    if ([61, 66, 80].includes(code)) return ['rainy-4', 'Rain'];
    if ([63, 81].includes(code)) return ['rainy-5', 'Rain'];
    if ([65, 67, 82].includes(code)) return ['rainy-6', 'Rain'];
    if ([71, 77, 85].includes(code)) return ['snowy-4', 'Snow'];
    if (code === 73) return ['snowy-5', 'Snow'];
    if ([75, 86].includes(code)) return ['snowy-6', 'Snow'];
    if ([95, 96, 99].includes(code)) return ['thunder', 'Thunderstorms'];
    return ['cloudy', 'Current weather'];
  };
  const sceneFor = code => {
    if ([0, 1].includes(code)) return 'clear';
    if (code === 2) return 'partly';
    if (code === 3) return 'cloudy';
    if ([45, 48].includes(code)) return 'fog';
    if ([51, 53, 55, 56, 57].includes(code)) return 'drizzle';
    if ([61, 63, 65, 66, 67, 80, 81, 82].includes(code)) return 'rain';
    if ([71, 73, 75, 77, 85, 86].includes(code)) return 'snow';
    if ([95, 96, 99].includes(code)) return 'storm';
    return 'neutral';
  };
  const icon = field('icon');
  // The template resolves the asset directory against the application's context path.
  const assetBase = icon.getAttribute('src').replace(/[^/]+$/, '');

  async function loadWeather() {
    if (loading) return;
    loading = true;
    lastAttempt = Date.now();
    weatherCard.setAttribute('aria-busy', 'true');
    const controller = new AbortController();
    activeController = controller;
    let timeout;
    try {
      if (!window.isSecureContext) throw new Error('Location requires HTTPS. Open the secure application URL.');
      if (!navigator.geolocation) throw new Error('Location is unavailable in this browser.');
      field('status').textContent = 'Getting your location…';
      const position = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve,
        error => reject(new Error(error.code === 1 ? 'Allow location access in your browser to see local weather.'
          : error.code === 3 ? 'Location request timed out. Retrying in 15 minutes.'
          : 'Your location is unavailable. Check device location settings.')),
        { timeout: 10000, maximumAge: 0, enableHighAccuracy: false }));
      if (disposed) return;
      timeout = setTimeout(() => controller.abort(), 12000);
      const params = new URLSearchParams({
        latitude: String(position.coords.latitude), longitude: String(position.coords.longitude),
        current: 'temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m',
        daily: 'temperature_2m_max,temperature_2m_min',
        temperature_unit: 'fahrenheit', wind_speed_unit: 'mph',
        timezone: 'auto', timeformat: 'unixtime', forecast_days: '1',
      });
      const response = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`, { signal: controller.signal });
      if (!response.ok) throw new Error('Weather request failed');
      const data = await response.json();
      if (disposed) return;
      const current = data.current;
      if (!current || !Number.isFinite(current.temperature_2m) || !Number.isFinite(current.time)) {
        throw new Error('Weather data unavailable');
      }
      const [asset, description] = conditions(current.weather_code, current.is_day);
      const imageUrl = `${assetBase}${asset}.svg`;
      icon.setAttribute('src', imageUrl);
      weatherCard.setAttribute('data-weather-scene', sceneFor(current.weather_code));
      weatherCard.setAttribute('data-weather-day', current.is_day === 0 ? 'night' : 'day');
      weatherCard.setAttribute('data-weather-heavy', String([65, 67, 75, 82, 86, 95, 96, 99].includes(current.weather_code)));
      field('temperature').textContent = rounded(current.temperature_2m);
      field('condition').textContent = description;
      field('feels').textContent = rounded(current.apparent_temperature, '°');
      field('humidity').textContent = rounded(current.relative_humidity_2m, '%');
      field('wind').textContent = rounded(current.wind_speed_10m, ' mph');
      field('high').textContent = rounded(data.daily?.temperature_2m_max?.[0], '°');
      field('low').textContent = rounded(data.daily?.temperature_2m_min?.[0], '°');
      const time = new Intl.DateTimeFormat('en-US', {
        hour: 'numeric', minute: '2-digit', timeZone: data.timezone, timeZoneName: 'short',
      }).format(new Date(current.time * 1000));
      field('status').textContent = `Updated ${time}`;
      loaded = true;
    } catch (error) {
      if (disposed) return;
      field('status').textContent = loaded ? `${error.message} Showing last forecast.` : error.message;
    } finally {
      clearTimeout(timeout);
      loading = false;
      weatherCard.setAttribute('aria-busy', 'false');
    }
  }

  weatherCard.setAttribute('data-weather-paused', String(document.hidden));
  loadWeather();
  const timer = setInterval(loadWeather, interval);
  const onVisible = () => {
    weatherCard.setAttribute('data-weather-paused', String(document.hidden));
    if (!document.hidden && Date.now() - lastAttempt >= interval) loadWeather();
  };
  document.addEventListener('visibilitychange', onVisible);
  document.addEventListener('scheduler:before-page', () => {
    disposed = true;
    clearInterval(timer);
    activeController?.abort();
    document.removeEventListener('visibilitychange', onVisible);
  }, { once: true });
}
}
document.addEventListener('scheduler:page', initializeWeather);
initializeWeather();
})();
