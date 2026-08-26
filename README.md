# hop-interlis-plugin

An Apache Hop plugin for reading, transforming, validating and writing INTERLIS transfer data (XTF).

The plugin projects INTERLIS classes onto stable, typed Hop rows, keeps circular arcs intact through a
SQL/MM WKB bridge and uses the shared Hop geometry value type from
[`hop-geometry-type-plugin`](https://github.com/edigonzales/hop-geometry-type-plugin).

The authoritative architecture and implementation specification lives in [`docs/spec/`](docs/spec/).

## Status

**Phase 2** (`INTERLIS Output` + typed roundtrip) is implemented:

- `INTERLIS Output` transform writes typed Hop rows of one INTERLIS class to an XTF file
  (streaming, multi-basket, overwrite protection, strict mandatory checks);
- the full roundtrip `XTF → INTERLIS Input → INTERLIS Output → XTF` is verified in unit,
  pipeline and `hop-run` E2E tests – **circular arcs survive the write/read cycle** as
  SQL/MM curves;
- dialog with target file, model source, class browser, identity/basket options and a
  field mapping grid (auto-map by name, status per property).

**Phase 1** (`INTERLIS Input` for XTF) is implemented:

- `INTERLIS Input` transform reads one INTERLIS class from an XTF file and emits stable,
  typed Hop rows: TID/BID, primitives (text, boolean, integer, decimal without precision loss,
  date/time, enumerations), inherited attributes, multiple geometry attributes as real Hop
  `Geometry` fields, flattened `0..1`/`1` structures and simple roles as `<role>_ref`;
- models are detected from the transfer file (`%DATA`) or configured explicitly,
  with model directories and `%XTF_DIR` resolution;
- **circular arcs survive as SQL/MM curves** (verified in unit, pipeline and `hop-run` E2E
  tests, including a GeoPackage round trip via the GeoTools plugin with a registered
  `COMPOUNDCURVE` column);
- dialog with model source, class browser and live schema preview; probing failures never
  make the dialog unusable;
- central mapping plan shared by `getFields()`, runtime and GUI (no schema drift).

Phase 0 (project foundation) is implemented as well:

- multi-module Maven build (Java 21, Apache Hop 2.18.1, iox-ili 1.24.4, ili2c 5.6.8);
- INTERLIS model compilation and schema extraction (`TransferDescription` → descriptors);
- streaming XTF reader on top of iox-ili;
- INTERLIS ↔ Hop geometry bridge via SQL/MM WKB;
- plugin packaging as installable ZIP with a distribution checker;
- one-command local development workflow and `hop-run` E2E suite.

See [`docs/progress/phase-02.md`](docs/progress/phase-02.md),
[`docs/progress/phase-01.md`](docs/progress/phase-01.md) and
[`docs/progress/phase-00.md`](docs/progress/phase-00.md) for details and known limitations.

## Quick start for developers

```bash
git clone https://github.com/edigonzales/hop-geometry-type-plugin

git clone https://github.com/edigonzales/hop-interlis-plugin

cd hop-interlis-plugin
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

This builds the geometry type plugin and this plugin, runs all tests, checks the distribution,
installs both plugins into `$HOP_HOME` and restarts Hop GUI. The Hop startup log is printed at the end.

Environment checks:

```bash
bash scripts/check-env.sh "$HOP_HOME"
```

Build without installing:

```bash
./mvnw -B -ntp clean verify
python3 scripts/check-distribution.py
```

Run the packaged-plugin E2E suite against a Hop installation (optionally builds and
installs `hop-geotools-plugin` for the GeoPackage pipeline):

```bash
bash scripts/run-e2e.sh "$HOP_HOME"
```

### Requirements

- Java 21 (a local `.sdkmanrc` pins the SDKMAN identifier used for development; Apache Hop 2.18 requires Java 21)
- Maven 3.x or the included Maven wrapper
- an Apache Hop 2.18.x installation for local testing (`HOP_HOME`)
- a checkout of `hop-geometry-type-plugin` next to this repository
- a checkout of `hop-geotools-plugin` next to this repository (only for the GeoPackage E2E)

## Modules

| Module | Purpose |
|---|---|
| `hop-interlis-core` | Model compilation, schema descriptors, transfer reader/writer, mapping plans, geometry mapper. No SWT, no Hop runtime. |
| `hop-interlis-transforms` | Hop transforms and dialogs (`INTERLIS Input`, `INTERLIS Output`). |
| `assemblies/assemblies-hop-interlis` | Installable plugin ZIP. |

## License

MIT, see [LICENSE](LICENSE).
