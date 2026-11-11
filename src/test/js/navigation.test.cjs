const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = (readFileSync('src/main/resources/static/js/shared.js', 'utf8') + '\n' + readFileSync('src/main/resources/static/js/navigation.js', 'utf8'));
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup(featureStyle = false) {
  const events = {}, windows = {}, requests = [], historyCalls = [], replacements = [], assigned = [];
  const main = { setAttribute() {}, removeAttribute() {}, replaceWith(value) { replacements.push(value); } };
  const nav = { replaceWith() {} };
  const styles = [];
  const styleSource = { getAttribute: () => '/css/logs.css', remove() {} };
  const location = { href: 'https://app.test/events', origin: 'https://app.test', pathname: '/events', search: '', assign: url => assigned.push(url) };
  const document = {
    body: { append() {} },
    head: { querySelectorAll: () => styles, append: link => styles.push(link) },
    createElement: () => ({ events: {}, setAttribute() {}, addEventListener(type, fn) { this.events[type] = fn; }, remove() {} }),
    getElementById: () => main, querySelector: () => nav,
    addEventListener: (name, fn) => { events[name] = fn; }, dispatchEvent() {}
  };
  const context = {
    URL, URLSearchParams, AbortController, AbortSignal, Event, document, location,
    FormData: class extends URLSearchParams { constructor(form) { super(form.fields); } },
    history: { state: {}, replaceState() {}, pushState: (...args) => historyCalls.push(args) }, scrollX: 0, scrollY: 0,
    window: { addEventListener: (name, fn) => { windows[name] = fn; }, scrollTo() {} },
    DOMParser: class { parseFromString(text) { return { getElementById: () => ({ dataset: { pageTitle: text }, querySelectorAll: () => featureStyle ? [styleSource] : [], focus() {} }), querySelector: () => nav }; } },
    fetch: (url, options) => new Promise((resolve, reject) => requests.push({ url, options, resolve, reject }))
  };
  vm.runInNewContext(script, context);
  const click = path => events.click({ button: 0, target: { closest: () => ({ href: `https://app.test${path}`, closest: () => true, hasAttribute: () => false }) }, preventDefault() {} });
  const complete = (index, title) => requests[index].resolve({ ok: true, text: async () => title });
  const submit = (fields, submitter) => events.submit({
    target: { method: 'get', action: 'https://app.test/events/1/schedule', closest: () => true, fields },
    submitter, preventDefault() {}
  });
  return { click, submit, complete, requests, historyCalls, replacements, assigned, windows, location, styles };
}

test('day buttons preserve the selected date and other filters in GET navigation', () => {
  const ui = setup();
  ui.submit({ departmentId: '7', view: 'needs' }, { name: 'date', value: '2030-06-02' });
  assert.equal(ui.requests[0].url.searchParams.get('date'), '2030-06-02');
  assert.equal(ui.requests[0].url.searchParams.get('departmentId'), '7');
  assert.equal(ui.requests[0].url.searchParams.get('view'), 'needs');
  ui.submit({ departmentId: '7', view: 'needs' }, { name: 'date', value: '' });
  assert.equal(ui.requests[1].url.searchParams.get('date'), '');
});

test('automatic form submission keeps hidden filters without a submit button', () => {
  const ui = setup();
  ui.submit({ departmentId: '7', date: '2030-06-02', view: 'mine' });
  assert.equal(ui.requests[0].url.searchParams.get('date'), '2030-06-02');
  assert.equal(ui.requests[0].url.searchParams.get('view'), 'mine');
});
test('duplicate pending clicks issue one request; completed page can intentionally reload', async () => {
  const ui = setup();
  ui.click('/logs'); ui.click('/logs');
  assert.equal(ui.requests.length, 1);
  ui.complete(0, 'Logs'); await tick();
  assert.equal(ui.replacements.length, 1);
  ui.click('/logs');
  assert.equal(ui.requests.length, 2);
});
test('superseded navigation aborts and ignores late responses; back navigation still loads', async () => {
  const ui = setup();
  ui.click('/logs'); ui.click('/settings');
  assert.equal(ui.requests[0].options.signal.aborted, true);
  ui.complete(1, 'Settings'); await tick();
  ui.complete(0, 'Logs'); await tick();
  assert.equal(ui.replacements.length, 1);
  assert.equal(ui.replacements[0].dataset.pageTitle, 'Settings');
  ui.windows.popstate({ state: { scroll: [0, 10] } });
  ui.complete(2, 'Events'); await tick();
  assert.equal(ui.historyCalls.length, 1);
  assert.equal(ui.replacements.length, 2);
});
test('failed navigation falls back to full navigation', async () => {
  const ui = setup();
  ui.click('/logs');
  ui.requests[0].reject(Error('offline')); await tick();
  assert.deepEqual(ui.assigned, ['https://app.test/logs']);
});

test('partial navigation waits for feature CSS and does not duplicate it on later visits', async () => {
  const ui = setup(true); ui.click('/logs'); ui.complete(0, 'Logs'); await tick();
  assert.equal(ui.styles.length, 1); assert.equal(ui.replacements.length, 0);
  ui.styles[0].events.load(); await tick(); assert.equal(ui.replacements.length, 1);
  ui.click('/logs'); ui.complete(1, 'Logs'); await tick();
  assert.equal(ui.styles.length, 1); assert.equal(ui.replacements.length, 2);
});
test('feature CSS failure falls back to a full page load', async () => {
  const ui = setup(true); ui.click('/logs'); ui.complete(0, 'Logs'); await tick();
  ui.styles[0].events.error(); await tick();
  assert.equal(ui.replacements.length, 0); assert.deepEqual(ui.assigned, ['https://app.test/logs']);
});
