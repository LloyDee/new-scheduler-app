(() => {
function closeShiftMenu(menu) {
  menu.querySelector('.shift-menu-toggle').setAttribute('aria-expanded', 'false');
  menu.querySelector('.shift-menu-items').hidden = true;
}
function initializeSchedule() {
  document.querySelectorAll('[data-shift-details]').forEach(card => {
    if (card.dataset.detailsReady) return;
    card.dataset.detailsReady = 'true';
    const dialog = document.getElementById(card.dataset.shiftDetails);
    const title = card.querySelector('[data-shift-open]');
    const search = dialog.querySelector('[data-shift-search]');
    const people = [...dialog.querySelectorAll('.shift-details-list li')];
    const filter = () => {
      const query = search?.value.trim().toLocaleLowerCase() || '';
      let count = 0;
      people.forEach(person => {
        person.hidden = !person.querySelector('span').textContent.toLocaleLowerCase().includes(query);
        if (!person.hidden) count++;
      });
      dialog.querySelector('[data-shift-count]').textContent = count + ' volunteers shown';
      dialog.querySelector('[data-shift-empty]').hidden = !people.length || count !== 0;
    };
    const open = trigger => {
      if (search) search.value = '';
      filter();
      SchedulerDialogs.open(dialog, trigger);
    };
    card.querySelectorAll('[data-shift-open]').forEach(trigger => {
      trigger.addEventListener('click', () => open(trigger));
    });
    card.addEventListener('click', event => {
      if (event.target.closest('button, a, input, select, textarea, label, form, dialog, .shift-menu, [role="button"]')) return;
      if (window.getSelection()?.toString()) return;
      open(title);
    });
    search?.addEventListener('input', filter);
  });
  document.querySelectorAll('.shift-menu').forEach(menu => {
    if (menu.dataset.menuReady) return;
    menu.dataset.menuReady = 'true';
    const toggle = menu.querySelector('.shift-menu-toggle');
    const items = menu.querySelector('.shift-menu-items');
    toggle.addEventListener('click', () => {
      items.hidden = !items.hidden;
      toggle.setAttribute('aria-expanded', String(!items.hidden));
      if (!items.hidden) items.querySelector('[data-assignment-dialog], a, button')?.focus();
    });
    let tabbing = false;
    menu.addEventListener('pointerdown', () => { tabbing = false; });
    menu.addEventListener('keydown', event => {
      tabbing = event.key === 'Tab';
      if (event.key === 'Escape') { closeShiftMenu(menu); toggle.focus(); }
    });
    menu.addEventListener('focusout', event => {
      // Pointer focus changes can precede a mobile click; only Tab dismisses on blur.
      if (tabbing && event.relatedTarget && !menu.contains(event.relatedTarget)) closeShiftMenu(menu);
    });
  });
  document.querySelectorAll('[data-team-dialog]').forEach(trigger => {
    if (trigger.dataset.teamReady) return;
    trigger.dataset.teamReady = 'true';
    const dialog = document.getElementById(trigger.dataset.teamDialog);
    const search = dialog.querySelector('[data-team-search]');
    const people = [...dialog.querySelectorAll('.team-sheet-list li')];
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase();
      let count = 0;
      people.forEach(person => {
        person.hidden = !person.querySelector('span').textContent.toLocaleLowerCase().includes(query);
        if (!person.hidden) count++;
      });
      dialog.querySelector('[data-team-empty]').hidden = count !== 0;
      dialog.querySelector('[data-team-count]').textContent = count + ' volunteers shown';
    };
    trigger.addEventListener('click', () => {
      search.value = '';
      filter();
      SchedulerDialogs.open(dialog, trigger, { focus: search });
    });
    search.addEventListener('input', filter);
    dialog.querySelector('[data-team-close]').addEventListener('click', () => SchedulerDialogs.close(dialog));
  });
  document.querySelectorAll('[data-assignment-dialog]').forEach(trigger => {
    if (trigger.dataset.assignmentReady) return;
    trigger.dataset.assignmentReady = 'true';
    const dialog = document.getElementById(trigger.dataset.assignmentDialog);
    if (!dialog) return;
    const form = dialog.querySelector('form');
    const search = dialog.querySelector('[data-assignment-search]');
    const choices = [...dialog.querySelectorAll('.assignment-choices label')];
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase();
      let selected = 0;
      let shown = 0;
      choices.forEach(choice => {
        const checked = choice.querySelector('input').checked;
        if (checked) selected++;
        choice.hidden = !choice.querySelector('span').textContent.toLocaleLowerCase().includes(query);
        if (!choice.hidden) shown++;
      });
      dialog.querySelector('[data-assignment-count]').textContent = selected + ' selected, '
        + Math.max(0, Number(dialog.dataset.requiredCount) - selected) + ' remaining';
      dialog.querySelector('[data-assignment-empty]').hidden = shown !== 0;
    };
    search.addEventListener('input', filter);
    form.addEventListener('change', filter);
    trigger.addEventListener('click', () => {
      const menu = trigger.closest('.shift-menu');
      if (menu) closeShiftMenu(menu);
      form.reset();
      filter();
      SchedulerDialogs.open(dialog, menu?.querySelector('.shift-menu-toggle') || trigger, { unsaved: true });
    });
    dialog.querySelector('[data-assignment-cancel]').addEventListener('click', () => SchedulerDialogs.close(dialog));
  });
}
document.addEventListener('scheduler:page', initializeSchedule);
document.addEventListener('click', event => {
  document.querySelectorAll('.shift-menu').forEach(menu => {
    if (!menu.contains(event.target)) closeShiftMenu(menu);
  });
});
initializeSchedule();
})();
