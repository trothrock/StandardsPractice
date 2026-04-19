// server.js — Jazz Standards Practice Tracker
// Run with: node server.js
// Then open: http://localhost:3000

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const ROOT = __dirname;

const MIME_TYPES = {
  '.html': 'text/html',
  '.js':   'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.ico':  'image/x-icon',
};

const server = http.createServer((req, res) => {
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
