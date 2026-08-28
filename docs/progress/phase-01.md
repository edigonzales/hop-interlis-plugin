# Phase 1 – Vorzeigbarer `INTERLIS Input` für XTF

Status: **abgeschlossen** (Stand 2026-08-26)

## Implemented

### Typ-Schicht (Arbeitspaket 1.1)

- `InterlisValueKind` ersetzt die grobe `InterlisAttributeKind`-Kategorisierung:
  TEXT, MTEXT, NAME, URI, BOOLEAN, INTEGER, DECIMAL, DATE, DATETIME, TIME, ENUM, GEOMETRY,
  STRUCTURE – die einzige Quelle für Hop-Schematypen und Wertkonvertierung.
- `InterlisSchemaExtractor` unterscheidet jetzt präzise:
  - `INTERLIS.NAME`/`INTERLIS.URI`/`INTERLIS.MTEXT` über die TypeAlias-Domain-Namen;
  - BOOLEAN über `Type.isBoolean()` (vordefinierte Enumeration);
  - INTEGER vs. DECIMAL über `PrecisionDecimal.getAccuracy()`;
  - `INTERLIS.XMLDate`/`XMLDateTime`/`XMLTime` → DATE/DATETIME/TIME;
  - Textlänge (`textMaxLength`) und Dezimalstellen (`decimalPlaces`) als Deskriptorfelder.

### Mapping-Plan-Schicht (Arbeitspakete 1.2/1.3)

- `InterlisPropertyPath`, `InterlisFieldSource`, `InterlisFieldPlan`,
  `InterlisRowMappingPlan` (Core, Hop-frei).
- `InterlisRowSchemaBuilder`: baut den Plan deterministisch:
  - Reihenfolge: reservierte Felder (`_ili_tid`, `_ili_bid`, `_ili_class`, `_ili_topic`,
    `_ili_operation`) vor Modell-Properties in stabiler Modellreihenfolge;
  - `0..1`/`1`-Strukturen flatten (depth-first an der Position des Struktur-Attributs,
    Separator konfigurierbar); verschachtelte Strukturen rekursiv;
  - einfache Rollen als `<role>_ref`;
  - BAG/LIST-Strukturen und mehrwertige Rollen werden **nicht still ignoriert**,
    sondern als Plan-Warnings gemeldet (Hinweis auf `INTERLIS Structure Explode`);
  - Namenskollisionen → `InterlisMappingException` mit Feldname und Klasse;
  - `selectedPropertyPaths` begrenzt die Projektion (Struktur-Selektion schliesst
    alle Flatten-Kinder ein).
- `InterlisPrimitiveCodec`: locale-unabhängiges Parse/Format pro ValueKind
  (BOOLEAN strikt, INTEGER→Long, DECIMAL→BigDecimal ohne double-Umweg,
  DATE/DATETIME als ISO ohne Zeitzonen-Überraschungen; dokumentierte Regeln).
- `InterlisObjectToRowMapper` (Interface) + `DefaultInterlisObjectToRowMapper`:
  `map(envelope, plan)` → `Object[]`; pro Feld nur vorberechnete Indizes/Pfade;
  Fehler tragen Feldname, Klasse, TID und BID; optionale fehlende Strukturen/Rollen → null;
  mehrfache Strukturwerte → Fehler.
- `InterlisProjectionService`/`InterlisModelRequest`: gemeinsame Modellauflösung
  (explizite Modelle oder `%DATA`-Erkennung aus dem XTF-Header, `%XTF_DIR`-Auflösung)
  für Runtime, `getFields()`, `check()` und GUI.
- `InterlisModelService.detectModelNames(Path)` (Header-Erkennung).

### Transform (Arbeitspaket 1.4/1.6)

- `INTERLIS_INPUT` (`InterlisInputMeta/Data/InterlisInput`, `classLoaderGroup="sogeo-geometry"`,
  Kategorie Geospatial, SVG-Icon):
  - Meta: `fileName`, `modelNames` (`%DATA`), `modelDirectories`
    (`%XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch`), `className`,
    `includeTid/includeBid/includeClassName/includeTopicName`, `defaultSrid`;
  - `getFields()`: liefert das typisierte `IRowMeta` aus demselben Plan wie die Runtime;
    Design-Time-Proben bleiben bei unresolved Variablen, fehlenden Dateien oder
    Modellfehlern still (Runtime wirft harte Fehler);
  - `check()`: ERROR/WARNING/OK mit präzisen Meldungen (Datei fehlt, Klasse unbekannt, …);
  - Runtime: streaming über `XtfTransferReader`, ein Output-Row pro `processRow()`,
    Objekte anderer Klassen werden übersprungen, `dispose()` schliesst den Reader;
  - Hop-XML-Serialisierung der Meta getestet (Roundtrip).

### GUI (Arbeitspaket 1.5)

- `InterlisInputDialog` (SWT): Transform name, Data file (+Browse), Models, Model dirs,
  Reload, Class-Combo (alle transferierbaren Klassen), Checkboxen für reservierte Felder,
  Default SRID, Statuszeile und Live-Schema-Preview (Feldname | Hop-Typ | INTERLIS-Quelle
  inkl. Plan-Warnings). Probing-Fehler erscheinen als Meldung im Dialog; der Dialog bleibt
  immer bedienbar.
- `InterlisInputDialogController` (SWT-frei, getestet): Probe + Schema-Preview-Formatierung;
  der Dialog rendert nur Widgets.
- Der modale Model-Browser-Baum (03-gui §3/§5) ist als GUI-Ausbau der nächsten Iteration
  vorgesehen; die Klassenauswahl erfolgt über die vollständige Klassen-Combo plus
  Schema-Preview.

### E2E

- `scripts/run-e2e.sh` + `scripts/check-e2e-output.py`: baut/installiert die Plugin-ZIPs
  (INTERLIS + Geometry, optional GeoTools), führt die e2e-Pipelines per `hop-run` aus
  (`-p E2E_INPUT_DIR=... -p E2E_OUTPUT_DIR=...`) und prüft die Ergebnisse automatisiert.
- Eingecheckte Pipelines (XML, variable Platzhalter):
  - `e2e/pipelines/02-interlis-input-to-csv.hpl` (Geometry-XTF → CSV, inkl. ARC)
  - `e2e/pipelines/03-interlis-input-structures.hpl` (Struktur/Rolle/Vererbung → CSV)
  - `e2e/pipelines/04-interlis-to-gpkg.hpl` (Arc-XTF → GeoTools Vector Writer → GeoPackage)
- Regenerierung der generierten Pipelines: `E2ePipelineGeneratorTest` mit
  `-De2e.parameterized=true -De2e.outputDir=e2e/pipelines`.

## Tests

`./mvnw -B -ntp clean verify` grün (89 Tests, Core 60 + Transforms 29), u. a.:

- `InterlisPrimitiveCodecTest` (12): alle Primitiven, striktes BOOLEAN, Long ohne Trunkierung,
  BigDecimal ohne Präzisionsverlust, Datum/Zeit, Null, Roundtrip, falsche Hop-Typen
- `InterlisRowSchemaBuilderTest` (9): stabile Schemata, reservierte Felder konfigurierbar,
  Flattening an Property-Position, Rollen-`_ref`, selektierte Pfade,
  Kollisionen, BAG/LIST-Warnung, mehrwertige Rolle (separate Assoziation)
- `InterlisObjectToRowMapperTest` (9): TID/BID, Primitive/Vererbung/Rolle, Geometrie,
  optionale Struktur/Rolle → null, mehrere Geometrien, invalide Werte mit TID/BID-Kontext,
  mehrfache Strukturwerte → Fehler, Dezimalpräzision
- `HopRowSchemaFactoryTest` (3): IRowMeta-Typen inkl. ValueMetaGeometry, Präzision, Flattening
- `InterlisInputPipelineTest` (5): echte Hop-Pipelines – typisierte Rows mit mehreren
  Geometrien, **ARC bleibt CircularString** (exakte Kontrollpunkte), Modell-Erkennung aus XTF,
  Strukturen/Rollen/Vererbung, Klassen-Filter
- `InterlisInputMetaTest` (7): getFields typisiert; unresolved Variablen/fehlende Datei bleiben
  still; check ERROR/OK/unbekannte Klasse; Hop-XML-Serialisierungs-Roundtrip
- `InterlisInputDialogControllerTest` (6): Probe-Ergebnisse, freundliche Fehlermeldungen,
  Schema-Preview mit Warnings
- `InterlisInputPluginContractTest` (2): Plugin-ID, Classloader-Group, Icon
- `E2ePipelineGeneratorTest` (2): Pipeline-Generierung (parameterisiert/absolut)

E2E (echte Hop-2.18.1-Distribution, `scripts/run-e2e.sh`):

```text
02: 2 Geometry-Rows; o2-Axis als COMPOUNDCURVE (CIRCULARSTRING mit exakten
    Kontrollpunkten 2600000/1200000 – 2600050/1200050 – 2600100/1200000)
03: Flattening (Main Street/10), Rolle (m1), Vererbung (inherited note)
04: GeoPackage mit 2 Kurven-Features; Axis-Spalte als COMPOUNDCURVE registriert
    (inkl. gpkg_geom_COMPOUNDCURVE-Extension) – beweist zugleich den gemeinsamen
    sogeo-geometry-Classloader-Pfad INTERLIS → GeoTools
```

## Manual verification

- `scripts/run-e2e.sh <HOP_HOME>` gegen frische Apache-Hop-2.18.1-Distribution: alle
  drei Pipelines grün, Assertions grün (inkl. GeoPackage via hop-geotools-plugin).
- `scripts/dev-sync-hop-plugin.sh` installiert das Plugin und startet Hop GUI
  (Desktop-Verifikation durch den Entwickler; der Dialog wird in Hop GUI aus der
  Geospatial-Kategorie geöffnet).

## Known limitations

- HTTP(S)-Model-Repositories sind angebunden und werden über `ilimodels.xml`
  sowie den lokalen Repository-Cache aufgelöst; lokale Verzeichnisse haben
  weiterhin Vorrang. Der gemeinsame Default für `modelDirectories` ist
  `%XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch`. `%XTF_DIR`
  bleibt ein Platzhalter für das XTF-Elternverzeichnis, keine Einschränkung
  auf dieses eine Verzeichnis.
- Die Modell-only-Probe befüllt das Klassen-Dropdown bereits bei leerem
  Klassenfeld. Die Schema Preview entsteht erst nach der Klassenauswahl.
- Der modale Model-Browser-Baum fehlt noch; Klassenauswahl via Combo + Schema-Preview.
- `DATE`/`DATETIME` werden ohne Zeitzonen-Offset interpretiert (Dokumentation im Codec);
  `XMLTime` wird als String geführt.
- `BAG/LIST`-Strukturen und mehrwertige Rollen werden gemeldet, aber noch nicht
  explodiert/gesammelt (Phase 3/4).
- Text file output paddet Strings auf die Modell-Textlänge (Hop-Standardverhalten);
  der E2E-Checker strippt die Werte vor dem Vergleich.

## Decisions / notes

- Core bleibt Hop-frei: der Mapping-Plan kennt keine `IValueMeta`; `HopRowSchemaFactory`
  (transforms) und der Core-Mapper leiten ihre Typen aus **denselben** Deskriptoren ab –
  Schema und Runtime können so nicht divergieren.
- ili2c exponiert nur single-valued Rollen leichter Assoziationen als Klassen-Rollen;
  `0..*`-Rollen sind separate Link-Objekte (Phase 4) – der Builder meldet das als Warnung,
  falls solche Rollen doch im Schema auftauchen.
- `hop-run -p KEY=VALUE` löst Variablen in Transform-Konfigurationen auf; die E2E-Pipelines
  sind deshalb als statische XML-Dateien mit `${E2E_INPUT_DIR}`/`${E2E_OUTPUT_DIR}`
  eingecheckt und brauchen keine Laufzeit-Generierung.
- Straight-only `CURVEPOLYGON`/`COMPOUNDCURVE` (gerade Ringe/Segmente zählen nicht als
  Kurve) werden zu `Polygon`/`LineString` normalisiert – inkl. verschärftem Regressionstest.

## Next phase

Phase 2 – `INTERLIS Output` + vollständiger Typed Roundtrip (XTF → INTERLIS Input →
INTERLIS Output → XTF, ilivalidator-konform).
