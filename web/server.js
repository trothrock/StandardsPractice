// server.js — Jazz Standards Practice Tracker
// Run with: node server.js
// Then open: http://localhost:3000

const http = require('http');
const fs = require('fs');
const path = require('path');
const { execFile, execFileSync } = require('child_process');
const spotify = require('./spotify');

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
      const meta = [(s.tags || [s.style]).filter(Boolean).join(' / '), s.composer].filter(Boolean).map(escapeHtml).join(' · ');
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

// Auto backup: the page posts its localStorage DB here on every change. We overwrite a single
// backups/jazz-tracker.json and let git history keep past versions. The laptop is usually asleep once practice ends, so instead of committing on
// idle we commit + push the previous session's data (with the Spotify data) when the next session
// starts: the first change after a quiet gap. Startup and shutdown also commit anything pending.
const REPO_ROOT = path.join(ROOT, '..');
const BACKUP_DIR = path.join(ROOT, 'backups');
const BACKUP_FILE = path.join(BACKUP_DIR, 'jazz-tracker.json');
const BACKUP_PATHS = ['web/backups', 'web/data'];
const SESSION_GAP_MS = 30 * 60 * 1000;
let lastBackupAt = 0;

function localDateStr(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function handleBackup(req, res) {
  let raw = '';
  req.on('data', chunk => { raw += chunk; });
  req.on('end', () => {
    const reply = (status, body) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(body));
    };
    let db;
    try {
      db = JSON.parse(raw);
    } catch (e) {
      reply(400, { ok: false, error: 'Invalid JSON' });
      return;
    }
    // Don't let a fresh/empty browser profile overwrite a real backup
    if (!db || typeof db !== 'object' || !db.songs || !Object.keys(db.songs).length || !(db.activity || []).length) {
      reply(400, { ok: false, error: 'Refusing to back up an empty database' });
      return;
    }
    const newSession = Date.now() - lastBackupAt > SESSION_GAP_MS;
    const prevDate = lastBackupAt ? localDateStr(new Date(lastBackupAt)) : localDateStr();
    lastBackupAt = Date.now();
    // Commit the previous session first so this write lands in the next session's commit
    const ready = newSession ? commitBackups(prevDate) : Promise.resolve();
    ready.then(() => writeBackup(db, reply));
  });
}

function writeBackup(db, reply) {
  fs.mkdir(BACKUP_DIR, { recursive: true }, () => {
    fs.writeFile(BACKUP_FILE, JSON.stringify(db, null, 2), err => {
      if (err) {
        console.error('Backup write failed:', err.message);
        reply(500, { ok: false, error: err.message });
        return;
      }
      backupDirty = true;
      reply(200, { ok: true, file: path.basename(BACKUP_FILE) });
    });
  });
}

function git(args) {
  return new Promise((resolve, reject) => {
    execFile('git', ['-C', REPO_ROOT, ...args], (err, stdout, stderr) => {
      if (err) { err.stderr = stderr; reject(err); } else resolve(stdout);
    });
  });
}

let backupDirty = false;
let commitInFlight = Promise.resolve();

function commitBackups(date = localDateStr()) {
  commitInFlight = commitInFlight.then(async () => {
    try {
      await git(['add', '--', ...BACKUP_PATHS]);
      const changed = await git(['diff', '--cached', '--quiet', '--', ...BACKUP_PATHS]).then(() => false, () => true);
      if (!changed) return;
      backupDirty = false;
      await git(['commit', '-m', `Back up practice data (${date})`, '--', ...BACKUP_PATHS]);
      console.log('Backup: committed practice data');
      await git(['push']);
      console.log('Backup: pushed to origin');
    } catch (err) {
      console.error('Backup commit/push failed (will retry next time):', (err.stderr || err.message).trim());
    }
  });
  return commitInFlight;
}

// Commit anything pending before the server goes away (Ctrl+C or closing the Terminal window)
function commitBackupsSync() {
  try {
    execFileSync('git', ['-C', REPO_ROOT, 'add', '--', ...BACKUP_PATHS]);
    try {
      execFileSync('git', ['-C', REPO_ROOT, 'diff', '--cached', '--quiet', '--', ...BACKUP_PATHS]);
      return;
    } catch (e) { /* has changes */ }
    execFileSync('git', ['-C', REPO_ROOT, 'commit', '-m', `Back up practice data (${localDateStr()})`, '--', ...BACKUP_PATHS]);
    execFileSync('git', ['-C', REPO_ROOT, 'push'], { timeout: 20000 });
    console.log('Backup: committed and pushed on shutdown');
  } catch (err) {
    console.error('Backup on shutdown failed:', err.message);
  }
}

for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
  process.on(sig, () => {
    if (backupDirty) commitBackupsSync();
    process.exit(0);
  });
}

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/api/sync-notes') {
    handleNotesSync(req, res);
    return;
  }

  if (req.method === 'POST' && req.url === '/api/backup') {
    handleBackup(req, res);
    return;
  }

  if (req.method === 'GET' && req.url === '/api/spotify/login') {
    try {
      res.writeHead(302, { Location: spotify.getLoginUrl() });
    } catch (err) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end(err.message);
      return;
    }
    res.end();
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/api/spotify/callback')) {
    const params = Object.fromEntries(new URL(req.url, 'http://127.0.0.1').searchParams);
    spotify.handleCallback(params)
      .then(({ user }) => spotify.syncRecentPlays({ force: true }).then(sync => ({ user, sync })))
      .then(({ user, sync }) => {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`<p>Spotify connected as <b>${escapeHtml(user)}</b>. Saved ${sync.added} recent plays.</p>` +
          `<p><a href="http://localhost:${PORT}/">Back to the tracker</a></p>`);
      })
      .catch(err => {
        console.error('Spotify login failed:', err.message);
        res.writeHead(400, { 'Content-Type': 'text/html' });
        res.end(`<p>Spotify login failed: ${escapeHtml(err.message)}</p><p><a href="/api/spotify/login">Try again</a></p>`);
      });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/spotify/sync') {
    Promise.all([spotify.syncRecentPlays({ force: true }), spotify.syncReferencePlaylists({ force: true })])
      .then(([result, playlists]) => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true, ...result, playlists: playlists.counts }));
      })
      .catch(err => {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: false, error: err.message }));
      });
    return;
  }

  if (req.method === 'POST' && req.url === '/api/spotify/play') {
    let raw = '';
    req.on('data', chunk => { raw += chunk; });
    req.on('end', () => {
      const reply = (status, body) => {
        res.writeHead(status, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(body));
      };
      let trackId, positionMs;
      try { ({ trackId, positionMs } = JSON.parse(raw)); } catch { /* checked below */ }
      if (!/^[A-Za-z0-9]{22}$/.test(trackId || '') || !Number.isFinite(positionMs)) {
        reply(400, { ok: false, error: 'trackId and positionMs are required' });
        return;
      }
      spotify.playTrackAt(trackId, positionMs)
        .then(result => reply(200, result))
        .catch(err => reply(502, { ok: false, error: err.message }));
    });
    return;
  }

  if (req.method === 'GET' && req.url === '/api/spotify/plays') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ...spotify.getStatus(), plays: spotify.loadPlays().plays, references: spotify.loadReferencePlaylists().playlists }));
    return;
  }

  if (req.method === 'GET' && req.url === '/api/spotify/status') {
    spotify.checkConnection()
      .then(result => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(result));
      })
      .catch(err => {
        console.error('Spotify check failed:', err.message);
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: false, error: err.message }));
      });
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
  spotify.startDailySync();
  commitBackups(); // pick up anything left uncommitted by a previous run
});
