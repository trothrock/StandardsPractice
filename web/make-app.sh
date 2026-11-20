#!/bin/bash
# Builds "Practice Tracker.app" in ~/Applications. Double-clicking it runs restart-tracker.sh:
# it stops any running tracker server, starts a fresh one in the background, and opens the page.
# Rerun this only if the project folder moves (the app remembers its path).
set -e
cd "$(dirname "$0")"
WEB="$(pwd)"
APP="$HOME/Applications/Practice Tracker.app"
chmod +x restart-tracker.sh

SRC=$(mktemp -d)
trap 'rm -rf "$SRC"' EXIT
cat > "$SRC/main.applescript" <<EOF
try
	do shell script quoted form of "$WEB/restart-tracker.sh"
on error errMsg
	set choice to button returned of (display dialog "Practice Tracker couldn't start." & return & return & errMsg buttons {"Show Log", "OK"} default button "OK" with icon caution)
	if choice is "Show Log" then do shell script "open -a Console ~/Library/Logs/PracticeTracker.log"
end try
EOF
rm -rf "$APP"
mkdir -p "$HOME/Applications"
osacompile -o "$APP" "$SRC/main.applescript"

# A piano-keys icon, drawn from the emoji, so it's easy to spot in the Dock.
cat > "$SRC/icon.swift" <<'EOF'
import AppKit
let size = 1024.0
let img = NSImage(size: NSSize(width: size, height: size))
img.lockFocus()
let rect = NSRect(x: 0, y: 0, width: size, height: size).insetBy(dx: 100, dy: 100)
NSColor(calibratedRed: 0.12, green: 0.11, blue: 0.10, alpha: 1).setFill()
NSBezierPath(roundedRect: rect, xRadius: 185, yRadius: 185).fill()
let s = NSAttributedString(string: "🎹", attributes: [.font: NSFont.systemFont(ofSize: 560)])
let sz = s.size()
s.draw(at: NSPoint(x: (size - sz.width) / 2, y: (size - sz.height) / 2 + 10))
img.unlockFocus()
let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
EOF
if swift "$SRC/icon.swift" "$SRC/icon.png" 2>/dev/null; then
  ICONSET="$SRC/applet.iconset"; mkdir "$ICONSET"
  for n in 16 32 128 256 512; do
    sips -z $n $n "$SRC/icon.png" --out "$ICONSET/icon_${n}x${n}.png" >/dev/null
    sips -z $((n*2)) $((n*2)) "$SRC/icon.png" --out "$ICONSET/icon_${n}x${n}@2x.png" >/dev/null
  done
  iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/applet.icns"
  touch "$APP"
fi
# Changing the icon breaks the signature osacompile made, so sign it again (ad hoc).
codesign --force --deep --sign - "$APP" 2>/dev/null
echo "Built $APP"
