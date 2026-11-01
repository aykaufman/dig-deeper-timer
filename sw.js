// Offline support: the page is network-first (so updates show up), photos are cached the first time they load.
const CACHE = "ddt-v1";
self.addEventListener("install", e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(["./", "index.html", "manifest.webmanifest", "icons/icon-192.png"]))); self.skipWaiting(); });
self.addEventListener("activate", e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k))))); self.clients.claim(); });
self.addEventListener("fetch", e => {
  const r = e.request; if (r.method !== "GET") return;
  const u = new URL(r.url);
  if (u.origin === location.origin && u.pathname.includes("/img/")) {
    e.respondWith(caches.open(CACHE).then(c => c.match(r).then(hit => hit || fetch(r).then(res => { if (res.ok) c.put(r, res.clone()); return res; }))));
    return;
  }
  e.respondWith(fetch(r).then(res => { if (res.ok && (u.origin === location.origin || u.host.includes("fonts.g"))) caches.open(CACHE).then(c => c.put(r, res.clone())); return res; }).catch(() => caches.match(r)));
});
