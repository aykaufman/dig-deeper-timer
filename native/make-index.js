// Turns the web app's index.html into the Android app's page.
const fs = require('fs');
const [src, out] = process.argv.slice(2);
let h = fs.readFileSync(src, 'utf8');
const rep = (a, b) => { if (h.split(a).length !== 2) throw new Error('not found once: ' + a.slice(0, 60)); h = h.replace(a, b); };
rep('<script>\nconst DATA=', '<script src="native.js"></script>\n<script>\nconst DATA=');
rep('if("serviceWorker" in navigator&&location.protocol==="https:")', 'if(false)');
rep('if(!dl){try{const a=', 'if(!dl&&window.__nativeSave){try{await window.__nativeSave(`dig-deeper-log-${new Date().toISOString().slice(0,10)}.csv`,csv);if(msg)msg.textContent="Pick where to save the backup."}catch(e){if(msg)msg.textContent="Couldn\'t save the file here."}return}\n  if(!dl){try{const a=');
rep('for(const k in LOCAL)IMG[k]="img/"+LOCAL[k];', 'window.__LOCAL=LOCAL;for(const k in LOCAL)IMG[k]=window.__img?window.__img(LOCAL[k]):"img/"+LOCAL[k];');
fs.writeFileSync(out, h);
