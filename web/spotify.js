// spotify.js — minimal Spotify Web API client (server-side only; secrets never reach the browser)
// Credentials come from the environment:
//   SPOTIFY_JAZZ_STANDARDS_CLIENT_ID + SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET
// Two kinds of access:
//   app  — Client Credentials token for public catalog data (search, tracks, artists)
//   user — Authorization Code login for the user's own data; the refresh token is saved
//          to .spotify-token.json so the login survives server restarts
// Recent plays are pulled at most once per calendar day and appended to data/spotify-plays.json.
// The reference-recording playlists are re-read once a day into data/spotify-playlists.json.
// A private "Daily Standards" playlist is created once a day after both have synced, and logged
// to data/spotify-daily-playlists.json.
// Run directly to check connectivity: node spotify.js

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const API_BASE = 'https://api.spotify.com/v1';
const AUTH_URL = 'https://accounts.spotify.com/authorize';
const TOKEN_URL = 'https://accounts.spotify.com/api/token';
const REDIRECT_URI = 'http://127.0.0.1:3000/api/spotify/callback';
const SCOPES = 'user-read-recently-played playlist-read-private playlist-read-collaborative user-modify-playback-state user-read-playback-state playlist-modify-private playlist-modify-public';
const PLAYBACK_SCOPES = ['user-modify-playback-state', 'user-read-playback-state'];
const DAILY_PLAYLIST_SCOPE = 'playlist-modify-private';
const LIBRARY_REMOVE_SCOPE = 'playlist-modify-public'; // the scope Spotify documents for removing playlists from a library

const TOKEN_FILE = path.join(__dirname, '.spotify-token.json');
const PLAYS_FILE = path.join(__dirname, 'data', 'spotify-plays.json');
const PLAYLISTS_FILE = path.join(__dirname, 'data', 'spotify-playlists.json');
const DAILY_PLAYLISTS_FILE = path.join(__dirname, 'data', 'spotify-daily-playlists.json');
const DB_BACKUP_FILE = path.join(__dirname, 'backups', 'jazz-tracker.json'); // the page's latest saved data

const DAILY_PLAYLIST_SIZE = 6;
const DAILY_PLAYLIST_KEEP_DAYS = 7;
const LIBRARY_REMOVE_LIMIT = 40; // Spotify's maximum per request
const LISTENING_GOAL_DEFAULT = 20;
const MIN_PICK_WEIGHT = 0.05; // recordings past their goal still come up now and then

// The user's chosen reference recordings, found by playlist name among their own playlists
const REFERENCE_PLAYLISTS = [
  { name: 'Standards Practice', label: 'Instrumental' },
  { name: 'Standards with Vocals', label: 'Vocal' },
];

const tokens = {
  app: null,  // { value, expiresAt }
  user: null, // { value, expiresAt }
};
const pendingStates = new Set();

function getBasicAuth() {
  const id = (process.env.SPOTIFY_JAZZ_STANDARDS_CLIENT_ID || '').trim();
  const secret = (process.env.SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET || '').trim();
  if (!id || !secret) throw new Error('SPOTIFY_JAZZ_STANDARDS_CLIENT_ID and SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET must both be set');
  return Buffer.from(`${id}:${secret}`).toString('base64');
}

function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; }
}

function writeJson(file, data, mode) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n', mode ? { mode } : undefined);
}

async function requestToken(params) {
  const res = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: {
      'Authorization': `Basic ${getBasicAuth()}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(params),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Token request failed (${res.status}): ${body.error_description || body.error || res.statusText}`);
  return body;
}

// Refresh a minute early so in-flight requests don't race the expiry
function cacheToken(kind, body) {
  tokens[kind] = { value: body.access_token, expiresAt: Date.now() + (body.expires_in - 60) * 1000 };
  return tokens[kind].value;
}

async function getAccessToken(kind) {
  if (tokens[kind] && Date.now() < tokens[kind].expiresAt) return tokens[kind].value;
  if (kind === 'app') return cacheToken('app', await requestToken({ grant_type: 'client_credentials' }));

  const saved = readJson(TOKEN_FILE, null);
  if (!saved?.refresh_token) throw new Error('Spotify account not connected — visit /api/spotify/login');
  const body = await requestToken({ grant_type: 'refresh_token', refresh_token: saved.refresh_token });
  // Spotify may rotate the refresh token
  if (body.refresh_token && body.refresh_token !== saved.refresh_token) {
    writeJson(TOKEN_FILE, { ...saved, refresh_token: body.refresh_token }, 0o600);
  }
  return cacheToken('user', body);
}

// GET a Web API path, e.g. spotifyGet('/search', { q: 'Autumn Leaves', type: 'track' })
// Pass { as: 'user' } for endpoints that read the logged-in user's data.
async function spotifyGet(apiPath, params = {}, { as = 'app' } = {}, attempt = 0) {
  const url = new URL(API_BASE + apiPath);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);

  const res = await fetch(url, { headers: { 'Authorization': `Bearer ${await getAccessToken(as)}` } });

  if (res.status === 401 && tokens[as] && attempt === 0) {
    tokens[as] = null;
    return spotifyGet(apiPath, params, { as }, attempt + 1);
  }
  if (res.status === 429 && attempt < 2) {
    const waitSec = Number(res.headers.get('Retry-After')) || 1;
    await new Promise(r => setTimeout(r, waitSec * 1000));
    return spotifyGet(apiPath, params, { as }, attempt + 1);
  }

  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Spotify ${apiPath} failed (${res.status}): ${body.error?.message || res.statusText}`);
  return body;
}

// POST or DELETE a Web API path as the logged-in user
async function spotifyWrite(method, apiPath, payload, attempt = 0) {
  const res = await fetch(API_BASE + apiPath, {
    method,
    headers: { 'Authorization': `Bearer ${await getAccessToken('user')}`, 'Content-Type': 'application/json' },
    body: payload ? JSON.stringify(payload) : undefined,
  });

  if (res.status === 401 && tokens.user && attempt === 0) {
    tokens.user = null;
    return spotifyWrite(method, apiPath, payload, attempt + 1);
  }
  if (res.status === 429 && attempt < 2) {
    const waitSec = Number(res.headers.get('Retry-After')) || 1;
    await new Promise(r => setTimeout(r, waitSec * 1000));
    return spotifyWrite(method, apiPath, payload, attempt + 1);
  }

  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Spotify ${apiPath} failed (${res.status}): ${body.error?.message || res.statusText}`);
  return body;
}

// ── User login (Authorization Code flow) ──

function getLoginUrl() {
  const state = crypto.randomBytes(16).toString('hex');
  pendingStates.add(state);
  const url = new URL(AUTH_URL);
  url.search = new URLSearchParams({
    response_type: 'code',
    client_id: (process.env.SPOTIFY_JAZZ_STANDARDS_CLIENT_ID || '').trim(),
    scope: SCOPES,
    redirect_uri: REDIRECT_URI,
    state,
  });
  return url.toString();
}

async function handleCallback({ code, state, error }) {
  if (error) throw new Error(`Spotify login was declined: ${error}`);
  if (!state || !pendingStates.delete(state)) throw new Error('Login state mismatch — start again from /api/spotify/login');
  const body = await requestToken({ grant_type: 'authorization_code', code, redirect_uri: REDIRECT_URI });
  cacheToken('user', body);
  const me = await spotifyGet('/me', {}, { as: 'user' }).catch(() => ({}));
  writeJson(TOKEN_FILE, { refresh_token: body.refresh_token, scope: body.scope, user: me.display_name || me.id || null }, 0o600);
  return { user: me.display_name || me.id || 'your account' };
}

function isConnected() {
  return !!readJson(TOKEN_FILE, null)?.refresh_token;
}

// ── Daily recent-plays sync ──

function localDate(d = new Date()) {
  return d.toLocaleDateString('en-CA'); // YYYY-MM-DD in local time
}

// Spotify lists album art largest first (640/300/64); 300 stays sharp at thumbnail size on retina
function pickAlbumImage(images = []) {
  const fit = images.filter(i => i.width >= 80).sort((a, b) => a.width - b.width)[0];
  return (fit || images[0])?.url || null;
}

function loadPlays() {
  return readJson(PLAYS_FILE, { lastSyncDate: null, plays: [] });
}

// Pull the latest 50 plays and keep any not already saved. Skips if already done today unless force is set.
async function syncRecentPlays({ force = false } = {}) {
  const store = loadPlays();
  const today = localDate();
  if (!force && store.lastSyncDate === today) return { skipped: true, added: 0, total: store.plays.length };

  const data = await spotifyGet('/me/player/recently-played', { limit: 50 }, { as: 'user' });

  const known = new Set(store.plays.map(p => p.playedAt));
  const fresh = (data.items || [])
    .filter(item => !known.has(item.played_at))
    .map(item => ({
      playedAt: item.played_at,
      trackId: item.track.id,
      name: item.track.name,
      artists: item.track.artists.map(a => a.name),
      album: item.track.album?.name || null,
      albumId: item.track.album?.id || null,
      albumImage: pickAlbumImage(item.track.album?.images),
      durationMs: item.track.duration_ms,
      context: item.context?.uri || null,
    }));

  store.plays = store.plays.concat(fresh).sort((a, b) => a.playedAt.localeCompare(b.playedAt));
  store.lastSyncDate = today;
  writeJson(PLAYS_FILE, store);
  // All 50 unseen after a previous sync means older plays may have scrolled out of reach
  const truncated = fresh.length === 50 && known.size > 0;
  return { skipped: false, added: fresh.length, total: store.plays.length, truncated };
}

// Fill in album art and album ids for plays saved before they were recorded. Spotify refuses
// the multi-track lookup for development-mode apps, so each track is fetched individually.
async function backfillAlbumInfo() {
  const store = loadPlays();
  const missing = [...new Set(store.plays
    .filter(p => (p.albumImage === undefined || p.albumId === undefined) && p.trackId)
    .map(p => p.trackId))];
  if (!missing.length) return 0;
  const albums = new Map();
  for (const id of missing) {
    const track = await spotifyGet(`/tracks/${id}`);
    albums.set(id, { albumImage: pickAlbumImage(track.album?.images), albumId: track.album?.id || null });
  }
  store.plays.forEach(p => { if (albums.has(p.trackId)) Object.assign(p, albums.get(p.trackId)); });
  writeJson(PLAYS_FILE, store);
  return missing.length;
}

// ── Reference playlists ──

function loadReferencePlaylists() {
  return readJson(PLAYLISTS_FILE, { lastSyncDate: null, playlists: [] });
}

async function fetchAllPages(apiPath, params) {
  let items = [], offset = 0, total = Infinity;
  while (offset < total) {
    const page = await spotifyGet(apiPath, { ...params, limit: 50, offset }, { as: 'user' });
    items = items.concat(page.items || []);
    total = page.total; offset += 50;
  }
  return items;
}

async function syncReferencePlaylists({ force = false } = {}) {
  const store = loadReferencePlaylists();
  const today = localDate();
  // A store saved before album ids were recorded is re-read even if already synced today
  const stale = store.playlists.some(pl => pl.tracks.some(t => t.albumId === undefined));
  if (!force && !stale && store.lastSyncDate === today) return { skipped: true };

  const me = await spotifyGet('/me', {}, { as: 'user' });
  const owned = (await fetchAllPages('/me/playlists', {})).filter(p => p && p.owner?.id === me.id);
  const playlists = [];
  for (const ref of REFERENCE_PLAYLISTS) {
    const found = owned.find(p => p.name.trim().toLowerCase() === ref.name.toLowerCase());
    if (!found) { console.error(`Spotify: playlist "${ref.name}" not found`); continue; }
    // Playlist entries carry the track under "item" (older responses used "track")
    const tracks = (await fetchAllPages(`/playlists/${found.id}/items`, {}))
      .map(e => ({ addedAt: e.added_at, t: e.item || e.track }))
      .filter(({ t }) => t && t.type === 'track' && t.id)
      .map(({ addedAt, t }) => ({
        trackId: t.id,
        name: t.name,
        artists: t.artists.map(a => a.name),
        album: t.album?.name || null,
        albumId: t.album?.id || null,
        albumImage: pickAlbumImage(t.album?.images),
        addedAt,
      }));
    playlists.push({ name: ref.name, label: ref.label, id: found.id, tracks });
  }
  writeJson(PLAYLISTS_FILE, { lastSyncDate: today, playlists });
  return { skipped: false, counts: playlists.map(p => `${p.name}: ${p.tracks.length}`) };
}

// ── Daily playlist ──

// These three mirror jazz_practice.html, so a play counts here exactly as it does on the song page
function listeningKey(title) {
  return String(title).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/\s+-\s+.*$/, '')
    .replace(/\s*[\(\[].*?[\)\]]/g, '')
    .replace(/&/g, ' and ')
    .replace(/[‘’'"`.,!?]/g, '')
    .replace(/[^a-z0-9]+/g, ' ').trim()
    .replace(/^(the|an?) /, '');
}

function librarySongFor(title, byKey) {
  const alts = [...String(title).matchAll(/[\(\[]([^\)\]]*)[\)\]]/g)].map(m => listeningKey(m[1]));
  const key = [listeningKey(title), ...alts].find(k => byKey.has(k));
  return key ? byKey.get(key) : undefined;
}

function sameRecording(play, ref) {
  return play.trackId === ref.trackId || (
    listeningKey(play.name) === listeningKey(ref.name) &&
    (play.album || '').toLowerCase() === (ref.album || '').toLowerCase() &&
    (play.artists[0] || '') === (ref.artists[0] || ''));
}

function weightedPick(items, weightOf, random) {
  const weights = items.map(weightOf);
  let r = random() * weights.reduce((sum, w) => sum + w, 0);
  const i = weights.findIndex(w => (r -= w) < 0);
  return items[i === -1 ? items.length - 1 : i];
}

// Picks today's recordings: the song being learned first (from the reference playlists, else its
// added recordings), then other songs at random, favoring those furthest from the listening goal.
// One song from the previous playlist sits out, so two days in a row can't match while the pool
// has enough songs. No song appears twice, even when both reference playlists have a recording.
function selectDailyTracks({ references, plays, db, previousTrackIds = [], random = Math.random }) {
  const goal = db.listeningGoal || LISTENING_GOAL_DEFAULT;
  const byKey = new Map(Object.keys(db.songs || {}).map(name => [listeningKey(name), name]));
  const seen = new Set();
  const pool = references.flatMap(pl => pl.tracks)
    .filter(t => !seen.has(t.trackId) && seen.add(t.trackId))
    .map(t => ({ ...t, song: librarySongFor(t.name, byKey) || listeningKey(t.name) }));
  const weights = new Map();
  const weightOf = t => {
    if (!weights.has(t.trackId)) {
      const heard = plays.filter(p => sameRecording(p, t)).length;
      weights.set(t.trackId, Math.max(Math.max(1 - heard / goal, 0) ** 2, MIN_PICK_WEIGHT));
    }
    return weights.get(t.trackId);
  };

  const current = db.currentNewSong;
  const currentOptions = pool.filter(t => t.song === current);
  if (!currentOptions.length && current) {
    currentOptions.push(...(db.songs?.[current]?.otherRecordings || []).map(t => ({ ...t, song: current })));
  }
  const picks = currentOptions.length ? [weightedPick(currentOptions, weightOf, random)] : [];

  let rest = pool.filter(t => t.song !== current);
  const repeats = rest.filter(t => previousTrackIds.includes(t.trackId));
  if (repeats.length) {
    const sittingOut = repeats[Math.floor(random() * repeats.length)];
    const without = rest.filter(t => t !== sittingOut);
    if (new Set(without.map(t => t.song)).size >= DAILY_PLAYLIST_SIZE - picks.length) rest = without;
  }
  while (picks.length < DAILY_PLAYLIST_SIZE && rest.length) {
    const pick = weightedPick(rest, weightOf, random);
    picks.push(pick);
    rest = rest.filter(t => t.song !== pick.song);
  }
  return picks.map(t => ({ trackId: t.trackId, name: t.name, artists: t.artists, song: t.song }));
}

function loadDailyPlaylists() {
  return readJson(DAILY_PLAYLISTS_FILE, { playlists: [] });
}

// Creates today's playlist once. It waits for today's plays and reference playlists, so progress
// is current. The new playlist is logged before it's filled: a failure in between is retried
// against the same playlist instead of leaving an empty one behind.
async function buildDailyPlaylist() {
  const today = localDate();
  const store = loadDailyPlaylists();
  let entry = store.playlists.find(p => p.date === today);
  if (entry?.filled) return { skipped: true };

  const granted = (readJson(TOKEN_FILE, null)?.scope || '').split(' ');
  if (!granted.includes(DAILY_PLAYLIST_SCOPE)) return { skipped: true, reason: 'scope' };
  const references = loadReferencePlaylists();
  const playsStore = loadPlays();
  if (references.lastSyncDate !== today || playsStore.lastSyncDate !== today) return { skipped: true };

  if (!entry) {
    const db = readJson(DB_BACKUP_FILE, null);
    if (!db?.songs) return { skipped: true, reason: 'no-backup' };
    const previous = store.playlists.filter(p => p.date < today).at(-1);
    const tracks = selectDailyTracks({
      references: references.playlists,
      plays: playsStore.plays,
      db,
      previousTrackIds: previous?.tracks.map(t => t.trackId),
    });
    if (!tracks.length) return { skipped: true, reason: 'empty-pool' };
    const title = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    const name = `Daily Standards – ${title}`;
    const created = await spotifyWrite('POST', '/me/playlists', { name, public: false, description: 'Created by the practice tracker' });
    entry = { date: today, id: created.id, name, tracks, filled: false };
    store.playlists.push(entry);
    writeJson(DAILY_PLAYLISTS_FILE, store);
  }

  await spotifyWrite('POST', `/playlists/${entry.id}/items`, { uris: entry.tracks.map(t => `spotify:track:${t.trackId}`) });
  entry.filled = true;
  writeJson(DAILY_PLAYLISTS_FILE, store);
  return { skipped: false, name: entry.name, count: entry.tracks.length };
}

// Takes daily playlists out of the library once they're a week old. Spotify has no true delete,
// so each is only unfollowed. Only playlists logged by buildDailyPlaylist are ever touched, and
// their log entries stay, marked with the removal date. Any past the per-request limit wait for
// the next hourly run.
async function removeOldDailyPlaylists() {
  const store = loadDailyPlaylists();
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - DAILY_PLAYLIST_KEEP_DAYS);
  const due = store.playlists.filter(p => p.date <= localDate(cutoff) && !p.removedOn).slice(0, LIBRARY_REMOVE_LIMIT);
  if (!due.length) return { removed: 0 };

  const granted = (readJson(TOKEN_FILE, null)?.scope || '').split(' ');
  if (!granted.includes(LIBRARY_REMOVE_SCOPE)) return { removed: 0, reason: 'scope' };

  const uris = due.map(p => encodeURIComponent(`spotify:playlist:${p.id}`)).join(',');
  await spotifyWrite('DELETE', `/me/library?uris=${uris}`);
  const today = localDate();
  due.forEach(p => { p.removedOn = today; });
  writeJson(DAILY_PLAYLISTS_FILE, store);
  return { removed: due.length };
}

// Safe to call after any sync: overlapping calls share one run, and it never rejects
let dailyPlaylistRun = null;
let warnedDailyScope = false;
function warnDailyScope() {
  if (warnedDailyScope) return;
  warnedDailyScope = true;
  console.error('Spotify: daily playlists need new permission — reconnect at /api/spotify/login');
}
function runDailyPlaylist() {
  if (dailyPlaylistRun) return dailyPlaylistRun;
  dailyPlaylistRun = buildDailyPlaylist()
    .then(r => {
      if (!r.skipped) console.log(`Spotify: created "${r.name}" (${r.count} songs)`);
      if (r.reason === 'scope') warnDailyScope();
      if (r.reason === 'no-backup') console.error('Spotify: daily playlist skipped — no practice data backup to read yet');
      if (r.reason === 'empty-pool') console.error('Spotify: daily playlist skipped — no recordings to choose from');
    })
    .catch(e => console.error('Spotify daily playlist failed:', e.message))
    .then(removeOldDailyPlaylists)
    .then(r => {
      if (r.removed) console.log(`Spotify: removed ${r.removed} old daily ${r.removed === 1 ? 'playlist' : 'playlists'} from the library`);
      if (r.reason === 'scope') warnDailyScope();
    })
    .catch(e => console.error('Spotify daily playlist cleanup failed:', e.message))
    .finally(() => { dailyPlaylistRun = null; });
  return dailyPlaylistRun;
}

// Try a sync now and then hourly, so a server left running past midnight still picks up the new day
function startDailySync() {
  const run = () => {
    if (!isConnected()) return;
    syncRecentPlays()
      .then(r => { if (!r.skipped) console.log(`Spotify: saved ${r.added} new plays (${r.total} total)${r.truncated ? ' — hit the 50-play limit, some may be missing' : ''}`); })
      .catch(e => console.error('Spotify sync failed:', e.message))
      .then(backfillAlbumInfo)
      .then(n => { if (n) console.log(`Spotify: added album info for ${n} tracks`); })
      .catch(e => console.error('Spotify album info backfill failed:', e.message))
      .then(() => syncReferencePlaylists())
      .then(r => { if (r && !r.skipped) console.log(`Spotify: reference playlists synced (${r.counts.join(', ')})`); })
      .catch(e => console.error('Spotify playlist sync failed:', e.message))
      .then(runDailyPlaylist);
  };
  run();
  setInterval(run, 60 * 60 * 1000).unref();
}

// ── Playback ──

// Starts a track at a position on the user's Spotify. Plays on the active device, or on the
// first available one when nothing is active (an open but idle Spotify app). Failures come
// back as { ok:false, reason } — scope (reconnect needed), premium, no-device — not thrown.
async function playTrackAt(trackId, positionMs) {
  const granted = (readJson(TOKEN_FILE, null)?.scope || '').split(' ');
  if (!PLAYBACK_SCOPES.every(s => granted.includes(s))) return { ok: false, reason: 'scope' };
  const put = (deviceId, attempt = 0) => getAccessToken('user').then(async token => {
    const url = new URL(API_BASE + '/me/player/play');
    if (deviceId) url.searchParams.set('device_id', deviceId);
    const res = await fetch(url, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ uris: [`spotify:track:${trackId}`], position_ms: Math.max(0, Math.round(positionMs)) }),
    });
    if (res.status === 401 && attempt === 0) { tokens.user = null; return put(deviceId, 1); }
    return res;
  });
  let res = await put();
  if (res.status === 404) {
    const { devices = [] } = await spotifyGet('/me/player/devices', {}, { as: 'user' });
    const device = devices.find(d => !d.is_restricted);
    if (!device) return { ok: false, reason: 'no-device' };
    res = await put(device.id);
  }
  if (res.ok) return { ok: true };
  const body = await res.json().catch(() => ({}));
  if (res.status === 403) return { ok: false, reason: 'premium', error: body.error?.message || 'Playback control needs Spotify Premium' };
  if (res.status === 404) return { ok: false, reason: 'no-device' };
  throw new Error(`Spotify playback failed (${res.status}): ${body.error?.message || res.statusText}`);
}

function getStatus() {
  const store = loadPlays();
  return {
    connected: isConnected(),
    user: readJson(TOKEN_FILE, null)?.user || null,
    lastSyncDate: store.lastSyncDate,
    playCount: store.plays.length,
  };
}

// Connectivity probe: app auth + one search call
async function checkConnection() {
  const data = await spotifyGet('/search', { q: 'Autumn Leaves', type: 'track', limit: 1 });
  const t = data.tracks?.items?.[0];
  return {
    ok: true,
    sample: t ? { name: t.name, artist: t.artists.map(a => a.name).join(', '), album: t.album.name } : null,
    ...getStatus(),
  };
}

module.exports = { spotifyGet, checkConnection, getLoginUrl, handleCallback, syncRecentPlays, startDailySync, getStatus, loadPlays, syncReferencePlaylists, loadReferencePlaylists, selectDailyTracks, runDailyPlaylist, playTrackAt };

if (require.main === module) {
  checkConnection()
    .then(r => console.log(JSON.stringify(r, null, 2)))
    .catch(e => { console.error(e.message); process.exit(1); });
}
