// Page tests use a lifecycle double; dialogs.test.cjs exercises the controller itself.
exports.SchedulerDialogs = {
  open(dialog, opener, options = {}) {
    dialog.showModal();
    for (const selector of ['.app-dialog-content', '.volunteer-dialog-content', '.team-sheet-list']) {
      const content = dialog.querySelector?.(selector);
      if (content) content.scrollTop = 0;
    }
    (options.focus || dialog.querySelector?.('h2'))?.focus?.();
    const previousClose = dialog.events?.close;
    dialog.addEventListener('close', () => { previousClose?.(); opener?.focus(); });
    dialog.addEventListener('click', event => {
      if (event.target !== dialog) return;
      const r = dialog.getBoundingClientRect();
      if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close();
    });
    return true;
  },
  close(dialog) { dialog.close(); }
};
