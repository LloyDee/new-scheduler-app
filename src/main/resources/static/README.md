Frontend assets
===============

`css/app.css` is the stylesheet entry point. Its imports separate theme tokens,
shell layout, reusable controls, feature styles, and final print overrides. Keep
responsive rules beside their owning styles. Change the owning declaration
instead of appending a second override at the end of the entry point.

The layout loads `js/shared.js` before the other deferred scripts. `Scheduler`
provides page initialization (once per main element), cancellable requests with
timeouts, CSRF POST options, and accessible action menus. `SchedulerDialogs`
owns dialog focus, history, unsaved changes, and detail requests. Feature scripts
retain their domain-specific interactions.

Shell scripts load once and remain active across partial navigation. Register
element setup through `Scheduler.page`; delegate events for replaceable results
and dialog details. Use `scheduler:before-page` to cancel page timers, requests,
or document listeners, and `scheduler:detail` to initialize fetched dialog content.

Page-specific stylesheet links belong inside the main content so section
responses include them. Navigation loads these styles into the persistent head,
deduplicates URLs, and waits before swapping the page. A stylesheet failure falls
back to full navigation. Do not rely on scripts embedded in fetched HTML being
executed.

Run frontend checks with `node --test src/test/js/*.test.cjs` from the repository
root.
