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

test('assignment picker searches, counts hidden selections, and opens without focusing search', () => {
  const listeners = [];
  const el = (props = {}) => Object.assign({ dataset: {}, addEventListener(type, fn) { listeners.push([this, type, fn]); }, focus() { this.focused = true; } }, props);
  const emit = (target, type) => listeners.filter(([e, t]) => e === target && t === type).forEach(([, , fn]) => fn());
  const search = el({ value: '' }), heading = el();
  const inputs = [{ checked: true }, { checked: false }, { checked: true }];
  const choices = ['Alice', 'Bob', 'Carol'].map((name, i) => ({ hidden: false, querySelector: s => s === 'input' ? inputs[i] : { textContent: name } }));
  const count = {}, empty = {}, content = {}, cancel = el();
  const form = el({ reset() { search.value = ''; inputs.forEach((v, i) => v.checked = i !== 1); } });
  const dialog = el({ dataset: { requiredCount: '2' }, showModal() {}, close() {}, querySelectorAll: () => choices,
    querySelector: s => ({ form, '[data-assignment-search]': search, 'h2': heading, '[data-assignment-count]': count, '[data-assignment-empty]': empty, '[data-assignment-cancel]': cancel, '.app-dialog-content': content })[s] });
  const trigger = el({ dataset: { assignmentDialog: 'assign' }, closest: () => null });
  const document = { querySelectorAll: s => s === '[data-assignment-dialog]' ? [trigger] : [], getElementById: () => dialog, addEventListener() {} };
  vm.runInNewContext(readFileSync('src/main/resources/static/js/schedule.js', 'utf8'), { document });
  emit(trigger, 'click');
  assert.equal(heading.focused, true);
  assert.equal(search.focused, undefined);
  assert.equal(count.textContent, '2 selected, 0 remaining');
  search.value = '  BOB '; emit(search, 'input');
  assert.deepEqual(choices.map(c => c.hidden), [true, false, true]);
  assert.equal(count.textContent, '2 selected, 0 remaining');
  search.value = 'missing'; emit(search, 'input');
  assert.equal(empty.hidden, false);
  search.value = ''; emit(search, 'input');
  inputs[0].checked = false; emit(form, 'change');
  assert.equal(choices[0].hidden, false);
  assert.equal(count.textContent, '1 selected, 1 remaining');
  inputs[1].checked = true; inputs[0].checked = true; emit(form, 'change');
  assert.equal(count.textContent, '3 selected, 0 remaining');
  emit(trigger, 'click');
  assert.equal(search.value, '');
  assert.equal(count.textContent, '2 selected, 0 remaining');
  assert.ok(choices.every(c => !c.hidden));
});

test('three-dot menu focuses Choose Volunteers and survives pointer blur before activation', () => {
  const listeners = [];
  const el = props => Object.assign({ dataset: {}, addEventListener(type, fn) { listeners.push([this, type, fn]); }, focus() { this.focused = true; }, setAttribute() {} }, props);
  const choose = el({}), toggle = el({}), items = { hidden: true, querySelector: () => choose };
  const menu = el({ contains: target => target === toggle || target === choose, querySelector: s => s === '.shift-menu-toggle' ? toggle : items });
  const document = { querySelectorAll: s => s === '.shift-menu' ? [menu] : [], addEventListener() {} };
  vm.runInNewContext(readFileSync('src/main/resources/static/js/schedule.js', 'utf8'), { document });
  const click = listeners.find(([target, type]) => target === toggle && type === 'click')[2];
  click();
  assert.equal(items.hidden, false);
  assert.equal(choose.focused, true);
  const emit = (type, event = {}) => listeners.find(([target, name]) => target === menu && name === type)[2](event);
  emit('keydown', { key: 'Tab' });
  emit('pointerdown');
  emit('focusout', { relatedTarget: {} });
  assert.equal(items.hidden, false, 'pointer blur must not hide the action before click');
  emit('keydown', { key: 'Tab' });
  emit('focusout', { relatedTarget: choose });
  assert.equal(items.hidden, false);
  emit('focusout', { relatedTarget: {} });
  assert.equal(items.hidden, true, 'Tab out still dismisses the menu');
  click();
  click();
  assert.equal(items.hidden, true);
});
