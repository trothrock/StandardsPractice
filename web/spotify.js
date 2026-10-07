// spotify.js — minimal Spotify Web API client (server-side only; the key never reaches the browser)
// Credentials come from the environment:
//   SPOTIFY_JAZZ_STANDARDS_CLIENT_ID + SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET → Client Credentials token,
//   cached and refreshed automatically.
// Run directly to check connectivity: node spotify.js

const API_BASE = 'https://api.spotify.com/v1';
const TOKEN_URL = 'https://accounts.spotify.com/api/token';

let cachedToken = null; // { value, expiresAt }

function getBasicAuth() {
  const id = (process.env.SPOTIFY_JAZZ_STANDARDS_CLIENT_ID || '').trim();
  const secret = (process.env.SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET || '').trim();
  if (!id || !secret) throw new Error('SPOTIFY_JAZZ_STANDARDS_CLIENT_ID and SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET must both be set');
  return Buffer.from(`${id}:${secret}`).toString('base64');
}

async function getAccessToken() {
  if (cachedToken && Date.now() < cachedToken.expiresAt) return cachedToken.value;

  const res = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: {
      'Authorization': `Basic ${getBasicAuth()}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Token request failed (${res.status}): ${body.error_description || body.error || res.statusText}`);

  // Refresh a minute early so in-flight requests don't race the expiry
  cachedToken = { value: body.access_token, expiresAt: Date.now() + (body.expires_in - 60) * 1000 };
  return cachedToken.value;
}

// GET a Web API path, e.g. spotifyGet('/search', { q: 'Autumn Leaves', type: 'track' })
async function spotifyGet(apiPath, params = {}, attempt = 0) {
  const url = new URL(API_BASE + apiPath);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);

  const res = await fetch(url, { headers: { 'Authorization': `Bearer ${await getAccessToken()}` } });

  if (res.status === 401 && cachedToken && attempt === 0) {
    cachedToken = null;
    return spotifyGet(apiPath, params, attempt + 1);
  }
  if (res.status === 429 && attempt < 2) {
    const waitSec = Number(res.headers.get('Retry-After')) || 1;
    await new Promise(r => setTimeout(r, waitSec * 1000));
    return spotifyGet(apiPath, params, attempt + 1);
  }

  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Spotify ${apiPath} failed (${res.status}): ${body.error?.message || res.statusText}`);
  return body;
}

// Connectivity probe: auth + one search call
async function checkConnection() {
  const data = await spotifyGet('/search', { q: 'Autumn Leaves', type: 'track', limit: 1 });
  const t = data.tracks?.items?.[0];
  return {
    ok: true,
    sample: t ? { name: t.name, artist: t.artists.map(a => a.name).join(', '), album: t.album.name } : null,
  };
}

module.exports = { spotifyGet, checkConnection };

if (require.main === module) {
  checkConnection()
    .then(r => console.log(JSON.stringify(r, null, 2)))
    .catch(e => { console.error(e.message); process.exit(1); });
}
