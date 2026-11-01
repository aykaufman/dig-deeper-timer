// Runs only inside the Android app: swaps browser-only features for native ones.
import { TextToSpeech } from '@capacitor-community/text-to-speech';
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';

if (Capacitor.isNativePlatform()) {

// Voice: the Android WebView has no speechSynthesis, so speak through Android's own text-to-speech.
let queue = [], speaking = false, list = [];
function next() {
  if (!queue.length) { speaking = false; return; }
  speaking = true;
  const u = queue.shift();
  TextToSpeech.speak({ text: u.text, rate: u.rate || 1, lang: (u.voice && u.voice.lang) || 'en-US', voice: u.voice ? u.voice._i : undefined, category: 'playback' })
    .catch(() => {})
    .then(() => { try { u.onend && u.onend(); } catch (e) {} next(); });
}
const synth = {
  get speaking() { return speaking; },
  get pending() { return queue.length > 0; },
  onvoiceschanged: null,
  getVoices() { return list; },
  speak(u) { queue.push(u); if (!speaking) next(); },
  cancel() { queue = []; TextToSpeech.stop().catch(() => {}); },
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
}
