#!/bin/bash
# Restarts the practice tracker server in the background and opens it in the browser.
# Used by "Practice Tracker.app" (built by make-app.sh) so a double-click from the Dock or
# Desktop picks up code changes without a Terminal window. Output goes to the log below.
cd "$(dirname "$0")" || exit 1

PORT=3000
LOG="$HOME/Library/Logs/PracticeTracker.log"
# Apps launched from Finder get a bare PATH, so look for node where installers put it.
export PATH="/usr/local/bin:/opt/homebrew/bin:$HOME/.volta/bin:$PATH"
NODE=$(command -v node)
if [ -z "$NODE" ]; then
  # nvm installs aren't on any fixed path; take the newest one if present.
  NODE=$(ls -d "$HOME"/.nvm/versions/node/*/bin/node 2>/dev/null | tail -1)
fi
if [ -z "$NODE" ]; then echo "Couldn't find node" >&2; exit 1; fi

# Apps launched from Finder also skip ~/.zshrc, where the API credentials are exported, so ask
# an interactive zsh for them. The marker keeps anything else the rc files print out of the eval.
eval "$(/bin/zsh -i -c 'for v in SPOTIFY_JAZZ_STANDARDS_CLIENT_ID SPOTIFY_JAZZ_STANDARDS_CLIENT_SECRET DISCOGS_TOKEN; do
  [ -n "${(P)v}" ] && printf "__TRACKER_ENV__ export %s=%q\n" $v "${(P)v}"; done' 2>/dev/null < /dev/null \
  | sed -n 's/^__TRACKER_ENV__ //p')"

# Stop whatever holds the port (an earlier run, or one started from Terminal) so the new code loads.
old=$(lsof -tiTCP:$PORT -sTCP:LISTEN)
if [ -n "$old" ]; then
  kill $old
  for _ in $(seq 20); do lsof -tiTCP:$PORT -sTCP:LISTEN >/dev/null || break; sleep 0.25; done
  old=$(lsof -tiTCP:$PORT -sTCP:LISTEN)
  [ -n "$old" ] && kill -9 $old && sleep 0.5
fi

{ echo; echo "=== $(date '+%Y-%m-%d %H:%M:%S') starting with $NODE ==="; } >> "$LOG"
nohup "$NODE" server.js >> "$LOG" 2>&1 < /dev/null &

# Wait until it answers, then open the page.
for _ in $(seq 40); do
  curl -s -o /dev/null "http://localhost:$PORT/" && { open "http://localhost:$PORT/"; exit 0; }
  sleep 0.25
done
echo "The server didn't start — see $LOG" >&2
exit 1
