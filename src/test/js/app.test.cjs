const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/app.js', 'utf8');

function setup() {
  const listeners = {};
  const timers = [];
  const notification = type => ({ dataset: { notification: type }, remove() { this.removed = true; } });
  const success = notification('success');
  const error = notification('error');
  const document = {
    querySelectorAll: selector => selector === '[data-notification]' ? [success, error] : [],
    querySelector: () => null,
    addEventListener(type, listener) { (listeners[type] ||= []).push(listener); },
    createElement: () => ({ setAttribute() {}, remove() { this.removed = true; } }),
  };
  vm.runInNewContext(script, { document, window: {
    setTimeout: (fn, delay) => timers.push({ fn, delay }),
    matchMedia: () => ({ matches: true }),
  } });
  return { success, error, timers, fire(type, target) { listeners[type].forEach(fn => fn({ target })); } };
}

test('only success messages expire; errors remain until dismissed', () => {
  const ui = setup();
  assert.equal(ui.timers.length, 1);
  assert.equal(ui.timers[0].delay, 2500);
  ui.timers[0].fn();
  assert.equal(ui.success.removed, true);
  assert.equal(ui.error.removed, undefined);
  ui.fire('click', { closest: selector => selector === '[data-dismiss-notification]' ? { closest: () => ui.error } : null });
  assert.equal(ui.error.removed, true);
});

test('field messages persist while invalid and clear when resolved, preserving help text', () => {
  const ui = setup();
  const attributes = { 'aria-describedby': 'phone-help' };
  const input = {
    validity: { valid: false }, willValidate: true, validationMessage: 'Enter a 10-digit phone number.',
    getAttribute: name => attributes[name],
    setAttribute: (name, value) => { attributes[name] = value; },
    removeAttribute: name => { delete attributes[name]; },
    insertAdjacentElement(position, error) { this.error = error; },
  };
  ui.fire('invalid', input);
  const error = input.error;
  assert.equal(error.textContent, input.validationMessage);
  assert.equal(attributes['aria-invalid'], 'true');
  assert.equal(attributes['aria-describedby'], `phone-help ${error.id}`);
  input.validationMessage = 'Still invalid';
  ui.fire('input', input);
  assert.equal(error.textContent, 'Still invalid');
  assert.equal(error.removed, undefined);
  input.validity.valid = true;
  ui.fire('change', input);
  assert.equal(error.removed, true);
  assert.equal(attributes['aria-invalid'], undefined);
  assert.equal(attributes['aria-describedby'], 'phone-help');
});
