#!/usr/bin/env bash
set -euo pipefail

# Prints the development environment status for hop-interlis-plugin.
#
# Usage: check-env.sh [HOP_HOME] [HOP_GEOMETRY_TYPE_REPO]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOP_HOME="${1:-}"
GEOMETRY_REPO="${2:-${HOP_GEOMETRY_TYPE_REPO:-$PROJECT_DIR/../hop-geometry-type-plugin}}"

ok() { printf '[OK]   %s\n' "$1"; }
fail() { printf '[FAIL] %s\n' "$1"; }

if command -v java >/dev/null 2>&1; then
  JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
  ok "Java: $JAVA_VERSION"
else
  fail "Java is not on the PATH"
fi

if command -v mvn >/dev/null 2>&1; then
  ok "Maven: $(mvn -version 2>/dev/null | head -n 1)"
elif [[ -x "$PROJECT_DIR/mvnw" ]]; then
  ok "Maven wrapper: $PROJECT_DIR/mvnw"
else
  fail "Neither mvn nor the Maven wrapper is available"
fi

if [[ -n "$HOP_HOME" ]]; then
  if [[ -f "$HOP_HOME/hop-gui.sh" ]]; then
    ok "Hop home: $HOP_HOME"
  else
    fail "Not an Apache Hop home (hop-gui.sh missing): $HOP_HOME"
  fi
else
  fail "HOP_HOME not given; pass it as argument 1"
fi

if [[ -f "$GEOMETRY_REPO/pom.xml" ]]; then
  ok "Geometry type plugin repository: $GEOMETRY_REPO"
else
  fail "Geometry type plugin repository not found: $GEOMETRY_REPO"
fi

if [[ -f "$PROJECT_DIR/pom.xml" ]]; then
  ok "INTERLIS plugin repository: $PROJECT_DIR"
else
  fail "INTERLIS plugin repository not found: $PROJECT_DIR"
fi
