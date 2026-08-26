# Phase 2 – `INTERLIS Output` und vollständiger Typed Roundtrip

Status: **abgeschlossen** (Stand 2026-08-26)

## Implemented

### Writer-Schicht (Arbeitspaket 2.2)

- `InterlisTransferWriter` (Interface): `startTransfer/startBasket/writeObject/endBasket/
  endTransfer/close` mit erzwungener Event-Reihenfolge.
- `XtfTransferWriter` (iox-ili `XtfWriter`): schreibt den XTF-Header aus der
  `TransferDescription` (Modellname/-version aus dem Metamodell), unterstützt mehrere
  Baskets pro Transfer; deterministisches `close()` (idempotent).
- `XtfTransferReader.setModel(...)`: der XTF-2.4-Reader braucht das Modell zur
  Topic-Auflösung; der Reader signalisiert das Transferende bei 2.4 nicht mit `null`,
  sondern mit einer Exception – beides wird jetzt sauber behandelt.

### Inverse Mapping-Schicht (Arbeitspaket 2.1)

- `RowToIomMapper` (Core, Hop-frei): mappt Plan-geordnete Werte-Arrays → `IomObject`:
  - Objekt-Tag + TID aus dem projizierten `OBJECT_ID`-Feld (fehlende TID → Fehler);
  - Primitive im INTERLIS-Lexik (BigDecimal ohne Präzisionsverlust);
  - Geometrien über die SQL/MM-WKB-Brücke (ARC bleibt ARC, per Test bewiesen);
  - geflattete Strukturen werden **genau einmal** rekonstruiert, sobald ein Kind gesetzt ist;
    komplett leere optionale Strukturen werden nicht erzeugt; leere mandatory Strukturen
    sind im Strict-Mode ein Fehler;
  - Rollen als `REF`-Objekte (`<role REF="oid"/>`);
  - Strict-Mode (Default) lehnt fehlende mandatory Werte ab; Lenient-Mode lässt sie
    undefined (Validator meldet sie später);
  - falsche Hop-Typen → Fehler mit Feldname; fehlende TID → Fehler mit Klassennamen.
- `InterlisRowBindings` (transforms): bindet die eingehende `IRowMeta` einmalig an die
  Plan-Feldreihenfolge; fehlende Eingabefelder werden sofort gemeldet.

### Transform (Arbeitspaket 2.3)

- `INTERLIS_OUTPUT` (`InterlisOutputMeta/Data/InterlisOutput`,
  `classLoaderGroup="sogeo-geometry"`, Geospatial-Kategorie, Icon):
  - Meta: `fileName`, `modelNames` (explizit; `%DATA` wird mit klarer Meldung abgelehnt),
    `modelDirectories`, `className`, `objectIdField` (Default `_ili_tid`),
    `basketIdField` (Default `_ili_bid`, leer = fester BID), `basketId` (Default `b1`),
    `overwrite` (Default: bestehende Datei → Fehler);
  - `getFields()`: Schema bleibt unverändert (Rows fliessen durch);
  - Runtime: streaming – pro Row Basket-Wechsel erkennen (Input muss nach BID gruppiert
    sein), Werte binden, `RowToIomMapper`, `writeObject`; am Ende Basket/Transfer schliessen;
    `dispose()` ist idempotent;
  - `check()`: ERROR/WARNING/OK (Datei, Klasse, TID-Feld, Modell auflösbar, unbekannte Klasse).

### GUI (Arbeitspaket 2.3)

- `InterlisOutputDialog`: XTF-Datei (+Overwrite), Models, Model dirs, Reload,
  Class-Combo, Object-ID-Feld, BID-Feld/Default-BID, Mapping-Grid
  (INTERLIS property | Hop field | Typ | Status, auto-map by name) und Statuszeile;
  Probing-Fehler bleiben Meldungen im Dialog.
- `InterlisOutputDialogController` (SWT-frei, getestet): Probe + Mapping-Grid-Inhalte.
- `InterlisProbeResult` ist jetzt ein gemeinsamer Record des transforms-Moduls
  (von Input- und Output-Controller genutzt).

### E2E

- `e2e/pipelines/05-xtf-roundtrip.hpl`: INTERLIS Input (Geometry-XTF) → INTERLIS Output
  (`roundtrip.xtf`).
- `e2e/pipelines/06-roundtrip-check.hpl`: INTERLIS Input auf `roundtrip.xtf` → CSV.
- `scripts/run-e2e.sh` + `scripts/check-e2e-output.py` prüfen den Roundtrip:
  TIDs/BIDs/Namen, Point/Polygon und **der ARC ist nach dem XTF-Write/Read-Zyklus
  weiterhin ein CIRCULARSTRING** mit exakten Kontrollpunkten.

## Tests

`./mvnw -B -ntp clean verify` grün (124 Tests, Core 76 + Transforms 48), neu u. a.:

- `RowToIomMapperTest` (12): Klassentag/TID, alle Primitiven im INTERLIS-Lexik,
  Geometrie (inkl. ARC ohne Linearisation mit exakten A1/A2/C1/C2), Struktur-Rekonstruktion
  (genau einmal), leere optionale Struktur nicht erzeugt, Rolle als REF, null optionale Rolle,
  Strict-/Lenient-Mode, falscher Hop-Typ mit Feldname, fehlende TID
- `XtfTransferWriterTest` (4): Write/Read-Roundtrip mit gleichen Werten (inkl. 2 Baskets),
  Header-Modelle, Event-Reihenfolge erzwungen, ARC-Write/Read-Zyklus
- `InterlisOutputPipelineTest` (3): echte Hop-Pipelines – Input→Output→Re-Read mit
  semantischem Vergleich (inkl. ARC), Struktur/Rolle/Vererbung-Roundtrip,
  bestehende Datei ohne overwrite → Fehler
- `InterlisOutputMetaTest` (8): getFields passthrough, tryProject (%DATA-Ablehnung,
  unresolved Variablen), check ERROR/OK/unbekannte Klasse, Hop-XML-Roundtrip
- `InterlisOutputDialogControllerTest` (4): Probe, Mapping-Grid
  (technische Felder/Attribute/Rollen mit Auto-Map-Status)
- `InterlisOutputPluginContractTest` (2): Plugin-ID, Classloader-Group, Icon
- `E2ePipelineGeneratorTest`: erzeugt zusätzlich 05/06 (parameterisiert)

E2E (frische Apache-Hop-2.18.1-Distribution):

```text
05 + 06: XTF → INTERLIS Input → INTERLIS Output → roundtrip.xtf →
         INTERLIS Input → CSV; ARC bleibt CIRCULARSTRING (2600000/1200000 –
         2600050/1200050 – 2600100/1200000)
02/03/04 weiterhin grün (Input-CSV, Strukturen, GeoPackage mit COMPOUNDCURVE)
```

## Manual verification

- `bash scripts/run-e2e.sh <HOP_HOME>` gegen frische Hop-2.18.1-Distribution:
  alle fünf Pipelines + Assertions grün.
- Der Roundtrip-Output (`roundtrip.xtf`) wird ausserdem von den Hop-Transforms selbst
  wieder gelesen (06); die Validator-Anbindung (ilivalidator-Pfad) folgt in Phase 6.

## Known limitations

- `INTERLIS Output` verlangt explizite Modellnamen (`%DATA` nicht möglich, klare Meldung).
- Input muss nach BID gruppiert sein; ein später wiederkehrender BID schlägt fehl
  (Hinweis im Dialog/Log).
- Date/DateTime werden ohne Zeitzonen-Offset interpretiert (wie im Codec dokumentiert).
- Numerische Geometrie-Lexik wird von iox-ili normalisiert (`2600050.000` → `2600050.0`);
  semantisch identisch, Tests vergleichen numerisch.
- `ORDERED`-Rollen/Assoziationsattribute werden erst in Phase 4 abgebildet.
- Validator-/MetaConfig-Optionen kommen mit Phase 6.

## Decisions / notes

- Der inverse Mapper bleibt Hop-frei: er bekommt Plan-geordnete Werte-Arrays; das
  `IRowMeta`-Binding liegt als dünne Schicht im transforms-Modul (`InterlisRowBindings`).
  Damit bleibt die gesamte Mapping-Logik im Core testbar.
- `InterlisProjectionResult` trägt jetzt auch das `CompiledInterlisModel` – der
  XTF-Writer braucht die `TransferDescription` für den Header.
- Der XTF-Writer schreibt die XTF-Version passend zur Modell-Version (2.3/2.4); der
  2.4-Reader benötigt das Modell zur Topic-Auflösung, daher setzt der Input-Transform
  das Modell nach der Header-Erkennung auf den Reader.

## Next phase

Phase 3 – Strukturen umfassend: rekursives Single-Structure-Flattening verfeinern,
`INTERLIS Structure Explode` und `INTERLIS Structure Collect` für BAG/LIST-Strukturen.
