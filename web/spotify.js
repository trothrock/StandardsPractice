// spotify.js — minimal Spotify Web API client (server-side only; secrets never reach the browser)
// Credentials come from the environment:
//   SPOTIFY_JAZZ_STANDARDS_CLIENT_ID + SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET
// Two kinds of access:
//   app  — Client Credentials token for public catalog data (search, tracks, artists)
//   user — Authorization Code login for the user's own data; the refresh token is saved
//          to .spotify-token.json so the login survives server restarts
// Recent plays are pulled at most once per calendar day and appended to data/spotify-plays.json.
// Run directly to check connectivity: node spotify.js

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const API_BASE = 'https://api.spotify.com/v1';
const AUTH_URL = 'https://accounts.spotify.com/authorize';
const TOKEN_URL = 'https://accounts.spotify.com/api/token';
const REDIRECT_URI = 'http://127.0.0.1:3000/api/spotify/callback';
const SCOPES = 'user-read-recently-played';

const TOKEN_FILE = path.join(__dirname, '.spotify-token.json');
const PLAYS_FILE = path.join(__dirname, 'data', 'spotify-plays.json');

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

// Fill in album art for plays saved before images were recorded. Spotify refuses the
// multi-track lookup for development-mode apps, so each track is fetched individually.
async function backfillAlbumImages() {
  const store = loadPlays();
  const missing = [...new Set(store.plays.filter(p => p.albumImage === undefined && p.trackId).map(p => p.trackId))];
  if (!missing.length) return 0;
  const images = new Map();
  for (const id of missing) {
    const track = await spotifyGet(`/tracks/${id}`);
    images.set(id, pickAlbumImage(track.album?.images));
  }
  store.plays.forEach(p => { if (images.has(p.trackId)) p.albumImage = images.get(p.trackId); });
  writeJson(PLAYS_FILE, store);
  return missing.length;
}

// Try a sync now and then hourly, so a server left running past midnight still picks up the new day
function startDailySync() {
  const run = () => {
    if (!isConnected()) return;
    syncRecentPlays()
      .then(r => { if (!r.skipped) console.log(`Spotify: saved ${r.added} new plays (${r.total} total)${r.truncated ? ' — hit the 50-play limit, some may be missing' : ''}`); })
      .catch(e => console.error('Spotify sync failed:', e.message))
      .then(backfillAlbumImages)
      .then(n => { if (n) console.log(`Spotify: added album art for ${n} tracks`); })
      .catch(e => console.error('Spotify album art backfill failed:', e.message));
  };
  run();
  setInterval(run, 60 * 60 * 1000).unref();
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

module.exports = { spotifyGet, checkConnection, getLoginUrl, handleCallback, syncRecentPlays, startDailySync, getStatus, loadPlays };

if (require.main === module) {
  checkConnection()
    .then(r => console.log(JSON.stringify(r, null, 2)))
    .catch(e => { console.error(e.message); process.exit(1); });
}
