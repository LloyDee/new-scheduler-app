const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/notifications.js', 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

function setup(items = []) {
  class Element {
    constructor() { this.children = []; this.events = {}; this.hidden = false; this.textContent = ''; this.classes = new Set(); this.classList = { toggle: (name, on) => on ? this.classes.add(name) : this.classes.delete(name) }; }
    addEventListener(name, fn) { this.events[name] = fn; }
    setAttribute(name, value) { this[name] = value; }
    replaceChildren() { this.children = []; }
    append(...items) { this.children.push(...items); }
    showModal() { this.open = true; }
    focus() { this.focused = true; }
  }
  const elements = Object.fromEntries(['bell', 'dialog', 'badge', 'status', 'messages', 'retry'].map(name => [name, new Element()]));
  elements.bell.dataset = { url: '/api/notifications' };
  const state = { items, requests: [], failRead: false, failLoad: false, failAck: false, deferLoad: null, eventId: '', deferCount: null, failCount: false };
  const windowEvents = {};
  const documentEvents = {};
  let now = Date.now();
  const context = {
    Date: class extends Date { static now() { return now; } }, AbortSignal, Event,
    document: {
      hidden: false,
      getElementById: id => id === 'main' ? { dataset: { eventId: state.eventId } } : elements[id.replace('notification-', '')],
      createElement: () => new Element(),
      querySelector: selector => ({ content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'token' }),
      addEventListener(name, fn) { documentEvents[name] = fn; }, dispatchEvent(event) { documentEvents[event.type]?.(event); }
    },
    window: { addEventListener: (name, fn) => { windowEvents[name] = fn; } },
    setInterval: fn => { state.poll = fn; },
    fetch: async (url, options) => {
      state.requests.push({ url, options });
      if (url.includes('/count')) {
        if (state.deferCount) await state.deferCount;
        return { ok: !state.failCount, json: async () => ({ count: state.items.filter(i => !i.readAt).length }) };
      }
      if (url.endsWith('/acknowledge')) {
        if (state.failAck) return { ok: false };
        state.items = state.items.filter(i => !url.includes(`/${i.id}/`));
        return { ok: true };
      }
      if (url.endsWith('/read')) {
        if (state.failRead) return { ok: false };
        const ids = JSON.parse(options.body);
        state.items = state.items.filter(item => {
          if (!ids.includes(item.id)) return true;
          if (item.acknowledgmentRequired) { item.readAt = 'now'; return true; }
          return false;
        });
        return { ok: true };
      }
      if (state.deferLoad) await state.deferLoad;
      return { ok: !state.failLoad, json: async () => [...state.items] };
    }
  };
  vm.runInNewContext(script, context);
  return { ...elements, state, windowEvents, documentEvents, advance: ms => { now += ms; }, close: () => { elements.dialog.open = false; elements.dialog.events.close(); } };
}
const item = id => ({ id, message: `<script>message ${id}</script>\nsecond line`, createdAt: '2026-10-29T12:00:00Z' });

test('badge transitions from count to dot to empty as device reads notifications', async () => {
  const ui = setup([item(1), item(2)]);
  await tick();
  assert.equal(ui.badge.textContent, '2');
  assert.ok(ui.badge.classes.has('has-count'));
  ui.state.items = [item(2)];
  ui.advance(30000);
  await ui.state.poll();
  await tick();
  assert.equal(ui.badge.textContent, '');
  assert.equal(ui.badge.hidden, false);
  ui.bell.events.click();
  await tick();
  assert.equal(ui.messages.children[0].children[1].textContent, item(2).message);
  assert.equal(ui.badge.hidden, true);
  const ack = ui.state.requests.find(request => request.url.endsWith('/read'));
  assert.equal(ack.options.headers['X-CSRF-TOKEN'], 'token');
  assert.equal(ack.options.body, '[2]');
  assert.equal(ui.messages.children.length, 1);
  ui.close();
  assert.equal(ui.messages.children.length, 0);
  assert.ok(ui.bell.focused);
  ui.bell.events.click();
  await tick();
  assert.equal(ui.status.textContent, 'No unread notifications');
});

test('opening an announcement reads it while acknowledgment remains an explicit retryable action', async () => {
  const ui = setup([{ ...item(1), title: '<Arrival>', departmentName: 'Parking', acknowledgmentRequired: true }]);
  ui.bell.events.click(); await tick();
  assert.equal(ui.badge.hidden, true);
  assert.equal(ui.state.items.length, 1);
  const card = ui.messages.children[0];
  assert.equal(card.children[0].textContent, 'Parking — <Arrival>');
  const action = card.children.at(-1);
  assert.equal(action.textContent, 'I’ve read this');
  assert.equal(ui.state.requests.some(r => r.url.endsWith('/acknowledge')), false);
  ui.state.failAck = true; await action.events.click();
  assert.equal(action.disabled, false);
  assert.match(ui.status.textContent, /Unable to acknowledge/);
  ui.state.failAck = false; await action.events.click();
  assert.equal(action.textContent, 'Acknowledged');
  assert.equal(ui.state.items.length, 0);
  const request = ui.state.requests.find(r => r.url.endsWith('/acknowledge'));
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'token');
});

test('failed acknowledgement preserves displayed messages and retry does not consume new arrivals', async () => {
  const ui = setup([item(1)]);
  ui.state.failRead = true;
  ui.bell.events.click();
  await tick();
  assert.equal(ui.retry.hidden, false);
  assert.equal(ui.messages.children.length, 1);
  assert.equal(ui.state.items.length, 1);
  ui.state.items.push(item(2));
  ui.state.failRead = false;
  await ui.retry.events.click();
  assert.deepEqual(ui.state.items.map(item => item.id), [2]);
  assert.equal(ui.messages.children.length, 1);
  assert.equal(ui.badge.hidden, false);
});

test('load errors can retry and a popup closed before loading never marks messages read', async () => {
  const ui = setup([item(1)]);
  ui.state.failLoad = true;
  ui.bell.events.click();
  await tick();
  assert.equal(ui.retry.hidden, false);
  assert.equal(ui.messages.children.length, 0);
  ui.state.failLoad = false;
  let release;
  ui.state.deferLoad = new Promise(resolve => { release = resolve; });
  const loading = ui.retry.events.click();
  ui.close();
  release();
  await loading;
  assert.equal(ui.state.requests.filter(request => request.url.endsWith('/read')).length, 0);
  assert.equal(ui.state.items.length, 1);
});

test('coalesces page, focus and visibility refreshes and reuses counts for 30 seconds', async () => {
  const ui = setup([item(1)]);
  ui.windowEvents.focus();
  ui.documentEvents['scheduler:page']();
  ui.documentEvents.visibilitychange();
  await tick();
  assert.equal(ui.state.requests.length, 1);
  await ui.state.poll();
  assert.equal(ui.state.requests.length, 1);
  ui.advance(30000);
  await ui.state.poll();
  assert.equal(ui.state.requests.length, 2);
});

test('event changes fetch their own count; failures retry and announcements invalidate counts', async () => {
  const ui = setup();
  await tick();
  ui.state.eventId = '7';
  ui.state.failCount = true;
  await ui.documentEvents['scheduler:page']();
  ui.state.failCount = false;
  await ui.windowEvents.focus();
  assert.equal(ui.state.requests.filter(r => r.url.includes('eventId=7')).length, 2);
  ui.state.items.push(item(1));
  await ui.documentEvents['scheduler:announcements']();
  await tick();
  assert.equal(ui.badge.hidden, false);
});

test('mutation during a pending count schedules one follow-up and rejects stale context', async () => {
  const ui = setup();
  await tick();
  let release;
  ui.state.deferCount = new Promise(resolve => { release = resolve; });
  ui.advance(30000);
  const pending = ui.state.poll();
  ui.documentEvents['scheduler:announcements']();
  ui.documentEvents['scheduler:announcements']();
  ui.state.eventId = '9';
  const next = ui.documentEvents['scheduler:page']();
  ui.state.deferCount = null;
  release();
  await Promise.all([pending, next]);
  await tick();
  assert.equal(ui.state.requests.filter(r => r.url.endsWith('/count')).length, 3);
  assert.equal(ui.state.requests.filter(r => r.url.includes('eventId=9')).length, 1);
});
