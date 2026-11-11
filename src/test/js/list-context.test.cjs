const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const script = fs.readFileSync('src/main/resources/static/js/list-context.js', 'utf8');
function setup(path, editor = false) {
  const handlers = {};
  const location = new URL(path, 'https://scheduler.test');
  const window = { scrollY: 640, addEventListener() {} };
  const document = {
    addEventListener(type, callback) { handlers[type] = callback; },
    querySelector() { return editor ? {} : null; },
    createElement() { return {}; }
  };
  vm.runInNewContext(script, { window, document, location, URL });
  function click(href) {
    const link = { href: new URL(href, location).href, closest() { return {}; } };
    handlers.click({ target: { closest() { return link; } } });
    return new URL(link.href);
  }
  return { handlers, window, click };
}
test('edit links carry the current filters, page and scroll', () => {
  const { click } = setup('/events/1/volunteers?departmentId=3&q=Alex%20%26%20Sam&page=2');
  const url = click('/events/1/volunteers?edit=9');
  const target = new URL(url.searchParams.get('returnTo'), url);
  assert.equal(target.searchParams.get('q'), 'Alex & Sam');
  assert.equal(target.searchParams.get('departmentId'), '3');
  assert.equal(target.searchParams.get('page'), '2');
  assert.equal(target.searchParams.get('scroll'), '640');
});
test('cancel and post reuse the original context after a validation retry', () => {
  const target = '/events/1/schedule?date=2027-06-04&page=2&scroll=900';
  const { click, handlers } = setup('/events/1/schedule?edit=9&returnTo=' + encodeURIComponent(target), true);
  assert.equal(click('/events/1/schedule').pathname + click('/events/1/schedule').search, target);
  const form = { method: 'post', action: 'https://scheduler.test/events/1/schedule/save',
    querySelector() { return null; }, append(input) { this.input = input; } };
  handlers.submit({ target: form });
  assert.equal(form.input.value, target);
});
test('return context cannot replace links with external URLs', () => {
  const { click } = setup('/events/1/schedule?edit=9&returnTo=https%3A%2F%2Fevil.test%2Fevents%2F1%2Fschedule', true);
  assert.equal(click('/events/1/schedule').origin, 'https://scheduler.test');
});
test('scroll restoration applies to lists and not editors', () => {
  const { window } = setup('/events/1/schedule');
  assert.deepEqual(Array.from(window.schedulerListScroll(new URL('https://scheduler.test/events/1/schedule?scroll=900'))), [0, 900]);
  assert.equal(window.schedulerListScroll(new URL('https://scheduler.test/events/1/schedule?edit=9&scroll=900')), null);
});
