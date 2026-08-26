#!/usr/bin/env bash
set -euo pipefail

# Runs the packaged-plugin E2E suite against an Apache Hop installation:
#
#   1. installs hop-geometry-type-plugin and hop-interlis-plugin into HOP_HOME
#   2. optionally builds/installs hop-geotools-plugin (for the GeoPackage pipeline)
#   3. copies test fixtures into a temp work dir
#   4. executes the e2e/pipelines/*.hpl files via hop-run (variables: E2E_INPUT_DIR, E2E_OUTPUT_DIR)
#   5. asserts the produced output values
#
# Usage: run-e2e.sh <HOP_HOME>
#
# Environment:
#   HOP_GEOTOOLS_REPO  checkout of hop-geotools-plugin (default: ../hop-geotools-plugin);
#                      when absent the GeoPackage pipeline is skipped.

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <HOP_HOME>"
  echo "Example: $0 ~/Applications/hop"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOP_HOME="$(cd "$1" && pwd)"

if [[ ! -f "$HOP_HOME/hop-run.sh" ]]; then
  echo "Not an Apache Hop home (hop-run.sh missing): $HOP_HOME" >&2
  exit 1
fi

# Pick a JDK >= 21 for hop-run: HOP_JAVA_HOME or JAVA_HOME win when usable;
# otherwise the SDKMAN candidates are scanned (Temurin preferred, then highest
# version; Java 8/11/17 are ignored).
source "$SCRIPT_DIR/lib-java.sh"
select_java_home
export JAVA_HOME="$SELECTED_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export HOP_JAVA_HOME="$JAVA_HOME"
echo "==> Using JDK $JAVA_HOME"

GEOMETRY_REPO="${HOP_GEOMETRY_TYPE_REPO:-$PROJECT_DIR/../hop-geometry-type-plugin}"
GEOMETRY_ZIP="$(find "$GEOMETRY_REPO/assemblies/assemblies-hop-geometry-type/target" \
  -maxdepth 1 -name 'hop-geometry-type-plugin-*.zip' -print 2>/dev/null | head -n 1)"
INTERLIS_ZIP="$(find "$PROJECT_DIR/assemblies/assemblies-hop-interlis/target" \
  -maxdepth 1 -name 'hop-interlis-plugin-*.zip' -print 2>/dev/null | head -n 1)"

if [[ -z "$GEOMETRY_ZIP" || ! -f "$GEOMETRY_ZIP" ]]; then
  echo "Geometry type plugin ZIP not found; build hop-geometry-type-plugin first." >&2
  exit 1
fi
if [[ -z "$INTERLIS_ZIP" || ! -f "$INTERLIS_ZIP" ]]; then
  echo "INTERLIS plugin ZIP not found; run 'mvn clean verify' first." >&2
  exit 1
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hop-interlis-e2e.XXXXXX")"
cleanup() {
  if [[ "${KEEP_E2E_WORKDIR:-false}" != "true" ]]; then
    rm -rf "$WORK_DIR"
  else
    echo "Keeping E2E work dir: $WORK_DIR"
  fi
}
trap cleanup EXIT

echo "==> Installing plugins into $HOP_HOME"
rm -rf "$HOP_HOME/plugins/misc/hop-geometry-type"
unzip -q -o "$GEOMETRY_ZIP" -d "$HOP_HOME"
rm -rf "$HOP_HOME/plugins/transforms/interlis"
unzip -q -o "$INTERLIS_ZIP" -d "$HOP_HOME"

GEOTOOLS_REPO="${HOP_GEOTOOLS_REPO:-$PROJECT_DIR/../hop-geotools-plugin}"
if [[ -f "$GEOTOOLS_REPO/pom.xml" ]]; then
  echo "==> Building hop-geotools-plugin"
  mvn -f "$GEOTOOLS_REPO/pom.xml" -B -ntp clean install -DskipTests
  GEOTOOLS_ZIP="$(find "$GEOTOOLS_REPO/assemblies/assemblies-hop-geotools/target" \
    -maxdepth 1 -name 'hop-geotools-plugin-*.zip' -print | head -n 1)"
  if [[ -z "$GEOTOOLS_ZIP" || ! -f "$GEOTOOLS_ZIP" ]]; then
    echo "GeoTools plugin ZIP was not created" >&2
    exit 1
  fi
  echo "==> Installing GeoTools plugin"
  rm -rf "$HOP_HOME/plugins/transforms/geotools-vector"
  unzip -q -o "$GEOTOOLS_ZIP" -d "$HOP_HOME"
  RUN_GPKG=true
else
  echo "==> hop-geotools-plugin not found at $GEOTOOLS_REPO; skipping GeoPackage pipeline"
  RUN_GPKG=false
fi

echo "==> Preparing test data in $WORK_DIR"
mkdir -p "$WORK_DIR/input" "$WORK_DIR/output"
cp "$PROJECT_DIR/hop-interlis-core/src/test/resources/data/"*.xtf "$WORK_DIR/input/"
cp "$PROJECT_DIR/hop-interlis-core/src/test/resources/models/"*.ili "$WORK_DIR/input/"

run_pipeline() {
  local pipeline="$1"
  echo "==> E2E: $(basename "$pipeline")"
  "$HOP_HOME/hop-run.sh" -r local -f "$pipeline" \
    -p E2E_INPUT_DIR="$WORK_DIR/input" -p E2E_OUTPUT_DIR="$WORK_DIR/output"
}

run_pipeline "$PROJECT_DIR/e2e/pipelines/02-interlis-input-to-csv.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/03-interlis-input-structures.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/05-xtf-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/06-roundtrip-check.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/07-structures-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/08-structures-roundtrip-check.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/09-associations-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/10-associations-roundtrip-check.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/11-association-rows-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/12-association-rows-roundtrip-check.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/13-generic-transfer-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/14-generic-transfer-check.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/15-generic-delete-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/16-generic-delete-check.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/17-validate.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/18-enumerations.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/19-delete-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/20-delete-check.hpl"
if [[ "$RUN_GPKG" == "true" ]]; then
  run_pipeline "$PROJECT_DIR/e2e/pipelines/04-interlis-to-gpkg.hpl"
fi

echo "==> Asserting outputs"
if [[ "$RUN_GPKG" == "true" ]]; then
  python3 "$PROJECT_DIR/scripts/check-e2e-output.py" "$WORK_DIR/output" --with-gpkg
else
  python3 "$PROJECT_DIR/scripts/check-e2e-output.py" "$WORK_DIR/output"
fi

echo "E2E OK"
