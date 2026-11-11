// Loaded once by the shell; feature scripts can register after any page transition.
const Scheduler = (() => {
  function page(initialize) {
    const initialized = new WeakSet();
    const run = () => {
      const main = document.getElementById?.('main');
      if (main && initialized.has(main)) return;
      if (main) initialized.add(main);
      initialize();
    };
    document.addEventListener('scheduler:page', run);
    run();
  }

  async function request(url, { timeout = 20000, check = true, ...options } = {}) {
    const deadline = AbortSignal.timeout(timeout);
    const signal = options.signal ? AbortSignal.any([options.signal, deadline]) : deadline;
    const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', ...options, signal });
    if (check && (!response.ok || response.redirected)) throw new Error('Request failed');
    return response;
  }

  function post(body) {
    const token = document.querySelector('meta[name="_csrf"]').content;
    const header = document.querySelector('meta[name="_csrf_header"]').content;
    return { method: 'POST', headers: { [header]: token, ...(body == null ? {} : { 'Content-Type': 'application/json' }) },
      ...(body == null ? {} : { body: JSON.stringify(body) }) };
  }

  const styles = new Map();
  async function loadStyles(root) {
    await Promise.all([...root.querySelectorAll('link[rel="stylesheet"][href]')].map(source => {
      const href = new URL(source.getAttribute('href'), location.href).href;
      if (!styles.has(href)) {
        const existing = [...document.head.querySelectorAll('link[rel="stylesheet"]')].find(link => link.href === href);
        if (existing?.sheet) styles.set(href, Promise.resolve());
        else styles.set(href, new Promise((resolve, reject) => {
          const link = existing || document.createElement('link');
          link.rel = 'stylesheet'; link.href = href;
          link.addEventListener('load', resolve, { once: true });
          link.addEventListener('error', () => { styles.delete(href); link.remove(); reject(new Error('Styles unavailable')); }, { once: true });
          if (!existing) document.head.append(link);
        }));
      }
      return styles.get(href).then(() => source.remove());
    }));
  }

  function menus(name, { focusFirst = false, blur = false, toggle: toggleOverride, items: itemsOverride } = {}) {
    const root = `.${name}`, toggleSelector = toggleOverride || `.${name}-toggle`, itemsSelector = itemsOverride || `.${name}-items`;
    let tabbing = false;
    const close = menu => {
      menu.querySelector(toggleSelector).setAttribute('aria-expanded', 'false');
      menu.querySelector(itemsSelector).hidden = true;
    };
    const closeAll = except => document.querySelectorAll(root).forEach(menu => { if (menu !== except) close(menu); });
    document.addEventListener('click', event => {
      const toggle = event.target.closest(toggleSelector);
      const menu = event.target.closest(root);
      if (toggle) {
        const items = menu.querySelector(itemsSelector), opening = items.hidden;
        closeAll();
        items.hidden = !opening;
        toggle.setAttribute('aria-expanded', String(opening));
        if (opening && focusFirst) items.querySelector('button, a')?.focus();
      } else if (!menu || event.target.closest(`${itemsSelector} a, ${itemsSelector} button`)) closeAll();
    });
    document.addEventListener('pointerdown', () => { tabbing = false; });
    document.addEventListener('keydown', event => {
      tabbing = event.key === 'Tab';
      if (event.key !== 'Escape') return;
      const menu = event.target.closest(root) || [...document.querySelectorAll(root)]
        .find(candidate => !candidate.querySelector(itemsSelector).hidden);
      if (menu && !menu.querySelector(itemsSelector).hidden) {
        close(menu); menu.querySelector(toggleSelector).focus(); event.stopPropagation();
      }
    });
    document.addEventListener('focusout', event => {
      const menu = event.target.closest(root);
      // Mobile pointer focus can leave a menu before its click is delivered.
      if ((tabbing || blur) && menu && !menu.contains(event.relatedTarget)) close(menu);
    });
    return { close, closeAll };
  }
  return { page, request, post, menus, loadStyles };
})();
