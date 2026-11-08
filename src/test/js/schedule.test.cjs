const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
function setup(names = ['Alice', 'Bob']) {
  const listeners = new Map();
  const element = (extra = {}) => Object.assign({ dataset: {}, addEventListener(type, fn) { const key = [this, type]; listeners.set(key, fn); }, focus() { this.focused = true; } }, extra);
  const emit = (target, type, event = {}) => { for (const [[el, name], fn] of listeners) if (el === target && name === type) fn(event); };
  const search = names.length ? element({ value: '' }) : null;
  const people = names.map(name => ({ hidden: false, querySelector: () => ({ textContent: name }) }));
  const count = {}, empty = {}, heading = element(), content = { scrollTop: 50 };
  const title = element(), viewAll = element();
  const dialog = element({ opens: 0, showModal() { this.opens++; }, close() { emit(this, 'close'); }, getBoundingClientRect: () => ({ left: 10, right: 100, top: 10, bottom: 100 }), querySelector: selector => ({ '[data-shift-search]': search, '[data-shift-count]': count, '[data-shift-empty]': empty, 'h2': heading, '.app-dialog-content': content })[selector], querySelectorAll: () => people });
  const card = element({ dataset: { shiftDetails: 'details' }, querySelector: () => title, querySelectorAll: () => [title, viewAll] });
  const page = {};
  const document = { querySelectorAll: selector => selector === '[data-shift-details]' ? [card] : [], getElementById: () => dialog, addEventListener: (name, fn) => { page[name] = fn; } };
  const selection = { value: '' };
  vm.runInNewContext(readFileSync('src/main/resources/static/js/schedule.js', 'utf8'), { document, window: { getSelection: () => selection.value } });
  return { emit, card, title, viewAll, dialog, search, people, count, empty, content, heading, page, selection };
}
test('card tap opens details, resets scroll and focuses title; repeated initialization is safe', () => {
  const s = setup(); s.page['scheduler:page']();
  s.emit(s.card, 'click', { target: { closest: () => null } });
  assert.equal(s.dialog.opens, 1); assert.equal(s.content.scrollTop, 0); assert.equal(s.heading.focused, true);
  s.dialog.close(); assert.equal(s.title.focused, true);
});
test('interactive targets and text selection do not open the card', () => {
  const s = setup();
  s.emit(s.card, 'click', { target: { closest: () => ({}) } });
  s.selection.value = 'selected text';
  s.emit(s.card, 'click', { target: { closest: () => null } });
  assert.equal(s.dialog.opens, 0);
});
test('explicit triggers reset search and restore focus to the actual opener', () => {
  const s = setup(); s.search.value = 'bob'; s.emit(s.search, 'input');
  assert.equal(s.people[0].hidden, true); assert.equal(s.people[1].hidden, false);
  s.search.value = 'missing'; s.emit(s.search, 'input'); assert.equal(s.empty.hidden, false);
  s.emit(s.viewAll, 'click'); assert.equal(s.search.value, ''); assert.equal(s.people[0].hidden, false);
  s.emit(s.dialog, 'click', { target: s.dialog, clientX: 0, clientY: 0 }); assert.equal(s.viewAll.focused, true);
});
test('empty teams open without a search field or misleading search error', () => {
  const s = setup([]); s.emit(s.title, 'click'); assert.equal(s.dialog.opens, 1); assert.equal(s.empty.hidden, true);
});
