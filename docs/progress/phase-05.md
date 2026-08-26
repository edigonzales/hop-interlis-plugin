# Phase 5 – Advanced Envelope und generische Transfer-Transforms

Status: **abgeschlossen** (Stand 2026-08-26)

## Implemented

### Arbeitspaket 5.1 – InterlisObjectEnvelope

- `InterlisObjectEnvelope` existierte seit Phase 1; neu trägt der XTF-Reader die
  Transfer-Operation (`INSERT`/`UPDATE`/`DELETE`/`NONE` via
  `InterlisObjectOperation.fromIom/toIom`), und `InterlisEnvelopeRowLayout`
  definiert das kanonische Envelope-Row-Layout zentral (Feldnamen, Reihenfolge,
  `toRow`/`fromRow`, `objectRow(...)`):

  ```text
  _ili_event_type _ili_model _ili_topic _ili_bid _ili_class _ili_tid
  _ili_operation _ili_object _ili_line _ili_column
  ```

  `_ili_line`/`_ili_column` kommen aus den IOM-Quellpositionen des Readers.

### Arbeitspaket 5.2 – eigener Value Type

- `ValueMetaInterlisObject` aus Phase 3 (dort als Carrier-Typ vorgezogen) erfüllt
  die Anforderungen: Deep-Copy, binäre (De-)Serialisierung, `getString()` nur für
  Debug/Preview, keine verlustbehaftete String-Konvertierung; neu zusätzlich
  `getBinaryString()` für generische Konsumenten (z. B. Text file output).

### Arbeitspaket 5.3 – INTERLIS Transfer Input

- `INTERLIS_TRANSFER_INPUT`: streamt den kompletten XTF-Eventstrom als Envelope-Rows
  mit konstantem Schema (beliebig viele Klassen in einem Stream). Modi
  `OBJECTS` (nur Objektzeilen mit Transfer-/Basket-Kontext) und `EVENTS`
  (exakte Eventsequenz). Modellauflösung für den 2.4-Reader via `%DATA`-Detektion
  und `%XTF_DIR`-Auflösung.

### Arbeitspaket 5.4 – INTERLIS Object to Row

- `INTERLIS_OBJECT_TO_ROW`: projiziert das `_ili_object`-Payload auf getypte
  Zeilen einer Klasse (Model/Class/Feldprojektion wie INTERLIS Input, inkl.
  Flattening attributierter Assoziationen mit Link-Pufferung pro Basket);
  `appendEnvelopeFields` (Default: Envelope ersetzen); andere Klassen werden
  übersprungen. Design-Time-Probe ohne Transferdatei.

### Arbeitspaket 5.5 – INTERLIS Row to Object

- `INTERLIS_ROW_TO_OBJECT`: inverse Operation; Ausgabe = konstantes
  Envelope-Schema; regenerierte Link-Objekte (`RowToIomMapper.mapAll`) werden als
  zusätzliche OBJECT-Zeilen emittiert → mehrere Klassen können über
  Append/Merge in einen Stream zusammengeführt werden.

### Arbeitspaket 5.6 – INTERLIS Transfer Output

- `INTERLIS_TRANSFER_OUTPUT`: schreibt Envelope-Rows zurück als XTF.
  - Object Mode (Default): leitet Transfer-/Basket-Events aus den OBJECT-Zeilen ab
    (Basket-Gruppierung über `_ili_bid`); explizite Event-Zeilen sind ein Fehler.
  - Event Mode: schreibt die explizite Eventsequenz; die State-Machine des
    Writers lehnt ungültige Reihenfolgen ab.
  - Operationen (INSERT/UPDATE/DELETE) werden auf das IOM-Objekt übertragen
    (`ili:operation`); explizite Modellnamen für den Header.

### Demo / E2E

- E2E 13/14: verlustfreier generischer Roundtrip (`Transfer Input EVENTS` →
  `Transfer Output EVENTS`) über die Assoziations-Fixture (14 Objekte gemischter
  Klassen, Basket erhalten).
- E2E 15/16: Delete-Operation (`ili:operation="DELETE"`) überlebt den generischen
  Roundtrip.

## Tests

`./mvnw -B -ntp clean verify` grün (257 Tests, Core 132 + Transforms 125). Neu u. a.:

- Core: `InterlisEnvelopeRowLayoutTest` (Row-Mapping, Roundtrip, unbekannte
  Werte, Delete-Operation des Readers, Operations-Codes).
- Transforms: `InterlisTransferInputPipelineTest` (konstantes Schema, Eventsequenz,
  gemischte Klassen, Delete), `GenericEnvelopePipelineTest` (Object to Row, Row to
  Object inkl. Link-Objekt, generischer Roundtrip, Delete-Roundtrip,
  Object-Mode-Fehlerpfad), `GenericEnvelopeMetaTest` (Schemas, Plugin-Contracts,
  XML-Roundtrips, check()).
- E2E: Pipelines 13–16 inkl. Assertions in `check-e2e-output.py` (14 Pipelines
  gesamt).

## Decisions / notes

- **Envelope-Feldnamen**: `_ili_event_type`/`_ili_model`/`_ili_line`/`_ili_column`
  gemäss 01-Arch §3.4 (die Roadmap-Kurzform `_ili_event` wird nicht verwendet).
- **Delete-Repräsentation**: OBJECT-Zeilen mit `_ili_operation=DELETE`
  (kein separates DELETE_OBJECT-Event; iox liefert Object-Events mit Operation).
- **Object to Row puffert** bei geflatteten Assoziationsattributen pro Basket
  (Flush bei END-Events oder BID-Wechsel); Streams müssen wie beim Output
  basket-gruppiert sein.
- **Row to Object emittiert Link-Objekte direkt nach ihrer Klassenzeile** (nicht
  gesammelt am Ende).
- Typed Input/Output bleiben unverändert einfache Fassaden über denselben
  Mapper-Kern.

## Known limitations

- **iox-ili 1.24.0 Reader-Bug**: externe Basket-Referenzen (`ili:bid`) auf
  Rollen-Membern von Standalone-Assoziationen erzeugen beim 2.4-Reader ein
  doppeltes REF-Member; der 2.4-Writer crasht darauf. `_ref_bid` wird gelesen
  (eigene Fixture `HopIli_Associations_V1_extref.xtf`), aber der generische
  Roundtrip solcher Zeilen ist bis zum iox-Fix eingeschränkt (dokumentiert in
  Phase-4-Progress).
- Transfer Output benötigt explizite Modellnamen (der Envelope-Stream trägt
  keinen Header).
- `_ili_line`/`_ili_column` werden gelesen, beim Schreiben nicht reproduziert.

## Next phase

Phase 6 – Validation, Enumerationen, Transferkontrolle und UX-Härtung
(`INTERLIS Validate`, `INTERLIS Enumerations`, Basket-Metadaten,
Delete/Update im Typed-Output, UX-Polish).
