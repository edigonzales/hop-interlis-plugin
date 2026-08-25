# Phase 0 – Projektfundament und technische Verifikation

Status: **abgeschlossen** (Stand 2026-08-25)

## Implemented

### Build- und Modulgerüst

- Maven-Multimodul-Projekt:
  - `hop-interlis-core` – INTERLIS-Logik ohne SWT/Hop-Runtime
  - `hop-interlis-transforms` – Hop-Transforms
  - `assemblies/assemblies-hop-interlis` – installierbares Plugin-ZIP
- Maven Wrapper (`./mvnw`, Maven 3.9.9, `only-script`-Distribution)
- `.sdkmanrc` mit Java 21 (21.0.10-tem)
- Zentrale Versionen im Parent-POM:
  - Apache Hop 2.18.1 (verbindliche Baseline; Property `hop.version` im Parent-POM, keine
    weiteren hartkodierten Hop-Versionen im Projekt)
  - iox-ili 1.24.4, ili2c-core 5.6.8, ili2c-tool 5.6.8, iox-api 1.0.3, ehibasics 1.4.1
    (exakt die Kombination, die ili2db 5.5.1 produktiv verwendet; verifiziert über `jars.interlis.ch`)
  - hop-geometry-type 0.1.0-SNAPSHOT und `org.locationtech.jts:jts-core` als `provided`
- GitHub-Actions-CI (verify auf Ubuntu/macOS/Windows, baut Geometry-Plugin mit, prüft Distribution)

### Core-Services (Spikes 1–4)

- `InterlisModelService` / `InterlisModelServiceImpl`:
  - kompiliert lokale `.ili`-Dateien und Modellnamen über Model directories via
    `Ili2c.Main.runCompiler(Configuration, Settings, Ili2cMetaAttrs)` → `TransferDescription`
  - Cache mit deterministischem Key (inkl. `lastModified` lokaler Dateien)
  - Schutz gegen stilles Ignorieren angeforderter Modelle (Language-Version-Mismatch etc.)
  - `listTransferableClasses`, `findClass`, `clearCache`
- `InterlisSchemaExtractor` + Deskriptoren (`InterlisClassDescriptor`,
  `InterlisAttributeDescriptor`, `InterlisRoleDescriptor`, `InterlisStructureDescriptor`,
  `InterlisCardinality`): qualified names, Attribut-Typen/Kardinalitäten, geerbte Attribute,
  Geometrieattribute (Kind + Dimension + ARCS-Fähigkeit), Strukturen, Rollen
- `XtfTransferReader` (iox-ili `ReaderFactory`/`IoxReader`): Eventstrom
  `START_TRANSFER / START_BASKET / OBJECT / END_BASKET / END_TRANSFER` als
  `InterlisObjectEnvelope` (Eventtyp, Model/Topic, BID, Class-Tag, TID/OID, Operation, IomObject);
  Model-Erkennung aus dem XTF-Header
- `InterlisGeometryMapper`: IOM ↔ Hop-Geometry über SQL/MM WKB
  (`Iox2wkb` mit `asCompoundCurve=true`/`asCurvePolygon=true`, Stroke-Toleranz 0.0;
  `Wkb2iox` + `CurveGeometrySupport`); reine Geraden-CompoundCurves/CurvePolygons werden zu
  `LineString`/`Polygon` normalisiert (verlustfrei, keine Kurven betroffen)

### Hop-Plugin-Gerüst (Spike 5)

- Experimenteller Transform `INTERLIS_TEST` (`InterlisTestMeta/Data/InterlisTest`):
  `@Transform(..., classLoaderGroup = "sogeo-geometry")`, Jandex-Index, SVG-Icon,
  erzeugt `IRowMeta` mit `_ili_tid`, `_ili_bid`, `name` und einem echten `ValueMetaGeometry`-Feld,
  läuft programmatisch in einer echten Hop-Pipeline (`LocalPipelineEngine`).
  Wird in Phase 1 durch `INTERLIS Input` abgelöst.

### Packaging und lokale Entwicklung

- Assembly: Transform-Plugin-JAR (mit Jandex-Index) unter `plugins/transforms/interlis/`;
  **alle** weiteren JARs – inklusive `hop-interlis-core` – unter `.../lib/`
  (Hop nimmt pro Plugin-Ordner nur genau ein JAR in den Plugin-Classloader auf);
  Runtime-JARs: iox-ili, ili2c-core/tool, ehibasics, antlr, jaxb-Stack, activation, base64,
  legacy `com.vividsolutions:jts-core` für iox-ili-intern
  – explizit **nicht** gebündelt: `hop-*`, `hop-geometry-type`, `org.locationtech.jts:jts-core`
- `InterlisRuntimeSupport`: merge den `sogeo-geometry`-Classloader-Group zur Laufzeit
  (Hop erstellt den Group-Classloader lazy und merged die JARs eines Plugins erst, wenn dessen
  Klassen über das Plugin-Registry geladen werden; ohne diesen Schritt wirft ein Transform, der
  `ValueMetaGeometry` direkt referenziert, `NoClassDefFoundError`). Der Support lädt die
  Value-Meta-Plugin-Klassen über `ValueMetaFactory`, verifiziert `ValueMetaGeometry` und liefert
  bei fehlendem Geometry-Plugin eine verständliche Fehlermeldung.
- `scripts/check-distribution.py` (u. a. Jandex-Index, Ein-JAR-Regel am Plugin-Root,
  Duplikate, Tests-/Sources-JARs, Legacy-JTS-Isolation)
- `scripts/dev-sync-hop-plugin.sh` / `scripts/dev-install-and-run.sh`
  (Geometry-Plugin bauen → INTERLIS bauen+testen → Distribution prüfen → installieren → Hop GUI neu starten)
- `scripts/check-env.sh`
- `e2e/pipelines/01-interlis-test.hpl` (per Hop-eigener XML-Serialisierung generiert)

## Tests

Alle 34 Tests grün (`./mvnw -B -ntp clean verify` + `check-distribution.py`):

- `InterlisModelServiceTest` (8): lokale Kompilierung, Modell über Directory, Klassenliste,
  `findClass`, Fehlerdiagnosen (unbekanntes Modell, fehlende Datei), Cache-Wiederverwendung/-Key
- `InterlisSchemaExtractorTest` (8): qualified names, Typen/Kardinalitäten, Enumeration, BOOLEAN,
  Geometriekinds + Dimension + ARCS, Vererbung in stabiler Reihenfolge, Strukturen, Rollen,
  Strukturen sind keine transferierbaren Klassen
- `XtfTransferReaderTest` (6): Eventreihenfolge, Model-Erkennung, Object-Tag/TID/BID/Topic,
  Attributwerte, Geometriewerte als IOM-Strukturen (inkl. ARC-Segment), vererbte Attribute + Strukturen
- `InterlisGeometryMapperTest` (8): COORD 2D/3D, POLYLINE gerade → `LineString`,
  POLYLINE mit ARC → `CompoundCurve` mit `CircularString` (Kontrollpunkte exakt),
  **ARC-Roundtrip IOM → Hop → IOM bleibt ARC**, SURFACE → `Polygon`, Null-Handling, Kind-Mismatch
- `InterlisTestPipelineTest` (2): Transform im Hop-Plugin-Registry auffindbar;
  echte Pipeline-Ausführung mit typisierten Geometry-Rows (inkl. Null-Geometry)
- `InterlisTestPluginContractTest` (2): Plugin-ID, Classloader-Group, Icon

Zusätzlich verifiziert mit echter Hop-Distribution: Plugin-ZIPs in frisches
Apache-Hop-2.18.1-Client installiert, `hop-run` führt `e2e/pipelines/01-interlis-test.hpl` aus
(siehe „Manual verification“).

## Manual verification

- `./mvnw -B -ntp clean verify` grün; `python3 scripts/check-distribution.py` grün.
- Frische Hop-2.18.1-Client-Distribution: Geometry-Plugin + INTERLIS-Plugin entpackt,
  `hop-run.sh -r local -f e2e/pipelines/01-interlis-test.hpl` läuft mit Exit-Code 0 durch und
  loggt die drei Test-Rows (inkl. Geometry-Wert `POINT (2600000 1200000)` und Null-Geometry).
  Das verifiziert Plugin-Discovery, Classloader-Group-Merge und echte Pipeline-Ausführung
  in einer realen Hop-Runtime.
- `bash scripts/check-env.sh <HOP_HOME>` zeigt den erwarteten Umgebungsstatus.
- `scripts/dev-sync-hop-plugin.sh` startet Hop GUI mit installiertem Plugin (Desktop-Verifikation
  durch den Entwickler; im Agent-Kontext über `hop-run` abgedeckt).

## Known limitations

- INTERLIS-Modell-Repositories (HTTP) sind noch nicht angebunden; Phase 0 unterstützt lokale
  `.ili`-Dateien und Modellverzeichnisse. Repository-Auflösung folgt in Phase 1.
- `%XTF_DIR`-/`%ILI_DIR`-Platzhalter in Modellpfaden sind noch nicht aufgelöst (Phase 1).
- Enumerationen tragen im Deskriptor vorerst den generischen Typnamen `ENUMERATION`;
  die benannten Enum-Typen kommen mit der Phase-1-Typ-Mapping-Tabelle.
- 3D-Kurvengeometrien (ARC mit Z) können noch nicht geschrieben werden – Limitierung des
  `hop-geometry-type-plugin`-Curve-WKB-Writers („Curve WKB with Z/M ordinates is not supported yet“);
  nicht-kurvige 3D-Geometrien funktionieren. Zu prüfen, wenn 3D-Kurven gebraucht werden.
- `DELETE_OBJECT`-Events existieren im Envelope-Modell, werden aber von iox-ili 1.24.4 beim
  XTF-Lesen nicht als eigene Events geliefert (XTF 2.4-Deletes werden attributbezogen behandelt).
- Der `INTERLIS Test`-Transform ist ein Phase-0-Platzhalter und wird in Phase 1 durch
  `INTERLIS Input` ersetzt.

## Decisions / notes

- **Hop-Baseline 2.18.1 (Java 21):** Die Baseline wurde auf Apache Hop 2.18.1 angehoben.
  Hop-2.18-Artefakte sind Java-21-Bytecode, daher kompiliert das Projekt mit
  `maven.compiler.release=21` und verwendet Java 21 (`21.0.10-tem`). Der Hop-Versions-Pin liegt
  ausschliesslich in der Property `hop.version` des Parent-POM; es gibt keinen zweiten
  hartkodierten Hop-Versionswert im Projekt.
- Modell-Kompilierung läuft über `Ili2c.Main.runCompiler` (ili2c-tool) mit `Settings`
  (`ILI_LANGUAGE_VERSION`, `ILIDIRS`); die Sprachversion wird **nicht** forciert, sondern
  pro Datei auto-detektiert, damit 2.3- und 2.4-Modelle gemischt funktionieren.
- ili2c legt konkrete Klassen und Strukturen beide als `Table` ab; die Unterscheidung erfolgt
  über `Table.isIdentifiable()` (identifizierbar = Klasse).
- INTERLIS-2-Attribute sind ohne `MANDATORY`-Schlüsselwort optional (0..1) – die Deskriptoren
  bilden das so ab.
- Das kanonische IOM-ARC-Segment trägt `C1/C2` (Endpunkt) + `A1/A2` (Zwischenpunkt);
  das XTF-Encoding `<START>/<END>` existiert nicht (Start = Ende des Vorgängersegments).
- Der Model-Header des XTF-Readers verwendet den Tag `iom04.metamodel.ModelEntry` mit Attribut
  `model` (nicht `MODEL`/`NAME`).
- Hop-2.18-Pipelines werden in Tests über `PipelineEngineFactory` + `LocalPipelineRunConfiguration`
  ausgeführt; `Pipeline` ist abstrakt, `LocalPipelineEngine` die konkrete Engine.
- Hop nimmt pro Plugin-Ordner nur genau **ein** JAR (das mit Jandex-Index) in den
  Plugin-Classloader auf; weitere Projekt-JARs gehören nach `lib/`.
- Hop erstellt den `classLoaderGroup`-Classloader lazy und merged die JARs eines Gruppen-Plugins
  erst, wenn dessen Klassen über das Plugin-Registry geladen werden. `InterlisRuntimeSupport`
  stösst diesen Merge explizit an (via `ValueMetaFactory.getValueMetaPluginClasses()`), bevor
  `ValueMetaGeometry`/JTS referenziert werden.

## Next phase

Phase 1 – vorzeigbarer `INTERLIS Input` für XTF mit Model Browser GUI, `getFields()`,
`IomObject -> Object[]`-Mapper, Schema-Preview und `hop-run`-E2E.
