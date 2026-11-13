#!/bin/bash
cd "$(dirname "$0")"
# A tracker left running from earlier would keep the port, and with it the old code, so stop it first
old=$(lsof -tiTCP:3000 -sTCP:LISTEN)
if [ -n "$old" ]; then
  kill $old
  for _ in 1 2 3 4 5 6 7 8 9 10; do lsof -tiTCP:3000 -sTCP:LISTEN >/dev/null || break; sleep 0.5; done
fi
(sleep 1; open http://localhost:3000) &
node server.js
