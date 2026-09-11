#!/usr/bin/env bash
set -euo pipefail

# Runs the packaged-plugin E2E suite against an Apache Hop installation:
#
#   1. installs hop-geometry-type-plugin and hop-interlis-plugin into HOP_HOME
#   2. optionally builds/installs hop-vector-raster-plugin (for the GeoPackage pipeline)
#   3. copies test fixtures into a temp work dir
#   4. executes the e2e/pipelines/*.hpl files via hop-run (variables: E2E_INPUT_DIR, E2E_OUTPUT_DIR)
#   5. asserts the produced output values
#
# Usage: run-e2e.sh <HOP_HOME>
#
# Environment:
#   HOP_VECTOR_RASTER_REPO  checkout of hop-vector-raster-plugin (default: ../hop-vector-raster-plugin);
#                           when absent the GeoPackage pipeline is skipped.
#   HOP_VECTOR_RASTER_ZIP   prebuilt compatible Vector/Raster plugin ZIP.

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
GEOMETRY_ZIP="${HOP_GEOMETRY_TYPE_ZIP:-$(find "$GEOMETRY_REPO/assemblies/assemblies-hop-geometry-type/target" \
  -maxdepth 1 -name 'hop-geometry-type-plugin-*.zip' -print 2>/dev/null | head -n 1)}"
INTERLIS_ZIP="$(find "$PROJECT_DIR/assemblies/assemblies-hop-interlis/target" \
  -maxdepth 1 -name 'hop-interlis-plugin-*.zip' -print 2>/dev/null | head -n 1)"

if [[ -z "$GEOMETRY_ZIP" || ! -f "$GEOMETRY_ZIP" ]]; then
  echo "Geometry type plugin ZIP not found; build hop-geometry-type-plugin first." >&2
  exit 1
fi
if [[ -z "$INTERLIS_ZIP" || ! -f "$INTERLIS_ZIP" ]]; then
  echo "INTERLIS plugin ZIP not found; run './mvnw -B -ntp clean verify' first." >&2
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

# Use deterministic engine settings without touching the user's Hop projects/configuration.
export HOP_CONFIG_FOLDER="$WORK_DIR/config"
export HOP_AUDIT_FOLDER="$WORK_DIR/audit"
mkdir -p "$HOP_CONFIG_FOLDER/metadata/pipeline-run-configuration"
cat > "$HOP_CONFIG_FOLDER/metadata/pipeline-run-configuration/local.json" <<'JSON'
{
  "name": "local",
  "engineRunConfiguration": {"Local": {"rowset_size": "2", "safe_mode": true}},
  "configurationVariables": []
}
JSON

echo "==> Installing plugins into $HOP_HOME"
rm -rf "$HOP_HOME/plugins/misc/hop-geometry-type"
unzip -q -o "$GEOMETRY_ZIP" -d "$HOP_HOME"
rm -rf "$HOP_HOME/plugins/transforms/interlis"
unzip -q -o "$INTERLIS_ZIP" -d "$HOP_HOME"

VECTOR_RASTER_REPO="${HOP_VECTOR_RASTER_REPO:-$PROJECT_DIR/../hop-vector-raster-plugin}"
if [[ -n "${HOP_VECTOR_RASTER_ZIP:-}" ]]; then
  VECTOR_RASTER_ZIP="$HOP_VECTOR_RASTER_ZIP"
elif [[ -f "$VECTOR_RASTER_REPO/pom.xml" ]]; then
  echo "==> Building hop-vector-raster-plugin"
  if [[ -x "$VECTOR_RASTER_REPO/mvnw" ]]; then
    (cd "$VECTOR_RASTER_REPO" && ./mvnw -B -ntp clean verify)
  else
    mvn -f "$VECTOR_RASTER_REPO/pom.xml" -B -ntp clean verify
  fi
  VECTOR_RASTER_ZIP="$(find "$VECTOR_RASTER_REPO/assemblies/assemblies-hop-vector-raster/target" \
    -maxdepth 1 -name 'hop-vector-raster-plugin-*.zip' -print | head -n 1)"
else
  VECTOR_RASTER_ZIP=""
fi
if [[ -n "$VECTOR_RASTER_ZIP" && -f "$VECTOR_RASTER_ZIP" ]]; then
  echo "==> Installing Vector/Raster plugin from $VECTOR_RASTER_ZIP"
  rm -rf "$HOP_HOME/plugins/transforms/vector-raster"
  unzip -q -o "$VECTOR_RASTER_ZIP" -d "$HOP_HOME"
  RUN_GPKG=true
else
  echo "==> No compatible Vector/Raster ZIP; skipping optional GeoPackage pipeline"
  if [[ "${REQUIRE_VECTOR_RASTER_E2E:-false}" == "true" ]]; then
    echo "Required Vector/Raster E2E dependency is missing" >&2
    exit 1
  fi
  RUN_GPKG=false
fi

echo "==> Preparing test data in $WORK_DIR"
mkdir -p "$WORK_DIR/input" "$WORK_DIR/output"
cp "$PROJECT_DIR/hop-interlis-core/src/test/resources/fixtures/data/2.3/"*.xtf "$WORK_DIR/input/"
cp "$PROJECT_DIR/hop-interlis-core/src/test/resources/fixtures/data/2.4/"*.xtf "$WORK_DIR/input/"
cp "$PROJECT_DIR/hop-interlis-core/src/test/resources/fixtures/models/2.3/"*.ili "$WORK_DIR/input/"
cp "$PROJECT_DIR/hop-interlis-core/src/test/resources/fixtures/models/2.4/"*.ili "$WORK_DIR/input/"
cp "$PROJECT_DIR/docs/biblios/user/examples/"*.ili "$WORK_DIR/input/"
cp "$PROJECT_DIR/docs/biblios/user/examples/"*.xtf "$WORK_DIR/input/"
cp "$PROJECT_DIR/e2e/fixtures/"*.xtf "$WORK_DIR/input/"

run_pipeline() {
  local pipeline="$1"
  local expected_exit="${2:-0}"
  local actual_exit=0
  local expected_message="${3:-}"
  local log_file="$WORK_DIR/$(basename "$pipeline").log"
  echo "==> E2E: $(basename "$pipeline")"
  "$HOP_HOME/hop-run.sh" -r local -f "$pipeline" \
    -p E2E_INPUT_DIR="$WORK_DIR/input" -p E2E_OUTPUT_DIR="$WORK_DIR/output" > "$log_file" 2>&1 || actual_exit=$?
  cat "$log_file"
  if [[ "$actual_exit" != "$expected_exit" ]]; then
    echo "Unexpected exit code for $pipeline: $actual_exit (expected $expected_exit)" >&2
    return 1
  fi
  if [[ -n "$expected_message" ]] && ! grep -Fq "$expected_message" "$log_file"; then
    echo "Expected diagnostic not found: $expected_message" >&2
    return 1
  fi
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
run_pipeline "$PROJECT_DIR/e2e/pipelines/21-p1-overlay-3d.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/22-p1-append.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/23-p1-validation-failure.hpl" 1
run_pipeline "$PROJECT_DIR/e2e/pipelines/24-p1-validation-limit.hpl" 1
run_pipeline "$PROJECT_DIR/e2e/pipelines/25-p2-header-reordered.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/26-p2-carrier.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/27-p2-incomplete-event.hpl" 1 "END_TRANSFER required"
run_pipeline "$PROJECT_DIR/e2e/pipelines/28-p2-invalid-operation.hpl" 1 "Invalid _ili_operation"
run_pipeline "$PROJECT_DIR/e2e/pipelines/29-binding-structures.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/30-binding-join.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/31-binding-missing-field.hpl" 1 "missing_parent_field"
run_pipeline "$PROJECT_DIR/e2e/pipelines/32-binding-key-type.hpl" 1 "expected String, actual Integer"
run_pipeline "$PROJECT_DIR/e2e/pipelines/33-doc-demo-input.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/34-doc-structure-flatten.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/35-doc-list-explode-collect.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/36-doc-role-join.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/37-doc-arc-roundtrip.hpl"
run_pipeline "$PROJECT_DIR/e2e/pipelines/38-doc-validation.hpl"
if [[ "$RUN_GPKG" == "true" ]]; then
  run_pipeline "$PROJECT_DIR/e2e/pipelines/04-interlis-to-gpkg.hpl"
fi

echo "==> Asserting outputs"
if [[ "$RUN_GPKG" == "true" ]]; then
  python3 "$PROJECT_DIR/scripts/check-e2e-output.py" "$WORK_DIR/output" --with-gpkg
else
  python3 "$PROJECT_DIR/scripts/check-e2e-output.py" "$WORK_DIR/output"
fi
python3 "$PROJECT_DIR/scripts/check-doc-examples-output.py" "$WORK_DIR/output"

echo "E2E OK"
