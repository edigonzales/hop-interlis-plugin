# Examples

Runnable pipelines for Apache Hop 2.19.0 / Java 21 with the packaged INTERLIS and
Geometry Type plugins. The GeoPackage example additionally requires the compatible
Vector/Raster plugin (`Vector Writer`). The [German handbook](../docs/biblios/user/master.adoc)
explains settings and expected values.

Every pipeline uses `E2E_INPUT_DIR` (absolute model/transfer directory) and
`E2E_OUTPUT_DIR` (existing output directory). The new handbook pipelines declare
these as pipeline parameters; the legacy examples use the same names as Hop
variables. Pass both with `-p` to `hop-run.sh`.

Use a local pipeline run configuration named `local`, or replace `-r local` with
the name of your existing local configuration.

## Current handbook tutorials

The single canonical set of local INTERLIS 2.4 models/transfers is in
[`docs/biblios/user/examples/`](../docs/biblios/user/examples/). These examples
need no remote model repository. Run from the repository root:

```bash
mkdir -p /tmp/interlis-tutorial-output
"$HOP_HOME/hop-run.sh" -r local \
  -f examples/primitive-collections/primitive-collections.hpl \
  -p E2E_INPUT_DIR="$PWD/docs/biblios/user/examples" \
  -p E2E_OUTPUT_DIR=/tmp/interlis-tutorial-output
```

| Pipeline | Fixture pair (`-modell.ili`, `-transfer.xtf`) | Expected output |
|---|---|---|
| [xtf-to-gpkg](xtf-to-gpkg/xtf-to-gpkg.hpl) | `demo` | `doc-gebaeude.gpkg`: two buildings, polygon geometry, SRID 2056; Vector/Raster required |
| [primitive-collections](primitive-collections/primitive-collections.hpl) | `sammlungen` | `doc-sammlungen.xtf`: four typed collections; ordered text/boolean/enum LISTs, numeric BAG `[7,7,9]`, second object with empty collections |
| [child-preserve](child-preserve/child-preserve.hpl) | `erhaltung` | `doc-erhaltung.xtf`: only child `bleibt`, concrete `SpezialKind`, hidden attribute, subtype field and nested ordered details retained |
| [reference-fields](reference-roundtrip/reference-fields.hpl) | `referenzen` | `doc-referenzen-felder.csv`: internal TID z1 with null BID, external TID z2 with BID b2 |
| [reference-roundtrip](reference-roundtrip/reference-roundtrip.hpl) | `referenzen` | `doc-referenzen.xtf`: full EVENTS stream, both baskets and targets retained; full validation before publication |
| [reference-check](reference-roundtrip/reference-check.hpl) | `referenzen` model plus previous output | `doc-referenzen-check.csv`: re-read the **published** output in a separate run |

The reference sequence runs `reference-fields`, then `reference-roundtrip`, then
`reference-check`, waiting for successful completion between runs. XTF writers
publish only at successful pipeline completion. A downstream file reader in the
same pipeline cannot inspect the newly prepared file.

The six earlier handbook tutorials also run directly from their committed files:
[A: read](../e2e/pipelines/33-doc-demo-input.hpl),
[B: flatten](../e2e/pipelines/34-doc-structure-flatten.hpl),
[C: LIST](../e2e/pipelines/35-doc-list-explode-collect.hpl),
[D: role](../e2e/pipelines/36-doc-role-join.hpl),
[E: ARC](../e2e/pipelines/37-doc-arc-roundtrip.hpl),
[F: validation](../e2e/pipelines/38-doc-validation.hpl).
Their exact CSV/XTF results are checked by `scripts/check-doc-examples-output.py`.

The LIST example explicitly uses child carriers, `PRESERVE` and BID plus parent
key. Collect replaces the whole selected collection: filtering removes children.
Renumber remaining LIST children from zero per parent when filtering creates gaps.
The child-preservation example projects only `Name` via the saved
`selectedChildFields` configuration; Explode now exposes this selection through `Fields…`. Collect exposes its selection in `Options…`.

## Existing regression examples

These examples remain available with their original configuration. Their fixtures
are under `hop-interlis-core/src/test/resources/fixtures/models/2.3/` or `2.4/`
and `fixtures/data/2.3/` or `2.4/`, for example
`models/2.3/HopIli_Associations_V1.ili` and
`data/2.3/HopIli_Associations_V1_mapping.xtf`. Copy the model, transfer and required
imports to a common input directory and use the same `-p` invocation above.

| Directory | Workflow |
|---|---|
| `xtf-to-csv/` | One typed class to CSV |
| `xtf-roundtrip/` | Typed write, followed by a separate re-read check |
| `structures/` | Legacy Explode/Collect roundtrip; omitted new options retain REBUILD semantics |
| `associations/` | Flattened association attributes and association rows |
| `validation/` | Validation diagnostics to CSV |
| `advanced-envelope/` | Supported generic EVENTS roundtrip and DELETE preservation |

Install ZIPs directly into `$HOP_HOME` as described in the handbook. The INTERLIS
ZIP is built under `assemblies/assemblies-hop-interlis/target/`.
`scripts/run-e2e.sh` installs the packaged plugins into the supplied disposable Hop
installation and runs all regression and handbook pipelines. Set
`REQUIRE_VECTOR_RASTER_E2E=true` for the complete acceptance suite.


## Prio 3: direct writer and selective updates

The pipelines in [prio3](prio3/) are executable examples of the new runtime
functions. Prepare a common local input directory from the existing small fixtures:

```bash
mkdir -p /tmp/interlis-prio3-input /tmp/interlis-prio3-output
cp hop-interlis-core/src/test/resources/fixtures/models/2.4/*.ili /tmp/interlis-prio3-input/
cp hop-interlis-core/src/test/resources/fixtures/data/2.4/*.xtf /tmp/interlis-prio3-input/
cp e2e/fixtures/p1-3d.xtf /tmp/interlis-prio3-input/
"$HOP_HOME/hop-run.sh" -r local -f examples/prio3/multiclass.hpl \
  -p E2E_INPUT_DIR=/tmp/interlis-prio3-input -p E2E_OUTPUT_DIR=/tmp/interlis-prio3-output
```

Run `object-update.hpl` and `structure-update.hpl` with the same parameters.
`multiclass.hpl` writes Target and Item streams (three Target copies) to one XTF.
`object-update.hpl` changes only Item.Name from Details.Note and preserves XYZ,
collections and header. `structure-update.hpl` changes Children.Name from Hidden
while preserving subtype attributes, nested contents and references. Their outputs
are `example-multiclass.xtf`, `example-update.xtf` and `example-children.xtf`.
These examples enable overwrite for repeatable demonstrations; newly created
writers default to overwrite off. All enable full validation before publication.
# Model migration

[`migration/`](migration/README.md) contains a complete two-class XTF model migration,
its `.ilimap` mapping and an executable Hop workflow. Open the mapping directly in Hop
for graphical editing, syntax-highlighted DSL and a complete sample preview.
