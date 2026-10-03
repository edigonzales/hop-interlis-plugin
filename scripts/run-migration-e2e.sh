#!/usr/bin/env bash
set -euo pipefail

# Called after installing the distribution; uses only disposable configuration/output.
HOP_MIGRATION_HOME="$1"
MIGRATION_WORK="$2"
MIGRATION_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export HOP_CONFIG_FOLDER="$MIGRATION_WORK/migration-config"
export HOP_AUDIT_FOLDER="$MIGRATION_WORK/migration-audit"
mkdir -p "$HOP_CONFIG_FOLDER/metadata/workflow-run-configuration" "$MIGRATION_WORK/migration"
cat > "$HOP_CONFIG_FOLDER/metadata/workflow-run-configuration/local.json" <<'JSON'
{"name":"local","engineRunConfiguration":{"Local":{"safe_mode":true}}}
JSON
cp "$MIGRATION_ROOT/examples/migration/"* "$MIGRATION_WORK/migration/"
MIGRATION_TARGET="$MIGRATION_WORK/migration/result.xtf"
"$HOP_MIGRATION_HOME/hop-run.sh" -r local -f "$MIGRATION_WORK/migration/migrate.hwf" \
  -p OUTPUT_XTF="$MIGRATION_TARGET" > "$MIGRATION_WORK/migration-success.log" 2>&1
cat "$MIGRATION_WORK/migration-success.log"
python3 "$MIGRATION_ROOT/scripts/check-migration-output.py" "$MIGRATION_TARGET"
cp "$MIGRATION_TARGET" "$MIGRATION_WORK/migration-expected.xtf"
MIGRATION_EXIT=0
"$HOP_MIGRATION_HOME/hop-run.sh" -r local -f "$MIGRATION_WORK/migration/migrate.hwf" \
  -p OUTPUT_XTF="$MIGRATION_TARGET" > "$MIGRATION_WORK/migration-existing.log" 2>&1 || MIGRATION_EXIT=$?
if [[ "$MIGRATION_EXIT" == 0 ]]; then
  echo "Migration unexpectedly overwrote an existing output" >&2
  exit 1
fi
if ! grep -Fq 'already exists' "$MIGRATION_WORK/migration-existing.log"; then
  cat "$MIGRATION_WORK/migration-existing.log"
  exit 1
fi
cmp "$MIGRATION_TARGET" "$MIGRATION_WORK/migration-expected.xtf"
cmp "$MIGRATION_ROOT/examples/migration/source.xtf" "$MIGRATION_WORK/migration/source.xtf"
echo "Migration Workflow E2E: target validation, references, curves, XYZ, nested collections and overwrite protection OK"
