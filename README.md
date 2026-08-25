# hop-interlis-plugin

An Apache Hop plugin for reading, transforming, validating and writing INTERLIS transfer data (XTF).

The plugin projects INTERLIS classes onto stable, typed Hop rows, keeps circular arcs intact through a
SQL/MM WKB bridge and uses the shared Hop geometry value type from
[`hop-geometry-type-plugin`](https://github.com/edigonzales/hop-geometry-type-plugin).

The authoritative architecture and implementation specification lives in [`docs/spec/`](docs/spec/).

## Status

**Phase 0** (project foundation and technical verification) is implemented:

- multi-module Maven build (Java 21, Apache Hop 2.18.1, iox-ili 1.24.4, ili2c 5.6.8);
- INTERLIS model compilation and schema extraction (`TransferDescription` → descriptors);
- streaming XTF reader on top of iox-ili;
- INTERLIS ↔ Hop geometry bridge via SQL/MM WKB with verified **ARC roundtrip**
  (`IomObject` → Hop geometry → `IomObject`, no implicit linearization);
- plugin packaging as installable ZIP with a distribution checker;
- experimental `INTERLIS Test` transform registered in the `sogeo-geometry` classloader group,
  executed in real Hop pipelines;
- one-command local development workflow.

See [`docs/progress/phase-00.md`](docs/progress/phase-00.md) for details and known limitations.

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

### Requirements

- Java 21 (a local `.sdkmanrc` pins the SDKMAN identifier used for development; Apache Hop 2.18 requires Java 21)
- Maven 3.x or the included Maven wrapper
- an Apache Hop 2.18.x installation for local testing (`HOP_HOME`)
- a checkout of `hop-geometry-type-plugin` next to this repository

## Modules

| Module | Purpose |
|---|---|
| `hop-interlis-core` | Model compilation, schema descriptors, transfer reader, geometry mapper. No SWT, no Hop runtime. |
| `hop-interlis-transforms` | Hop transforms (experimental `INTERLIS Test` transform in Phase 0). |
| `assemblies/assemblies-hop-interlis` | Installable plugin ZIP. |

## License

MIT, see [LICENSE](LICENSE).
