(() => {
const windowMs = 30 * 60 * 1000;
let busy = false;
let lastSuccess = 0;
const identity = document.querySelector('meta[name="device-visits-key"]')?.content;
const storageKey = identity ? `schedulerVisit:${identity}` : null;
async function recordVisit() {
  if (busy) return;
  let saved = lastSuccess;
  try { if (storageKey) saved = Math.max(saved, Number(localStorage.getItem(storageKey)) || 0); } catch (_) { /* Optional cache. */ }
  if (saved > 0 && Date.now() - saved >= 0 && Date.now() - saved < windowMs) return;
  busy = true;
  try {
    const ua = navigator.userAgent;
    const match = ua.match(/(Edg|OPR|Firefox|CriOS)\/([\d.]+)/) || ua.match(/(Chrome|Version)\/([\d.]+)/);
    const labels = { Edg: "Edge", OPR: "Opera", CriOS: "Chrome", Version: "Safari" };
    const browser = match ? `${labels[match[1]] || match[1]} ${match[2]}` : "Unknown";
    const operatingSystem = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1) ? "iOS / iPadOS"
      : /Android/.test(ua) ? "Android" : /Windows/.test(ua) ? "Windows" : /Mac/.test(ua) ? "macOS" : /Linux/.test(ua) ? "Linux" : "Unknown";
    const deviceCategory = /iPad|Tablet/.test(ua) || operatingSystem === "iOS / iPadOS" && !/iPhone|iPod/.test(ua) || /Android/.test(ua) && !/Mobile/.test(ua)
      ? "Tablet" : /Mobile|iPhone|iPod/.test(ua) ? "Mobile" : "Desktop";
    const data = { browser, operatingSystem, deviceCategory, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone, language: navigator.language };
    let cached;
    try { cached = JSON.parse(localStorage.getItem("schedulerLocation")); } catch (_) { /* Storage may be disabled. */ }
    if (cached && Date.now() - cached.timestamp < 86400000) Object.assign(data, cached.location);
    else {
      try {
        const response = await fetch("https://ipapi.co/json/", { signal: AbortSignal.timeout(3000), credentials: "omit", referrerPolicy: "no-referrer" });
        if (!response.ok) throw new Error("Location unavailable");
        const result = await response.json();
        if (result.error) throw new Error("Location unavailable");
        const location = {};
        for (const [key, source] of [["city", "city"], ["region", "region"], ["country", "country_name"]]) {
          if (typeof result[source] === "string" && result[source]) location[key] = result[source];
        }
        Object.assign(data, location);
        try { localStorage.setItem("schedulerLocation", JSON.stringify({ timestamp: Date.now(), location })); } catch (_) { /* Optional cache. */ }
      } catch (_) { /* Record visits even without location. */ }
    }
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const url = document.querySelector('meta[name="device-visits-url"]')?.content;
    if (token && header && url) {
      const response = await fetch(url, { method: "POST", credentials: "same-origin", keepalive: true,
        headers: { "Content-Type": "application/json", [header]: token }, body: JSON.stringify(data), signal: AbortSignal.timeout(10000) });
      if (response.ok) {
        lastSuccess = Date.now();
        try { if (storageKey) localStorage.setItem(storageKey, String(lastSuccess)); } catch (_) { /* Optional cache. */ }
      }
    }
  } catch (_) { /* Tracking must never interrupt the application. */ }
  finally { busy = false; }
}
document.addEventListener('scheduler:page', recordVisit);
recordVisit();
})();
