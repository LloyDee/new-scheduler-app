(() => {
  function formatLogs() {
    const times = document.querySelectorAll('[data-log-time]');
    if (!times.length) return;
    const formatter = new Intl.DateTimeFormat('en-US', {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: 'numeric', minute: '2-digit', hour12: true, timeZoneName: 'short',
    });
    times.forEach(time => {
      const value = time.getAttribute('datetime');
      if (!value) return;
      const date = new Date(value);
      if (!Number.isNaN(date.getTime())) time.textContent = formatter.format(date);
    });
    document.querySelectorAll('[data-logs-timezone]').forEach(label => {
      label.textContent = `your local timezone (${formatter.resolvedOptions().timeZone})`;
    });
  }
  document.addEventListener('scheduler:page', formatLogs);
  formatLogs();
})();
