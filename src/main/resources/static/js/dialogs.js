// Native dialogs provide the focus trap; this controller owns their lifecycle.
const SchedulerDialogs = (() => {
  const stack = [];
  let draining = null;
  let sequence = 0;
  const snapshot = dialog => JSON.stringify([...dialog.querySelectorAll('input[name], select[name], textarea[name]')]
    .map(field => [field.name, field.value, field.checked, field.tagName === 'SELECT'
      ? [...field.selectedOptions].map(option => option.value) : null]));
  const dirty = entry => entry.unsaved && snapshot(entry.dialog) !== entry.initial;
  const approve = entry => !dirty(entry) || window.confirm('Discard unsaved changes?');
  const finish = (entry, discard = true) => {
    stack.splice(stack.indexOf(entry), 1);
    if (discard && entry.unsaved) entry.fields.forEach(({ field, value, checked, selected }) => {
      field.value = value;
      field.checked = checked;
      if (selected) [...field.options].forEach((option, index) => { option.selected = selected[index]; });
    });
    entry.dialog.close();
    const target = entry.opener?.isConnected && entry.opener.getClientRects().length
      ? entry.opener : stack.at(-1)?.dialog || document.getElementById('main');
    target?.focus({ preventScroll: true });
  };
  function close(dialog, { commit = false } = {}) {
    const entry = stack.at(-1);
    if (!entry || entry.dialog !== dialog || draining) return false;
    if (!commit && !approve(entry)) return false;
    finish(entry, !commit);
    draining = {};
    draining.promise = new Promise(resolve => { draining.resolve = resolve; });
    history.back();
    return true;
  }
  function open(dialog, opener = document.activeElement, options = {}) {
    if (!dialog || dialog.open || draining) return false;
    const fields = [...dialog.querySelectorAll('input[name], select[name], textarea[name]')].map(field => ({
      field, value: field.value, checked: field.checked,
      selected: field.tagName === 'SELECT' ? [...field.options].map(option => option.selected) : null
    }));
    const entry = { dialog, opener, fields, unsaved: options.unsaved, initial: snapshot(dialog), id: ++sequence };
    dialog.showModal();
    stack.push(entry);
    history.pushState({ ...history.state, schedulerDialog: entry.id }, '', location.href);
    dialog.querySelectorAll('.app-dialog-content, .team-sheet-list').forEach(content => { content.scrollTop = 0; });
    const focus = options.focus || dialog.querySelector('[autofocus], h2, input, button');
    if (focus) {
      if (focus.matches('h1, h2, h3') && !focus.hasAttribute('tabindex')) focus.setAttribute('tabindex', '-1');
      focus.focus({ preventScroll: true });
    }
    return true;
  }
  document.addEventListener('cancel', event => {
    if (!stack.some(entry => entry.dialog === event.target)) return;
    event.preventDefault();
    close(event.target);
  }, true);
  document.addEventListener('click', event => {
    const dialog = stack.at(-1)?.dialog;
    if (event.target !== dialog) return;
    const rect = dialog.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) close(dialog);
  });
  document.addEventListener('submit', event => {
    const entry = stack.at(-1);
    if (!entry || !entry.dialog.contains(event.target)) return;
    const method = event.submitter?.getAttribute('formmethod') || event.target.method;
    if (method === 'dialog') {
      event.preventDefault();
      close(entry.dialog);
    } else {
      // Wait for validation/confirmation handlers before allowing a successful save to leave.
      queueMicrotask(() => { if (!event.defaultPrevented) entry.unsaved = false; });
    }
  }, true);
  window.addEventListener('popstate', event => {
    if (draining) {
      event.stopImmediatePropagation();
      const done = draining;
      draining = null;
      done.resolve();
      return;
    }
    const entry = stack.at(-1);
    if (!entry) return;
    event.stopImmediatePropagation();
    if (approve(entry)) finish(entry);
    else history.pushState({ ...history.state, schedulerDialog: entry.id }, '', location.href);
  });
  window.addEventListener('beforeunload', event => {
    if (stack.some(dirty)) { event.preventDefault(); event.returnValue = ''; }
  });
  async function dismissAll() {
    if (draining) await draining.promise;
    while (stack.length) {
      if (!close(stack.at(-1).dialog)) return false;
      await draining.promise;
    }
    return true;
  }
  return { open, close, dismissAll };
})();
