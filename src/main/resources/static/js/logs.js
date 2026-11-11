(() => {
  function formatLogs() {
    const times = document.querySelectorAll('[data-log-time]');
    if (!times.length) return;
    const options = {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: 'numeric', minute: '2-digit', hour12: true,
    };
    const formatter = new Intl.DateTimeFormat('en-US', { ...options, timeZoneName: 'short' });
    const compactFormatter = new Intl.DateTimeFormat('en-US', options);
    times.forEach(time => {
      const value = time.getAttribute('datetime');
      if (!value) return;
      const date = new Date(value);
      if (!Number.isNaN(date.getTime())) {
        time.textContent = (time.getAttribute('data-log-time') === 'compact' ? compactFormatter : formatter).format(date);
      }
    });
    document.querySelectorAll('[data-logs-timezone]').forEach(label => {
      label.textContent = `your local timezone (${formatter.resolvedOptions().timeZone})`;
    });
  }
  Scheduler.page(formatLogs);
})();
