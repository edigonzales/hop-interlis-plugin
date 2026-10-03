# XTF model migration

Open `migration.ilimap` in Hop to edit the mapping. **Load models** displays both
schemas; select a rule or structure context, then drag source attributes to target
attributes. Select an assignment to inspect its function graph or edit its expression.
**Enumeration table** and **Reference** create the corresponding ilimap constructs.
The DSL tab supports syntax highlighting and editing even while syntax is incomplete.
Graphical edits preserve unrelated source and comments; Undo/Redo applies to both views.

The example migrates two classes and their reference from `MigrationDemo_V1` to
`MigrationDemo_V2`. It renames an attribute, supplies a new mandatory value, translates
an enumeration, trims structure values and copies a curve, XYZ coordinate and nested
structure occurrences. Source and target model definitions share `MigrationDemo.ili`.
The model source fingerprint in the mapping is intentional: changing this file requires
reviewing the mapping and preparing/confirming a new fingerprint.

Run `migrate.hwf` with a distinct, nonexistent `OUTPUT_XTF`:

```bash
"$HOP_HOME/hop-run.sh" -r local -f examples/migration/migrate.hwf \
  -p OUTPUT_XTF=/absolute/path/to/migrated.xtf
```

The workflow action runs the entire mapping as one Java job. Output validation is on;
the original and any existing target are protected. Paths within `.ilimap` are relative
to the mapping file. Hop variables belong in the action's file/model-directory fields;
the `.ilimap` language itself does not expand Hop variables.

**Preview complete XTF** runs and validates the entire selected small sample in a
temporary output. The display shows up to 100 objects; processing is not truncated.
A valid sample does not establish that a different production input is valid.

For a new migration use **Prepare migration**: select models, directories and XTF paths.
The draft contains compatible copies and review comments for unresolved changes or
potential information loss. Check these, supply missing rules, then **Confirm review**.
Execution never infers new mappings. A changed model fingerprint blocks execution.

Current limits: one XTF input and output, local mapping files, one editable collection
level in the graphical view. Deeper explicit mappings and advanced expressions remain
available in the DSL. Concrete structure subtypes require an explicit supported mapping;
they are rejected rather than copied as their base type. Joins, splits and external Hop
subpipelines have no graphical authoring controls yet. The migration engine holds its
indexes and target objects in memory; it does not inherit the spill limits of Hop's
INTERLIS Output/Update transforms. Atomic publication requires filesystem support for
atomic rename (overwrite) or hard links (no overwrite), otherwise the job fails safely.
