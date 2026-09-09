# Examples

Runnable example pipelines for the typical workflows. All pipelines are
parameterized: they expect two environment variables

```text
E2E_INPUT_DIR   directory with the INTERLIS models and transfer files
E2E_OUTPUT_DIR  directory where output files are written
```

The models and transfer files the examples use are committed under
`hop-interlis-core/src/test/resources` (e.g. `models/HopIli_Associations_V1.ili`,
`data/HopIli_Associations_V1_mapping.xtf`); copy them to your input directory.

Run an example with the packaged plugin:

```bash
hop-run \
  -j PROJECT_HOME \
  -f examples/xtf-to-csv/xtf-to-csv.hpl \
  -r local \
  -e E2E_INPUT_DIR=/path/to/input \
  -e E2E_OUTPUT_DIR=/path/to/output
```

(`hop-run` is in the `hop` distribution; install the plugin ZIP from
`assemblies/assemblies-hop-interlis/target/` into `hop/plugins` first.)

| Directory | Workflow |
|---|---|
| `xtf-to-csv/` | INTERLIS Input reads one class into typed rows, written to CSV |
| `xtf-roundtrip/` | INTERLIS Input → INTERLIS Output; the re-read check verifies the roundtrip |
| `structures/` | Structures with BAG/LIST via Structure Explode/Collect roundtrip |
| `associations/` | Flattened association attributes and association rows roundtrips |
| `validation/` | INTERLIS Validate emits validation error rows to CSV |
| `advanced-envelope/` | Generic envelope pipelines: lossless transfer roundtrip and DELETE preservation with INTERLIS Transfer Input/Output, Object to Row, Row to Object |
