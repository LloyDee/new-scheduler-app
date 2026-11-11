const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const tick = () => new Promise(resolve => setImmediate(resolve));

// Minimal native-dialog boundary so lifecycle tests run without a browser dependency.
function fixture() {
  const listeners = new Map(), windowListeners = new Map(), requests = [];
  let confirmations = 0, allowDiscard = false;
  const element = () => ({ focus() {}, matches: () => false, hasAttribute: () => true,
    setAttribute() {}, addEventListener(type, callback) { this[type] = callback; } });
  const heading = { ...element(), id: 'loaded-title', matches: () => true };
  const retry = element();
  const field = { name: 'volunteerIds', value: '42', checked: true, tagName: 'INPUT' };
  const dialog = {
    dataset: { detailUrl: '/events/1/schedule/2/options' }, open: false, isConnected: true,
    attributes: new Map(), html: '',
    set innerHTML(value) { this.html = value; }, get innerHTML() { return this.html; },
    showModal() { this.open = true; }, close() { this.open = false; },
    setAttribute(name, value) { this.attributes.set(name, value); },
    removeAttribute(name) { this.attributes.delete(name); },
    querySelector(selector) {
      if (selector === '[data-detail-retry]') return this.html.includes('data-detail-retry') ? retry : null;
      if (selector === 'h2[id]') return this.html === 'loaded' ? heading : null;
      return heading;
    },
    querySelectorAll(selector) { return selector.startsWith('input') && this.html === 'loaded' ? [field] : []; }
  };
  const document = {
    activeElement: null, getElementById: () => element(),
    addEventListener(type, callback) { listeners.set(type, callback); },
    dispatchEvent(event) { listeners.get(event.type)?.(event); }
  };
  const context = vm.createContext({ document, location: { href: 'http://localhost/events/1/schedule' },
    AbortController, AbortSignal,
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } },
    queueMicrotask,
    window: { addEventListener(type, callback) { windowListeners.set(type, callback); },
      confirm() { confirmations++; return allowDiscard; } },
    history: { state: {}, pushState() {}, back() { queueMicrotask(() => windowListeners.get('popstate')({ stopImmediatePropagation() {} })); } },
    fetch(url, options) { return new Promise(resolve => requests.push({ url, options, resolve })); }
  });
  vm.runInContext((fs.readFileSync('src/main/resources/static/js/shared.js', 'utf8') + '\n' + fs.readFileSync('src/main/resources/static/js/dialogs.js', 'utf8')), context);
  const api = vm.runInContext('SchedulerDialogs', context);
  const response = (overrides = {}) => ({ ok: true, redirected: false, headers: { get: () => 'true' }, text: async () => 'loaded', ...overrides });
  return { api, dialog, field, retry, requests, response,
    confirmations: () => confirmations, allowDiscard: () => { allowDiscard = true; } };
}

test('opening fetches once, shows loading, and tracks asynchronously loaded edits', async () => {
  const f = fixture();
  assert.equal(f.requests.length, 0);
  f.api.open(f.dialog, null, { unsaved: true });
  assert.equal(f.dialog.open, true);
  assert.match(f.dialog.innerHTML, /Loading details/);
  assert.equal(f.dialog.attributes.get('aria-busy'), 'true');
  f.api.open(f.dialog);
  assert.equal(f.requests.length, 1);
  f.requests[0].resolve(f.response()); await tick();
  assert.equal(f.dialog.innerHTML, 'loaded');
  assert.equal(f.dialog.attributes.has('aria-busy'), false);
  f.field.checked = false;
  assert.equal(f.api.close(f.dialog), false);
  assert.equal(f.confirmations(), 1);
  f.allowDiscard();
  assert.equal(f.api.close(f.dialog), true);
  assert.equal(f.field.checked, true);
  await tick();
  f.api.open(f.dialog, null, { unsaved: true });
  assert.equal(f.requests.length, 2, 'reopening refreshes the choices');
  assert.equal(f.api.close(f.dialog), true, 'loading alone is not an unsaved edit');
});

test('failed or redirected requests show an explicit retry action', async () => {
  const f = fixture();
  f.api.open(f.dialog);
  f.requests[0].resolve(f.response({ redirected: true })); await tick();
  assert.match(f.dialog.innerHTML, /Could not load details/);
  assert.match(f.dialog.innerHTML, /data-detail-retry/);
  f.retry.click();
  assert.equal(f.requests.length, 2);
  assert.match(f.dialog.innerHTML, /Loading details/);
  f.requests[1].resolve(f.response()); await tick();
  assert.equal(f.dialog.innerHTML, 'loaded');
});

test('closing during loading aborts the request and ignores late content', async () => {
  const f = fixture();
  f.api.open(f.dialog);
  f.api.close(f.dialog);
  assert.equal(f.requests[0].options.signal.aborted, true);
  f.requests[0].resolve(f.response()); await tick();
  assert.equal(f.dialog.open, false);
  assert.notEqual(f.dialog.innerHTML, 'loaded');
});
