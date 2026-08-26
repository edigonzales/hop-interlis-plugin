# Phase 6 – Validation, Enumerationen, Transferkontrolle und UX-Härtung

Status: **abgeschlossen** (Stand 2026-08-26)

## Implemented

### Arbeitspaket 6.1 – INTERLIS Validate

- Neuer Transform `INTERLIS_VALIDATE` (inputlos, File-Mode): validiert eine
  XTF-Datei mit dem Streaming-Validator von iox-ili 1.24.4
  (`ch.interlis.iox_j.validator.Validator`) und emittiert eine Error-Row pro
  Finding. Keine neue Abhängigkeit nötig – der Validator steckt in iox-ili.
- Findings werden über eine eigene `LogEventFactory`/`IoxLogging`-Implementierung
  eingesammelt; das Fehlerschema ist zentral in `InterlisValidationRowLayout`
  definiert (13 Felder, `_ili_`-Präfix):

  ```text
  _ili_severity _ili_message _ili_source_file _ili_line _ili_column
  _ili_model _ili_topic _ili_bid _ili_class _ili_tid
  _ili_attribute_path _ili_constraint_name _ili_raw_event_type
  ```

  Felder, die iox nicht liefert (Column, BID, raw event type), bleiben `null`
  (gemäss 01-Arch §20.1).
- Optionen: `stopOnFirstError`, `maxErrors` (Default 10'000), Severity-Filter
  (`includeWarnings` Default an, `includeInfo` Default aus) und `failOnErrors`
  („Route errors + optional fail after completion" – Rows werden erst emittiert,
  danach schlägt der Transform fehl).

### Arbeitspaket 6.2 – Validator Config / MetaConfig

- Lokale Validator-Config-Datei (`configFile`, TOML, via
  `ValidationConfig.mergeConfigFile`) inkl. getestetem Multiplicity-OFF-Fall.
- `ilidata:`-Referenzen/MetaConfig bleiben eingeschränkt (MetaConfig wird über
  die Modell-Meta-Attribute gemergt; ilidata-Auflösung folgt, sobald die
  Repository-Schicht dran ist – dokumentiert).

### Arbeitspaket 6.3 – INTERLIS Enumerations

- Neuer Transform `INTERLIS_ENUMERATIONS` (inputlos): listet die
  Enumerationen eines Modells als Lookup-Rows:

  ```text
  enum_definition enum_value enum_path parent_value depth is_leaf
  ```

- `InterlisEnumerationExtractor` (Core) entdeckt Enum-Typen über
  Attribut-Domains von Klassen/Strukturen **und** `DOMAIN`-Aliase in Topics
  (Dedup über die Typ-Identität, erste Definition in Modellreihenfolge gewinnt);
  Sub-Enumerationen werden rekursiv geflattet (parent/depth/leaf).
- Numerische ITF-Codes folgen mit Phase 7 (01-Arch §15: kein ITF-Code im
  XTF-Default).

### Arbeitspaket 6.4 – Basket-Metadaten

- `InterlisBasketMetadata` (Core): Consistency/Kind/Start-State/End-State als
  lesbare Strings, zentrale IOM-Code-Mappings (`IOM_COMPLETE`/`IOM_FULL` = 0
  werden als „nicht gesetzt" behandelt, da XTF sie nicht emittiert).
- Der XTF-Reader trägt die Metadaten auf START_BASKET-, OBJECT- und
  END_BASKET-Envelopes; das Envelope-Row-Layout wächst um vier Felder
  (`_ili_basket_consistency`, `_ili_basket_kind`, `_ili_basket_start_state`,
  `_ili_basket_end_state`); `XtfTransferWriter.startBasket(...)`-Overload
  schreibt sie zurück – der generische Event-Roundtrip erhält die
  Basket-Metadaten damit verlustfrei.
- **iox-2.4-Writer-Lücke abgefangen:** UPDATE/INITIAL-Baskets erfordern laut
  XTF Start-/End-State; der iox-Writer crasht auf fehlenden Werten (NPE).
  Unser Writer wirft stattdessen eine klare `InterlisWriteException`.

### Arbeitspaket 6.5 – Delete / Update Transfer

- Typed Input: neues Feld `_ili_operation` (`includeOperation`), Dialog-Checkbox.
- Typed Output: neues `operationField` (leer = aus) – die Operation wird auf das
  geschriebene Objekt übertragen (`ili:operation`); DELETE-Objekte tragen nur
  ihre Identität, deshalb überspringt der Mapper die Mandatory-Prüfungen für
  DELETE (`RowWriteOptions.operation`).
- DELETE überlebt damit sowohl den generischen (Phase 5) als auch den **typed**
  Roundtrip (E2E 19/20).

### Arbeitspaket 6.6 – UX Polish (gezielt)

- Neue modellbewusste Dialoge für Validate und Enumerations; Tooltips auf den
  neuen Feldern (Operation/Source-Object im Output, Operation im Input);
- Konfigurationsprüfungen (`check()`) für alle neuen Transforms; die grossen
  UX-Themen (Model-Browser-Suche, zuletzt verwendete Quellen) bleiben bewusst
  bei den Dialog-Basics.

## Tests

`./mvnw -B -ntp clean verify` grün (280 Tests, Core 140 + Transforms 140). Neu u. a.:

- Core: `InterlisEnumerationExtractorTest` (Domains + Inline-Enums,
  Sub-Enum-Hierarchie, Predefined-Model-Ausschluss),
  `InterlisBasketMetadataTest` (Mapping, Reader-Envelopes, Writer-Roundtrip,
  klare Fehlermeldung bei fehlenden States).
- Transforms: `InterlisValidatePipelineTest` (valide Datei → 0 Rows, invalide
  Datei → Error-Rows mit Klasse/TID/Line, failOnErrors, stopOnFirstError,
  Severity-Filter, Config-Datei mit Multiplicity-OFF),
  `InterlisEnumerationsPipelineTest` (Schema + Hierarchie),
  `InterlisInputOperationPipelineTest` (Operation lesen + typed DELETE-Roundtrip),
  `Phase6MetaTest` (Contracts, Schemas, XML-Roundtrips, Envelope-Schema mit
  Basket-Metadaten).
- E2E: Pipelines 17–20 (Validate-CSV, Enumerations-CSV, typed DELETE-Roundtrip
  inkl. Check) – 18 Pipelines gesamt, `check-e2e-output.py` prüft Severity,
  Klasse-Kontext, Sub-Enum-Hierarchie und DELETE-Operation.

## Decisions / notes

- **Fehlerschema**: 01-Arch §20.1 mit `_ili_`-Präfix (Konsistenz zu den
  technischen Feldern des Plugins); §20.1 im Spec entsprechend aktualisiert.
- **Enumerationen**: `enum_definition` ist der Scoped-Name des Alias/Attributs
  (Inline-Enums haben keinen eigenen Scoped-Namen); Sub-Enum-Werte tragen den
  Pfad `Definition.beta.beta_1`.
- **Enum-Werte im XTF**: Sub-Enum-Werte werden als voller Pfad geschrieben
  (`beta.beta_1`) – der Validator bestätigt das (unsere erste „valide" Fixture
  war tatsächlich invalide).
- **DELETE-Mapping**: Mandatory-Prüfungen entfallen für DELETE
  (`RowWriteOptions.isDelete()`); DELETE-Objekte enthalten im XTF nur die TID.

## Known limitations

- Stream-Mode-Validierung (Envelope-Stream statt Datei) bleibt Phase 7/8
  (Second-Pass-Semantik), gemäss 02-Spec §23.2.
- `ilidata:`-Config-Referenzen/MetaConfig-Auflösung eingeschränkt; lokale
  Config-Datei voll unterstützt.
- `_ili_column`/`_ili_bid`/`_ili_raw_event_type` bleiben `null` (iox liefert sie
  nicht).
- Keine ITF-Enum-Codes (Phase 7).

## Next phase

Phase 7 – ITF / INTERLIS 1 (ITF Reader/Writer, AREA/SURFACE-Modi, Linetable-
Transforms, ITF-Enum-Codes).
