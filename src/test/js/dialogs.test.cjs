const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');

function setup() {
  const listeners = {}, windows = {}, entries = [{}];
  const on = map => (name, fn) => { map[name] = fn; };
  let index = 0, prompts = 0, allow = true;
  const main = { focus() { this.focused = true; } };
  const history = {
    get state() { return entries[index]; },
    pushState(state) { entries.splice(++index, Infinity, state); },
    back() { queueMicrotask(() => pop()); }
  };
  function pop() {
    index--;
    const event = { state: history.state, stopImmediatePropagation() { this.stopped = true; } };
    windows.popstate(event);
    return event;
  }
  const document = { addEventListener: on(listeners), getElementById: () => main };
  const context = { document, history, location: { href: 'https://app.test/events' }, queueMicrotask,
    window: { addEventListener: on(windows), confirm() { prompts++; return allow; } } };
  vm.runInNewContext(readFileSync('src/main/resources/static/js/dialogs.js', 'utf8') + '\nthis.api = SchedulerDialogs;', context);
  const opener = { isConnected: true, getClientRects: () => [1], focus() { this.focused = true; } };
  const heading = { hasAttribute: () => false, matches: () => true, setAttribute() {}, focus() { this.focused = true; } };
  const field = { name: 'volunteerIds', value: '1', checked: false };
  const content = { scrollTop: 100 };
  const form = { reset() { field.checked = false; } };
  const dialog = { open: false, showModal() { this.open = true; }, close() { this.open = false; },
    querySelector: () => heading,
    querySelectorAll: selector => selector === 'form' ? [form] : selector.startsWith('input') ? [field] : [content],
    contains: target => target === form,
    getBoundingClientRect: () => ({ left: 10, right: 100, top: 10, bottom: 100 }) };
  return { ...context, listeners, windows, opener, dialog, heading, field, form, content, pop,
    get index() { return index; }, get prompts() { return prompts; }, set allow(value) { allow = value; } };
}

test('opening resets scroll and focuses heading; Back closes without leaving the page', () => {
  const s = setup();
  s.api.open(s.dialog, s.opener);
  assert.equal(s.content.scrollTop, 0);
  assert.ok(s.heading.focused);
  assert.equal(s.index, 1);
  assert.ok(s.pop().stopped);
  assert.equal(s.dialog.open, false);
  assert.ok(s.opener.focused);
  assert.equal(s.index, 0);
  assert.equal(s.pop().stopped, undefined);
});

test('Back preserves dirty changes when discard is declined, then discards on acceptance', () => {
  const s = setup(); s.api.open(s.dialog, s.opener, { unsaved: true });
  s.field.checked = true; s.allow = false; s.pop();
  assert.equal(s.dialog.open, true); assert.equal(s.field.checked, true); assert.equal(s.index, 1);
  s.allow = true; s.pop();
  assert.equal(s.dialog.open, false); assert.equal(s.field.checked, false); assert.equal(s.prompts, 2);
});

test('Escape and backdrop use the same unsaved guard; clicks inside do not dismiss', async () => {
  const s = setup(); s.api.open(s.dialog, s.opener, { unsaved: true });
  s.field.checked = true; s.allow = false;
  let prevented = false;
  s.listeners.cancel({ target: s.dialog, preventDefault() { prevented = true; } });
  assert.ok(prevented); assert.ok(s.dialog.open);
  s.listeners.click({ target: s.dialog, clientX: 50, clientY: 50 });
  assert.equal(s.prompts, 1);
  s.allow = true;
  s.listeners.click({ target: s.dialog, clientX: 0, clientY: 0 });
  await Promise.resolve();
  assert.equal(s.dialog.open, false); assert.equal(s.index, 0);
});

test('navigation waits for dialog history cleanup and cancels when changes are retained', async () => {
  const s = setup(); s.api.open(s.dialog, s.opener, { unsaved: true });
  s.field.checked = true; s.allow = false;
  assert.equal(await s.api.dismissAll(), false);
  s.allow = true;
  assert.equal(await s.api.dismissAll(), true);
  assert.equal(s.index, 0);
});

test('a successful save can leave without a dirty warning; prevented saves remain guarded', async () => {
  const s = setup(); s.api.open(s.dialog, s.opener, { unsaved: true }); s.field.checked = true;
  s.form.method = 'post';
  s.listeners.submit({ target: s.form, defaultPrevented: true }); await Promise.resolve();
  let prevented = false;
  s.windows.beforeunload({ preventDefault() { prevented = true; } }); assert.ok(prevented);
  s.listeners.submit({ target: s.form }); await Promise.resolve();
  prevented = false;
  s.windows.beforeunload({ preventDefault() { prevented = true; } }); assert.equal(prevented, false);
});

test('Done commits picker choices and consumes its history entry without prompting', async () => {
  const s = setup(); s.api.open(s.dialog, s.opener, { unsaved: true });
  s.field.checked = true;
  assert.equal(s.api.close(s.dialog, { commit: true }), true);
  await Promise.resolve();
  assert.equal(s.field.checked, true);
  assert.equal(s.prompts, 0);
  assert.equal(s.index, 0);
});

test('method=dialog close buttons use the guard and removed openers fall back to main', async () => {
  const s = setup(); s.api.open(s.dialog, s.opener, { unsaved: true });
  s.field.checked = true; s.allow = false; s.form.method = 'dialog';
  let prevented = false;
  s.listeners.submit({ target: s.form, preventDefault() { prevented = true; } });
  assert.ok(prevented); assert.ok(s.dialog.open);
  s.allow = true; s.opener.isConnected = false;
  s.listeners.submit({ target: s.form, preventDefault() {} });
  await Promise.resolve();
  assert.equal(s.dialog.open, false);
  assert.ok(s.document.getElementById('main').focused);
});

test('nested details consume one history entry per Back press', () => {
  const s = setup(); s.api.open(s.dialog, s.opener);
  const parent = s.dialog;
  const child = { ...parent, open: false };
  s.api.open(child, s.opener);
  s.pop();
  assert.equal(child.open, false); assert.equal(parent.open, true);
  assert.equal(s.index, 1);
  s.pop();
  assert.equal(parent.open, false); assert.equal(s.index, 0);
});
