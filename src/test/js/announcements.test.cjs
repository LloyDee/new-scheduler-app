const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/announcements.js', 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

function setup({ items = [], composer = false } = {}) {
  class Element {
    constructor() { this.children = []; this.events = {}; this.dataset = {}; this.isConnected = true; this.textContent = ''; }
    append(...items) { for (const item of items) { item.parent = this; this.children.push(item); } }
    replaceChildren() { this.children = []; }
    remove() { this.parent.children = this.parent.children.filter(c => c !== this); }
    setAttribute(name, value) { this[name] = value; }
    addEventListener(name, fn) { this.events[name] = fn; }
  }
  const home = new Element(); const form = new Element(); const select = new Element();
  const button = new Element(); const output = new Element();
  form.action = '/events/7/announcements'; select.value = '4'; select.selectedOptions = [{ textContent: 'Parking' }];
  form.querySelector = s => s === 'select' ? select : s === 'button' ? button : output;
  const events = {}; const state = { items, requests: [], fail: false, count: 18, release: null };
  const context = {
    AbortSignal, Event, encodeURIComponent,
    document: {
      querySelector: s => s === '[data-home-announcements]' ? home : s === '[data-announcement-composer]' ? (composer ? form : null) : { content: s.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'token' },
      getElementById: id => ({ dataset: id === 'main' ? { eventId: '7' } : { url: '/api/notifications' } }),
      createElement: () => new Element(),
      addEventListener: (name, fn) => { events[name] = fn; },
      dispatchEvent: e => { events[e.type]?.(); }
    },
    window: { addEventListener() {} },
    fetch: async (url, options) => {
      state.requests.push({ url, options });
      if (state.fail) return { ok: false };
      if (url.includes('/audience?')) return { ok: true, json: async () => ({ count: state.count }) };
      if (options.method === 'POST') { state.items = []; return { ok: true }; }
      const snapshot = [...state.items];
      if (state.release) await state.release;
      return { ok: true, json: async () => snapshot };
    }
  };
  vm.runInNewContext(script, context);
  return { home, form, select, button, output, events, state };
}
const message = { id: 5, scope: 'DEPARTMENT', title: '<Arrival>', departmentName: 'Parking', message: '<script>6:30 AM</script>', acknowledgmentRequired: true };

test('Home displays pending department messages without marking them read and retries acknowledgment', async () => {
  const ui = setup({ items: [message, { ...message, scope: 'GLOBAL' }] }); await tick();
  assert.equal(ui.home.children.length, 1);
  const card = ui.home.children[0];
  assert.equal(card.children[0].textContent, 'Parking — <Arrival>');
  assert.equal(card.children[1].textContent, message.message);
  assert.equal(ui.state.requests.length, 1);
  assert.equal(ui.state.requests[0].url, '/api/notifications?eventId=7');
  const action = card.children[2];
  ui.state.fail = true; await action.events.click();
  assert.equal(action.disabled, false);
  assert.match(card.children[3].textContent, /Unable to save/);
  ui.state.fail = false; await action.events.click(); await tick();
  assert.equal(ui.home.children.length, 0);
  const post = ui.state.requests.find(r => r.options.method === 'POST');
  assert.equal(post.url, '/api/notifications/5/acknowledge');
  assert.equal(post.options.headers['X-CSRF-TOKEN'], 'token');
});

test('Home ordinary messages use the read endpoint and section changes initialize new cards', async () => {
  const ui = setup({ items: [{ ...message, acknowledgmentRequired: false }] }); await tick();
  const action = ui.home.children[0].children[2]; assert.equal(action.textContent, 'Mark as read');
  await action.events.click(); await tick();
  const post = ui.state.requests.find(r => r.options.method === 'POST');
  assert.equal(post.url, '/api/notifications/read'); assert.equal(post.options.body, '[5]');
  ui.state.items = [message]; ui.events['scheduler:page'](); await tick();
  assert.equal(ui.home.children.length, 1);
});

test('audience preview disables empty sends and refreshes department counts', async () => {
  const ui = setup({ composer: true }); await tick();
  assert.equal(ui.output.textContent, 'Send to 18 Parking volunteers.'); assert.equal(ui.button.disabled, false);
  ui.state.count = 0; await ui.select.events.change();
  assert.equal(ui.button.disabled, true);
  ui.state.fail = true; await ui.select.events.change();
  assert.match(ui.output.textContent, /Unable to preview/); assert.equal(ui.button.disabled, true);
});
