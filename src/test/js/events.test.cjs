const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/app.js', 'utf8');

test('circuit follows event type on initial load and when type changes', () => {
  const fields = {
    '[data-assembly-field]': {},
    '[data-circuit-field]': {},
    '[data-event-preview]': {}
  };
  let change;
  const form = {
    elements: {
      type: { value: 'REGIONAL_CONVENTION' },
      startDate: { value: '2027-06-04' },
      assembly: { value: 'with Circuit Overseer' },
      circuit: { value: 'FL-58B' }
    },
    querySelector: selector => fields[selector],
    addEventListener: (name, callback) => { if (name === 'change') change = callback; }
  };
  vm.runInNewContext(script, { document: {
    querySelectorAll: () => [],
    querySelector: selector => selector === '[data-event-form]' ? form : null,
    addEventListener() {}
  } });
  assert.equal(fields['[data-circuit-field]'].hidden, true);
  assert.equal(form.elements.circuit.disabled, true);
  form.elements.type.value = 'CIRCUIT_ASSEMBLY';
  change();
  assert.equal(fields['[data-circuit-field]'].hidden, false);
  assert.equal(form.elements.circuit.disabled, false);
  assert.equal(form.elements.circuit.value, 'FL-58B');
  form.elements.type.value = 'REGIONAL_CONVENTION';
  change();
  assert.equal(fields['[data-circuit-field]'].hidden, true);
  assert.equal(form.elements.circuit.disabled, true);
});
