# Phase 4 – Assoziationen und Role Join

Status: **abgeschlossen** (Stand 2026-08-26)

## Implemented

### Arbeitspaket 4.1 – Simple Role Mapping

- Einfache Rollen werden als `<role>_ref`-Felder projiziert (bereits aus Phase 1,
  jetzt vollständig): optional zusätzlich `<role>_ref_bid` (externe
  Basket-Referenzen, `ProjectionOptions.includeRoleRefBid`).
- Der Rollen-Deskriptor trägt jetzt `associationScopedName`, damit die
  Schema-Projektion die zugehörige Assoziation kennt; ili2c exponierte
  Klassenrollen teils nur über `getOpposideRoles()` – der Extractor sammelt
  beide Quellen.
- Die in der Roadmap erwähnten `ili.kind`/`ili.target`-Metadaten auf der
  Hop-ValueMeta sind in Hop 2.18 nicht möglich (`IValueMeta` hat keinen
  Attribute-Kanal mehr); die Metadaten leben im Plan/Deskriptor und in der GUI
  (Dokumentiert).

### Arbeitspaket 4.2 – Association Attributes

- Attributierte Assoziationen werden in XTF immer als **separate Link-Objekte**
  übertragen (empirisch gegen ili2pg-Referenzdaten und den iox-Writer
  verifiziert); embedded REFs tragen keine Attribute.
- Für eindeutig einbettbare Assoziationen (binär, Rolle max 1 auf der Klasse,
  Gegenrolle 1..1) flattet `INTERLIS Input` deshalb `<role>_ref` und
  `<role>_<attribute>` aus dem Link-Objekt: Link-Objekte werden pro Basket
  gepuffert, Klassenzeilen am Basket-Ende emittiert (ein Pass, Reihenfolge
  erhalten). Konfigurierbar über `ProjectionOptions.flattenAssociationAttributes`
  (Default an).
- Nicht eindeutig einbettbare attributierte Rollen werden mit Warnung
  übersprungen und auf Association-Rows verwiesen (kein stiller Datenverlust).
- `INTERLIS Output` erzeugt beim Schreiben von Klassenzeilen mit geflatteten
  Assoziationswerten das Link-Objekt zusätzlich (`RowToIomMapper.mapAll` →
  `InterlisWriteResult`).

### Arbeitspaket 4.3 – Association Rows

- `InterlisAssociationDescriptor` + Extractor: m:n-/n-äre/attributierte
  Assoziationen sind eigene Projektionswurzeln (`InterlisPlanRoot`-Interface,
  von Klasse und Assoziation implementiert; `InterlisRowMappingPlan` ist
  generalisiert).
- Projektion: `<role>_ref` pro Rolle (immer, unabhängig von Kardinalität),
  optional `<role>_ref_bid`, `<role>_order_pos` für ORDERED-Rollen,
  Assoziationsattribute als normale Felder; `_ili_tid` nur wenn die Assoziation
  eine eigene OID besitzt (XTF-Link-Objekte tragen sonst keine TID).
- Read/Write: REF-Member inkl. `order_pos` und externer Basket-Referenz;
  `RowToIomMapper` erlaubt fehlende TID für nicht-identifizierbare
  Assoziationen; `INTERLIS Output` schreibt Link-Objekte.
- Model Browser/Input-/Output-Dialoge listen Assoziationen als auswählbare
  Transfer-Viewables (`(association)`-Suffix).

### Arbeitspaket 4.4 – INTERLIS Role Join

- Neuer Transform `INTERLIS_ROLE_JOIN` (Meta/Data/Runtime/GUI, Icon):
  - beide Ströme explizit via `findInputRowSet` (Hop 2.x, wie StreamLookup),
    Lookup einmal in-memory geladen, Limit `maxLookupRows`;
  - modellbewusste Konfiguration: Main-Klasse + Rolle → Zielklasse,
    Referenzfeld default `<role>_ref`, Lookup-TID default `_ili_tid`,
    hinzuzufügende Felder (Default: alle Attribute der Zielklasse), Prefix;
  - `failOnMissingMandatoryReference` (Default an: mandatory Rolle ohne
    Zielobjekt ist ein harter Fehler), `failOnDuplicateTid`;
  - modellgetriebene `getFields()` (getypte Zielklassen-Felder, Geometrie als
    Hop-Geometry), SWT-freier Controller mit Tests.

### Testmodell

- `HopIli_Associations_V1.ili` (INTERLIS 2.4): `Person`, `Organisation`,
  `Address`, `Project`, `Task`; `AddressOwnership` (1:1 mit Attribut `Share`),
  `Membership` (m:n mit Attributen), `PersonProject` (n-är),
  `PersonTask` (`(ORDERED)`-Rolle, korrekte INTERLIS-Syntax
  `Task (ORDERED) -- {0..*} Task;`).
- XTF-2.4-Fixture vom iox-Writer erzeugt, externer Basket-Ref (`ili:bid`) von
  Hand ergänzt (der 2.4-Writer schreibt keine `bid`-Attribute auf
  REF-Membern – dokumentierte Einschränkung).

## Tests

`./mvnw -B -ntp clean verify` grün (236 Tests, Core 126 + Transforms 110). Neu u. a.:

- Core: Extractor (Assoziationen, Rollen-Zuordnung, opposide roles),
  `InterlisRowSchemaBuilderAssociationTest` (Assoziationsprojektion,
  ORDERED, ref_bid, Flattening, Warnungen), `InterlisObjectToRowMapperAssociationTest`
  (Association-Rows, order pos, ref bid, Link-Auflösung),
  `RowToIomMapperAssociationTest` (Link-Objekte schreiben, order pos, ref bid,
  Link-Erzeugung aus Klassenzeilen, Fehlerfälle).
- Transforms: `InterlisInputAssociationPipelineTest` (Association-Rows lesen,
  geflattete Attribute, voller Roundtrip inkl. regeneriertem Link-Objekt),
  `InterlisRoleJoinMetaTest` (Probe, getFields, XML-Roundtrip, Plugin-Contract),
  `InterlisRoleJoinPipelineTest` (echter Lookup, synthetischer Lookup,
  fehlendes mandatory Ziel, Duplikat-TID).
- E2E: Pipelines 09–12 (Person-Roundtrip mit Link-Attributen, PersonTask-Roundtrip
  mit ORDER_POS) inkl. Assertions in `check-e2e-output.py`.

## Decisions / notes

- **Attributierte Assoziationen sind nie embedded** (XTF/iox-Verhalten): das
  Flattening liest aus separat gepufferten Link-Objekten; Klassenzeilen werden
  bei aktivem Flattening pro Basket bis zum Basket-Ende verzögert emittiert.
- **`<role>_order_pos` statt `_ili_order_pos`**: je ORDERED-Rolle ein Feld
  (mehrere ORDERED-Rollen bleiben eindeutig); die Roadmap-Beispielspalte
  `_ili_order_pos` entspricht dem Fall mit genau einer ORDERED-Rolle.
- **ref_bid**: Read-Seite vollständig (inkl. `ili:bid` auf REF-Membern);
  Write-Seite durch den iox-2.4-Writer begrenzt (schreibt kein `bid` auf
  REF-Membern) – dokumentierte Einschränkung.
- **Hop 2.18 `IValueMeta` ohne Attribute**: INTERLIS-Metadaten
  (ili.kind/target/min/max) bleiben im Plan/Deskriptor; die GUI zeigt sie.
- Nicht-identifizierbare Assoziationen: `_ili_tid` fehlt im Schema; der Writer
  schreibt Link-Objekte ohne TID (Referenzverhalten ili2pg).

## Known limitations

- Externe Basket-Referenzen werden gelesen, aber beim Schreiben nicht emittiert
  (iox-2.4-Writer-Lücke).
- Das Flattening attributierter Assoziationen puffert Klassenzeilen pro Basket
  (Reihenfolge bleibt erhalten, Speicher begrenzt durch Basketgrösse).
- Role Join ist ein in-memory Lookup (Limit `maxLookupRows`); ein
  Streaming-/Sorted-Join bleibt späteren Phasen vorbehalten.
- Lightweight Associations (INTERLIS 2.4) werden nicht separat projiziert.

## Next phase

Phase 5 – Advanced Envelope und generische Transfer-Transforms
(`InterlisObjectEnvelope`, `INTERLIS Transfer Input`, `INTERLIS Object to Row`,
`INTERLIS Row to Object`, `INTERLIS Transfer Output`); der
`ValueMetaInterlisObject` aus Phase 3 wird dort wiederverwendet.
