(() => {
  const bell = document.getElementById('notification-bell');
  if (!bell) return;
  const dialog = document.getElementById('notification-dialog');
  const badge = document.getElementById('notification-badge');
  const status = document.getElementById('notification-status');
  const messages = document.getElementById('notification-messages');
  const retry = document.getElementById('notification-retry');
  const url = bell.dataset.url;
  let pending = [];
  let generation = 0;
  let busy = false;
  let refreshVersion = 0;
  const counts = new Map();
  let notifying = false;
  const context = () => document.getElementById('main')?.dataset?.eventId || '';
  function notifyAnnouncements() {
    notifying = true;
    try { document.dispatchEvent(new Event('scheduler:announcements')); }
    finally { notifying = false; }
  }

  async function request(path, options = {}, eventId = context()) {
    const query = !options.method && eventId ? `?eventId=${encodeURIComponent(eventId)}` : '';
    const response = await fetch(url + path + query, { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(15000), ...options });
    if (!response.ok) throw new Error('Notification request failed');
    return response;
  }

  async function refresh(force = false) {
    const version = ++refreshVersion;
    const key = context();
    if (force === true) for (const entry of counts.values()) { entry.time = 0; entry.dirty = true; }
    if (!counts.has(key)) counts.set(key, { time: 0 });
    const entry = counts.get(key);
    try {
      if (!entry.pending && Date.now() - entry.time >= 30000) {
        entry.pending = (async () => {
          do {
            entry.dirty = false;
            const result = await (await request('/count', {}, key)).json();
            entry.count = result.count;
          } while (entry.dirty);
          entry.time = Date.now();
        })().finally(() => { entry.pending = null; });
      }
      await entry.pending;
      if (version !== refreshVersion || key !== context()) return;
      const count = entry.count;
      badge.hidden = count === 0;
      badge.classList.toggle('has-count', count > 1);
      badge.textContent = count > 1 ? String(count) : '';
      bell.setAttribute('aria-label', count ? `Notifications, ${count} unread` : 'Notifications, no unread messages');
    } catch (_) { /* Keep the last known badge; opening provides an explicit retry. */ }
  }

  async function load() {
    if (busy) return;
    busy = true;
    const current = generation;
    retry.hidden = true;
    status.textContent = pending.length ? 'Saving read status…' : 'Loading notifications…';
    try {
      if (!pending.length) {
        const unread = await (await request('')).json();
        if (!dialog.open || generation !== current) return;
        messages.replaceChildren();
        for (const item of unread) {
          const article = document.createElement('article');
          article.className = 'notification-message';
          if (item.title || item.scope === 'GLOBAL') {
            const heading = document.createElement('h3');
            heading.textContent = item.title ? `${item.departmentName || 'Admin Notification:'} — ${item.title}` : 'Admin Notification:';
            article.append(heading);
          }
          const time = document.createElement('time');
          time.dateTime = item.createdAt;
          time.textContent = new Date(item.createdAt).toLocaleString();
          const text = document.createElement('p');
          text.textContent = item.message;
          article.append(time, text);
          if (item.acknowledgmentRequired && !item.acknowledgedAt) {
            const acknowledge = document.createElement('button');
            acknowledge.textContent = 'I’ve read this';
            acknowledge.addEventListener('click', async () => {
              acknowledge.disabled = true;
              try {
                const token = document.querySelector('meta[name="_csrf"]').content;
                const header = document.querySelector('meta[name="_csrf_header"]').content;
                await request(`/${item.id}/acknowledge`, { method: 'POST', headers: { [header]: token } });
                acknowledge.textContent = 'Acknowledged';
                notifyAnnouncements();
                refresh(true);
              } catch (_) { acknowledge.disabled = false; status.textContent = 'Unable to acknowledge. Please retry.'; }
            });
            article.append(acknowledge);
          }
          messages.append(article);
        }
        pending = unread.map(item => item.id);
      }
      if (pending.length) {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const header = document.querySelector('meta[name="_csrf_header"]').content;
        await request('/read', { method: 'POST', headers: { 'Content-Type': 'application/json', [header]: token }, body: JSON.stringify(pending) });
        if (generation !== current) return;
        pending = [];
      }
      if (dialog.open) status.textContent = messages.children.length ? '' : 'No unread notifications';
      await refresh(true);
      if (typeof document.dispatchEvent === 'function') notifyAnnouncements();
    } catch (_) {
      if (dialog.open && generation === current) {
        status.textContent = pending.length ? 'Unable to save read status. These messages remain unread. Please retry.' : 'Unable to load notifications. Please retry.';
        retry.hidden = false;
      }
    } finally {
      busy = false;
      if (dialog.open && generation !== current) load();
    }
  }

  bell.addEventListener('click', () => {
    if (dialog.open) return;
    generation++;
    pending = [];
    messages.replaceChildren();
    SchedulerDialogs.open(dialog, bell);
    load();
  });
  retry.addEventListener('click', load);
  dialog.addEventListener('close', () => {
    generation++;
    pending = [];
    messages.replaceChildren();
    refresh();
  });
  window.addEventListener('focus', refresh);
  document.addEventListener('scheduler:page', refresh);
  document.addEventListener('scheduler:announcements', () => { if (!notifying) refresh(true); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  setInterval(() => { if (!document.hidden) refresh(); }, 30000);
  refresh();
})();
