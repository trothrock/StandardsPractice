// discogs.js — Discogs API client (server-side only; the token never reaches the browser)
// The token comes from the environment as DISCOGS_TOKEN.
// Everything fetched for the Players tab and the Records tab's owned-record badges is kept in data/discogs-cache.json, which is not committed:
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
const MAX_BIO_REFERENCES = 15;  // each one is a request, so a very long bio can't stall its page
const VARIOUS_ARTISTS_ID = 194; // Discogs' placeholder for compilations

let cache = null;

function getCache() {
  if (!cache) {
    try { cache = JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8')); } catch { cache = {}; }
    cache = { releases: {}, masters: {}, candidates: {}, artists: {}, bioNames: {}, ...cache };
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

// Discogs writes which tracks a credit covers like "A1 to B2, B4". Returns the positions named, or
// null if it can't be read against the track list, in which case the credit counts for the album.
function positionsIn(spec, tracks) {
  const order = tracks.map(t => t.position);
  const found = [];
  for (const part of String(spec).split(',').map(s => s.trim()).filter(Boolean)) {
    const range = part.split(/\s+to\s+/i).map(s => s.trim());
    if (range.length === 2) {
      const from = order.indexOf(range[0]), to = order.indexOf(range[1]);
      if (from < 0 || to < from) return null;
      found.push(...order.slice(from, to + 1));
    } else if (order.includes(part)) found.push(part);
    else return null;
  }
  return found.length ? found : null;
}

// The people a release credits. Leaders are often only named as the release's main artist, so they
// are added from there; bands named that way are left out since their members are credited anyway.
// Each person is either on the whole album or on the listed track positions.
function lineupOf(release) {
  const people = new Map();
  const person = (id, name) => {
    if (!people.has(id)) people.set(id, { id, name: displayName(name), instruments: [], status: null, leader: false, playing: [], other: [] });
    return people.get(id);
  };
  release.credits.forEach(c => {
    const { instruments, unknown } = readRole(c.role);
    if (!instruments.length && !unknown) return;
    const p = person(c.id, c.name);
    instruments.forEach(i => { if (!p.instruments.includes(i)) p.instruments.push(i); });
    if (instruments.length) { p.status = 'player'; p.playing.push(c.tracks); }
    else { if (!p.status) p.status = 'unsure'; p.other.push(c.tracks); }
  });
  release.artists.forEach(a => {
    if (!a.id || a.id === VARIOUS_ARTISTS_ID || (GROUP_NAME.test(a.name) && !people.has(a.id))) return;
    const p = person(a.id, a.name);
    p.status = 'player';
    p.leader = true;
  });
  return [...people.values()].map(({ playing, other, ...p }) => {
    const specs = playing.length ? playing : other;
    const read = specs.map(spec => (spec ? positionsIn(spec, release.tracks) : null));
    const whole = p.leader || !specs.length || read.includes(null);
    return { ...p, whole, positions: whole ? [] : [...new Set(read.flat())] };
  }).sort((a, b) => (b.leader - a.leader) || (a.status === b.status ? 0 : a.status === 'player' ? -1 : 1));
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
  tracks: release.tracks,
  lineup: lineupOf(release),
});

// ── Artists ──

const artistQueue = new Set();
let artistPumpRunning = false;

function normalizeArtist(a) {
  const images = a.images || [];
  const image = images.find(i => i.type === 'primary') || images[0];
  return {
    id: a.id,
    name: displayName(a.name),
    realname: a.realname || null,
    profile: a.profile || '',
    photo: image?.uri || image?.uri150 || null, // the 150px version looks soft in the grid on a retina screen
    images: images.map(i => i.uri).filter(Boolean),
    urls: a.urls || [],
    groups: (a.groups || []).map(g => ({ id: g.id, name: displayName(g.name) })),
    url: a.uri || `https://www.discogs.com/artist/${a.id}`,
  };
}

const BIO_REFERENCE_PATHS = { a: 'artists', r: 'releases', m: 'masters', l: 'labels' };

// The name behind a reference like [a12345], or '' if it can't be found
async function bioReferenceName(kind, id) {
  const store = getCache(), key = kind + id;
  if (store.bioNames[key] !== undefined) return store.bioNames[key];
  let name = '';
  try {
    const found = await discogsGet(`/${BIO_REFERENCE_PATHS[kind]}/${id}`);
    name = found ? displayName(found.name || found.title || '') : '';
  } catch (e) {
    return ''; // not saved, so it's tried again next time
  }
  store.bioNames[key] = name;
  return name;
}

// Discogs writes bios with its own tags: [a=Name], [a12345], [url=...]text[/url], [b]bold[/b]
async function readableBio(profile) {
  const text = String(profile || '');
  const references = [...new Set([...text.matchAll(/\[([arml])=?(\d+)\]/g)].map(m => m[1] + m[2]))].slice(0, MAX_BIO_REFERENCES);
  const names = {};
  for (const ref of references) names[ref] = await bioReferenceName(ref[0], ref.slice(1));
  return text
    .replace(/\[([arml])=?(\d+)\]/g, (_, kind, id) => names[kind + id] || '')
    .replace(/\[[arml]=([^\]]+)\]/g, (_, name) => displayName(name))
    .replace(/\[url=[^\]]*\]([\s\S]*?)\[\/url\]/gi, '$1')
    .replace(/\[\/?(?:url|b|i|u|s)\]/gi, '')
    .replace(/[ \t]+$/gm, '')
    .trim();
}

async function getArtist(id) {
  const store = getCache();
  if (!store.artists[id] || store.artists[id].profile === undefined) {
    const artist = await discogsGet(`/artists/${id}`);
    if (!artist) throw new Error('Discogs has no record of this artist');
    store.artists[id] = normalizeArtist(artist);
    saveCache();
  }
  const artist = store.artists[id];
  if (artist.bio === undefined) {
    artist.bio = await readableBio(artist.profile);
    saveCache();
  }
  const { name, realname, photo, bio, url } = artist;
  return { id: artist.id, name, realname: realname ? await readableBio(realname) : null, photo, bio, url };
}

const DISCOGRAPHY_PAGE_SIZE = 50;
const discographyPages = new Map();

// Long careers run to thousands of entries, so these come a page at a time and aren't kept on disk
async function getDiscography(id, page) {
  const key = `${id}:${page}`;
  if (!discographyPages.has(key)) {
    const found = await discogsGet(`/artists/${id}/releases`, { sort: 'year', sort_order: 'asc', per_page: DISCOGRAPHY_PAGE_SIZE, page });
    if (!found) throw new Error('Discogs has no discography for this artist');
    discographyPages.set(key, {
      page: found.pagination?.page || page,
      pages: found.pagination?.pages || 1,
      total: found.pagination?.items || 0,
      items: (found.releases || []).map(r => ({
        id: r.id,
        title: r.title,
        artist: String(r.artist || '').replace(/\s\(\d+\)/g, '').replace(/\*(?=,|\s|$)/g, ''), // often several names joined
        year: r.year || null,
        role: r.role || '',
        url: `https://www.discogs.com/${r.type === 'master' ? 'master' : 'release'}/${r.id}`,
      })),
    });
  }
  return discographyPages.get(key);
}

// One artist at a time and each waiting its own turn, so a first load of everyone's photos can't
// hold up a search made in the meantime
async function pumpArtists() {
  if (artistPumpRunning) return;
  artistPumpRunning = true;
  try {
    for (const id of artistQueue) {
      try {
        const artist = await discogsGet(`/artists/${id}`);
        const store = getCache();
        store.artists[id] = artist ? normalizeArtist(artist) : { id: Number(id), photo: null };
        saveCache();
      } catch (e) {
        console.error('Discogs artist lookup failed:', e.message);
      }
      artistQueue.delete(id);
    }
  } finally {
    artistPumpRunning = false;
  }
}

// Photos known so far. Anyone not looked up yet is fetched in the background for the page to ask again.
function artistPhotos(ids) {
  const store = getCache(), photos = {};
  let missing = 0;
  ids.forEach(id => {
    if (store.artists[id]) photos[id] = store.artists[id].photo;
    else { missing++; artistQueue.add(id); }
  });
  pumpArtists();
  return { photos, missing };
}

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

// ── Collection ──

// The records owned, read from the token holder's Discogs collection. A day-old copy is good enough
// for marking album covers; one that's older is fetched again, a page of 100 at a time.
const COLLECTION_MAX_AGE_MS = 24 * 60 * 60 * 1000;
let collectionLoad = null;

async function fetchCollection() {
  const me = await discogsGet('/oauth/identity');
  if (!me?.username) throw new Error('Discogs did not say whose token this is');
  const items = [];
  for (let page = 1, pages = 1; page <= pages; page++) {
    const body = await discogsGet(`/users/${encodeURIComponent(me.username)}/collection/folders/0/releases`, { per_page: 100, page });
    pages = body?.pagination?.pages || 1;
    (body?.releases || []).forEach(r => {
      const b = r.basic_information || {};
      items.push({ releaseId: b.id || r.id, masterId: b.master_id || null, title: b.title || '', artists: (b.artists || []).map(a => displayName(a.name)) });
    });
  }
  return { fetchedAt: Date.now(), items };
}

async function getCollection(refresh) {
  const store = getCache();
  if (!refresh && store.collection && Date.now() - store.collection.fetchedAt < COLLECTION_MAX_AGE_MS) return store.collection;
  if (!collectionLoad) {
    collectionLoad = fetchCollection()
      .then(c => { store.collection = c; saveCache(); return c; })
      .finally(() => { collectionLoad = null; });
  }
  try { return await collectionLoad; } catch (e) {
    if (store.collection) return store.collection; // a stale copy beats none
    throw e;
  }
}

// The master each confirmed release belongs to, so a different pressing of the same album still counts.
// Only releases already fetched are answered; none is looked up for this.
function knownMasters(releaseIds) {
  const store = getCache(), masters = {};
  releaseIds.forEach(id => { if (store.releases[id]?.masterId) masters[id] = store.releases[id].masterId; });
  return masters;
}

// ── Routes ──

const SPOTIFY_ALBUM_ID = /^[A-Za-z0-9]{22}$/;
const NUMERIC_ID = /^\d{1,12}$/;
const MAX_BULK_RELEASES = 200;
const MAX_BULK_ARTISTS = 500;

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
  if (url.pathname === '/api/discogs/artist') {
    const id = params.get('id') || '';
    if (!NUMERIC_ID.test(id)) { reply(400, { ok: false, error: 'an artist id is required' }); return true; }
    respond(getArtist(id).then(artist => ({ artist })));
    return true;
  }
  if (url.pathname === '/api/discogs/discography') {
    const id = params.get('id') || '', page = Number(params.get('page') || 1);
    if (!NUMERIC_ID.test(id) || !Number.isInteger(page) || page < 1) { reply(400, { ok: false, error: 'an artist id is required' }); return true; }
    respond(getDiscography(id, page));
    return true;
  }
  if (url.pathname === '/api/discogs/artists') {
    const ids = (params.get('ids') || '').split(',').filter(Boolean).slice(0, MAX_BULK_ARTISTS);
    if (!ids.every(id => NUMERIC_ID.test(id))) { reply(400, { ok: false, error: 'ids must be numbers' }); return true; }
    reply(200, { ok: true, ...artistPhotos(ids) });
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
  if (url.pathname === '/api/discogs/collection') {
    const ids = (params.get('releases') || '').split(',').filter(Boolean).slice(0, MAX_BULK_RELEASES);
    if (!ids.every(id => NUMERIC_ID.test(id))) { reply(400, { ok: false, error: 'releases must be numbers' }); return true; }
    respond(getCollection(params.get('refresh') === '1').then(c => ({ items: c.items, fetchedAt: c.fetchedAt, masters: knownMasters(ids) })));
    return true;
  }
  reply(404, { ok: false, error: 'Unknown Discogs route' });
  return true;
}

module.exports = { handleRequest, readableBio, lineupOf, positionsIn, readRole, normalizeRelease, rankCandidates, titleWithoutArtists, findCandidates, getRelease, masterRelease };
