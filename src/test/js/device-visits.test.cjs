const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/device-visits.js', 'utf8');

async function run(mode, cache = null, ua = 'Mozilla/5.0 Windows Chrome/140.0 Edg/140.1') {
  const requests = [];
  const storage = new Map(cache ? [['schedulerLocation', JSON.stringify(cache)]] : []);
  const context = {
    navigator: { userAgent: ua, platform: 'Win32', language: 'en-US', maxTouchPoints: 0 },
    Intl, Date, AbortSignal,
    document: { addEventListener() {}, querySelector: (selector) => ({ content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : selector.includes('device-visits-url') ? '/api/device-visits' : 'token' }) },
    localStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value) },
    fetch: async (url, options) => {
      requests.push({ url, options });
      if (url === '/api/device-visits') return { ok: true };
      if (mode === 'timeout') throw new DOMException('Timeout', 'TimeoutError');
      return { ok: mode !== 'failure', json: async () => ({ city: 'Austin', region: 'Texas', country_name: 'United States' }) };
    }
  };
  await vm.runInNewContext(script, context);
  await new Promise(resolve => setImmediate(resolve));
  const visit = requests.find(r => r.url === '/api/device-visits');
  assert.ok(visit);
  assert.equal(visit.options.headers['X-CSRF-TOKEN'], 'token');
  return { data: JSON.parse(visit.options.body), requests, storage };
}

test('records successful location and correctly recognizes Edge ahead of Chrome', async () => {
  const { data, storage } = await run('success');
  assert.equal(data.city, 'Austin');
  assert.equal(data.region, 'Texas');
  assert.equal(data.browser, 'Edge 140.1');
  assert.equal(data.deviceCategory, 'Desktop');
  assert.ok(storage.has('schedulerLocation'));
});
for (const mode of ['timeout', 'failure']) test(`records visit when location lookup returns ${mode}`, async () => {
  const { data } = await run(mode);
  assert.equal(data.city, undefined);
  assert.equal(data.language, 'en-US');
});
test('uses cached location without another external request', async () => {
  const { data, requests } = await run('failure', { timestamp: Date.now(), location: { city: 'Chicago' } });
  assert.equal(data.city, 'Chicago');
  assert.equal(requests.length, 1);
});
test('recognizes mobile Safari', async () => {
  const { data } = await run('success', null, 'Mozilla iPhone Version/18.0 Mobile Safari/605.1');
  assert.equal(data.browser, 'Safari 18.0');
  assert.equal(data.deviceCategory, 'Mobile');
  assert.equal(data.operatingSystem, 'iOS / iPadOS');
});

function tracker({ storage = new Map(), blocked = false, identity = 'alex' } = {}) {
  let now = Date.now();
  const events = {};
  const state = { requests: [], ok: true, pending: null };
  const context = {
    navigator: { userAgent: 'Windows Chrome/140', language: 'en', platform: 'Win32' },
    Intl, AbortSignal,
    Date: class extends Date { static now() { return now; } },
    document: {
      addEventListener: (name, fn) => { events[name] = fn; },
      querySelector: selector => ({ content: selector.includes('device-visits-key') ? identity : selector.includes('device-visits-url') ? '/api/device-visits' : 'token' })
    },
    localStorage: {
      getItem(key) { if (blocked) throw Error('blocked'); return storage.get(key); },
      setItem(key, value) { if (blocked) throw Error('blocked'); storage.set(key, value); }
    },
    fetch: async (url) => {
      state.requests.push(url);
      if (state.pending) await state.pending;
      return { ok: state.ok, json: async () => ({ city: 'Austin' }) };
    }
  };
  vm.runInNewContext(script, context);
  return { state, events, advance: ms => { now += ms; } };
}
const settle = () => new Promise(resolve => setImmediate(resolve));

test('page clicks, reloads and other tabs reuse successful activity; people have separate windows', async () => {
  const storage = new Map();
  const first = tracker({ storage });
  first.events['scheduler:page']();
  await settle();
  await first.events['scheduler:page']();
  assert.equal(first.state.requests.filter(url => url === '/api/device-visits').length, 1);
  const second = tracker({ storage });
  await settle();
  assert.equal(second.state.requests.length, 0);
  const other = tracker({ storage, identity: 'sam' });
  await settle();
  assert.ok(other.state.requests.includes('/api/device-visits'));
  first.advance(1800000);
  await first.events['scheduler:page']();
  assert.equal(first.state.requests.filter(url => url === '/api/device-visits').length, 2);
});

test('blocked storage still throttles; failed tracking remains retryable', async () => {
  const ui = tracker({ blocked: true });
  ui.state.ok = false;
  await settle();
  ui.state.ok = true;
  await ui.events['scheduler:page']();
  await ui.events['scheduler:page']();
  assert.equal(ui.state.requests.filter(url => url === '/api/device-visits').length, 2);
});
