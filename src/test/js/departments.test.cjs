const { SchedulerDialogs } = require('./helpers/dialogs.cjs');
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
function setup(auto = false) {
  function el(extra = {}) { return Object.assign({dataset: {}, handlers: {}, addEventListener(n, fn) { (this.handlers[n] ||= []).push(fn); }, fire(n, event = {}) { for (const fn of this.handlers[n] || []) fn(event); }, focus() { this.focused = true; }}, extra); }
  const title = el(), content = {scrollTop: 50};
  const dialog = el({dataset: {departmentAutoOpen: String(auto)}, opens: 0, showModal() { this.opens++; }, close() { this.fire('close'); }, querySelector: s => s === 'h2' ? title : content, getBoundingClientRect: () => ({left: 10, right: 100, top: 10, bottom: 100})});
  const menus = [0,1].map(() => {
    const toggle = el({setAttribute(n,v) { this[n] = v; }}), items = el({hidden: true}), open = el({getAttribute: () => 'dialog'});
    return el({toggle, items, open, contains: t => t === toggle, querySelector: s => ({'.department-menu-toggle': toggle, '.department-menu-items': items, '[data-department-open]': open})[s]});
  });
  const cardTitle = el();
  const card = el({dataset: {departmentCard: 'dialog'}, querySelector: () => cardTitle});
  const selection = {value: ''};
  const document = el({getElementById: () => dialog, querySelectorAll: s => s === '.department-menu' ? menus : s === '[data-department-card]' ? [card] : [dialog], querySelector: () => menus[0].toggle});
  for (const menu of menus) {
    for (const node of [menu, menu.toggle]) {
      node.closest = selector => selector === '.department-menu' ? menu : selector === '.department-menu-toggle' && node === menu.toggle ? node : null;
      const fire = node.fire.bind(node);
      node.fire = (type, event = {}) => { fire(type, event); document.fire(type, { target: node, ...event }); };
    }
  }
  vm.runInNewContext((readFileSync('src/main/resources/static/js/shared.js','utf8') + '\n' + readFileSync('src/main/resources/static/js/departments.js','utf8')), { SchedulerDialogs, document, window: {getSelection: () => selection.value}});
  return {document, menus, dialog, title, content, card, cardTitle, selection};
}
test('menus open exclusively, dismiss outside and restore focus with Escape', () => {
  const s = setup(); const [a,b] = s.menus;
  a.toggle.fire('click'); assert.equal(a.items.hidden, false);
  b.toggle.fire('click'); assert.equal(a.items.hidden, true); assert.equal(b.items.hidden, false);
  b.fire('keydown', {key: 'Escape', stopPropagation() {}}); assert.equal(b.items.hidden, true); assert.equal(b.toggle.focused, true);
  a.toggle.fire('click'); s.document.fire('click', {target: {closest: () => null}}); assert.equal(a.items.hidden, true);
});
test('View closes menu, resets dialog and restores focus to ellipsis', () => {
  const s = setup(); const a = s.menus[0]; s.document.fire('scheduler:page');
  a.toggle.fire('click'); a.open.fire('click', {currentTarget: a.open});
  assert.equal(s.dialog.opens, 1); assert.equal(s.content.scrollTop, 0); assert.equal(s.title.focused, true); assert.equal(a.items.hidden, true);
  s.dialog.fire('click', {target: s.dialog, clientX: 0, clientY: 0}); assert.equal(a.toggle.focused, true);
});
test('direct view opens once across repeated initialization', () => {
  const s = setup(true); s.document.fire('scheduler:page'); assert.equal(s.dialog.opens, 1);
});
test('card taps and title activation open once, close menus and restore title focus', () => {
  const s = setup(); s.document.fire('scheduler:page');
  s.menus[0].toggle.fire('click');
  s.card.fire('click', {target: {closest: () => null}});
  assert.equal(s.dialog.opens, 1); assert.equal(s.menus[0].items.hidden, true);
  assert.equal(s.content.scrollTop, 0); assert.equal(s.title.focused, true);
  s.dialog.close(); assert.equal(s.cardTitle.focused, true);
  s.cardTitle.fire('click'); assert.equal(s.dialog.opens, 2);
});
test('card taps ignore controls, menu surfaces and selected text', () => {
  const s = setup();
  s.card.fire('click', {target: {closest: () => ({})}});
  s.selection.value = 'selected name';
  s.card.fire('click', {target: {closest: () => null}});
  assert.equal(s.dialog.opens, 0);
});

test('mobile focus changes leave actions clickable while Tab out dismisses', () => {
  const s = setup(); const menu = s.menus[0];
  menu.toggle.fire('click');
  menu.fire('keydown', {key: 'Tab'});
  menu.fire('pointerdown');
  menu.fire('focusout', {relatedTarget: {}});
  assert.equal(menu.items.hidden, false);
  menu.open.fire('click', {currentTarget: menu.open});
  assert.equal(s.dialog.opens, 1);
  menu.toggle.fire('click');
  menu.fire('keydown', {key: 'Tab'});
  menu.fire('focusout', {relatedTarget: {}});
  assert.equal(menu.items.hidden, true);
});
