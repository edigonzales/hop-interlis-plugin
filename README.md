# hop-interlis-plugin

An Apache Hop plugin for reading, transforming, validating and writing INTERLIS transfer data (XTF).

The plugin projects INTERLIS classes onto stable, typed Hop rows, keeps circular arcs intact through a
SQL/MM WKB bridge and uses the shared Hop geometry value type from
[`hop-geometry-type-plugin`](https://github.com/edigonzales/hop-geometry-type-plugin).

The authoritative architecture and implementation specification lives in [`docs/spec/`](docs/spec/).

## Status

**Phase 6** (validation, enumerations, transfer control) is implemented:

- `INTERLIS Validate` validates an XTF file with the iox-ili streaming
  validator and emits one error row per finding (13-field schema:
  severity, message, source file/line, model/topic/class/TID context,
  attribute path, constraint name); options for config file, multiplicity
  checks, max errors, stop-on-first-error, severity filter and
  fail-after-completion;
- `INTERLIS Enumerations` lists a model's enumeration values (incl.
  sub-enumeration hierarchy) as lookup rows;
- basket metadata (consistency, kind, start/end state) survives the generic
  envelope roundtrip (four new envelope fields, writer support with clear
  errors for invalid UPDATE baskets);
- DELETE/UPDATE operations work in the typed workflow: `INTERLIS Input`
  exposes `_ili_operation`, `INTERLIS Output` writes it back (verified for
  DELETE through the full typed roundtrip).

**Phase 5** (advanced envelope and generic transfer transforms) is implemented:

- `INTERLIS Transfer Input` streams the complete XTF event stream as canonical
  envelope rows with a constant schema (`_ili_event_type`, `_ili_model`,
  `_ili_topic`, `_ili_bid`, `_ili_class`, `_ili_tid`, `_ili_operation`,
  `_ili_object`, `_ili_line`, `_ili_column`) in `OBJECTS` or lossless `EVENTS`
  mode – any number of classes travel in one Hop stream;
- `INTERLIS Object to Row` projects the `_ili_object` payload onto typed class
  rows (incl. flattened association attributes) and `INTERLIS Row to Object` maps
  typed rows back to envelope rows (incl. regenerated association link objects),
  so several classes can be merged into one stream;
- `INTERLIS Transfer Output` writes envelope rows back as XTF – object mode
  derives transfer/basket events (basket grouping by `_ili_bid`), event mode
  writes the explicit event sequence; INSERT/UPDATE/DELETE operations are
  preserved (`ili:operation`, verified for DELETE through the full roundtrip);
- the lossless generic roundtrip `Transfer Input (EVENTS) → Transfer Output
  (EVENTS)` is verified in unit, pipeline and `hop-run` E2E tests (mixed classes,
  baskets preserved);
- the `InterlisObject` Hop value type now also serves binary consumers
  (`getBinaryString()`), and the canonical envelope layout lives centrally in
  `InterlisEnvelopeRowLayout`.

**Phase 4** (associations and role join) is implemented:

- associations are first-class transfer viewables: `m:n`/`n`-ary/attributed
  associations project onto their own typed rows (`<role>_ref`, `<role>_ref_bid`,
  `<role>_order_pos` for ORDERED roles, association attributes) and round-trip
  through `INTERLIS Input`/`INTERLIS Output` (verified in unit, pipeline and
  `hop-run` E2E tests, incl. ORDERED order positions);
- attributes of uniquely embeddable attributed associations are flattened onto
  class rows (`<role>_ref`, `<role>_<attribute>`), resolved from the association
  link objects per basket, and written back as regenerated link objects;
- `INTERLIS Role Join` joins a role's target class fields onto the main stream
  (model-driven key/field selection, in-memory lookup with limit, hard errors for
  missing mandatory references and duplicate lookup TIDs, model-aware dialog);
- reference roles support external basket references (`<role>_ref_bid`) on read.

**Phase 3** (structures end to end) is implemented:

- `INTERLIS Structure Explode` transform explodes one `LIST`/`BAG OF` structure
  attribute (also below single structures, e.g. `Home.Place.Phones`) into child rows
  (`_ili_parent_tid`, `_ili_parent_bid`, `_ili_index`, child fields incl. geometry and
  flattened nested structures);
- `INTERLIS Structure Collect` merges a sorted child stream back into the parent's
  source object (streaming merge, strict LIST index checks, replace semantics) and
  `INTERLIS Output` overlays the row onto the updated carrier, so the full
  `LIST`/`BAG` roundtrip `XTF → Input → Explode → Collect → Output → XTF` works –
  **LIST order, child geometries and nested structures survive** (verified in unit,
  pipeline and `hop-run` E2E tests);
- `INTERLIS Input` keeps the raw source object on demand (`Keep source object for
  Structure Explode`, technical `_ili_source_object` field of the new
  `InterlisObject` Hop value type); model-aware dialogs for Explode and Collect;
- recursive flattening of nested single structures (`Home_Place_Country_Name`)
  with warnings pointing multi-valued structures to Structure Explode.

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

See [`docs/progress/phase-06.md`](docs/progress/phase-06.md),
[`docs/progress/phase-05.md`](docs/progress/phase-05.md),
[`docs/progress/phase-04.md`](docs/progress/phase-04.md),
[`docs/progress/phase-03.md`](docs/progress/phase-03.md),
[`docs/progress/phase-02.md`](docs/progress/phase-02.md),
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
| `hop-interlis-core` | Model compilation, schema descriptors (classes, structures, associations), enumeration extraction, transfer reader/writer, canonical envelope + validation row layouts, basket metadata, mapping plans, structure plans/explode/collect, geometry mapper. No SWT, no Hop runtime. |
| `hop-interlis-transforms` | Hop transforms and dialogs (`INTERLIS Input`, `INTERLIS Output`, `INTERLIS Structure Explode`, `INTERLIS Structure Collect`, `INTERLIS Role Join`, `INTERLIS Transfer Input`, `INTERLIS Object to Row`, `INTERLIS Row to Object`, `INTERLIS Transfer Output`, `INTERLIS Validate`, `INTERLIS Enumerations`), `InterlisObject` value type. |
| `assemblies/assemblies-hop-interlis` | Installable plugin ZIP. |

## License

MIT, see [LICENSE](LICENSE).
