# Architektur und Datenmodell

## 1. Problemstellung

FME und Apache Hop besitzen unterschiedliche interne Datenmodelle:

- FME transportiert Features, die ihren Feature Type und eine Hauptgeometrie mit sich führen.
- Hop transportiert `Object[]`-Rows zusammen mit `IRowMeta`. Das Row-Schema gehört zum Stream und muss für normale Verarbeitung stabil bleiben.

INTERLIS wiederum besitzt:

- Klassen und Strukturen,
- Vererbung,
- Attribute und Rollen,
- ein- und mehrwertige Strukturen,
- Assoziationen mit eigenen Attributen,
- OIDs/TIDs,
- Topics und Baskets,
- Transferoperationen,
- mehrere Geometrieattribute pro Klasse,
- Kreisbögen,
- 2D/3D-Geometrien,
- Enumerationen,
- optional geordnete Rollen und Listen.

Die Architektur muss deshalb INTERLIS-Semantik erhalten, ohne die normale Hop-Welt mit wechselnden oder riesigen Sparse-Schemas zu belasten.

## 2. Vier Architekturschichten

```text
+---------------------------------------------------------------+
| 4. Hop user layer                                             |
|    normal Object[] + stable IRowMeta                          |
|    String / Long / BigDecimal / Date / Geometry / ...         |
+-------------------------------^-------------------------------+
                                |
                                | project / collect
+-------------------------------+-------------------------------+
| 3. Mapping layer                                               |
|    RowSchemaPlan                                               |
|    IomToRowMapper / RowToIomMapper                             |
|    StructureMapper / AssociationMapper                         |
+-------------------------------^-------------------------------+
                                |
+-------------------------------+-------------------------------+
| 2. Canonical INTERLIS envelope                                 |
|    InterlisObjectEnvelope                                      |
|    class, topic, bid, oid, operation, IomObject                |
+-------------------------------^-------------------------------+
                                |
+-------------------------------+-------------------------------+
| 1. IO/model layer                                              |
|    ili2c TransferDescription / model repositories              |
|    iox-ili IoxReader/IoxWriter                                 |
|    validation / XTF / ITF later                                |
+---------------------------------------------------------------+
```

Die Trennung zwischen Layer 2 und Layer 4 ist zentral. Sie verhindert, dass ein XTF mit zehn Klassen in einem Hop-Stream zehn verschiedene Schemata benötigt.

## 3. Canonical INTERLIS envelope

### 3.1 Java-Repräsentation

```java
public final class InterlisObjectEnvelope implements Serializable {
  private final InterlisEventType eventType;
  private final String modelName;
  private final String topicName;
  private final String basketId;
  private final String className;
  private final String objectId;
  private final InterlisObjectOperation operation;
  private final Iom_jObject object;
  private final InterlisSourceLocation sourceLocation;
  private final Map<String, String> transferMetadata;
}
```

`Iom_jObject` wird bewusst als konkrete serialisierbare Kopie gehalten. Ein vom Reader gelieferter beliebiger `IomObject` wird beim Eintritt in den Envelope gegebenenfalls kopiert.

### 3.2 Event-Type

```java
public enum InterlisEventType {
  START_TRANSFER,
  START_BASKET,
  OBJECT,
  DELETE_OBJECT,
  END_BASKET,
  END_TRANSFER
}
```

Im normalen Objektmodus werden primär `OBJECT` und optional `DELETE_OBJECT` sichtbar. Der vollständige Eventstrom ist Advanced-Funktionalität.

### 3.3 Object operation

```java
public enum InterlisObjectOperation {
  INSERT,
  UPDATE,
  DELETE,
  NONE
}
```

Die Abbildung erfolgt aus den IOX/IOM-Operationen. Für normale vollständige Transfers ist `NONE` bzw. die semantisch passende Default-Operation üblich.

### 3.4 Standardfelder eines Envelope-Hop-Streams

```text
_ili_event_type     String
_ili_model          String
_ili_topic          String
_ili_bid            String
_ili_class          String
_ili_tid            String
_ili_operation      String
_ili_object         InterlisObject
_ili_line           Integer
_ili_column         Integer
```

Die Positionen sind konstant und werden in `InterlisEnvelopeRowLayout` zentral
definiert (Phase 5 umgesetzt; die Schema-Factory `InterlisEnvelopeSchemaFactory`
und alle generischen Transforms verwenden exakt dieses Layout).

## 4. Typed row projection

Ein `RowSchemaPlan` beschreibt exakt, wie ein bestimmter INTERLIS-Class-Descriptor auf ein Hop-Row-Schema projiziert wird.

```java
public record RowSchemaPlan(
    InterlisClassDescriptor classDescriptor,
    List<RowFieldPlan> fields,
    StructureProjectionPolicy structurePolicy,
    RoleProjectionPolicy rolePolicy,
    boolean includeTid,
    boolean includeBid,
    boolean includeClassName,
    boolean includeTopicName) {
}
```

### 4.1 RowFieldPlan

```java
public record RowFieldPlan(
    String hopFieldName,
    IValueMeta valueMeta,
    FieldSource source,
    InterlisPropertyPath propertyPath,
    NullPolicy nullPolicy,
    ValueConverter converter,
    Map<String, String> semanticMetadata) {
}
```

`FieldSource`:

```java
public enum FieldSource {
  OBJECT_ID,
  BASKET_ID,
  CLASS_NAME,
  TOPIC_NAME,
  PRIMITIVE_ATTRIBUTE,
  GEOMETRY_ATTRIBUTE,
  FLATTENED_STRUCTURE_ATTRIBUTE,
  ROLE_REFERENCE,
  ASSOCIATION_ATTRIBUTE,
  ORDER_POSITION
}
```

## 5. Model Descriptor Layer

`TransferDescription` ist sehr mächtig, aber nicht als UI- und Mapping-API geeignet. Das Plugin baut darum aus ili2c-Metamodellobjekten immutable eigene Deskriptoren.

### 5.1 InterlisModelDescriptor

```java
public record InterlisModelDescriptor(
    String name,
    String version,
    String issuer,
    List<InterlisTopicDescriptor> topics,
    Map<String, InterlisElementDescriptor> elementsByScopedName) {
}
```

### 5.2 InterlisTopicDescriptor

```java
public record InterlisTopicDescriptor(
    String scopedName,
    String name,
    List<InterlisClassDescriptor> classes,
    List<InterlisAssociationDescriptor> associations,
    List<InterlisStructureDescriptor> structures) {
}
```

### 5.3 InterlisClassDescriptor

```java
public record InterlisClassDescriptor(
    String scopedName,
    String name,
    String topicScopedName,
    boolean identifiable,
    InterlisOidDescriptor oid,
    String baseClassScopedName,
    List<InterlisPropertyDescriptor> declaredProperties,
    List<InterlisPropertyDescriptor> effectiveProperties) {
}
```

`effectiveProperties` enthält standardmässig geerbte Attribute/Rollen in stabiler Modellreihenfolge.

### 5.4 Properties

```java
public sealed interface InterlisPropertyDescriptor
    permits InterlisAttributeDescriptor,
            InterlisRoleDescriptor,
            InterlisStructureAttributeDescriptor {
  String name();
  String scopedName();
  Cardinality cardinality();
}
```

### 5.5 InterlisAttributeDescriptor

Wichtige Informationen:

```text
name
scopedName
kind               PRIMITIVE | ENUM | GEOMETRY
mandatory
cardinality
iliType
textMaxLength
numericMin/numericMax
numericScale
unit
formattedType
geometryKind       COORD | MULTICOORD | POLYLINE | MULTIPOLYLINE | SURFACE | AREA | MULTISURFACE
coordDimension     2 | 3
crsHint            optional
allowsArcs
enumerationDescriptor
```

### 5.6 Structure descriptor

```java
public record InterlisStructureAttributeDescriptor(
    String name,
    String scopedName,
    Cardinality cardinality,
    CollectionKind collectionKind,
    InterlisStructureDescriptor structure) implements InterlisPropertyDescriptor {}
```

`CollectionKind`:

```java
SINGLE
BAG
LIST
```

### 5.7 Role descriptor

```java
public record InterlisRoleDescriptor(
    String name,
    String scopedName,
    Cardinality cardinality,
    String targetClassScopedName,
    boolean ordered,
    boolean embeddedInTransfer,
    String associationScopedName,
    List<InterlisAttributeDescriptor> associationAttributes) implements InterlisPropertyDescriptor {}
```

`embeddedInTransfer` ist Transferwissen. Es darf für Writer/Reader relevant sein, aber die normale UX nicht dominieren.

## 6. Datentyp-Mapping

### 6.1 Primitive Typen

| INTERLIS | Hop Meta | Java-Wert | Bemerkung |
|---|---|---|---|
| TEXT, MTEXT, NAME, URI | `ValueMetaString` | `String` | Länge aus Modell setzen |
| BOOLEAN | `ValueMetaBoolean` | `Boolean` | `true`/`false` |
| INT | `ValueMetaInteger` | `Long` | Bereich beim Write prüfen |
| numerischer Dezimaltyp | `ValueMetaBigNumber` | `BigDecimal` | Präzision nicht über `double` verlieren |
| ENUMERATION | `ValueMetaString` | `String` | vollständiger Enumeration-Elementname |
| DATE | `ValueMetaDate` oder geeigneter Date-Typ | `Date` | Format exakt definieren |
| DATETIME | `ValueMetaTimestamp` | Timestamp/Date | Zeitzonenregeln dokumentieren |
| OID/TID | `ValueMetaString` | `String` | nie numerisch interpretieren |
| Reference | `ValueMetaString` | `String` | Ziel-TID |
| BINARY/BLOB falls relevant | `ValueMetaBinary` | `byte[]` | spätere Vollständigkeit |

Für numerische INTERLIS-Typen ist `BigDecimal` die Default-Wahl. Nur wenn das Modell eindeutig ganzzahlig ist, wird `Long` verwendet.

### 6.2 Metadaten an IValueMeta

Wo Hop benutzerdefinierte Metainformationen sauber zulässt, sollen semantische Informationen mitgeführt werden, beispielsweise:

```text
ili.kind=ROLE
ili.scopedName=Model.Topic.Building.municipality
ili.target=Model.Topic.Municipality
ili.min=1
ili.max=1
```

Falls die direkte Persistenz benutzerdefinierter Attribute an `IValueMeta` in Hop nicht stabil genug ist, bleibt diese Information in `RowSchemaPlan` und in der Transform-Metakonfiguration. Die Runtime-Korrektheit darf nicht von nicht-standardisierten `IValueMeta`-Properties abhängen.

## 7. Geometriearchitektur

### 7.1 Verbindliche Vorgabe

Alle nach aussen sichtbaren Geometriefelder verwenden:

```java
com.atolcd.hop.core.row.value.ValueMetaGeometry
```

und Runtime-Werte aus:

```java
org.locationtech.jts.geom.Geometry
```

inklusive der im Geometry-Plugin bereitgestellten Curve-Unterklassen.

### 7.2 Kein `Iox2jts` als primäre Brücke

iox-ili enthält historische APIs auf Basis `com.vividsolutions.jts`. Diese Klassen dürfen nicht in den Hop-Row-Stream gelangen.

Zusätzlich kann eine klassische JTS-Abbildung Kreisbögen linearisieren. Darum wird die primäre Brücke über SQL/MM WKB implementiert.

### 7.3 IOM -> Hop Geometry

```text
IomObject geometry
      |
      v
Iox2wkb
  asCompoundCurve=true
  asCurvePolygon=true
      |
      v
SQL/MM WKB bytes
      |
      v
CurveGeometrySupport.readWkb(...)
      |
      v
Hop Geometry value
```

Der konkrete Aufruf hängt vom Geometry-Kind ab:

- COORD -> `Iox2wkb.coord2wkb`
- MULTICOORD -> `multicoord2wkb`
- POLYLINE -> `polyline2wkb(..., asCompoundCurve=true, ...)`
- SURFACE/AREA -> CurvePolygon-fähiger WKB-Aufruf
- MULTI* analog

### 7.4 Hop Geometry -> IOM

```text
Hop Geometry
    |
    v
CurveGeometrySupport.writeWkb(...)
    |
    v
SQL/MM WKB bytes
    |
    v
Wkb2iox.read(...)
    |
    v
IomObject geometry structure
```

Die Zuordnung wird gegen den erwarteten INTERLIS-Geometrietyp validiert.

### 7.5 SRID

INTERLIS-Geometrietypen definieren Koordinatendomänen; ein EPSG-Code ist nicht zwingend direkt aus dem Objekt ableitbar. Das Plugin darf daher kein SRID erfinden.

Regeln:

1. Wenn die Modell-/Metakonfiguration eindeutig ein CRS/EPSG-Mapping liefert, wird `Geometry.setSRID(...)` gesetzt.
2. Wenn kein eindeutiges Mapping existiert, bleibt SRID 0.
3. Ein konfigurierbarer `Default SRID` darf angeboten werden, muss im GUI klar als Override gekennzeichnet sein.
4. Beim Schreiben ist ein gesetztes SRID nur Metainformation; die Zulässigkeit der Koordinaten wird durch das Modell/Validator bestimmt.

## 8. Mehrere Geometrieattribute

Anders als FME benötigt Hop keine einzige Hauptgeometrie.

Beispiel:

```interlis
CLASS RoadElement =
  Axis : POLYLINE;
  Footprint : SURFACE;
END RoadElement;
```

Hop:

```text
_ili_tid       String
Axis           Geometry
Footprint      Geometry
```

Beide Geometrien bleiben gleichwertige Felder. Der Benutzer kann später für einen Vector Writer explizit das gewünschte Geometry-Feld auswählen.

## 9. Einwertige Strukturen

Beispiel:

```interlis
STRUCTURE Address =
  Street : TEXT*80;
  Number : TEXT*10;
END Address;

CLASS Person =
  Name : TEXT*100;
  Address : Address;
END Person;
```

Default-Projektion:

```text
_ili_tid
Name
Address_Street
Address_Number
```

### 9.1 Flattening-Regeln

- Default für Cardinality `0..1` und `1`.
- Präfix default: `<attributeName>_`.
- Rekursives Flattening ist erlaubt, aber maximale Tiefe konfigurierbar.
- Namenskollisionen werden beim Design-Time-Schema erkannt.
- Mehrwertige Unterstrukturen unter einer einwertigen Struktur werden nicht in unbegrenzt viele Spalten expandiert; sie bleiben Kandidaten für `Structure Explode`.

### 9.2 Null-Semantik

Wenn die gesamte Struktur fehlt:

```text
Address_Street = null
Address_Number = null
```

Beim Rückschreiben gilt:

- Sind alle projizierten Strukturfelder `null`, wird die optionale Struktur nicht erzeugt.
- Ist mindestens eines gesetzt, wird eine Struktur erzeugt.
- Bei mandatory Struktur führt eine vollständig leere Row je nach Write-Policy zu Fehler oder Validation Error.

## 10. BAG/LIST OF STRUCTURE

Mehrwertige Strukturen werden nicht in Felder `Address_1`, `Address_2`, ... expandiert.

### 10.1 Parent-Stream

```text
_ili_tid | Name | ...
123      | Meier
456      | Mueller
```

### 10.2 Child-Stream

`INTERLIS Structure Explode` erzeugt:

```text
_ili_parent_tid | _ili_index | Street          | Number
123         | 0          | Main Street     | 10
123         | 1          | Station Street  | 4
456         | 0          | Village Road    | 22
```

Für `LIST` ist `_ili_index` semantisch relevant und muss erhalten bleiben.

Für `BAG` ist Reihenfolge fachlich nicht relevant. Das Plugin darf für stabilen Roundtrip intern dennoch einen Index mitführen; im GUI wird dieser als optional/technisch markiert.

### 10.3 Parent identity

Wenn die Parent-Klasse eine TID besitzt, ist `_ili_parent_tid` Default.

Für nicht-identifizierbare Parent-Kontexte bzw. intern verschachtelte Strukturen braucht der Exploder zusätzlich eine Runtime-Korrelation:

```text
_ili_parent_key
```

Diese wird vom Plugin deterministisch pro Parent-Row erzeugt und ist nur für Pipeline-internes Collect nötig. Sie darf nicht als INTERLIS-OID ausgegeben werden.

### 10.4 Collect

`INTERLIS Structure Collect` erhält:

```text
Parent stream  ----\
                  +--> collect by _ili_tid/_ili_parent_key --> enriched parent/envelope
Child stream   ----/
```

LIST wird nach `_ili_index` sortiert. Fehlende oder doppelte Indizes sind Fehler, sofern `strict order` aktiv ist.

## 11. Rollen und einfache Assoziationen

### 11.1 Default-Darstellung

Eine einfache Rolle wird als Referenzfeld ausgegeben:

```text
municipality_ref : String
```

Der Wert ist die referenzierte Objekt-TID.

Optional können bei externen Referenzen zusätzliche Felder erscheinen:

```text
municipality_ref
municipality_ref_bid
```

### 11.2 Einbettung ist kein UX-Konzept

Ob die Beziehung im XTF als Embedded Link oder als eigenes Link-Objekt codiert ist, ist eine Transferfrage. Die Benutzeroberfläche zeigt primär:

```text
municipality -> Municipality {1}
```

und nicht:

```text
EmbeddedLinkStruct
```

### 11.3 Association attributes

Bei einer einfachen, eindeutig einbettbaren Assoziation mit Attributen kann der Benutzer wählen:

```text
owner_ref
owner_share
owner_since
```

Das ist eine Komfortprojektion. Intern weiss `RowSchemaPlan`, dass `share` und `since` Assoziationsattribute und nicht Attribute der Zielklasse sind.

**Transfer-Realität (Phase 4 verifiziert):** Attributierte Assoziationen werden in XTF immer als **separate Link-Objekte** übertragen; embedded REF-Elemente tragen keine Attribute. Die geflatteten Felder werden deshalb aus dem Link-Objekt aufgelöst: `INTERLIS Input` puffert die Link-Objekte pro Basket und emittiert Klassenzeilen am Basket-Ende. Beim Schreiben erzeugt `INTERLIS Output` das Link-Objekt aus den geflatteten Feldern (`<role>_ref` + Attribute) zusätzlich zur Klassenzeile.

## 12. m:n-, n-äre und komplexe Assoziationen

Solche Assoziationen werden als eigene Row-Typen behandelt.

Beispiel:

```text
Membership
----------
person_ref
organisation_ref
function
entryDate
```

Der Association-Descriptor besitzt eine stabile eigene Projektion.

### 12.1 Identifizierbare Assoziationen

Falls die Association eine OID/TID besitzt:

```text
_ili_tid
person_ref
organisation_ref
...
```

Diese TID wird erhalten.

### 12.2 Geordnete Rollen

Für jede `ORDERED`-Rolle wird zusätzlich ausgegeben:

```text
<role>_order_pos
```

(Bei genau einer ORDERED-Rolle entspricht das der Roadmap-Beispielspalte `_ili_order_pos`.)

Beim Write wird die Reihenfolge bzw. `order_pos` wiederhergestellt (`ili:order_pos` auf dem REF-Member des Link-Objekts).

## 13. INTERLIS Role Join

Der Transform ist Komfort, kein notwendiger Bestandteil des Datenmodells.

Input A:

```text
Building
_ili_tid
municipality_ref
...
```

Input B:

```text
Municipality
_ili_tid
Name
BfsNo
```

Konfiguration:

```text
Role: municipality -> Municipality {1}
Target TID field: auto (_ili_tid)
Add fields: Name, BfsNo
Prefix: municipality_
```

Output:

```text
_ili_tid
municipality_ref
municipality_Name
municipality_BfsNo
```

Wichtig: Der Join kann modellgetrieben die korrekten Schlüssel vorschlagen, ist runtime-seitig aber ein normaler Stream-Lookup/Join. Für grosse Zielstreams muss eine Strategie gewählt werden:

- in-memory lookup für kleine Referenzdaten,
- sorted/streaming join später,
- optional delegieren an bestehende Hop-Transforms.

Phase 4 implementiert den in-memory Lookup mit klarer Warnung/Limit-Konfiguration
(`maxLookupRows`, Default 500'000); Hop 2.x liest beide Ströme explizit via
`findInputRowSet` (kein Info-Hop-Konzept mehr).

## 14. Vererbung

### 14.1 Default-ETL-Projektion

Für eine konkrete Klasse werden alle effektiven geerbten Attribute/Rollen in einem flachen Schema ausgegeben.

```text
BaseObject
  createdAt

Building EXTENDS BaseObject
  name
  geometry

Hop row for Building:
_ili_tid
createdAt
name
geometry
```

### 14.2 Transfer-Tag bleibt erhalten

Bei Advanced Envelope bleibt `_ili_class` der tatsächlich transferierte Tag.

### 14.3 Optionale spätere Strategien

Zur Kompatibilität mit Spezialworkflows können später Strategien analog `SuperClass`/`SubClass` angeboten werden. Sie sind nicht Default und nicht Voraussetzung für die erste vollständige Hop-native Lösung.

## 15. Enumerationen

Default in normalen Rows:

```text
String = canonical enumeration element path/name
```

Kein numerischer ITF-Code im XTF-Default.

Ein separater `INTERLIS Enumerations`-Input kann später ein Modell als Lookup-Daten ausgeben:

```text
enum_definition
enum_value
enum_path
parent_value
depth
is_leaf
```

Dies ist für Mapping- und GUI-Pipelines nützlich.

## 16. OID/TID

Regeln:

- TIDs werden immer als String transportiert.
- Keine implizite Neunummerierung bei XTF.
- Writer unterstützt Policy:
  - `REQUIRE_INPUT`
  - `GENERATE_UUID`
  - später modellabhängige Generatoren.
- `GENERATE_UUID` ist nur zulässig, wenn die Modell-OID-Domain dies erlaubt.
- Unique-OID-Check kann beim Reader/Writer optional aktiviert werden.

## 17. Baskets

### 17.1 Normaler Modus

Jede Object-Row enthält:

```text
_ili_bid
```

Optional:

```text
_ili_topic
```

Damit kann ein Transfer mit mehreren Baskets gelesen und gruppiert verarbeitet werden.

### 17.2 Writer Basket policy

```java
public enum BasketWritePolicy {
  FROM_FIELD,
  SINGLE_BASKET,
  GROUP_BY_BID
}
```

`FROM_FIELD`/`GROUP_BY_BID` sind im normalen Row Writer praktisch identisch; `SINGLE_BASKET` erlaubt einen festen konfigurierten BID.

### 17.3 Basket metadata

Spätere Phase:

```text
_ili_bid
_ili_topic
_ili_basket_start_state
_ili_basket_end_state
_ili_consistency
_ili_domains
```

Diese Informationen gehören in einen separaten Basket-Metadatastream oder in Event-Envelopes, nicht in jede normale Business-Row, sofern nicht explizit gewünscht.

## 18. Delete Objects / Incremental Transfer

Advanced mode muss `DELETE_OBJECT` repräsentieren können.

Minimaler Row:

```text
_ili_event_type = DELETE_OBJECT
_ili_class
_ili_tid
_ili_bid
_ili_operation = DELETE
```

Das normale `INTERLIS Input` kann per Option entscheiden:

```text
Delete objects:
(*) Ignore
( ) Emit as rows with _ili_operation=DELETE
( ) Fail if encountered
```

Default zunächst: `Emit`, wenn die ausgewählte Klasse betroffen ist; der Benutzer verliert damit keine Information unbemerkt.

## 19. Lossless/Event mode

Advanced `INTERLIS Transfer Input` kann später zwei Modi bieten:

### OBJECTS

Nur Objekt-/Delete-Envelopes, Transfer- und Basketdaten als Felder.

### EVENTS

Exakter IOX-Ereignisstrom:

```text
START_TRANSFER
START_BASKET
OBJECT
OBJECT
END_BASKET
START_BASKET
...
END_TRANSFER
```

Der Event-Modus ist für:

- Diagnostik,
- Spezialtransformationen,
- möglichst genaue Transferreproduktion,
- Basket-Metadaten,
- inkrementelle Transfers.

Normale Benutzer brauchen ihn nicht.

## 20. Validierung

Validierung ist in drei Stellen möglich:

1. `INTERLIS Input` optional während Read.
2. `INTERLIS Output` optional vor/bei Write.
3. separater `INTERLIS Validate` Transform.

Der separate Transform ist wichtig, damit ETL-Pipelines Validierung als eigene fachliche Stufe modellieren können.

### 20.1 Fehlerdatenmodell

Validation errors werden als normale Rows repräsentiert (Phase 6 umgesetzt;
Namen mit `_ili_`-Präfix, zentral in `InterlisValidationRowLayout`):

```text
_ili_severity
_ili_message
_ili_source_file
_ili_line
_ili_column
_ili_model
_ili_topic
_ili_bid
_ili_class
_ili_tid
_ili_attribute_path
_ili_constraint_name
_ili_raw_event_type
```

Wo iox-ili/Validator nicht alle Werte liefert, bleiben Felder `null`.

## 21. Fehlerstrategie

Fehlerklassen:

```java
InterlisConfigurationException
InterlisModelException
InterlisReadException
InterlisWriteException
InterlisMappingException
InterlisGeometryException
InterlisStructureException
InterlisAssociationException
InterlisValidationException
```

Jede Exception enthält, wo verfügbar:

```text
className
objectId
basketId
propertyPath
sourceLine
sourceColumn
```

### 21.1 Strict vs lenient

Einige Mappings können lenient sein, aber Defaults bleiben sicher:

- unbekannte Modellklasse: strict error
- unbekanntes Attribut im XTF: Reader/iox entscheidet nach Standard; nicht still verschlucken
- ungültige Zahl: error
- unbekannte Enumeration: error
- Geometry type mismatch: error
- fehlende optionale Struktur: null
- fehlende mandatory Struktur: error oder Validator error gemäss Konfiguration

## 22. Performance-Grundsätze

- Modelle werden pro Konfiguration kompiliert und gecacht.
- `RowSchemaPlan` wird einmal pro Transform initialisiert.
- Feldpositionen werden einmal bestimmt, nicht pro Row über Namen gesucht.
- Converter werden im Plan vorkompiliert.
- Geometry-WKB-Converter dürfen wiederverwendbare Helfer nutzen, sofern thread-safe.
- Reader bleibt streaming; niemals gesamtes XTF in Memory laden.
- `Structure Explode` arbeitet rowweise.
- `Structure Collect` benötigt je nach Pipelineform Pufferung; Phase 3 dokumentiert Speichergrenzen.
- `Role Join` initial zunächst in-memory mit konfigurierbarer Schutzgrenze.

## 23. Threading

Hop kann Transform-Kopien parallel ausführen. Daher:

- `*Meta` enthält nur Konfiguration.
- `*Data` enthält Runtime-Zustand pro Transform-Kopie.
- `InterlisModelCache` darf global/shared sein, aber gecachte Deskriptoren und `TransferDescription` werden nach Erstellung als read-only behandelt.
- IOX Reader/Writer/Validator werden nie zwischen Transform-Kopien geteilt.
- Mutable Geometry-Converter werden pro Transform oder per ThreadLocal verwendet.

## 24. Zukunftssicherheit ITF

ITF wird später unter derselben Schichtenarchitektur ergänzt.

`InterlisTransferReaderFactory` liefert je nach Format eine Implementierung:

```java
interface InterlisTransferReader extends AutoCloseable {
  InterlisTransferEvent next() throws InterlisReadException;
  TransferDescription transferDescription();
}
```

Implementierungen:

```text
XtfInterlisTransferReader
ItfInterlisTransferReader
```

Die Typed Mapping Layer bleibt damit weitgehend identisch. ITF-spezifische Linetable-/AREA-Logik sitzt am IO-Rand.



## P1-Präzisierung der Geometriedimension (2026-09-09)

Die Koordinatendimension im Attributdescriptor stammt bei Linien, Flächen und
Multi-Geometrien aus der aufgelösten Koordinatendomäne (`LineType.controlPointDomain`).
Alias- und Vererbungsketten sowie unterstützte CHLV95-Wrapper verwenden denselben
Vertrag. Eine unauflösbare Dimension führt bei der Konvertierung zu einer
verständlichen Diagnose; es gibt keinen stillen 2D-Ersatz.

Gerade 3D-Geometrien verwenden den WKB-Pfad ohne Curve-Container und behalten XYZ.
2D-Kurven bleiben SQL/MM-Kurven. Die gemeinsame Geometry-Bibliothek unterstützt
3D-Kurven derzeit weder beim WKB-Lesen noch beim WKB-Schreiben: Ein tatsächlicher
3D-ARC wird explizit abgelehnt und nie still linearisiert.
