// Runs only inside the Android app: swaps browser-only features for native ones.
import { TextToSpeech } from '@capacitor-community/text-to-speech';
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { FileOpener } from '@capawesome-team/capacitor-file-opener';

if (Capacitor.isNativePlatform()) {

// Voice: the Android WebView has no speechSynthesis, so speak through Android's own text-to-speech.
// Android's plugin never reports back when a phrase is cut off, so each phrase also gets a time limit,
// and cancelling clears everything at once. Without this the voice went silent after the first interruption.
let queue = [], speaking = false, list = [], gen = 0;
function next() {
  if (!queue.length) { speaking = false; return; }
  speaking = true;
  const my = gen, u = queue.shift();
  let done = false;
  const finish = () => {
    if (done) return; done = true;
    if (my !== gen) return;               // cancelled meanwhile: a new phrase already took over
    try { u.onend && u.onend(); } catch (e) {}
    next();
  };
  const rate = u.rate || 1;
  setTimeout(finish, (String(u.text).length * 80) / rate + 2500);
  TextToSpeech.speak({ text: u.text, rate, lang: (u.voice && u.voice.lang) || 'en-US', voice: u.voice ? u.voice._i : undefined, category: 'playback' })
    .then(finish, finish);
}
const synth = {
  get speaking() { return speaking; },
  get pending() { return queue.length > 0; },
  onvoiceschanged: null,
  getVoices() { return list; },
  speak(u) { queue.push(u); if (!speaking) next(); },
  cancel() { gen++; queue = []; speaking = false; TextToSpeech.stop().catch(() => {}); },
  pause() {}, resume() {}
};
Object.defineProperty(window, 'speechSynthesis', { value: synth, configurable: true });
window.SpeechSynthesisUtterance = function (text) { this.text = text; this.rate = 1; this.voice = null; this.lang = 'en-US'; this.onend = null; };
TextToSpeech.getSupportedVoices().then(r => {
  list = (r.voices || []).map((v, i) => ({ name: v.name || v.voiceURI, lang: v.lang || 'en-US', _i: i }));
  try { synth.onvoiceschanged && synth.onvoiceschanged(); } catch (e) {}
}).catch(() => {});

// Backup: write the CSV and open Android's share sheet (save to Files, Drive, email…).
window.__nativeSave = async (name, data) => {
  const r = await Filesystem.writeFile({ path: name, data, directory: Directory.Cache, encoding: Encoding.UTF8 });
  await Share.share({ title: name, url: r.uri, dialogTitle: 'Save backup' });
};

// Back button: close what's open, step back to home, and only then leave the app.
App.addListener('backButton', () => {
  const pick = s => document.querySelector(s);
  const lb = pick('.lb'); if (lb) { lb.click(); return; }
  const btn = pick('#pmx') || pick('#exit') || pick('#back');
  if (btn) { btn.click(); return; }
  App.minimizeApp();
});

  // ---------- Photos: downloaded once, then kept on the phone ----------
  // The app itself doesn't carry the photos, so updates stay small. Until a photo is saved it loads from the web.
  const REMOTE = 'https://aykaufman.github.io/dig-deeper-timer/img/';
  const ls = (k, d) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } };
  const lsSet = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} };
  const saved = new Set(ls('ddt-photos', []));
  let base = ls('ddt-photobase', '');
  window.__img = f => (base && saved.has(f)) ? base + f : REMOTE + f;

  function pill(text, keepMs) {
    let el = document.getElementById('ddt-pill');
    if (!text) { if (el) el.remove(); return; }
    if (!el) {
      el = document.createElement('div'); el.id = 'ddt-pill';
      el.style.cssText = 'position:fixed;left:12px;bottom:calc(12px + env(safe-area-inset-bottom,0px));z-index:80;background:rgba(26,23,20,.88);color:#fff;font:600 13px/1.2 Figtree,system-ui,sans-serif;padding:8px 12px;border-radius:999px;pointer-events:none';
      document.body.appendChild(el);
    }
    el.textContent = text;
    if (keepMs) setTimeout(() => { if (el.textContent === text) el.remove(); }, keepMs);
  }

  async function savePhotos() {
    try {
      const u = await Filesystem.getUri({ directory: Directory.Data, path: 'img' });
      base = Capacitor.convertFileSrc(u.uri) + '/'; lsSet('ddt-photobase', base);
    } catch (e) { return; }
    const all = [...new Set(Object.values(window.__LOCAL || {}))];
    const todo = all.filter(f => !saved.has(f));
    if (!todo.length) return;
    let done = all.length - todo.length, failed = 0;
    const show = () => pill(`Saving photos to your phone · ${done} of ${all.length}`);
    show();
    const worker = async () => {
      while (todo.length) {
        const f = todo.shift();
        try {
          await Filesystem.downloadFile({ url: REMOTE + f, path: 'img/' + f, directory: Directory.Data, recursive: true });
          saved.add(f); lsSet('ddt-photos', [...saved]); done++; show();
        } catch (e) { failed++; }
      }
    };
    await Promise.all([worker(), worker(), worker()]);
    pill(failed ? 'Some photos didn\'t save. They\'ll finish next time you\'re online.' : 'All photos saved on your phone.', 4000);
  }

  // ---------- Update button ----------
  const REPO = 'aykaufman/dig-deeper-timer';
  let update = null, lastCheck = 0;
  async function checkUpdate() {
    if (Date.now() - lastCheck < 10 * 60 * 1000) return; lastCheck = Date.now();
    try {
      const [info, res] = await Promise.all([App.getInfo(), fetch(`https://api.github.com/repos/${REPO}/releases/latest`)]);
      if (!res.ok) return;
      const rel = await res.json();
      const n = parseInt(String(rel.tag_name || '').split('.').pop(), 10);
      const asset = (rel.assets || []).find(a => /\.apk$/.test(a.name));
      if (asset && n > parseInt(info.build, 10)) { update = { n, url: asset.browser_download_url, name: rel.tag_name }; banner(); }
    } catch (e) {}
  }
  function banner() {
    let el = document.getElementById('ddt-update');
    const onHome = !!document.querySelector('.hello');
    if (!update || !onHome) { if (el) el.style.display = 'none'; return; }
    if (!el) {
      el = document.createElement('div'); el.id = 'ddt-update';
      el.style.cssText = 'position:fixed;left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom,0px));z-index:90;display:flex;align-items:center;gap:12px;background:#1A1714;color:#fff;border-radius:18px;padding:12px 12px 12px 16px;box-shadow:0 10px 30px rgba(0,0,0,.25);font:500 15px/1.3 Figtree,system-ui,sans-serif;max-width:520px;margin:0 auto';
      el.innerHTML = '<span style="flex:1;min-width:0"><b style="display:block;font-weight:700">Update available</b><span id="ddt-utext" style="font-size:13px;color:#C9C0B5"></span></span><button id="ddt-ubtn" style="height:44px;padding:0 18px;border:0;border-radius:14px;background:#FF6A2B;color:#1F0E04;font:700 15px Figtree,system-ui,sans-serif">Update</button>';
      document.body.appendChild(el);
      el.querySelector('#ddt-ubtn').onclick = installUpdate;
    }
    el.querySelector('#ddt-utext').textContent = `Version ${update.name.replace(/^v/, '')} is ready. Your workouts stay.`;
    el.style.display = 'flex';
  }
  async function installUpdate() {
    const btn = document.getElementById('ddt-ubtn'), txt = document.getElementById('ddt-utext');
    btn.disabled = true; btn.textContent = 'Downloading…';
    try {
      const path = 'dig-deeper-update.apk';
      await Filesystem.downloadFile({ url: update.url, path, directory: Directory.Cache });
      const u = await Filesystem.getUri({ directory: Directory.Cache, path });
      btn.textContent = 'Update';
      await FileOpener.openFile({ path: u.uri, mimeType: 'application/vnd.android.package-archive' });
      txt.textContent = 'If Android asks, allow installs from Dig Deeper, then tap Update again.';
    } catch (e) {
      txt.textContent = 'Couldn\'t download the update. Check your connection and try again.';
    }
    btn.disabled = false; btn.textContent = 'Update';
  }

  const start = () => {
    setTimeout(savePhotos, 800);
    checkUpdate();
    setInterval(banner, 1000);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
  App.addListener('resume', checkUpdate);
}
