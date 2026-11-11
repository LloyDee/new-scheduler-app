(() => {
  const { close: closeMenu } = Scheduler.menus('shift-menu', { focusFirst: true });
  function filter(dialog) {
    const search = dialog.querySelector('[data-assignment-search], [data-team-search], [data-shift-search]');
    if (!search) return;
    const query = search.value.trim().toLocaleLowerCase();
    const choices = [...dialog.querySelectorAll('.assignment-choices label, .team-sheet-list li, .shift-details-list li')];
    let selected = 0, shown = 0;
    choices.forEach(choice => {
      if (choice.querySelector('input')?.checked) selected++;
      choice.hidden = !choice.querySelector('span').textContent.toLocaleLowerCase().includes(query);
      if (!choice.hidden) shown++;
    });
    const status = dialog.querySelector('[data-assignment-count], [data-team-count], [data-shift-count]');
    status.textContent = search.matches('[data-assignment-search]')
      ? selected + ' selected, ' + Math.max(0, Number(dialog.dataset.requiredCount) - selected) + ' remaining'
      : shown + ' volunteers shown';
    dialog.querySelector('[data-assignment-empty], [data-team-empty], [data-shift-empty]').hidden = shown !== 0;
  }
  document.addEventListener('scheduler:detail', event => filter(event.detail));
  document.addEventListener('input', event => {
    if (event.target.matches('[data-assignment-search], [data-team-search], [data-shift-search]')) filter(event.target.closest('dialog'));
  });
  document.addEventListener('change', event => {
    if (event.target.matches('.assignment-choices input')) filter(event.target.closest('dialog'));
  });
  document.addEventListener('click', event => {
    const close = event.target.closest('[data-assignment-cancel], [data-team-close]');
    if (close) { SchedulerDialogs.close(close.closest('dialog')); return; }
    const trigger = event.target.closest('[data-assignment-dialog], [data-team-dialog], [data-shift-open]');
    if (trigger) {
      const menu = trigger.closest('.shift-menu');
      if (menu) closeMenu(menu);
      const id = trigger.dataset.assignmentDialog || trigger.dataset.teamDialog || trigger.getAttribute('aria-controls');
      SchedulerDialogs.open(document.getElementById(id), menu?.querySelector('.shift-menu-toggle') || trigger,
        { unsaved: trigger.hasAttribute('data-assignment-dialog') });
      return;
    }
    const card = event.target.closest('[data-shift-details]');
    if (card && !event.target.closest('button, a, input, select, textarea, label, form, dialog, .shift-menu, [role="button"]')
        && !window.getSelection()?.toString()) {
      SchedulerDialogs.open(document.getElementById(card.dataset.shiftDetails), card.querySelector('[data-shift-open]'));
    }
  });
})();
