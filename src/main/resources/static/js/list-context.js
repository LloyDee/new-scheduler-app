(() => {
  const listPath = /^\/events\/\d+\/(schedule|volunteers)$/;
  const fields = ['departmentId', 'date', 'q', 'page', 'view'];
  function context() {
    const current = new URL(location.href);
    if (!listPath.test(current.pathname)) return null;
    const supplied = current.searchParams.get('returnTo');
    if (supplied) {
      const target = new URL(supplied, location.origin);
      if (target.origin === location.origin && target.pathname === current.pathname) return target;
    }
    const target = new URL(current.pathname, location.origin);
    fields.forEach(key => {
      if (current.searchParams.has(key)) target.searchParams.set(key, current.searchParams.get(key));
    });
    target.searchParams.set('scroll', String(Math.round(window.scrollY)));
    return target;
  }
  // Capture before section navigation and dialog handlers inspect the link or form.
  document.addEventListener('click', event => {
    const link = event.target.closest('a[href]');
    if (!link || !link.closest('#main')) return;
    const target = new URL(link.href, location.href);
    const origin = context();
    if (!origin || target.origin !== location.origin || target.pathname !== origin.pathname) return;
    if (target.searchParams.has('edit') || target.searchParams.has('create')) {
      target.searchParams.set('returnTo', origin.pathname + origin.search);
      link.href = target.href;
    } else if (document.querySelector('.editor-page') && !target.search) {
      link.href = origin.href;
    }
  }, true);
  document.addEventListener('submit', event => {
    const form = event.target;
    if (form.method.toLowerCase() !== 'post') return;
    const origin = context();
    const action = new URL(form.action, location.href);
    if (!origin || action.origin !== location.origin || !action.pathname.startsWith(origin.pathname.replace(/\/(schedule|volunteers)$/, '/'))) return;
    let input = form.querySelector('input[name="returnTo"]');
    if (!input) {
      input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'returnTo';
      form.append(input);
    }
    input.value = origin.pathname + origin.search;
  }, true);
  window.schedulerListScroll = url => {
    const value = url.searchParams.get('scroll');
    return listPath.test(url.pathname) && !url.searchParams.has('edit') && !url.searchParams.has('create')
      && value !== null && /^\d+$/.test(value) ? [0, Number(value)] : null;
  };
  const restore = () => {
    const scroll = window.schedulerListScroll(new URL(location.href));
    if (scroll) window.scrollTo(...scroll);
  };
  window.addEventListener('load', restore);
})();
