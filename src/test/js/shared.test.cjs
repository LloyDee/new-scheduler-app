const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const script = readFileSync('src/main/resources/static/js/shared.js', 'utf8') + '\nthis.api = Scheduler;';
function setup(extra = {}) {
  const context = { URL, AbortSignal, location: { href: 'https://app.test/events' }, ...extra };
  vm.runInNewContext(script, context);
  return context.api;
}
test('requests combine cancellation and timeout, validate responses and allow explicit redirect handling', async () => {
  const controller = new AbortController(); let options;
  const api = setup({ fetch: async (_, value) => { options = value; return { ok: true, redirected: true }; } });
  await assert.rejects(api.request('/test', { signal: controller.signal }), /Request failed/);
  await api.request('/test', { check: false, signal: controller.signal });
  assert.equal(options.credentials, 'same-origin');
  assert.equal(options.cache, 'no-store');
  controller.abort(); assert.equal(options.signal.aborted, true);
});
test('POST options consistently include CSRF and encode optional JSON bodies', () => {
  const api = setup({ document: { querySelector: name => ({ content: name.includes('_csrf_header') ? 'X-CSRF' : 'token' }) } });
  assert.equal(api.post().headers['X-CSRF'], 'token');
  assert.equal(api.post().body, undefined);
  assert.equal(api.post([1, 2]).body, '[1,2]');
  assert.equal(api.post([1]).headers['Content-Type'], 'application/json');
});
test('page registrations run immediately and on subsequent partial navigation', () => {
  const listeners = {}; let count = 0;
  const api = setup({ document: { addEventListener: (type, fn) => { listeners[type] = fn; } } });
  api.page(() => count++); listeners['scheduler:page'](); assert.equal(count, 2);
});
test('page setup is idempotent for the current main and initializes its replacement', () => {
  let main = {}, count = 0; const listeners = {};
  const api = setup({ document: { getElementById: () => main,
    addEventListener: (type, fn) => { listeners[type] = fn; } } });
  api.page(() => count++); listeners['scheduler:page'](); assert.equal(count, 1);
  main = {}; listeners['scheduler:page'](); assert.equal(count, 2);
});
test('partial styles load once before display, survive page replacement and retry failures', async () => {
  const links = [], source = () => ({ removed: false, getAttribute: () => '/css/logs.css', remove() { this.removed = true; } });
  const document = {
    head: { querySelectorAll: () => links, append: link => links.push(link) },
    createElement: () => ({ events: {}, addEventListener(type, fn) { this.events[type] = fn; }, remove() { links.splice(links.indexOf(this), 1); } })
  };
  const api = setup({ document }); const a = source(), b = source();
  let loaded = false;
  const first = api.loadStyles({ querySelectorAll: () => [a] }).then(() => { loaded = true; });
  const second = api.loadStyles({ querySelectorAll: () => [b] });
  assert.equal(links.length, 1); assert.equal(loaded, false);
  links[0].events.error(); await assert.rejects(first); await assert.rejects(second);
  const retry = api.loadStyles({ querySelectorAll: () => [a] });
  assert.equal(links.length, 1); links[0].events.load(); await retry;
  assert.equal(a.removed, true);
  await api.loadStyles({ querySelectorAll: () => [b] });
  assert.equal(b.removed, true); assert.equal(links.length, 1);
});
