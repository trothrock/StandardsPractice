// discogs.js — Discogs API client (server-side only; the token never reaches the browser)
// The token comes from the environment as DISCOGS_TOKEN.
// Everything fetched for the Players tab is kept in data/discogs-cache.json, which is not committed:
// it can always be downloaded again, while the choices made about it live in the practice data.
// Requests run one at a time, within Discogs' limit of 60 a minute.

const fs = require('fs');
const path = require('path');
const spotify = require('./spotify');

const API_BASE = 'https://api.discogs.com';
const USER_AGENT = 'StandardsPracticeTracker/1.0';
const CACHE_FILE = path.join(__dirname, 'data', 'discogs-cache.json');
const REQUEST_GAP_MS = 1100;
const BUSY_GAP_MS = 3500;       // once few requests are left in the current minute
const MAX_CANDIDATES = 6;
const VARIOUS_ARTISTS_ID = 194; // Discogs' placeholder for compilations

let cache = null;

function getCache() {
  if (!cache) {
    try { cache = JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8')); } catch { cache = {}; }
    cache = { releases: {}, masters: {}, candidates: {}, ...cache };
  }
  return cache;
}

function saveCache() {
  fs.mkdirSync(path.dirname(CACHE_FILE), { recursive: true });
  fs.writeFileSync(CACHE_FILE, JSON.stringify(cache) + '\n');
}

function getToken() {
  const token = (process.env.DISCOGS_TOKEN || '').trim();
  if (!token) throw new Error('DISCOGS_TOKEN must be set');
  return token;
}

let queue = Promise.resolve();
let lastRemaining = 60;

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function fetchOne(apiPath, params) {
  const url = new URL(API_BASE + apiPath);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  for (let attempt = 0; attempt < 4; attempt++) {
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT, 'Authorization': `Discogs token=${getToken()}` } });
    lastRemaining = Number(res.headers.get('X-Discogs-Ratelimit-Remaining') ?? lastRemaining);
    if (res.status === 429) { await sleep(15000); continue; }
    if (res.status === 404) return null;
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(`Discogs ${apiPath} failed (${res.status}): ${body.message || res.statusText}`);
    return body;
  }
  throw new Error('Discogs is limiting requests — try again in a minute');
}

// Each call waits its turn, so a burst from the page can't run past the rate limit
function discogsGet(apiPath, params = {}) {
  const run = queue.then(() => fetchOne(apiPath, params));
  queue = run.catch(() => {}).then(() => sleep(lastRemaining > 20 ? REQUEST_GAP_MS : BUSY_GAP_MS));
  return run;
}

// ── Lineups ──

// "Paul Chambers (3)" is how Discogs tells apart artists who share a name
const displayName = name => String(name).replace(/\s\(\d+\)$/, '');

const NOT_A_PLAYER = /produc|engineer|design|photo|liner|master|written|compos|lacquer|artwork|\bart\b|supervis|record(ed|ing)|\bmix|reissue|compil|coordinat|lyric|cover|\bnotes\b|a&r|executive|layout|transfer|\bedit|restor|research|technician|tuner|management|sleeve|illustrat|\btext\b|translat|music by|words by|publish|consult|ensemble|contractor|copyist|\bbooking\b|\blabel\b|\bcreated by\b/i;
const INSTRUMENT = /bass|drum|percussion|cymbal|piano|keyboard|organ|rhodes|wurlitzer|synth|clavinet|celest|harpsichord|melodica|guitar|banjo|mandolin|ukulele|sitar|sax|clarinet|flute|oboe|bassoon|piccolo|recorder|trumpet|cornet|flugelhorn|trombone|tuba|sousaphone|\bhorns?\b|euphonium|mellophon|vibraphone|\bvibes\b|marimb|xylophone|glockenspiel|vocal|voice|scat|\bchoir\b|\bchorus\b|harmonica|violin|viola|cello|\bharp\b|accordion|bandoneon|conga|bongo|timbales|tambourine|shekere|cabasa|agog|balafon|whistle|cowbell|triangle|\bbells?\b|shaker|guiro|surdo|cuica|berimbau|kalimba|turntable|\bstrings\b|\bbrass\b|\breeds\b|woodwind|\brap\b/i;

// "Performer [Group], Congas, Bells [Gankoqui]" → the instruments, and whether anything was left
// unrecognized. Someone with only an unrecognized role (Arranged By, Featuring) is left for a person
// to decide on rather than dropped.
function readRole(role) {
  const instruments = [];
  let unknown = false;
  String(role || '').split(/,(?![^\[]*\])/)
    .map(part => part.replace(/\[[^\]]*\]/g, '').trim())
    .filter(part => part && !/^performer$/i.test(part))
    .forEach(part => {
      if (NOT_A_PLAYER.test(part)) return;
      if (INSTRUMENT.test(part)) instruments.push(part);
      else unknown = true;
    });
  return { instruments, unknown };
}

const GROUP_NAME = /\b(quintet|quartet|trio|sextet|septet|octet|nonet|orchestra|band|group|ensemble|messengers|experiment|singers|incorporated|all[- ]stars|combo|brothers|sisters)\b|\s(and|&)\s/i;

// The people a release credits. Leaders are often only named as the release's main artist, so they
// are added from there; bands named that way are left out since their members are credited anyway.
function lineupOf(release) {
  const people = new Map();
  const person = (id, name) => {
    if (!people.has(id)) people.set(id, { id, name: displayName(name), instruments: [], status: null, leader: false });
    return people.get(id);
  };
  release.credits.forEach(c => {
    const { instruments, unknown } = readRole(c.role);
    if (!instruments.length && !unknown) return;
    const p = person(c.id, c.name);
    instruments.forEach(i => { if (!p.instruments.includes(i)) p.instruments.push(i); });
    if (instruments.length) p.status = 'player';
    else if (!p.status) p.status = 'unsure';
  });
  release.artists.forEach(a => {
    if (!a.id || a.id === VARIOUS_ARTISTS_ID || (GROUP_NAME.test(a.name) && !people.has(a.id))) return;
    const p = person(a.id, a.name);
    p.status = 'player';
    p.leader = true;
  });
  return [...people.values()].sort((a, b) => (b.leader - a.leader) || (a.status === b.status ? 0 : a.status === 'player' ? -1 : 1));
}

function normalizeRelease(rel) {
  const tracks = [];
  const credits = (rel.extraartists || []).filter(a => a.id).map(a => ({ id: a.id, name: a.name, role: a.role, tracks: a.tracks || '' }));
  const addTrack = t => {
    if (t.type_ === 'heading') return;
    if (t.type_ === 'index') { (t.sub_tracks || []).forEach(addTrack); return; }
    tracks.push({ position: t.position || '', title: t.title });
    (t.extraartists || []).filter(a => a.id).forEach(a => credits.push({ id: a.id, name: a.name, role: a.role, tracks: t.position || '', onTrack: true }));
  };
  (rel.tracklist || []).forEach(addTrack);
  return {
    id: rel.id,
    masterId: rel.master_id || null,
    title: rel.title,
    year: rel.year || null,
    url: rel.uri || `https://www.discogs.com/release/${rel.id}`,
    artists: (rel.artists || []).map(a => ({ id: a.id, name: displayName(a.name) })),
    tracks,
    credits,
  };
}

async function getRelease(id) {
  const store = getCache();
  if (store.releases[id]) return store.releases[id];
  const rel = await discogsGet(`/releases/${id}`);
  if (!rel) throw new Error(`Discogs has no release ${id}`);
  store.releases[id] = normalizeRelease(rel);
  saveCache();
  return store.releases[id];
}

async function masterRelease(masterId) {
  const store = getCache();
  if (!store.masters[masterId]) {
    const master = await discogsGet(`/masters/${masterId}`);
    if (!master?.main_release) throw new Error(`Discogs has no master ${masterId}`);
    store.masters[masterId] = master.main_release;
    saveCache();
  }
  return store.masters[masterId];
}

const lineupReply = release => ({
  id: release.id,
  masterId: release.masterId,
  title: release.title,
  year: release.year,
  url: release.url,
  artists: release.artists,
  lineup: lineupOf(release),
});

// ── Finding an album ──

const normalize = s => String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, '');
const cleanTitle = s => String(s).replace(/\s*[\(\[].*?[\)\]]/g, '').replace(/\s+-\s+.*$/, '').trim();
const escapeRegExp = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// Spotify's "Clifford Brown And Max Roach At Basin Street" is "At Basin Street" on Discogs
function titleWithoutArtists(title, artists) {
  let t = title;
  artists.forEach(a => { t = t.replace(new RegExp(`^${escapeRegExp(a)}\\b\\s*`, 'i'), '').replace(/^(and|&|with)\s+/i, ''); });
  return t.trim();
}

const TITLE_FILLER = new Set(['the', 'and', 'of', 'with', 'for', 'in', 'on', 'at', 'to', 'a', 'an']);
const titleWords = s => String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
  .replace(/[^a-z0-9]+/g, ' ').split(' ').filter(w => w && !TITLE_FILLER.has(w));

// Discogs search returns its nearest guesses even when nothing fits, so most of the album's title
// has to appear in a result. The words can be in the artist part, as in "Sinatra/Jobim: ...".
function titleFits(label, titles) {
  const have = new Set(titleWords(label));
  return titles.some(t => {
    const words = titleWords(t);
    return words.length > 0 && words.filter(w => have.has(w)).length / words.length >= 0.6;
  });
}

const toCandidate = (r, kind) => ({ kind, id: r.id, label: r.title, year: r.year ? String(r.year) : null });

// Best first: the title matches, then the closest year. Without a year to compare, a later
// reissue with the same title can look just as good as the original, so a person confirms each one.
function rankCandidates(list, titles, year) {
  const wanted = titles.map(normalize).filter(Boolean);
  const titleScore = c => {
    const name = normalize(c.label);
    return wanted.some(w => name.endsWith(w)) ? 0 : 1;
  };
  const yearGap = c => (year && c.year ? Math.abs(Number(c.year) - Number(year)) : 1000);
  return [...list].sort((a, b) => titleScore(a) - titleScore(b) || yearGap(a) - yearGap(b));
}

async function findCandidates(albumId, yearOverride) {
  const store = getCache();
  const saved = store.candidates[albumId];
  if (saved && saved.year === (yearOverride || saved.spotifyYear)) return saved.candidates;

  const album = await spotify.spotifyGet(`/albums/${albumId}`);
  const spotifyYear = (album.release_date || '').slice(0, 4) || null;
  const year = yearOverride || spotifyYear;
  const artists = album.artists.map(a => a.name);
  const title = cleanTitle(album.name);
  const shortened = titleWithoutArtists(title, artists);
  const titles = shortened && shortened !== title ? [title, shortened] : [title];

  const found = [];
  const addAll = (results, kind) => (results || []).forEach(r => {
    if (!found.some(c => c.kind === kind && c.id === r.id)) found.push(toCandidate(r, kind));
  });
  for (const t of titles) {
    const byTitle = await discogsGet('/database/search', { type: 'master', release_title: t, artist: artists[0], per_page: 15 });
    addAll(byTitle?.results, 'master');
  }
  if (!found.length) {
    const broad = await discogsGet('/database/search', { type: 'master', q: `${artists[0]} ${shortened || title}`, per_page: 10 });
    addAll(broad?.results, 'master');
  }
  const upc = album.external_ids?.upc;
  if (upc) {
    const byCode = await discogsGet('/database/search', { type: 'release', barcode: upc, per_page: 3 });
    addAll(byCode?.results, 'release');
  }

  // A barcode identifies the album exactly, so only title searches need the check
  const likely = found.filter(c => c.kind === 'release' || titleFits(c.label, titles));
  const candidates = rankCandidates(likely, titles, year).slice(0, MAX_CANDIDATES);
  store.candidates[albumId] = { year, spotifyYear, candidates };
  saveCache();
  return candidates;
}

// What was typed, or the one master or release a pasted Discogs link names
async function searchReleases(q) {
  const linked = String(q).match(/discogs\.com\/(?:[a-z]{2}\/)?(master|release)s?\/(\d+)/i);
  if (linked) {
    const kind = linked[1].toLowerCase(), id = Number(linked[2]);
    const release = await getRelease(kind === 'master' ? await masterRelease(id) : id);
    return [{ kind: 'release', id: release.id, label: `${release.artists.map(a => a.name).join(', ')} - ${release.title}`, year: release.year ? String(release.year) : null }];
  }
  const masters = await discogsGet('/database/search', { type: 'master', q, per_page: 10 });
  if (masters?.results?.length) return masters.results.map(r => toCandidate(r, 'master'));
  const releases = await discogsGet('/database/search', { type: 'release', q, per_page: 10 });
  return (releases?.results || []).map(r => toCandidate(r, 'release'));
}

async function searchArtists(q) {
  const found = await discogsGet('/database/search', { type: 'artist', q, per_page: 8 });
  return (found?.results || []).map(r => ({ id: r.id, name: displayName(r.title) }));
}

// ── Routes ──

const SPOTIFY_ALBUM_ID = /^[A-Za-z0-9]{22}$/;
const NUMERIC_ID = /^\d{1,12}$/;
const MAX_BULK_RELEASES = 200;

function handleRequest(req, res) {
  if (req.method !== 'GET' || !req.url.startsWith('/api/discogs/')) return false;
  const url = new URL(req.url, 'http://127.0.0.1');
  const params = url.searchParams;
  const reply = (status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(body));
  };
  const respond = work => work
    .then(result => reply(200, { ok: true, ...result }))
    .catch(err => reply(502, { ok: false, error: err.message }));

  if (url.pathname === '/api/discogs/candidates') {
    const albumId = params.get('albumId') || '', year = params.get('year') || null;
    if (!SPOTIFY_ALBUM_ID.test(albumId)) { reply(400, { ok: false, error: 'a Spotify album id is required' }); return true; }
    respond(findCandidates(albumId, year).then(candidates => ({ candidates })));
    return true;
  }
  if (url.pathname === '/api/discogs/lineup') {
    const kind = params.get('kind'), id = params.get('id') || '';
    if (!['master', 'release'].includes(kind) || !NUMERIC_ID.test(id)) { reply(400, { ok: false, error: 'kind and id are required' }); return true; }
    respond((kind === 'master' ? masterRelease(id) : Promise.resolve(id)).then(getRelease).then(r => ({ release: lineupReply(r) })));
    return true;
  }
  if (url.pathname === '/api/discogs/lineups') {
    const ids = (params.get('ids') || '').split(',').filter(Boolean).slice(0, MAX_BULK_RELEASES);
    if (!ids.every(id => NUMERIC_ID.test(id))) { reply(400, { ok: false, error: 'ids must be numbers' }); return true; }
    respond((async () => {
      const releases = {};
      for (const id of ids) releases[id] = lineupReply(await getRelease(id));
      return { releases };
    })());
    return true;
  }
  if (url.pathname === '/api/discogs/search') {
    const q = (params.get('q') || '').trim();
    if (!q) { reply(400, { ok: false, error: 'q is required' }); return true; }
    respond(params.get('type') === 'artist'
      ? searchArtists(q).then(artists => ({ artists }))
      : searchReleases(q).then(candidates => ({ candidates })));
    return true;
  }
  reply(404, { ok: false, error: 'Unknown Discogs route' });
  return true;
}

module.exports = { handleRequest, lineupOf, readRole, normalizeRelease, rankCandidates, titleWithoutArtists, findCandidates, getRelease, masterRelease };
