const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/weather.js', 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

function setup({ error, secure = true, deferLocation = false, code = 0, day = 1 } = {}) {
  const attributes = {};
  const fields = {}, events = {}, requests = [], intervals = new Map();
  const state = { now: 0, error, fail: false, locationCalls: 0, code, day };
  const card = { setAttribute(name, value) { attributes[name] = value; }, querySelector(selector) { return fields[selector] ||= { textContent: '—' }; } };
  const document = { hidden: false, querySelector: () => card,
    addEventListener: (name, fn) => { events[name] = fn; },
    removeEventListener: name => { delete events[name]; } };
  vm.runInNewContext(script, { document, window: { isSecureContext: secure },
    navigator: { geolocation: { getCurrentPosition(success, failure, options) {
      state.locationCalls++;
      state.locationOptions = options;
      state.resolveLocation = () => state.error ? failure({ code: state.error }) : success({ coords: { latitude: 40.7, longitude: -74 } });
      if (!deferLocation) state.resolveLocation();
    } } },
    Date: class extends Date { static now() { return state.now; } }, Intl, URLSearchParams, AbortController,
    setTimeout: () => 1, clearTimeout() {},
    setInterval: (fn, delay) => { intervals.set(delay, fn); return delay; },
    clearInterval: id => intervals.delete(id),
    fetch: async (url, options) => {
      requests.push({ url: new URL(url), options });
      if (state.fail) throw new Error('Weather request failed');
      return { ok: true, json: async () => ({ timezone: 'America/New_York',
        current: { temperature_2m: 72, time: 1793368800, weather_code: state.code, is_day: state.day } }) };
    }
  });
  return { attributes, fields, events, requests, intervals, state, document, status: () => fields['[data-weather-status]'].textContent };
}

test('uses browser coordinates, auto timezone and a 15 minute refresh', async () => {
  const app = setup(); await tick();
  assert.equal(app.requests.length, 1);
  const params = app.requests[0].url.searchParams;
  assert.equal(params.get('latitude'), '40.7');
  assert.equal(params.get('longitude'), '-74');
  assert.equal(params.get('timezone'), 'auto');
  assert.match(app.status(), /Updated/);
  app.intervals.get(900000)(); await tick();
  assert.equal(app.requests.length, 2);
  assert.equal(app.state.locationCalls, 2);
});

for (const [error, message] of [[1, /Allow location/], [2, /unavailable/], [3, /timed out/]]) {
  test(`explains location error ${error} without a fallback forecast`, async () => {
    const app = setup({ error }); await tick();
    assert.match(app.status(), message);
    assert.equal(app.requests.length, 0);
  });
}
test('explains insecure context', async () => {
  const app = setup({ secure: false }); await tick();
  assert.match(app.status(), /HTTPS/);
  assert.equal(app.state.locationCalls, 0);
});
test('retains forecast on failure and refreshes an overdue visible tab', async () => {
  const app = setup(); await tick();
  app.state.fail = true;
  app.state.now = 900001;
  app.events.visibilitychange(); await tick();
  assert.match(app.status(), /Showing last forecast/);
  assert.equal(app.fields['[data-weather-temperature]'].textContent, '72');
  app.events['scheduler:before-page']();
  assert.equal(app.intervals.size, 0);
  assert.equal(app.events.visibilitychange, undefined);
  assert.equal(app.requests[1].options.signal.aborted, true);
});
test('does not fetch after navigation while location is pending', async () => {
  const app = setup({ deferLocation: true });
  app.events['scheduler:before-page']();
  app.state.resolveLocation(); await tick();
  assert.equal(app.requests.length, 0);
});

for (const [scene, codes] of Object.entries({clear:[0,1], partly:[2], cloudy:[3], fog:[45,48], drizzle:[51,53,55,56,57], rain:[61,63,65,66,67,80,81,82], snow:[71,73,75,77,85,86], storm:[95,96,99], neutral:[100,null]})) {
  for (const code of codes) test('selects '+scene+' scene for code '+code, async () => {
    const app = setup({code}); await tick();
    assert.equal(app.attributes['data-weather-scene'], scene);
  });
}
test('updates day, condition and intensity with the forecast and retains them on failure', async () => {
  const app = setup({code: 2, day: 0}); await tick();
  assert.equal(app.attributes['data-weather-day'], 'night');
  app.state.code = 65; app.state.day = 1;
  app.intervals.get(900000)(); await tick();
  assert.equal(app.attributes['data-weather-scene'], 'rain');
  assert.equal(app.attributes['data-weather-heavy'], 'true');
  assert.equal(app.attributes['data-weather-day'], 'day');
  app.state.fail = true; app.intervals.get(900000)(); await tick();
  assert.equal(app.attributes['data-weather-scene'], 'rain');
});
test('pauses scene motion when the tab is hidden', async () => {
  const app = setup(); await tick();
  app.document.hidden = true; app.events.visibilitychange();
  assert.equal(app.attributes['data-weather-paused'], 'true');
  app.document.hidden = false; app.events.visibilitychange();
  assert.equal(app.attributes['data-weather-paused'], 'false');
});
test('leaves initial errors without a condition scene', async () => {
  const app = setup({error: 1}); await tick();
  assert.equal(app.attributes['data-weather-scene'], undefined);
});
