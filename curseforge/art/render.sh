#!/usr/bin/env bash
# Renders every image in banners.html to ../images/ with headless Edge (or Chrome).
# Usage: bash curseforge/art/render.sh [scene ...]   (default: all)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../images"
mkdir -p "$out"
browser="${BROWSER_BIN:-/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe}"
page="file:///$(cygpath -m "$here/banners.html" 2>/dev/null || echo "$here/banners.html")"

# name width height scale  (logo stays at exactly 400x400, CurseForge's avatar size)
scenes=(
  "header 1200 400 2"
  "logo 400 400 1"
  "components 1200 790 2"
  "steps 1200 430 2"
  "divider-features 1200 96 2"
  "divider-components 1200 96 2"
  "divider-getting-started 1200 96 2"
  "divider-casings 1200 96 2"
  "divider-compatibility 1200 96 2"
  "divider-faq 1200 96 2"
  "divider-credits 1200 96 2"
)
for s in "${scenes[@]}"; do
  read -r name w h scale <<<"$s"
  if [ $# -gt 0 ] && [[ ! " $* " == *" $name "* ]]; then continue; fi
  file="$(cygpath -w "$out/$name.png" 2>/dev/null || echo "$out/$name.png")"
  "$browser" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor="$scale" \
    --window-size="$w,$h" --virtual-time-budget=6000 --screenshot="$file" "$page#$name" >/dev/null 2>&1
  echo "rendered $name.png (${w}x${h} @${scale}x)"
done
