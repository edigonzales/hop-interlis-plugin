# Phase 3 – Strukturen umfassend

Status: **abgeschlossen** (Stand 2026-08-26)

## Implemented

### Arbeitspaket 3.1 – rekursives Single-Structure-Flattening

- Rekursion war im `InterlisRowSchemaBuilder` bereits vorhanden; neu abgesichert und getestet:
  - verschachtelte einwertige Strukturen über beliebig viele Ebenen
    (`Home_Place_Country_Name`), stabiler konfigurierbarer Separator, Kollisionsprüfung
    unverändert wirksam;
  - mehrwertige Strukturen **unter** einer einwertigen Struktur werden nicht expandiert,
    sondern mit Warnung auf `INTERLIS Structure Explode` verwiesen
    (`Home.Place.Phones` im Testmodell);
  - neues Testmodell `HopIli_Structures_V1.ili` (INTERLIS 2.4) mit
    `Address` (Text, COORD-Geometrie, verschachteltes `PostCode`), `Contact`,
    `Country/Place/Home` (verschachtelte einwertige Strukturen), `Person` mit
    `Home {0..1}`, `Addresses : LIST OF Address`, `Contacts : BAG OF Contact` –
    inkl. XTF-2.4-Fixture (vom iox-Writer selbst erzeugt, damit das 2.4-Format
    garantiert stimmt).

### Struktur-Dienste im Core (Hop-frei)

- `InterlisAttributeDescriptor.ordered`: unterscheidet `LIST` (Index semantisch) von
  `BAG` (Index nur technisch) über ili2c `CompositionType.isOrdered()`.
- `InterlisStructureLocator` + `InterlisStructurePlan`: löst einen Dotted-Path
  (`Addresses`, `Home.Place.Phones`) zu einem vorberechneten Plan auf; Kindfelder
  werden mit denselben Regeln projiziert wie Klassenfelder
  (`InterlisRowSchemaBuilder.buildStructureChildFields`), inkl. Flattening
  verschachtelter einwertiger Strukturen und Kollisionsprüfung.
- `InterlisStructureExploder`: `IomObject -> List<ExplodedChild(index, values)>`;
  0..n Kinder pro Parent, LIST-Reihenfolge aus dem Transfer.
- `InterlisStructureCollector`: sammelt Kinder zurück in eine **tiefe Kopie** des
  Carrier-Objekts; ersetzt die Zielstruktur immer (von Filtern entfernte Kinder
  tauchen nicht wieder auf); LIST wird nach Index validiert (Duplikate, Lücken bei
  strict) und sortiert geschrieben; BAG behält Stream-Reihenfolge; fehlende
  einwertige Pfadstrukturen werden auf dem Carrier angelegt.
- `IomFieldReader`/`IomFieldWriter`: gemeinsame Lese-/Schreibmaschinerie für
  Klassen-Mapping und Struktur-Mapping (eine Quelle der Wahrheit).
- `RowToIomMapper.map(carrier, values, plan, options)`: Overlay-Semantik für
  INTERLIS Output – Row-Werte werden auf eine Kopie des Carriers gelegt, bestehende
  einwertige Strukturen werden wiederverwendet (nie dupliziert), mehrwertige
  Strukturen aus dem Carrier bleiben erhalten; abweichende TID erzeugt ein neues
  Objekt mit kopierten Kindern.
- `InterlisStructureProjectionService`: zentrale Modellauflösung für Explode/Collect
  (Meta, Runtime und GUI teilen denselben Pfad).
- `InterlisModelServiceImpl`: Kompilieren ist jetzt über einen statischen Lock
  serialisiert (ili2c nutzt statischen Zustand; parallele Pipeline-Tests bzw.
  parallele Modellkompilierung in Hop liefen sonst in Races).

### Träger-Typ `ValueMetaInterlisObject`

- `@ValueMetaPlugin(id="46837547", classLoaderGroup="sogeo-geometry")` in den
  Transforms: trägt das rohe `IomObject` als technisches Hop-Feld
  (`_ili_source_object`). Deep-Copy via `Iom_jObject(IomObject)`, `getString()` als
  XML nur für Debug/Preview, binäre (De-)Serialisierung über Java-Serialisierung,
  keine verlustbehaftete String-Konvertierung.
- Der in Phase 5 (AP 5.2) spezifizierte Value Type wird damit vorgezogen, weil der
  Carrier bereits in Phase 3 ein echter Hop-Wert sein muss. Phase 5 übernimmt ihn.

### INTERLIS Input – „Keep source object for Structure Explode"

- Meta: `keepSourceObject` (Default false) + `sourceObjectFieldName` (Default
  `_ili_source_object`); `getFields()` hängt den Träger als technisches Feld an;
  Runtime emittiert das Quell-IomObject pro Zeile; GUI-Checkbox + Feldname.

### INTERLIS Structure Explode

- Meta: Model/Class/Structure-Path, `sourceObjectField`, `parentTidField`,
  `parentBidField`/`emitParentBid`, `parentKeyFieldName`, `indexFieldName`,
  `emitIndexForBag`, `selectedChildFields`, `includeParentFields`.
- Output-Schema: `_ili_parent_tid`, `_ili_parent_bid`, `_ili_index` (LIST semantisch,
  BAG technisch), Kindfelder, kopierte Parentfelder.
- Runtime: Streaming mit Pending-Iterator (0..n Zeilen pro Parent); fehlender
  Träger ist ein harter, verständlicher Fehler.
- GUI: modellbewusster Dialog (Model/Class/Structure-Combo, Live-Vorschau des
  Kindzeilen-Schemas), SWT-freier Controller mit Tests.

### INTERLIS Structure Collect

- Meta: `parentInputTransform`, `childInputTransform`, Key-Felder, Model/Class/
  Structure-Path, `sourceObjectField`, `strictOrdering`, `failOnDuplicateIndex`,
  `failOnChildWithoutParent`.
- Runtime: sortierter Streaming-Merge über beide Input-Streams (Hop 2.x:
  beide Ströme via `findInputRowSet`/`getRowFrom`); Parent-Strom sortiert nach
  Parent-Key, Child-Strom nach (Parent-Key, Index); Verletzungen, Duplikate,
  Waisen und fehlende LIST-Indizes sind harte Fehler mit Kontext.
- Ausgabe: Parent-Zeilen mit aktualisiertem Träger („typed parent + updated source
  object"); die Envelope-Ausgabe bleibt Phase 5 vorbehalten (dort existiert
  Row-to-Object/Transfer-Output).
- GUI: modellbewusster Dialog (Stream-Combos, Struktur-Combo, Live-Vorschau),
  SWT-freier Controller mit Tests.

### INTERLIS Output – Träger-Overlay

- Meta: `sourceObjectField` (leer = bisheriges Verhalten); Runtime legt die
  Row-Werte auf den Träger, so dass gesammelte LIST/BAG-Strukturen geschrieben
  werden; fehlender/null Träger ist ein harter Fehler.

### E2E

- `07-structures-roundtrip.hpl`: Input (keepSourceObject) → Structure Explode
  (Addresses) → Structure Collect → Output (`_ili_source_object`). Die Fan-out-Quelle
  hat `distributes=false`, weil Hop 2.18 bei mehreren Output-Hops standardmässig
  Round-Robin verteilt.
- `08-structures-roundtrip-check.hpl`: Roundtrip-Output wieder einlesen, explodieren,
  CSV; `check-e2e-output.py` prüft LIST-Reihenfolge (0,1,2 + p2), Kindgeometrie,
  verschachteltes `PostCode`.
- `run-e2e.sh` führt 07/08 zusätzlich aus.

## Tests

`./mvnw -B -ntp clean verify` grün (204 Tests, Core 110 + Transforms 94). Neu u. a.:

- Core: `InterlisStructureLocatorTest` (9), `InterlisStructureExploderTest` (8),
  `InterlisStructureCollectorTest` (10), `RowToIomMapperTest` +4 Overlay-Fälle,
  `InterlisRowSchemaBuilderTest` +2 (verschachteltes Flattening, Separator),
  `InterlisSchemaExtractorTest`/`InterlisPrimitiveCodecTest` angepasst (`ordered`).
- Transforms: `ValueMetaInterlisObjectTest` (8), Explode-Meta/-Pipeline/
  DialogController/Plugin-Contract (20), Collect-Meta/-Pipeline/DialogController/
  Plugin-Contract (18), Input-getFields mit Träger, E2E-Generator 07/08.
- Pipeline-Tests decken ab: LIST empty/one/many, LIST-Reihenfolge, BAG, Kindgeometrie,
  verschachtelte einwertige Struktur im Kind, 0..1 fehlt, Replace/Remove nach
  Downstream-Filter, Duplikat-Index, Waisen-Kind, fehlender Index, Roundtrip über
  echte XTF-Schreib/Lese-Zyklen.

E2E gegen frische Hop-2.18.1-Installation: alle 6 Pipelines grün
(`E2E outputs OK`, Hop läuft lokal mit Java 25 via `HOP_JAVA_HOME`, da das
GDAL-Plugin der Installation Java 23+ verlangt).

## Decisions / notes

- **Träger = `ValueMetaInterlisObject`**: vorgezogen aus Phase 5 AP 5.2 (der Carrier
  muss schon in Phase 3 ein Hop-Wert sein); Phase 5 übernimmt ihn unverändert.
- **Collect-Ausgabe**: „typed parent + updated `_ili_source_object`". Die
  Envelope-Ausgabe (02-Spec §21 bevorzugt sie für Write) braucht
  Row-to-Object/Transfer-Output und folgt in Phase 5.
- **Fan-out/Hop 2.18**: Eine Quelle mit mehreren Output-Hops verteilt Round-Robin
  (`TransformMeta.distributes`, Default true). Die Demo/E2E-Pipelines setzen
  `distributes=false` (GUI: Transform-Kontext). Dokumentiert im Progress und im
  Demo-Pipeline-Kommentar.
- **ili2c nicht thread-safe**: Modellkompilierung global serialisiert.
- **LIST vs. BAG** kommt aus ili2c `CompositionType.isOrdered()` und wird im
  Descriptor als `ordered` geführt.
- Hop-Besonderheit: ein per `getRowFrom` erschöpfter Info-RowSet wird von Hop aus
  den Input-RowSets entfernt; erneutes Lesen desselben RowSets löst eine interne
  Assertion aus. Der Collect-Runtime trackt die Erschöpfung deshalb selbst
  (`childStreamExhausted`).

## Known limitations

- Der technische Index bei BAG dient nur der deterministischen Verarbeitung und
  wird beim Schreiben ignoriert (Stream-Reihenfolge).
- Struktur-Pfade für Explode/Collect müssen explizit Modellnamen tragen
  (`%DATA` gibt es ohne Transferdatei nicht).
- Index-Semantik bei mehrstufigen Strukturpfaden unterhalb von LIST-Elementen ist
  auf die äusserste Ebene beschränkt (verschachtelte LISTs innerhalb eines
  Struktur-Elements werden mit Warnung übersprungen; zweiter Explode-Schritt nötig).
- Validator-Anbindung (ilivalidator) folgt in Phase 6; der Roundtrip wird über
  eigene Re-Read-Assertions geprüft.

## Next phase

Phase 4 – Assoziationen und Role Join: Assoziationsattribute, Association-Rows,
`INTERLIS Role Join`.
