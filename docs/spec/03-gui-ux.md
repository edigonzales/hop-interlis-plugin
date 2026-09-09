# GUI- und UX-Spezifikation

Die GUI ist ein Kernbestandteil des Plugins. INTERLIS ist semantisch reich; ein reines Formular mit Textfeldern für Modellname, Klassenname und Attributliste wäre zwar implementierbar, aber für den Alltag unnötig schwierig.

Ziel: Ein Benutzer soll ein XTF auswählen, das Modell sehen, eine Klasse auswählen und sofort verstehen, welche Hop-Felder entstehen. Strukturen, Rollen und Assoziationen werden im Modellbaum sichtbar und mit sinnvollen Handlungsoptionen versehen.

# 1. Gestaltungsprinzipien

1. **Progressive disclosure**: häufige Einstellungen oben, Advanced-Optionen in separatem Bereich/Tab.
2. **Model-first feedback**: sobald Datei/Modelle auflösbar sind, Model Browser und Schema Preview aktualisieren.
3. **Keine XTF-Interna als Default**: kein `EmbeddedLinkStruct`, keine IOX-Klassennamen.
4. **Semantik sichtbar**: Rolle, Struktur, LIST/BAG, Geometry, Cardinality müssen erkennbar sein.
5. **Sinnvolle Defaults**: `0..1`-Strukturen flatten, einfache Rollen als Referenzfeld, TID/BID einschalten.
6. **Nicht destruktive Probe**: Repository-/Schema-Probleme dürfen den Dialog nicht unöffnbar machen.
7. **Variablenfreundlich**: `${...}` wird unterstützt; bei unresolved Variablen zeigt Preview eine Erklärung statt Stacktrace.
8. **Stabile Feldnamen**: Output-Mapping ist explizit sichtbar und editierbar.
9. **Warnings statt Überraschungen**: LIST kann nicht flach in eine Row gepresst werden; GUI erklärt den nächsten Transform.
10. **Copy/paste-fähige Scoped Names**: Model/Topic/Class-Namen sind sichtbar.

# 2. Gemeinsame Model-Source-Komponente

Wiederverwendet in Input, Output, Object-to-Row, Row-to-Object, Validate, Enumerations.

```text
+------------------------------------------------------------------------------+
| INTERLIS model source                                                        |
+------------------------------------------------------------------------------+
| Data file      [ ${PROJECT_HOME}/data/input.xtf                         ] [...]|
|                                                                              |
| Models         [ %DATA                                                   v ]  |
| Model dirs     [ %XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch ] ...|
| Meta config    [                                                         ] ...|
|                                                                              |
| Model status   | Model loaded: 3 models, 7 topics, 42 classes                       |
| Class          [ Model.Topic.Building                                  v ]     |
|                [ Reload model ]                                               |
+------------------------------------------------------------------------------+
```

## Verhalten

- `Data file` optional bei reinen Model-Transforms, Pflicht beim Input/Validate.
- `Models = %DATA` bedeutet: aus Transfer ableiten.
- `Model dirs` kann lokale Verzeichnisse und Repository-URLs enthalten.
- Die beiden Standard-Repositories werden über `ilisite.xml` verknüpfte
  Parent-/Tochter-Repositories durchsucht; technische Importmodelle wie
  `TYPE`, `REFSYSTEM` und `SYMBOLOGY` bleiben intern verfügbar, werden aber
  nicht als fachliche Klassen angeboten.
- `Reload model` löst explizite Probe aus.
- Nach erfolgreicher Modellprobe darf das Klassenfeld zunächst leer bleiben. Das
  Dropdown wird mit allen transferierbaren Klassen und Assoziationen befüllt;
  erst eine konkrete Auswahl erzeugt die Schema Preview.
- Auto-Probe erfolgt debounced nach Änderungen; nicht bei jedem Tastendruck sofort Netzverkehr starten.
- Eigener, nicht-modaler Modellstatusbereich zwischen `Model dirs` und `Class`:
  - `INFO` für unvollständige Konfiguration oder ein geladenes Modell ohne
    Klassenauswahl.
  - `SUCCESS` für eine erfolgreiche Modellprobe.
  - `WARNING` für zusätzliche Probe-/Schemawarnungen.
  - `ERROR` mit kurzer Root-Cause; der Dialog bleibt nutzbar.
  Der Status ist umbrechend und passt seine Höhe beim Resize an.

Beispiel Fehler:

```text
Model status: ERROR - model preview unavailable
        Repository https://... could not be reached.
        Runtime will retry with resolved variables/settings.
```

# 3. Model Browser

## 3.1 Grundlayout

```text
+--------------------------------------+---------------------------------------+
| Model browser                        | Selected element                      |
+--------------------------------------+---------------------------------------+
| [Filter: gemeinde______________]     | Model.Topic.Building                  |
|                                      |                                       |
| v MyModel                            | CLASS Building                        |
|   v TopicA                           | Extends: BaseObject                   |
|     > Municipality                   | OID: INTERLIS.UUIDOID                 |
|     v Building                       |                                       |
|       # _tid                         | Properties                            |
|       A Name              TEXT*80    |  Name          TEXT*80                |
|       E Type              (enum)     |  Type          BuildingType           |
|       G Geometry          SURFACE    |  Geometry      SURFACE (ARCS)         |
|       S Address           {0..1}     |  Address       Address {0..1}          |
|         A Street          TEXT*80    |  Tags: structure, flattenable         |
|         A Number          TEXT*10    |                                       |
|       L Qualities         LIST {*}   |                                       |
|         ...                          |                                       |
|       R Municipality      -> {1}     |                                       |
|                                      |                                       |
+--------------------------------------+---------------------------------------+
```

Legende kann als Tooltip und kleine Fusszeile erscheinen:

```text
A attribute   E enumeration   G geometry   S structure   L list/bag   R role
```

ASCII in Spez ist nur Layout; reale GUI kann Icons nutzen.

## 3.2 Filter

Filter sucht case-insensitive in:

- Modellname,
- Topic,
- Klasse,
- Attribut/Rolle,
- Scoped Name.

Bei Match eines Child-Elements bleiben Ancestors sichtbar.

# 4. INTERLIS Input Dialog

## 4.1 Hauptdialog

```text
+================================================================================+
| INTERLIS Input                                                                 |
+================================================================================+
| Transform name  [ Read buildings                                           ]   |
|                                                                                |
| Source                                                                         |
| Data file       [ ${PROJECT_HOME}/data/buildings.xtf                       ]... |
| Models          [ %DATA                                                    v]   |
| Model dirs      [ %XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch ]... |
|                                                                                |
| Model status [ Model loaded: 1 model, 1 class                              ]    |
| Class          [ DMAVTYM_....Bodenbedeckung.Gebaeude                    v ]     |
|                [ Reload ]                                                       |
| [ Browse model... ]                                                            |
|                                                                                |
| Output fields                                                                  |
| +--------------------------------------------------------------------------+   |
| | Use | INTERLIS property       | Hop field              | Type            |   |
| |-----+--------------------------+------------------------+-----------------|   |
| | [x] | @TID                     | _ili_tid               | String          |   |
| | [x] | @BID                     | _ili_bid               | String          |   |
| | [x] | Art                      | Art                    | String (enum)   |   |
| | [x] | Name                     | Name                   | String          |   |
| | [x] | Geometrie                | Geometrie              | Geometry        |   |
| | [x] | Adresse.Strasse          | Adresse_Strasse        | String          |   |
| | [x] | Adresse.Nummer           | Adresse_Nummer         | String          |   |
| | [x] | Gemeinde                 | Gemeinde_ref           | String (role)   |   |
| +--------------------------------------------------------------------------+   |
|                                                                                |
| [ Get fields ] [ Reset defaults ] [ Model browser... ]                          |
|                                                                                |
| v Structures and relationships                                                 |
|   Address {0..1}          (*) Flatten  ( ) Keep object   Prefix [Address_]      |
|   Qualities LIST {0..*}   [x] Keep source object for Structure Explode          |
|                           -> Use "INTERLIS Structure Explode" downstream       |
|   Municipality -> {1}     [x] Output reference as Municipality_ref              |
|                                                                                |
| > Validation                                                                    |
| > Advanced                                                                      |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

## 4.2 Field grid columns

```text
Use
INTERLIS property
Hop field
Hop type
Cardinality
Kind
Notes
```

`Hop field` editierbar. `Hop type` grundsätzlich read-only; Sonderoptionen nur dort, wo sicher.

## 4.3 Get fields

`Get fields` erzeugt den Default-Plan neu. Wenn Benutzer Feldnamen angepasst hat, erscheint bei potenziell überschreibender Aktion:

```text
Refresh fields from model?

(*) Preserve matching custom output names
( ) Reset all output names to defaults
```

Kein stilles Überschreiben manueller Mappings.

## 4.4 Strukturen

Einwertig:

```text
Address : Address {0..1}

Mapping:
(*) Flatten to fields
( ) Keep as INTERLIS Object [Advanced]

Prefix [ Address_ ]
```

Mehrwertig:

```text
Qualities : LIST {0..*} OF Quality

This property cannot be represented as a fixed set of scalar fields.

[x] Keep source object for downstream Structure Explode

Suggested next transform:
    INTERLIS Structure Explode
```

Keine Option `Flatten` für LIST/BAG anbieten.

## 4.5 Rollen

```text
Municipality -> Municipality {1}

[x] Output reference
Field name [ Municipality_ref ]
[x] Output external basket reference if present
          [ Municipality_ref_bid ]

Tip: use "INTERLIS Role Join" to add target attributes.
```

# 5. Model Browser als Modal Dialog

```text
+================================================================================+
| Select INTERLIS class                                                          |
+================================================================================+
| Filter [ building________________________________________ ] [x] classes only   |
|                                                                                |
| v Model                                                                        |
|   v Topic                                                                      |
|     Building                                                                   |
|     BuildingPart                                                               |
|     BuildingAddress                                                            |
|                                                                                |
| -----------------------------------------------------------------------------  |
| Selected: Model.Topic.Building                                                  |
|                                                                                |
| Effective properties: 12                                                       |
| Geometry attributes: 2                                                         |
| Structures: 1 single, 1 list                                                   |
| Roles: 2                                                                        |
|                                                                                |
|                                                     [ Select ] [ Cancel ]        |
+================================================================================+
```

Für `INTERLIS Input` werden standardmässig nur transferierbare Klassen/Assoziationen auswählbar. Strukturen sind sichtbar, aber nicht als Hauptklasse auswählbar.

# 6. Schema Preview

Read-only SWT-Table-Bereich:

```text
+----------------------+----------------+--------------------------------------+
| Field                | Hop type       | Source                               |
+----------------------+----------------+--------------------------------------+
| _ili_tid             | String         | @TID                                 |
| _ili_bid             | String         | @BID                                 |
| Art                  | String         | enum BuildingType                    |
| Name                 | String         | TEXT*80                              |
| Geometry             | Geometry       | SURFACE, arcs allowed                |
| Axis                 | Geometry       | POLYLINE, arcs allowed               |
| Address_Street       | String         | Address.Street                       |
| Address_Number       | String         | Address.Number                       |
| Municipality_ref     | String         | -> Model.Topic.Municipality {1}     |
+----------------------+----------------+--------------------------------------+
```

Warnings darunter:

```text
! Qualities is LIST {0..*}; it is not part of the scalar row schema.
  Keep source object is enabled so it can be exploded downstream.
```

Die Tabelle ist eine native SWT-`Table` im Stil der bestehenden Hop-Dialoge.
Sie besitzt feste Spaltenüberschriften sowie horizontales und vertikales
Scrolling. Der Modellstatus wird als normale Formularzeile zwischen Modellquelle
und Klassenauswahl angezeigt. Links steht das Label `Model status`; rechts liegt
eine rechteckige, umbruchfähige Statusfläche innerhalb derselben Feldspalte wie
die übrigen Eingaben. Sie verwendet kein Zusatzsymbol und keine separate
Überschrift.
Statusmeldungen und Warnungen werden getrennt von den Feldzeilen angezeigt; eine
fehlgeschlagene Probe lässt den Dialog geöffnet und leert nur die Tabelle.

# 7. INTERLIS Output Dialog

```text
+================================================================================+
| INTERLIS Output                                                                |
+================================================================================+
| Transform name [ Write buildings                                           ]   |
|                                                                                |
| Target                                                                         |
| XTF file       [ ${PROJECT_HOME}/out/buildings.xtf                         ]... |
| Existing file  [ Fail if exists                                           v]   |
|                                                                                |
| Model                                                                          |
| Models         [ DMAVTYM_...                                               ]    |
| Model dirs     [ %XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch ]    |
| Class          [ ...Gebaeude                                              v]    |
|                                                                                |
| Identity                                                                       |
| TID field      [ _ili_tid                                                  v]   |
| Missing TID    [ Fail                                                     v]   |
|                                                                                |
| Basket                                                                         |
| Policy         [ From field                                               v]   |
| BID field      [ _ili_bid                                                  v]   |
| Fixed BID      [                                                           ]    |
|                                                                                |
| Field mapping                                                                  |
| +--------------------------------------------------------------------------+   |
| | INTERLIS property      | Input field          | Required | Status         |   |
| | Art                     | Art                  | no       | OK             |   |
| | Name                    | Name                 | no       | OK             |   |
| | Geometrie               | Geometrie            | yes      | Geometry       |   |
| | Adresse.Strasse         | Adresse_Strasse      | no       | OK             |   |
| | Gemeinde                | Gemeinde_ref         | yes      | role ref       |   |
| +--------------------------------------------------------------------------+   |
| [ Get incoming fields ] [ Auto-map by name ]                                   |
|                                                                                |
| > Validation                                                                    |
| > Advanced                                                                      |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

## Auto-map by name

Mapping-Reihenfolge:

1. exakter expliziter gespeicherter Pfad.
2. exakter Hop-Feldname.
3. Default flatten name.
4. case-insensitive nur als Vorschlag, nicht still anwenden bei Mehrdeutigkeit.

# 8. INTERLIS Transfer Input Dialog

Advanced-Transform; bewusst kompakter.

```text
+================================================================================+
| INTERLIS Transfer Input                                                        |
+================================================================================+
| Data file      [ ${PROJECT_HOME}/data/full.xtf                             ]... |
| Models         [ %DATA                                                    v]    |
| Model dirs     [ %XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch ]     |
|                                                                                |
| Mode           (*) Objects only   ( ) Full event stream                         |
|                                                                                |
| Object filter                                                                   |
| Topics         [ all                                                       ]... |
| Classes        [ all                                                       ]... |
| [ ] Include delete objects                                                      |
|                                                                                |
| Output schema                                                                   |
| _ili_event_type, _ili_model, _ili_topic, _ili_bid, _ili_class,                 |
| _ili_tid, _ili_operation, _ili_object, _ili_line, _ili_column                  |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

# 9. INTERLIS Object to Row Dialog

```text
+================================================================================+
| INTERLIS Object to Row                                                         |
+================================================================================+
| Object field    [ _ili_object                                             v]    |
| Class           [ Model.Topic.Building                                    v]    |
| [ Browse model... ]                                                            |
|                                                                                |
| Envelope fields                                                                 |
| (*) Replace envelope object with typed fields                                   |
| ( ) Keep envelope fields and append typed fields                                |
|                                                                                |
| Projection                                                                      |
| [same field/structure/role grid as INTERLIS Input]                              |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

# 10. INTERLIS Row to Object Dialog

```text
+================================================================================+
| INTERLIS Row to Object                                                         |
+================================================================================+
| Class       [ Model.Topic.Building                                         v]   |
|                                                                                |
| Identity                                                                       |
| TID field   [ _ili_tid                                                    v]   |
| BID field   [ _ili_bid                                                    v]   |
| Operation   [ _ili_operation                                              v]   |
|                                                                                |
| Mapping                                                                         |
| +--------------------------------------------------------------------------+   |
| | INTERLIS property | Input field | Type check | Status                    |   |
| +--------------------------------------------------------------------------+   |
|                                                                                |
| Output field [ _ili_object                                                 ]    |
|                                                                                |
| [ Preview envelope schema ]                                                     |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

# 11. INTERLIS Structure Explode Dialog

```text
+================================================================================+
| INTERLIS Structure Explode                                                     |
+================================================================================+
| Source object field [ _ili_source_object                                  v]    |
| Parent TID field    [ _ili_tid                                            v]    |
|                                                                                |
| Structure                                                                     |
| [ Qualities : LIST {0..*} OF Quality                                     v]    |
|                                                                                |
| Child row                                                                      |
| Parent field      [ _ili_parent_tid                                       ]    |
| Index field       [ _ili_index                                            ]    |
|                                                                                |
| Child fields                                                                   |
| +--------------------------------------------------------------------------+   |
| | Use | Property                | Output field         | Type               |   |
| | [x] | Code                    | Code                 | String             |   |
| | [x] | Date                    | Date                 | Date               |   |
| | [x] | Geometry                | Geometry             | Geometry           |   |
| +--------------------------------------------------------------------------+   |
|                                                                                |
| Copy parent fields                                                             |
| [ Add... ]  BFSNo, DatasetId                                                   |
|                                                                                |
| Preview                                                                         |
| _ili_parent_tid, _ili_index, Code, Date, Geometry                               |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

Wenn BAG:

```text
Index field [ _ili_index ]  [x] Emit technical stable index
Note: BAG order has no INTERLIS semantics. The index is only for deterministic
pipeline processing and may be omitted on write.
```

# 12. INTERLIS Structure Collect Dialog

```text
+================================================================================+
| INTERLIS Structure Collect                                                     |
+================================================================================+
| Parent input transform [ Persons                                            v] |
| Child input transform  [ Addresses                                          v] |
|                                                                                |
| Parent key field      [ _ili_tid                                             ] |
| Child parent key      [ _ili_parent_tid                                      ] |
|                                                                                |
| Structure             [ Addresses : LIST OF Address                        v]   |
| Index field           [ _ili_index                                         v]   |
|                                                                                |
| [x] Strict LIST ordering                                                       |
| [x] Fail on duplicate index                                                    |
| [x] Fail on child without parent                                               |
|                                                                                |
| Output                                                                         |
| (*) INTERLIS envelope                                                          |
| ( ) Parent rows + updated source object                                         |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

# 13. INTERLIS Role Join Dialog

```text
+================================================================================+
| INTERLIS Role Join                                                             |
+================================================================================+
| Main input     [ Buildings                                                 v]   |
| Lookup input   [ Municipalities                                            v]   |
|                                                                                |
| Role           [ Municipality -> Municipality {1}                          v]   |
|                                                                                |
| Join mapping                                                                    |
| Main ref field [ Municipality_ref                                           ]   |
| Target TID     [ _ili_tid                                                   ]   |
|                                                                                |
| Fields to add                                                                  |
| [x] Name                  -> Municipality_Name                                  |
| [x] BfsNo                 -> Municipality_BfsNo                                 |
| [ ] Geometry              -> Municipality_Geometry                              |
|                                                                                |
| Prefix          [ Municipality_                                             ]   |
|                                                                                |
| Missing reference                                                              |
| (*) Error if role is mandatory                                                  |
| ( ) Output null fields                                                          |
|                                                                                |
| Lookup protection                                                              |
| Max lookup rows [ 500000                                                   ]    |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

# 14. INTERLIS Validate Dialog

```text
+================================================================================+
| INTERLIS Validate                                                              |
+================================================================================+
| File            [ ${PROJECT_HOME}/data/input.xtf                           ]... |
| Models          [ %DATA                                                   v]    |
| Model dirs      [ %XTF_DIR;https://models.interlis.ch;https://models.geo.admin.ch ]     |
|                                                                                |
| Validator config [                                                        ]... |
| Meta config      [                                                        ]... |
|                                                                                |
| [x] Validate attribute/role multiplicity                                       |
| [ ] Stop after first error                                                      |
| Max errors        [ 10000                                                  ]    |
|                                                                                |
| Output severities                                                              |
| [x] Error   [x] Warning   [ ] Info                                              |
|                                                                                |
| Output schema preview                                                          |
| severity, message, source_file, line, column, model, topic, basket_id,          |
| class_name, object_id, attribute_path, constraint_name                          |
|                                                                                |
|                                                     [ OK ] [ Cancel ]            |
+================================================================================+
```

# 15. Validation Section in Input/Output

Collapsed by default:

```text
v Validation
  [ ] Validate while reading

  Validator config [                                                ] [...]
  Meta config      [                                                ] [...]

  [x] Validate attribute/role multiplicity
  Error handling [ Fail transform                                  v]
```

Bei Output:

```text
[x] Validate generated transfer
```

Für grosse Dateien muss GUI erklären, ob die gewählte Methode zusätzliche Passes benötigt.

# 16. Advanced Section

Input Beispiel:

```text
v Advanced
  [ ] Include _ili_class
  [ ] Include _ili_topic
  [ ] Include _ili_operation
  [x] Keep source object because selected structures need it

  Default SRID [       ]
  [ ] Force default SRID even if model CRS mapping is available

  Delete objects
  (*) Emit matching deletes with _ili_operation=DELETE
  ( ) Ignore
  ( ) Fail

  Inheritance
  (*) Exact selected transfer class
  ( ) Include subclasses [future]
```

# 17. Tooltips und Erklärtexte

Tooltips sollen modellsemantisch sein.

Beispiele:

```text
_ili_tid
"INTERLIS object identifier (TID/OID) as transferred. Kept as String."

_ili_bid
"INTERLIS basket identifier containing this object."

Municipality_ref
"Reference to role 'Municipality'. Value is the target object's TID."

_ili_index
"Zero-based order of an INTERLIS LIST element. LIST order is semantically
relevant and must be preserved when writing."
```

# 18. Dialog-Grössen und Verhalten

- Hauptdialog Input/Output minimum ca. 900x700.
- Resizable.
- Model Browser horizontal geteilt.
- Tabellen wachsen mit Dialog.
- OK/Cancel konsistent mit Hop.
- Transform name oben.
- Tab order sinnvoll.
- Keine modalen Fehlermeldungen bei jeder Auto-Probe; Statusbereich verwenden.
- Harte Fehlermeldung erst bei OK, wenn Konfiguration offensichtlich ungültig ist.

# 19. Internationalisierung

Code darf zunächst englische Plugin-/GUI-Texte enthalten, sollte aber von Anfang an Hop-i18n-fähig strukturiert werden.

Keys z.B.:

```text
InterlisInput.Name
InterlisInput.Description
InterlisInputDialog.File.Label
InterlisInputDialog.Class.Label
InterlisInputDialog.ModelStatus.Loaded
InterlisStructureExplodeDialog.ListOrder.Help
```

Deutsch kann als zweite Sprache ergänzt werden, ohne Java-Code zu ändern.

# 20. Preview / Test Button

Spätere UX-Verbesserung:

```text
[ Preview first 20 rows ]
```

Der Button darf nicht eine separate improvisierte Reader-Implementierung besitzen. Er ruft denselben Core Reader + Mapper wie Runtime auf.

Preview-Fenster:

```text
+----------------------------------------------------------------------------+
| Preview: Model.Topic.Building                                               |
+----------------------------------------------------------------------------+
| _ili_tid | Name | Type | Geometry | Municipality_ref | ...                 |
| ...                                                                        |
+----------------------------------------------------------------------------+
| 20 rows shown; 0 mapping errors                                             |
+----------------------------------------------------------------------------+
```

# 21. Warnungen für problematische Modellkonstrukte

Beispiel mehrfach geschachtelte LIST:

```text
! Property Inspections.Measurements is nested below a LIST structure.
  It cannot be represented in the parent scalar row.
  Explode Inspections first, then explode Measurements in a second step.
```

Beispiel komplexe Assoziation:

```text
Association Ownership is many-to-many and has association attributes.
It is represented as its own row type and cannot be flattened into Parcel.
```

# 22. UX für Assoziationen im Model Browser

```text
v Parcel
  A Number
  G Geometry
  R Owners -> Person {0..*}
      via Association Ownership
      association attributes:
        Share
        Since
```

Rechts:

```text
Relationship mapping

This relationship is many-to-many.
Recommended representation: association rows.

Association row schema:
  parcel_ref
  owner_ref
  Share
  Since

[ Select association as input class ]
```

Damit erklärt das GUI aktiv, wie das INTERLIS-Modell in Hop gedacht wird.

# 23. Konsistenz mit GeoTools-Plugin

Look & feel soll an das bestehende GeoTools-Plugin anschliessen:

- `BaseTransformDialog`
- `PropsUi.setLook`
- `TextVar`, `ComboVar`
- Schema Preview als robuste Design-Time-Hilfe
- unresolved Variables nicht als fataler Dialogfehler
- gemeinsamer `Geospatial`-Kategorieeintrag

Langfristig können INTERLIS-Transforms in einer Unterkategorie erscheinen, falls Hop-Kategorien dies sinnvoll unterstützen; für den Start bleibt `Geospatial` konsistent.

## P2: Asynchrone Modellprobe

Input, Output, Object to Row, Row to Object, Structure Explode/Collect und Role Join
verwenden einen gemeinsamen Probe-Koordinator: 300 ms Debounce, eine laufende und
eine ersetzbare Anfrage pro Dialog. Explizites Reload startet ohne Debounce und
invalidiert den kompilierten Cache. Konfiguration und Variablen werden vor dem
Hintergrundlauf kopiert. Nur die aktuelle Anfrage darf Ergebnisse im SWT-Thread
anzeigen; Schliessen des Dialogs verwirft ausstehende Ergebnisse. Probe-Status ist
typisiert und wird nicht aus englischen Meldungstexten abgeleitet.
