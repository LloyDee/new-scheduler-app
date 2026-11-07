const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('src/main/resources/static/js/logs.js', 'utf8');

function setup(zone, values) {
  const events = {};
  const makeTime = entry => {
    const { value, compact = false } = entry && typeof entry === 'object' ? entry : { value: entry };
    return { getAttribute: name => name === 'datetime' ? value : compact ? 'compact' : '', textContent: 'UTC fallback' };
  };
  const state = { times: values.map(makeTime), labels: [{}, {}] };
  vm.runInNewContext(script, {
    Intl: { DateTimeFormat: function(locale, options) {
      return new Intl.DateTimeFormat(locale, { ...options, timeZone: zone });
    } },
    document: {
      querySelectorAll: selector => selector === '[data-log-time]' ? state.times : state.labels,
      addEventListener: (name, fn) => { events[name] = fn; },
    },
  });
  state.navigate = values => { state.times = values.map(makeTime); events['scheduler:page'](); };
  state.refresh = () => events['scheduler:page']();
  return state;
}

test('Chicago viewer sees previous local date and correct daylight-saving offsets', () => {
  const state = setup('America/Chicago', ['2026-11-07T03:27:00Z', '2026-10-31T03:27:00Z']);
  assert.equal(state.times[0].textContent, 'Nov 6, 2026, 9:27 PM CST');
  assert.equal(state.times[1].textContent, 'Oct 30, 2026, 10:27 PM CDT');
  state.labels.forEach(label => assert.equal(label.textContent, 'your local timezone (America/Chicago)'));
});

test('another viewer timezone applies on initial load and section navigation', () => {
  const state = setup('Asia/Tokyo', ['2026-11-07T03:27:00Z']);
  assert.equal(state.times[0].textContent, 'Nov 7, 2026, 12:27 PM GMT+9');
  state.navigate(['2026-11-08T23:27:00Z', '2026-11-08T23:27:00Z']);
  state.refresh();
  state.times.forEach(time => assert.equal(time.textContent, 'Nov 9, 2026, 8:27 AM GMT+9'));
});

test('missing or invalid timestamps preserve fallback text and empty pages are safe', () => {
  const state = setup('America/Chicago', ['', null, 'invalid']);
  state.times.forEach(time => assert.equal(time.textContent, 'UTC fallback'));
  state.navigate([]);
});

test('last seen omits timezone suffix while first seen retains it across navigation', () => {
  const state = setup('America/Chicago', [
    { value: '2026-11-07T03:27:00Z', compact: true },
    '2026-11-07T03:27:00Z',
  ]);
  assert.equal(state.times[0].textContent, 'Nov 6, 2026, 9:27 PM');
  assert.equal(state.times[1].textContent, 'Nov 6, 2026, 9:27 PM CST');
  state.navigate([{ value: '2026-10-31T03:27:00Z', compact: true }]);
  assert.equal(state.times[0].textContent, 'Oct 30, 2026, 10:27 PM');
});
