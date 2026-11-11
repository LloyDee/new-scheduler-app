const { SchedulerDialogs } = require('./helpers/dialogs.cjs');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/shared.js', 'utf8') + '\n' + readFileSync('src/main/resources/static/js/schedule.js', 'utf8');
function setup(assignment = false, names = ['Alice', 'Bob', 'Carol']) {
  const listeners = {}, choices = names.map((name, i) => ({ hidden: false,
    input: { checked: i !== 1 }, querySelector(selector) { return selector === 'input' ? this.input : { textContent: name }; } }));
  const count = {}, empty = {}, heading = { focus() { this.focused = true; } }, content = { scrollTop: 50 };
  const search = { value: '', matches: () => assignment, closest: () => dialog };
  const dialog = { dataset: { requiredCount: '2' }, opens: 0, showModal() { this.opens++; }, close() { this.closeHandler?.(); },
    addEventListener(type, fn) { if (type === 'close') this.closeHandler = fn; },
    querySelector(selector) {
      if (selector.includes('search')) return names.length ? search : null;
      if (selector.includes('count')) return count;
      if (selector.includes('empty')) return empty;
      if (selector === 'h2') return heading;
      if (selector === '.app-dialog-content') return content;
    }, querySelectorAll: () => choices };
  const title = { focus() { this.focused = true; } };
  const card = { dataset: { shiftDetails: 'details' }, querySelector: () => title };
  const trigger = { dataset: assignment ? { assignmentDialog: 'assign' } : { teamDialog: 'team' },
    hasAttribute: () => assignment, closest: () => null, focus() { this.focused = true; } };
  const selection = { value: '' };
  const document = { querySelectorAll: () => [], getElementById: () => dialog,
    addEventListener(type, fn) { (listeners[type] ||= []).push(fn); } };
  vm.runInNewContext(script, { SchedulerDialogs, document, window: { getSelection: () => selection.value } });
  function fire(type, event) { for (const fn of listeners[type] || []) fn(event); }
  return { dialog, choices, search, count, empty, heading, content, title, trigger, selection, fire,
    cardClick(interactive = false) { fire('click', { target: { closest: selector => selector === '[data-shift-details]' ? card : interactive && selector.startsWith('button,') ? {} : null } }); },
    open() { fire('click', { target: { closest: selector => selector.includes('[data-assignment-dialog]') ? trigger : null } }); },
    detail() { fire('scheduler:detail', { detail: dialog }); },
    input() { fire('input', { target: { matches: () => true, closest: () => dialog } }); }
  };
}
test('card opens details and restores focus; selection and interactive controls preserve their behavior', () => {
  const s = setup(); s.cardClick(true); s.selection.value = 'selected text'; s.cardClick();
  assert.equal(s.dialog.opens, 0);
  s.selection.value = ''; s.cardClick(); assert.equal(s.dialog.opens, 1);
  assert.equal(s.content.scrollTop, 0); assert.equal(s.heading.focused, true);
  s.dialog.close(); assert.equal(s.title.focused, true);
});
test('explicit dialog triggers preserve opener and unsaved assignment option', () => {
  const s = setup(true); s.open(); assert.equal(s.dialog.opens, 1);
  s.dialog.close(); assert.equal(s.trigger.focused, true);
});
test('loaded detail filtering counts selected choices even when search hides them', () => {
  const s = setup(true); s.detail(); assert.equal(s.count.textContent, '2 selected, 0 remaining');
  s.search.value = ' BOB '; s.input();
  assert.deepEqual(s.choices.map(c => c.hidden), [true, false, true]);
  assert.equal(s.count.textContent, '2 selected, 0 remaining');
  s.search.value = 'missing'; s.input(); assert.equal(s.empty.hidden, false);
  s.choices[0].input.checked = false; s.fire('change', { target: { matches: () => true, closest: () => s.dialog } });
  assert.equal(s.count.textContent, '1 selected, 1 remaining');
});
test('team details report shown volunteers and handle empty teams', () => {
  const s = setup(false); s.detail(); assert.equal(s.count.textContent, '3 volunteers shown');
  s.search.value = 'bob'; s.input(); assert.equal(s.count.textContent, '1 volunteers shown');
  const empty = setup(false, []); empty.detail(); empty.open(); assert.equal(empty.dialog.opens, 1);
});
test('shared three-dot menu focuses its first action and dismisses only keyboard blur', () => {
  const listeners = {};
  const choose = { focus() { this.focused = true; } }, toggle = { setAttribute() {}, focus() { this.focused = true; } };
  const items = { hidden: true, querySelector: () => choose };
  const menu = { contains: node => node === choose, querySelector: selector => selector === '.shift-menu-toggle' ? toggle : items };
  const document = { querySelectorAll: () => [menu], addEventListener(type, fn) { (listeners[type] ||= []).push(fn); } };
  vm.runInNewContext(script, { SchedulerDialogs, document });
  const fire = (type, event = {}) => { for (const fn of listeners[type] || []) fn({ target: { closest: selector => selector === '.shift-menu' ? menu : null }, ...event }); };
  const click = () => fire('click', { target: { closest: selector => selector === '.shift-menu-toggle' ? toggle : selector === '.shift-menu' ? menu : null } });
  click(); assert.equal(items.hidden, false); assert.equal(choose.focused, true);
  fire('keydown', { key: 'Tab' }); fire('pointerdown'); fire('focusout', { relatedTarget: {} }); assert.equal(items.hidden, false);
  fire('keydown', { key: 'Tab' }); fire('focusout', { relatedTarget: choose }); assert.equal(items.hidden, false);
  fire('focusout', { relatedTarget: {} }); assert.equal(items.hidden, true);
  click(); fire('keydown', { key: 'Escape', stopPropagation() {} }); assert.equal(toggle.focused, true);
});
