(() => {
  async function request(url, options = {}) {
    const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(15000), ...options });
    if (!response.ok) throw new Error('Request failed');
    return response;
  }
  function postOptions(body) {
    const token = document.querySelector('meta[name="_csrf"]').content;
    const header = document.querySelector('meta[name="_csrf_header"]').content;
    return { method: 'POST', headers: { 'Content-Type': 'application/json', [header]: token }, ...(body ? { body: JSON.stringify(body) } : {}) };
  }
  let version = 0;
  async function home() {
    const container = document.querySelector('[data-home-announcements]');
    const bell = document.getElementById('notification-bell');
    if (!container || !bell) return;
    const current = ++version;
    try {
      const eventId = document.getElementById('main')?.dataset?.eventId;
      const items = await (await request(bell.dataset.url + (eventId ? `?eventId=${encodeURIComponent(eventId)}` : ''))).json();
      if (current !== version || !container.isConnected) return;
      container.replaceChildren();
      for (const item of items.filter(i => i.scope === 'DEPARTMENT')) {
        const card = document.createElement('article'); card.className = 'card notification-message';
        const heading = document.createElement('h2'); heading.textContent = `${item.departmentName} — ${item.title}`;
        const message = document.createElement('p'); message.textContent = item.message;
        const action = document.createElement('button'); action.textContent = item.acknowledgmentRequired ? 'I’ve read this' : 'Mark as read';
        const status = document.createElement('p'); status.setAttribute('role', 'status');
        action.addEventListener('click', async () => {
          action.disabled = true;
          try {
            await request(bell.dataset.url + (item.acknowledgmentRequired ? `/${item.id}/acknowledge` : '/read'), postOptions(item.acknowledgmentRequired ? null : [item.id]));
            card.remove(); document.dispatchEvent(new Event('scheduler:announcements'));
          } catch (_) { action.disabled = false; status.textContent = 'Unable to save. Please retry.'; }
        });
        card.append(heading, message, action, status); container.append(card);
      }
    } catch (_) {
      if (current !== version) return;
      container.textContent = 'Unable to load announcements. ';
      const retry = document.createElement('button'); retry.textContent = 'Retry'; retry.addEventListener('click', home); container.append(retry);
    }
  }
  function composer() {
    const form = document.querySelector('[data-announcement-composer]');
    if (!form || form.dataset.initialized) return;
    form.dataset.initialized = 'true';
    const select = form.querySelector('select'); const button = form.querySelector('button');
    const output = form.querySelector('[data-announcement-audience]'); let sequence = 0;
    async function preview() {
      const current = ++sequence; button.disabled = true; output.textContent = 'Loading audience…';
      try {
        const { count } = await (await request(form.action + '/audience?departmentId=' + encodeURIComponent(select.value))).json();
        if (sequence !== current) return;
        output.textContent = `Send to ${count} ${select.selectedOptions[0].textContent} volunteers.`; button.disabled = count === 0;
      } catch (_) { if (sequence === current) output.textContent = 'Unable to preview audience. Choose a department to retry.'; }
    }
    select.addEventListener('change', preview); preview();
  }
  function init() { composer(); home(); }
  document.addEventListener('scheduler:page', init);
  document.addEventListener('scheduler:announcements', home);
  window.addEventListener('focus', home);
  init();
})();
