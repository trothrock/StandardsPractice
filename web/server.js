// server.js — Jazz Standards Practice Tracker
// Run with: node server.js
// Then open: http://localhost:3000

const http = require('http');
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');

const PORT = 3000;
const ROOT = __dirname;

const MIME_TYPES = {
  '.html': 'text/html',
  '.js':   'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.ico':  'image/x-icon',
};

// Apple Notes sync: writes the level 2+ song list to a note that syncs via iCloud
const NOTE_TITLE = 'Jam Session Calls';

const NOTES_SCRIPT = [
  'on run argv',
  '  set noteTitle to item 1 of argv',
  '  set noteBody to item 2 of argv',
  '  tell application "Notes"',
  '    set matches to notes of default account whose name is noteTitle',
  '    if (count of matches) > 0 then',
  '      set body of item 1 of matches to noteBody',
  '    else',
  '      make new note at default folder of default account with properties {body:noteBody}',
  '    end if',
  '  end tell',
  'end run',
];

function escapeHtml(str) {
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function buildNoteBody(songs) {
  const d = new Date();
  const updated = d.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' });
  let html = `<div><h1>${NOTE_TITLE}</h1></div>`;
  html += `<div>${songs.length} songs at level 2+ · Updated ${escapeHtml(updated)}</div><div><br></div>`;
  for (const level of [3, 2]) {
    const group = songs.filter(s => s.level === level).sort((a, b) => a.name.localeCompare(b.name));
    if (!group.length) continue;
    html += `<div><h2>Level ${level} (${group.length})</h2></div><ul>`;
    for (const s of group) {
      const meta = [s.style, s.composer].filter(Boolean).map(escapeHtml).join(' · ');
      html += `<li><b>${escapeHtml(s.name)}</b>${meta ? ` — ${meta}` : ''}</li>`;
    }
    html += '</ul><div><br></div>';
  }
  return html;
}

function handleNotesSync(req, res) {
  let raw = '';
  req.on('data', chunk => { raw += chunk; });
  req.on('end', () => {
    let songs;
    try {
      songs = JSON.parse(raw).songs;
      if (!Array.isArray(songs)) throw new Error('songs must be an array');
    } catch (e) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: false, error: 'Invalid request body' }));
      return;
    }
    const args = NOTES_SCRIPT.flatMap(line => ['-e', line]).concat([NOTE_TITLE, buildNoteBody(songs)]);
    execFile('osascript', args, (err, stdout, stderr) => {
      res.writeHead(err ? 500 : 200, { 'Content-Type': 'application/json' });
      if (err) console.error('Notes sync failed:', stderr || err.message);
      res.end(JSON.stringify(err ? { ok: false, error: (stderr || err.message).trim() } : { ok: true, count: songs.length }));
    });
  });
}

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/api/sync-notes') {
    handleNotesSync(req, res);
    return;
  }

  // Default to index
  let urlPath = req.url === '/' ? '/jazz_practice.html' : req.url;

  // Strip query strings
  urlPath = urlPath.split('?')[0];

  const filePath = path.join(ROOT, urlPath);

  // Security: prevent directory traversal outside ROOT
  if (!filePath.startsWith(ROOT)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end(`Not found: ${urlPath}`);
      return;
    }

    const ext = path.extname(filePath);
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, {
      'Content-Type': contentType,
      // Allow future hosting from any origin during development
      'Access-Control-Allow-Origin': '*',
    });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log(`Jazz Standards Practice Tracker`);
  console.log(`--------------------------------`);
  console.log(`Server running at http://localhost:${PORT}`);
  console.log(`Press Ctrl+C to stop.`);
});
