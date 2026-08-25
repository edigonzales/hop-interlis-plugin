#!/usr/bin/env bash
set -euo pipefail

# Builds hop-geometry-type-plugin and hop-interlis-plugin, installs both into
# $HOP_HOME and restarts Hop GUI.
#
# Usage: dev-install-and-run.sh <HOP_HOME> [HOP_GEOMETRY_TYPE_REPO]

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <HOP_HOME> [HOP_GEOMETRY_TYPE_REPO]"
  echo "Example: $0 ~/Applications/hop"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOP_HOME="$(cd "$1" && pwd)"
GEOMETRY_REPO="${2:-${HOP_GEOMETRY_TYPE_REPO:-$PROJECT_DIR/../hop-geometry-type-plugin}}"

if [[ ! -f "$HOP_HOME/hop-gui.sh" ]]; then
  echo "Not an Apache Hop home (hop-gui.sh missing): $HOP_HOME" >&2
  exit 1
fi
if [[ ! -f "$GEOMETRY_REPO/pom.xml" ]]; then
  echo "Geometry type repository not found: $GEOMETRY_REPO" >&2
  echo "Clone edigonzales/hop-geometry-type-plugin next to this repository or pass it as argument 2." >&2
  exit 1
fi

GEOMETRY_REPO="$(cd "$GEOMETRY_REPO" && pwd)"
GEOMETRY_PLUGIN_DIR="$HOP_HOME/plugins/misc/hop-geometry-type"
INTERLIS_PLUGIN_DIR="$HOP_HOME/plugins/transforms/interlis"
LOG_FILE="${TMPDIR:-/tmp}/hop-interlis-dev-hop.log"

MVNW="$PROJECT_DIR/mvnw"

echo "==> Building and testing hop-geometry-type-plugin"
if [[ -x "$GEOMETRY_REPO/mvnw" ]]; then
  "$GEOMETRY_REPO/mvnw" -f "$GEOMETRY_REPO/pom.xml" -U -B -ntp clean install
else
  mvn -f "$GEOMETRY_REPO/pom.xml" -U -B -ntp clean install
fi

GEOMETRY_ZIP="$(find "$GEOMETRY_REPO/assemblies/assemblies-hop-geometry-type/target" \
  -maxdepth 1 -name 'hop-geometry-type-plugin-*.zip' -print | head -n 1)"
if [[ -z "$GEOMETRY_ZIP" || ! -f "$GEOMETRY_ZIP" ]]; then
  echo "Geometry type plugin ZIP was not created" >&2
  exit 1
fi

echo "==> Installing Geometry type plugin"
rm -rf "$GEOMETRY_PLUGIN_DIR"
unzip -q -o "$GEOMETRY_ZIP" -d "$HOP_HOME"

echo "==> Building and testing hop-interlis-plugin"
(
  cd "$PROJECT_DIR"
  if [[ -x "$MVNW" ]]; then
    "$MVNW" -U -B -ntp clean verify
  else
    mvn -U -B -ntp clean verify
  fi
  python3 scripts/check-distribution.py
)

INTERLIS_ZIP="$(find "$PROJECT_DIR/assemblies/assemblies-hop-interlis/target" \
  -maxdepth 1 -name 'hop-interlis-plugin-*.zip' -print | head -n 1)"
if [[ -z "$INTERLIS_ZIP" || ! -f "$INTERLIS_ZIP" ]]; then
  echo "INTERLIS plugin ZIP was not created" >&2
  exit 1
fi

echo "==> Installing INTERLIS plugin"
rm -rf "$INTERLIS_PLUGIN_DIR"
unzip -q -o "$INTERLIS_ZIP" -d "$HOP_HOME"

echo "==> Restarting Hop GUI"
if pgrep -f 'org\.apache\.hop\.ui\.hopgui\.HopGui' >/dev/null 2>&1; then
  pkill -f 'org\.apache\.hop\.ui\.hopgui\.HopGui' || true
  for _ in {1..20}; do
    if ! pgrep -f 'org\.apache\.hop\.ui\.hopgui\.HopGui' >/dev/null 2>&1; then
      break
    fi
    sleep 0.25
  done
fi

(
  cd "$HOP_HOME"
  nohup bash ./hop-gui.sh >"$LOG_FILE" 2>&1 &
)

echo "Installed: $GEOMETRY_PLUGIN_DIR"
echo "Installed: $INTERLIS_PLUGIN_DIR"
echo "Hop GUI restarted. Startup log: $LOG_FILE"
