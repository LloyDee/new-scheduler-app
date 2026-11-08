(() => {
  const closeMenu = menu => {
    menu.querySelector('.department-menu-toggle').setAttribute('aria-expanded', 'false');
    menu.querySelector('.department-menu-items').hidden = true;
  };
  const closeMenus = except => document.querySelectorAll('.department-menu').forEach(menu => { if (menu !== except) closeMenu(menu); });
  const openDialog = (dialog, opener) => {
    dialog.departmentOpener = opener;
    dialog.showModal();
    dialog.querySelector('h2').focus();
    dialog.querySelector('.app-dialog-content').scrollTop = 0;
  };
  function initializeDepartments() {
    document.querySelectorAll('[data-department-card]').forEach(card => {
      if (card.dataset.ready) return;
      card.dataset.ready = 'true';
      const title = card.querySelector('[data-department-card-open]');
      const open = () => {
        closeMenus();
        openDialog(document.getElementById(card.dataset.departmentCard), title);
      };
      title.addEventListener('click', open);
      card.addEventListener('click', event => {
        if (event.target.closest('button, a, input, select, textarea, label, form, dialog, .department-menu, [role="button"]')) return;
        if (window.getSelection()?.toString()) return;
        open();
      });
    });
    document.querySelectorAll('.department-menu').forEach(menu => {
      if (menu.dataset.ready) return;
      menu.dataset.ready = 'true';
      const toggle = menu.querySelector('.department-menu-toggle');
      const items = menu.querySelector('.department-menu-items');
      toggle.addEventListener('click', () => {
        const opening = items.hidden;
        closeMenus();
        items.hidden = !opening;
        toggle.setAttribute('aria-expanded', String(opening));
      });
      menu.addEventListener('keydown', event => {
        if (event.key === 'Escape') { closeMenu(menu); toggle.focus(); event.stopPropagation(); }
      });
      menu.addEventListener('focusout', event => {
        if (event.relatedTarget && !menu.contains(event.relatedTarget)) closeMenu(menu);
      });
      menu.querySelector('[data-department-open]').addEventListener('click', event => {
        closeMenu(menu);
        openDialog(document.getElementById(event.currentTarget.getAttribute('aria-controls')), toggle);
      });
      items.addEventListener('click', event => { if (event.target.closest('a, button')) closeMenu(menu); });
    });
    document.querySelectorAll('.department-dialog').forEach(dialog => {
      if (dialog.dataset.ready) return;
      dialog.dataset.ready = 'true';
      dialog.addEventListener('close', () => dialog.departmentOpener?.focus());
      dialog.addEventListener('click', event => {
        if (event.target !== dialog) return;
        const rect = dialog.getBoundingClientRect();
        if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
      });
      if (dialog.dataset.departmentAutoOpen === 'true') {
        openDialog(dialog, document.querySelector('.department-menu-toggle') || document.querySelector('main h1'));
      }
    });
  }
  document.addEventListener('click', event => { if (!event.target.closest('.department-menu')) closeMenus(); });
  document.addEventListener('scheduler:page', initializeDepartments);
  initializeDepartments();
})();
