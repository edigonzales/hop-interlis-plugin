# AGENTS.md

## Purpose

This repository contains `hop-interlis-plugin`, an Apache Hop plugin for reading, transforming, validating and writing INTERLIS transfer data.

The authoritative implementation specification lives in `docs/spec/` (or, while bootstrapping the repository, in this specification directory):

- `README.md`
- `00-overview.md`
- `01-architecture-data-model.md`
- `02-java-hop-implementation.md`
- `03-gui-ux.md`
- `04-testing-e2e.md`
- `05-roadmap-phases.md`
- `06-development-deployment.md`

Read the relevant specification documents before making architectural changes. Do not replace the architecture described there without a concrete technical reason and an accompanying specification update.

## INTERLIS library versions (pinned)

The INTERLIS libraries form a matching pair and are pinned in the parent POM
(`hop-interlis-plugin/pom.xml`); do not change them without an explicit request:

```text
ili2c     5.6.8
iox-ili   1.24.4
iox-api   1.0.3
ehibasics 1.4.1
```

Note: not every ili2c version has a matching `ili2c-tool` release (e.g. 5.6.5
exists only as SNAPSHOT), and newer/older iox-ili versions contain known XTF 2.4
reader bugs (external basket references on standalone association roles duplicate
their REF member and crash the 2.4 writer). Keep the pair as-is.

## Related repositories

Important implementation references:

- `https://github.com/edigonzales/hop-geometry-type-plugin`
- `https://github.com/edigonzales/hop-geotools-plugin`
- `https://github.com/claeis/iox-ili`
- `https://github.com/claeis/ili2fme`
- `https://github.com/apache/hop`

When checked out locally, related repositories are normally expected next to this repository, for example:

```text
../hop-interlis-plugin
../hop-geometry-type-plugin
../hop-geotools-plugin
```

Prefer inspecting actual source code over guessing Apache Hop, ili2c or iox-ili APIs.

## Java environment

This is a Java/Maven project.

The required Java major version is defined by the Maven build. Do not silently use another major version.

On the primary macOS development environment, SDKMAN is used and installed JDKs are available below:

```text
$HOME/.sdkman/candidates/java
```

Do not scan the whole filesystem to find a JDK. First use SDKMAN:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk current java
sdk list java
```

If a project `.sdkmanrc` exists, use:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
java -version
```

If the configured JDK is unavailable, inspect only:

```bash
ls -1 "$HOME/.sdkman/candidates/java"
```

and select a JDK matching the Maven compiler/enforcer configuration.

Never hard-code `/Users/stefan/...` paths into source code, Maven configuration or committed scripts. Use `$HOME`, environment variables or configurable parameters.

## Maven

Always prefer the Maven Wrapper when present:

```bash
./mvnw
```

rather than a globally installed `mvn`.

Standard full verification:

```bash
./mvnw -B -ntp clean verify
```

If the Maven Wrapper has not yet been added during the bootstrap phase, use the installed Maven temporarily, then add the wrapper as part of project setup.

Do not skip tests with `-DskipTests`, `-Dmaven.test.skip=true` or equivalent unless explicitly requested.

For iterative development, focused module tests are fine, but the complete `verify` build must pass before a task or roadmap phase is declared complete.

## Development workflow

The intended local feedback loop is:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

The script should:

1. pick a suitable JDK (see below);
2. locate/build `hop-geometry-type-plugin` if required;
3. build and test `hop-interlis-plugin`;
4. verify the produced distribution;
5. install the plugin into Apache Hop;
6. restart Hop GUI;
7. print the Hop startup log path.

Prefer improving this script instead of documenting additional manual copy/install procedures.

`HOP_HOME` must remain configurable. Do not commit a developer-specific Apache Hop installation path.

JDK selection (`scripts/lib-java.sh`, shared with `scripts/run-e2e.sh`): the
build requires Java >= 21 (`maven.compiler.release`). The scripts use
`HOP_JAVA_HOME` or `JAVA_HOME` when they provide a usable JDK, otherwise they
scan `$HOME/.sdkman/candidates/java` and prefer Temurin (`*-tem`) before the
highest version; unsuitable candidates (e.g. Java 8, 11, 17) and the SDKMAN
`current` symlink are ignored, and the scripts fail with an actionable message
when no suitable JDK exists. Do not replace this with hard-coded JDK paths.

## Architecture invariants

### Hop rows

Normal users work with typed Hop rows.

Do not expose `IomObject` as the normal user-facing representation.

Do not create a giant sparse row schema containing fields from unrelated INTERLIS classes.

### Schema analysis

INTERLIS model analysis must be centralized.

The same schema/mapping representation must drive:

- GUI schema preview;
- `getFields(...)`;
- runtime `IomObject -> Object[]` mapping;
- inverse row-to-INTERLIS mapping where applicable.

Do not independently reimplement model interpretation in SWT UI, transform metadata and transform runtime.

The intended architecture is:

```text
INTERLIS model
      |
      v
InterlisClassSchema
      |
      v
InterlisRowMappingPlan
      |
      +--------> IRowMeta
      |
      +--------> IomObject -> Object[]
      |
      +--------> GUI schema preview
```

### Runtime mapping

INTERLIS model introspection belongs in initialization/design time.

Do not traverse `TransferDescription` repeatedly for every row. Runtime mapping must use a precomputed mapping plan with known target indexes and converters.

### Geometry

Use `hop-geometry-type-plugin` as the shared Hop geometry representation.

The INTERLIS plugin must not introduce a second incompatible Geometry value type.

Multiple INTERLIS geometry attributes map to multiple Hop `Geometry` fields.

Do not add GeoTools merely to convert INTERLIS geometries.

Preserve curve geometries whenever the format and target representation allow it. Never silently linearize an INTERLIS `ARC`.

The preferred INTERLIS boundary conversion is the SQL/MM-WKB path described in the specification, using iox-ili `Iox2wkb` / `Wkb2iox` together with the curve-capable Hop Geometry value type.

### Structures and associations

Follow the mapping rules from the specification:

- scalar `0..1` / `1` structures: flatten by default;
- `BAG/LIST OF` structures: child rows via Structure Explode/Collect;
- preserve `LIST` ordering;
- simple roles: reference fields;
- complex/m:n associations: association rows;
- hide XTF embedded-link encoding details from normal users.

### Thread safety of the INTERLIS libraries

ili2c, iox-ili and ehibasics are single-threaded by design (built for CLI tools
such as ili2c, ilivalidator and ili2db); they are not thread-safe.

Rules:

- compile models only under the global `MODEL_LOCK` in `InterlisModelServiceImpl`
  (ili2c keeps static compiler state);
- treat a compiled `TransferDescription` as immutable: only read it, never
  mutate it; it may be shared across threads (verified: model getters return
  fresh copies or are pure reads);
- never share `IoxReader` / `IoxWriter` / validator instances across threads,
  transform copies or transforms;
- keep file-based transforms single-copy (parallel copies fail with an
  actionable error, see the threading table in the specification);
- global initialization goes through `InterlisRuntimeSupport` (idempotent).

### UI

GUI quality is part of the feature, not optional polish.

The SWT UI consumes model/schema services. It must not contain core INTERLIS model interpretation logic.

Schema probing failures must not make a transform dialog impossible to open. Show actionable diagnostics for unresolved variables, unavailable model repositories, invalid transfer files/models and unavailable schema previews.

## Apache Hop implementation

Follow the patterns used by `hop-geotools-plugin` unless there is a technical reason not to.

Typical transform classes are:

```text
Foo
FooMeta
FooData
FooDialog
```

Use the actual Apache Hop APIs from the configured Hop version. If uncertain, inspect Apache Hop source or an existing plugin compiled against the same version.

Keep these responsibilities separate:

- INTERLIS model/schema services;
- mapping plans and converters;
- INTERLIS transfer I/O;
- Hop transform runtime;
- SWT UI.

Avoid God classes and hidden global mutable state.

## Testing

Tests are part of every change.

Use at least:

- JUnit 5;
- AssertJ;
- Apache Hop pipeline/plugin tests where appropriate;
- real `hop-run` E2E tests for important vertical paths.

For mapping behavior, cover at least:

- normal value;
- null/optional value;
- invalid value;
- roundtrip where meaningful.

INTERLIS coverage should progressively include:

- primitive types;
- enumerations;
- inheritance;
- multiple geometry attributes;
- 2D and 3D geometry where applicable;
- `ARC` / curve geometry;
- structures;
- `BAG/LIST`;
- references;
- associations;
- basket semantics;
- multiple baskets;
- validation failures.

Prefer small deterministic models and transfer files committed under `src/test/resources`.

Tests must not depend on a remote INTERLIS model repository unless they explicitly test repository access. Do not replace deterministic local tests with network-dependent tests.

## E2E

Important user-visible functionality requires E2E tests using the packaged plugin, not only classes from the Maven test classpath.

The important chain is:

```text
build plugin
    -> install into isolated Hop
    -> execute .hpl with hop-run
    -> inspect output
```

At least the primary INTERLIS Input and Output paths must eventually have packaged-plugin E2E coverage.

## Test data

Keep committed test data minimal and purpose-built.

Prefer synthetic INTERLIS models that exercise one concept clearly. Large official datasets may be used to derive minimized fixtures, but do not commit large datasets merely for convenience.

A regression test should ideally contain the smallest model and transfer that reproduces the behavior.

## Error handling

Fail with actionable error messages and include relevant context such as model, topic, class, attribute, TID and basket ID.

Do not catch broad exceptions merely to continue with corrupted or semantically incorrect data.

Design-time schema probing is an exception: failures there should be reported without breaking the entire transform dialog.

## Logging

Use Apache Hop logging.

Do not use:

```java
System.out.println(...)
System.err.println(...)
```

in production code.

Avoid row-by-row logging at normal log levels.

## Code changes

Keep changes focused on the requested roadmap phase or task.

Do not opportunistically implement later phases or perform unrelated refactorings merely because nearby code could be cleaner.

When changing architecture described by `docs/spec`, update the relevant specification document in the same change.

Before adding a dependency:

1. check whether Apache Hop, iox-ili, ili2c or `hop-geometry-type-plugin` already provides the functionality;
2. avoid duplicate JTS implementations or incompatible geometry classes;
3. consider the Hop plugin classloader boundary;
4. document why the dependency is needed.

Do not introduce GeoTools as a transitive dependency of the INTERLIS core unless an explicit architectural decision requires it.

## Completion criteria

Before reporting a coding task as complete:

1. inspect the resulting diff;
2. run focused tests for the change;
3. run `./mvnw -B -ntp clean verify` (or the temporary Maven equivalent only during bootstrap);
4. run applicable E2E tests;
5. verify that generated build artifacts and local configuration were not accidentally tracked;
6. summarize implemented changes, tests executed, remaining limitations and the next roadmap phase if relevant.

Never claim that tests passed unless they were actually executed.
