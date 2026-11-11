(() => {
  const { close: closeMenu, closeAll: closeMenus } = Scheduler.menus('department-menu');
  const openDialog = (dialog, opener) => {
    SchedulerDialogs.open(dialog, opener);
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
      menu.querySelector('[data-department-open]').addEventListener('click', event => {
        closeMenu(menu);
        openDialog(document.getElementById(event.currentTarget.getAttribute('aria-controls')), toggle);
      });
    });
    document.querySelectorAll('.department-dialog').forEach(dialog => {
      if (dialog.dataset.ready) return;
      dialog.dataset.ready = 'true';
      if (dialog.dataset.departmentAutoOpen === 'true') {
        openDialog(dialog, document.querySelector('.department-menu-toggle') || document.querySelector('main h1'));
      }
    });
  }
  Scheduler.page(initializeDepartments);
})();
