# Java- und Hop-Implementierungsspezifikation

Dieses Dokument ist die implementierungsnahe Kern-Spezifikation. Klassennamen und Methodensignaturen sind als Soll-Architektur zu verstehen. Bei kleinen Anpassungen an tatsächliche Hop-2.18- oder iox-ili-Signaturen darf die Implementierung abweichen, solange Verantwortlichkeiten und Tests erhalten bleiben.

# 1. Maven-Module und Abhängigkeiten

## 1.1 Parent

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
  <hop.version>2.18.1</hop.version>
  <iox.ili.version>1.24.4</iox.ili.version>
  <ili2c.version>5.6.8</ili2c.version>
  <hop.geometry.type.version>0.1.0-SNAPSHOT</hop.geometry.type.version>
  <junit.version>5.12.0</junit.version>
  <assertj.version>3.27.3</assertj.version>
</properties>
```

Versionen werden nur im Parent gepflegt.

## 1.2 `hop-interlis-core`

Abhängigkeiten:

```text
ch.interlis:iox-ili
ch.interlis:ili2c-core
ch.interlis:ili2c-tool       falls für Repository-/CLI-nahe Hilfsklassen benötigt
org.apache.hop:hop-core      möglichst schlank, nur wenn IRowMeta-Planer hier liegt
hop-geometry-type            provided
jts-core                     provided
```

Empfehlung: Modell- und IOM-Logik möglichst Hop-neutral halten; `HopRowSchemaFactory` kann alternativ im transforms-Modul liegen.

## 1.3 `hop-interlis-valuetype`

```text
org.apache.hop:hop-core
ch.interlis:iox-ili
```

`ValueMetaInterlisObject` wird als Advanced-Transporttyp registriert.

## 1.4 `hop-interlis-transforms`

```text
hop-core
hop-engine
hop-ui             provided/compile gemäss bestehendem Plugin-Muster
hop-interlis-core
hop-interlis-valuetype
hop-geometry-type  provided
jts-core           provided
```

# 2. Package-Struktur

```text
ch.so.agi.hop.interlis
|
|-- core
|   |-- model
|   |-- io
|   |-- mapping
|   |-- geometry
|   |-- validation
|   `-- util
|
|-- value
|
`-- transforms
    |-- input
    |-- output
    |-- transferinput
    |-- transferoutput
    |-- objecttorow
    |-- rowtoobject
    |-- structureexplode
    |-- structurecollect
    |-- rolejoin
    |-- validate
    |-- enumerations
    `-- ui
```

# 3. Model Service

## 3.1 `InterlisModelRequest`

```java
public record InterlisModelRequest(
    List<String> modelNames,
    List<String> modelDirectories,
    String dataFile,
    String metaConfig,
    boolean inferModelsFromData,
    Map<String, String> ili2cSettings) {}
```

`dataFile` darf für `%XTF_DIR`-Auflösung bzw. Model-Inference verwendet werden.

## 3.2 `InterlisModelContext`

```java
public record InterlisModelContext(
    TransferDescription transferDescription,
    InterlisSchemaDescriptor schema,
    InterlisModelRequest request,
    String cacheKey) {}
```

## 3.3 `InterlisModelService`

```java
public final class InterlisModelService {

  public InterlisModelContext load(InterlisModelRequest request)
      throws InterlisModelException;

  public InterlisModelContext loadForTransfer(
      Path transferFile,
      InterlisModelRequest request)
      throws InterlisModelException;

  public List<String> detectModelNames(Path transferFile)
      throws InterlisModelException;

  public void clearCache();
}
```

### Verantwortlichkeiten

1. Model directories/Repositories normalisieren.
2. `%XTF_DIR` bzw. plugin-eigene Platzhalter auflösen.
3. Modelle bei Bedarf aus Transfer bestimmen.
4. ili2c über `IliManager`/geeignete aktuelle API kompilieren.
5. `TransferDescription` erzeugen.
6. `InterlisSchemaDescriptor` erzeugen.
7. Resultat cachen.

### Modellrepositorys (Phase 8)

Model directories dürfen zusätzlich zu lokalen Verzeichnissen
`http(s)://`-Repository-URIs enthalten. Nicht lokal gefundene Modelle werden
über die ilirepository-Maschinerie (`RepositoryAccess`, ili2c-tool)
aufgelöst:

- Index `ilimodels.xml` und Modell-Dateien landen im lokalen Cache
  (`~/.ilicache` Default, 24 h TTL, 15 s/40 s Timeouts, überschreibbar via
  System-Property `hop.interlis.repository.cache`);
- lokale Directories haben Vorrang (Override-Semantik);
- Imports innerhalb des Repositorys löst der ili2c `IliManager` auf;
- Fehlerfälle (unreachable, unbekanntes Modell) ergeben actionable
  Diagnostik mit Repository-Liste und Offline-/Override-Hinweisen;
- der kompilierte Model-Cache ist JVM-weit statisch (ein Compile pro
  Modell-Set und Hop-Sitzung).

### Cache-Key

Mindestens:

```text
sorted model names
resolved model repositories/directories
meta config
relevante ili2c settings
```

Dateibasierten lokalen Modellen kann optional `lastModified`/Hash beigefügt werden, damit Änderungen beim Entwickeln nicht unsichtbar bleiben.

# 4. Schema Extraction

## 4.1 `InterlisSchemaExtractor`

```java
public final class InterlisSchemaExtractor {

  public InterlisSchemaDescriptor extract(TransferDescription td)
      throws InterlisModelException;

  InterlisClassDescriptor extractClass(Viewable viewable, TransferDescription td);

  InterlisAssociationDescriptor extractAssociation(AssociationDef association);

  InterlisStructureDescriptor extractStructure(Table structure);

  InterlisPropertyDescriptor extractTransferElement(
      Viewable owner,
      Viewable.TransferElement transferElement);
}
```

Verbindliche Regeln:

- nur transferrelevante Modellobjekte aufnehmen;
- Properties in stabiler Modell-/Transferreihenfolge halten;
- geerbte Properties separat als `effectiveProperties` auflösen;
- Roles nicht als gewöhnliche Attribute tarnen;
- Structure Cardinality und LIST/BAG unterscheiden;
- AssociationDef samt Rollen und eigenen Attributen beschreiben;
- Geometry-Art und Dimension bestimmen;
- Enumerationshierarchie vollständig erfassen.

## 4.2 `InterlisSchemaDescriptor`

```java
public final class InterlisSchemaDescriptor {
  public List<InterlisModelDescriptor> models();
  public Optional<InterlisClassDescriptor> findClass(String scopedName);
  public Optional<InterlisStructureDescriptor> findStructure(String scopedName);
  public Optional<InterlisAssociationDescriptor> findAssociation(String scopedName);
  public List<InterlisClassDescriptor> classesInTopic(String topicScopedName);
  public List<InterlisElementDescriptor> search(String text);
}
```

# 5. Hop Row Schema Factory

## 5.1 `ProjectionOptions`

```java
public record ProjectionOptions(
    boolean includeTid,
    boolean includeBid,
    boolean includeClassName,
    boolean includeTopicName,
    boolean includeOperation,
    boolean includeInheritedProperties,
    SingleStructureMode singleStructureMode,
    RoleMode roleMode,
    String structureSeparator,
    int maxFlattenDepth,
    Integer defaultSrid,
    Set<String> selectedPropertyPaths) {}
```

Enums:

```java
public enum SingleStructureMode {
  FLATTEN,
  KEEP_OBJECT
}

public enum RoleMode {
  REFERENCE_FIELDS,
  KEEP_OBJECT
}
```

`KEEP_OBJECT` ist Advanced und nicht Default.

## 5.2 `HopRowSchemaFactory`

```java
public final class HopRowSchemaFactory {

  public RowSchemaPlan createPlan(
      InterlisClassDescriptor classDescriptor,
      ProjectionOptions options)
      throws InterlisMappingException;

  public RowMeta createRowMeta(RowSchemaPlan plan);

  IValueMeta createValueMeta(RowFieldPlan fieldPlan);

  IValueMeta createPrimitiveValueMeta(InterlisAttributeDescriptor attribute);

  ValueMetaGeometry createGeometryValueMeta(
      InterlisAttributeDescriptor geometryAttribute,
      String hopFieldName);
}
```

## 5.3 Feldreihenfolge

Default:

```text
1 _ili_tid       wenn aktiviert
2 _ili_bid       wenn aktiviert
3 _ili_class     wenn aktiviert
4 _ili_topic     wenn aktiviert
5 _ili_operation wenn aktiviert
6..n effective model properties in model order
```

Flattened structure fields erscheinen an der Stelle des Structure-Attributes in Depth-First-Reihenfolge.

Beispiel:

```interlis
A
B : Struct(X,Y)
C
```

wird:

```text
A
B_X
B_Y
C
```

nicht `A,C,B_X,B_Y`.

## 5.4 Hop-Typen

```java
private IValueMeta createPrimitiveValueMeta(InterlisAttributeDescriptor a) {
  return switch (a.valueKind()) {
    case TEXT, NAME, URI, ENUM -> new ValueMetaString(a.hopName());
    case BOOLEAN -> new ValueMetaBoolean(a.hopName());
    case INTEGER -> new ValueMetaInteger(a.hopName());
    case DECIMAL -> new ValueMetaBigNumber(a.hopName());
    case DATE -> new ValueMetaDate(a.hopName());
    case DATETIME -> new ValueMetaTimestamp(a.hopName());
    case BINARY -> new ValueMetaBinary(a.hopName());
    case GEOMETRY -> new ValueMetaGeometry(a.hopName());
    default -> throw ...;
  };
}
```

String length, precision and other sichere Metadaten werden gesetzt, soweit die Hop-API dies korrekt repräsentieren kann.

# 6. ValueMetaInterlisObject

## 6.1 Ziel

Der Advanced-Envelope-Stream soll `_ili_object` nicht bloss als anonymes `TYPE_SERIALIZABLE` führen. Ein eigener Value Type macht Schema und Debugging verständlicher.

```java
@ValueMetaPlugin(
    id = "<stable numeric id>",
    name = "INTERLIS Object",
    description = "INTERLIS transfer object envelope",
    classLoaderGroup = "sogeo-geometry")
public final class ValueMetaInterlisObject extends ValueMetaBase {
  ...
}
```

ID muss einmalig gewählt und danach nie geändert werden.

## 6.2 Native Runtime-Klasse

```java
InterlisObjectEnvelope
```

Nicht direkt ein beliebiges `IomObject`, weil der Envelope zusätzliche Transferinformationen und eine kontrollierte serialisierbare Form enthält.

## 6.3 Zu implementierende Kernmethoden

Mindestens analog etablierter Hop Value Types:

```java
public Object cloneValueData(Object value) throws HopValueException;
public String getString(Object value) throws HopValueException;
public Object getNativeDataType(Object value) throws HopValueException;
public Object convertData(IValueMeta sourceMeta, Object data) throws HopValueException;
public Object readData(DataInputStream in) throws HopFileException, HopEofException;
public void writeData(DataOutputStream out, Object data) throws HopFileException;
```

### Serialisierungsformat

Kein Java Object Serialization als langfristiges On-Disk-Protokoll, obwohl `Iom_jObject` serialisierbar ist.

Vorgeschlagen:

```text
version byte
nullable marker
length-prefixed UTF-8 metadata fields
IOM object encoded in a private deterministic binary/XML representation
```

Für Phase 4 darf intern zunächst eine klar versionierte Java-Serialization verwendet werden, **wenn** Tests zeigen, dass Hop Row-Caching funktioniert. Vor 1.0 soll auf ein eigenes stabiles Format umgestellt werden.

## 6.4 Alternative für frühe Phase

Phase 1/2 benötigt den Value Type noch nicht zwingend, weil `INTERLIS Input` direkt typed Rows erzeugen kann. Der Value Type kann deshalb in Phase 4 eingeführt werden, ohne das frühe Demo zu verzögern.

# 7. Geometry Mapper

## 7.1 `InterlisGeometryMapper`

```java
public final class InterlisGeometryMapper {

  public Geometry toHopGeometry(
      IomObject geometry,
      InterlisGeometryDescriptor descriptor,
      Integer srid)
      throws InterlisGeometryException;

  public IomObject toIomGeometry(
      Geometry geometry,
      InterlisGeometryDescriptor descriptor)
      throws InterlisGeometryException;
}
```

## 7.2 `toHopGeometry` Algorithmus

```text
if geometry == null:
    return null

dimension = descriptor.dimension
iox2wkb = new Iox2wkb(dimension, BIG_ENDIAN, false-or-supported-mode)

switch descriptor.kind:
  COORD:
      bytes = iox2wkb.coord2wkb(geometry)
  MULTICOORD:
      bytes = iox2wkb.multicoord2wkb(geometry)
  POLYLINE:
      bytes = iox2wkb.polyline2wkb(
          geometry,
          false,
          true,       // asCompoundCurve
          0.0 or safe tolerance preserving arcs)
  MULTIPOLYLINE:
      bytes = matching multi curve writer
  SURFACE / AREA:
      bytes = surface writer with asCurvePolygon=true
  MULTISURFACE:
      bytes = matching multi surface writer
  else:
      throw unsupported geometry type

hopGeom = CurveGeometrySupport.readWkb(bytes)
if srid != null:
    hopGeom.setSRID(srid)
return hopGeom
```

Die konkrete iox-ili-Methodenwahl wird durch Tests gegen alle Geometry-Kinds abgesichert. Kein Fallback darf Kurven stillschweigend linearisieren.

## 7.3 `toIomGeometry` Algorithmus

```text
if geometry == null:
    return null

bytes = CurveGeometrySupport.writeWkb(geometry)
iomGeometry = new Wkb2iox().read(bytes)
validateIomGeometryKind(iomGeometry, descriptor)
return normalizeForExpectedInterlisType(iomGeometry, descriptor)
```

Tests müssen CircularString, CompoundCurve und CurvePolygon explizit prüfen.

# 8. Primitive Value Converter

## 8.1 Interface

```java
public interface InterlisValueConverter {
  Object read(IomObject owner, InterlisPropertyPath path)
      throws InterlisMappingException;

  void write(IomObject target, InterlisPropertyPath path, Object value)
      throws InterlisMappingException;
}
```

In der Praxis werden hochspezialisierte stateless Converter registriert.

## 8.2 `PrimitiveValueCodec`

```java
public final class PrimitiveValueCodec {

  public Object parse(
      String raw,
      InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException;

  public String format(
      Object value,
      InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException;
}
```

### Parsing

```text
TEXT/NAME/URI -> raw
BOOLEAN       -> strict true/false mapping
INTEGER       -> Long.valueOf(raw)
DECIMAL       -> new BigDecimal(raw)
ENUM          -> validate canonical element, return String
DATE          -> model/INTERLIS compliant parser
DATETIME      -> model/INTERLIS compliant parser
```

Keine numerische Konvertierung über `Double` für Decimal.

### Formatting

- `BigDecimal.toPlainString()` bzw. modellkonforme Formatierung.
- Keine locale-abhängigen Dezimaltrenner.
- Date/DateTime immer standardkonform und locale-unabhängig.

# 9. Exakter `IomObject -> Object[]`-Algorithmus

## 9.1 Voraussetzungen

Zur Initialisierung existieren:

```java
RowSchemaPlan plan;
IRowMeta outputRowMeta;
InterlisGeometryMapper geometryMapper;
PrimitiveValueCodec primitiveCodec;
```

Der Plan enthält für jedes Output-Feld bereits:

- Output-Index,
- Property Path,
- Source-Kind,
- Converter,
- erwarteten INTERLIS-Typ.

Keine teure Modellnavigation pro Row.

## 9.2 Mapper API

```java
public final class IomToRowMapper {

  public Object[] map(
      InterlisObjectEnvelope envelope,
      RowSchemaPlan plan)
      throws InterlisMappingException;

  Object readField(
      InterlisObjectEnvelope envelope,
      RowFieldPlan field)
      throws InterlisMappingException;
}
```

## 9.3 Hauptalgorithmus

```text
function map(envelope, plan):
    assert envelope.eventType == OBJECT or accepted DELETE_OBJECT
    assert envelope.className compatible with plan.classDescriptor

    row = RowDataUtil.allocateRowData(plan.fields.size)

    for field in plan.fields:
        try:
            row[field.outputIndex] = readField(envelope, field)
        catch Exception e:
            throw InterlisMappingException(
                class=envelope.className,
                tid=envelope.objectId,
                bid=envelope.basketId,
                path=field.propertyPath,
                cause=e)

    return row
```

## 9.4 `readField` nach Source

```text
OBJECT_ID:
    return envelope.objectId

BASKET_ID:
    return envelope.basketId

CLASS_NAME:
    return envelope.className

TOPIC_NAME:
    return envelope.topicName

OPERATION:
    return envelope.operation.name

PRIMITIVE_ATTRIBUTE:
    owner = navigateToParentStructure(envelope.object, path)
    if owner == null:
        return null
    raw = owner.getattrvalue(path.leafName)
    if raw == null:
        return null
    return primitiveCodec.parse(raw, descriptor)

GEOMETRY_ATTRIBUTE:
    owner = navigateToParentStructure(...)
    if owner == null:
        return null
    if owner.getattrvaluecount(leafName) == 0:
        return null
    geomObj = owner.getattrobj(leafName, 0)
    if geomObj == null:
        return null
    return geometryMapper.toHopGeometry(geomObj, descriptor, resolvedSrid)

FLATTENED_STRUCTURE_ATTRIBUTE:
    owner = navigateStructurePath(envelope.object, path.parentSegments)
    if owner == null:
        return null
    leaf handled according to primitive/geometry/role leaf kind

ROLE_REFERENCE:
    owner = navigateToParentStructure(...)
    roleObj = owner.getattrobj(roleName, 0)
    if roleObj == null:
        return null
    return roleObj.getobjectrefoid()

ROLE_REFERENCE_BID:
    return roleObj.getobjectrefbid()

ASSOCIATION_ATTRIBUTE:
    linkObj = resolve embedded association object according to descriptor
    return read its primitive attribute
```

## 9.5 `navigateStructurePath`

```java
IomObject navigateSingleStructure(
    IomObject root,
    List<InterlisPathSegment> segments)
```

Algorithmus:

```text
current = root
for segment in segments:
    count = current.getattrvaluecount(segment.name)
    if count == 0:
        return null
    if count > 1:
        throw mapping error: cannot flatten multi-valued structure
    next = current.getattrobj(segment.name, 0)
    if next == null:
        throw mapping error: expected structure object
    current = next
return current
```

## 9.6 Objektklasse prüfen

`envelope.className` muss mit dem Plan kompatibel sein.

Default `EXACT_TAG`:

```text
envelope.className == selected concrete class scoped name
```

Später optional `ALLOW_SUBCLASSES`, dann wird Metamodell-Vererbung geprüft.

# 10. Exakter `Object[] -> IomObject`-Algorithmus

## 10.1 API

```java
public final class RowToIomMapper {

  public InterlisObjectEnvelope map(
      Object[] row,
      IRowMeta inputRowMeta,
      RowSchemaPlan plan,
      RowWriteOptions options)
      throws InterlisMappingException;
}
```

## 10.2 Initialisierung

Vor der ersten Row wird für jedes Plan-Feld der Input-Index bestimmt:

```java
public RowInputBinding bind(IRowMeta inputRowMeta, RowSchemaPlan plan)
```

Fehlende mandatory Mapping-Felder werden bereits hier gemeldet, nicht erst bei Row 100000.

## 10.3 Hauptalgorithmus

```text
tid = read configured TID field
if tid null:
    tid = oidPolicy.generateOrFail(...)

obj = new Iom_jObject(plan.classDescriptor.scopedName, tid)

for top-level model property in plan.writeProperties:
    write property using precomputed binding

bid = read BID or configured basket id
operation = read optional operation field or NONE

envelope = OBJECT envelope(... obj ...)
return envelope
```

## 10.4 Primitive write

```text
value = row[inputIndex]
if value == null:
    leave attribute undefined
else:
    raw = primitiveCodec.format(value, descriptor)
    target.setattrvalue(attributeName, raw)
```

## 10.5 Geometry write

```text
value = row[inputIndex]
if value == null:
    leave undefined
else if not Geometry:
    use ValueMetaGeometry.getNativeDataType or fail
geomObj = geometryMapper.toIomGeometry(value, descriptor)
target.addattrobj(attributeName, geomObj)
```

## 10.6 Flattened single structure write

Für jede einwertige geflattete Struktur wird zuerst geprüft, ob irgendein darunterliegendes Feld gesetzt ist.

```text
hasAnyValue = any descendant binding value != null

if !hasAnyValue:
    if structure optional:
        do not create structure
    else:
        strict mode -> error
        validation mode -> create no value and let validator report
else:
    structObj = new Iom_jObject(structure.scopedName, null)
    write descendant fields recursively
    parent.addattrobj(structureAttributeName, structObj)
```

Die Struktur wird genau einmal erzeugt, nicht pro Child-Feld.

## 10.7 Role write

```text
refTid = row[roleRefIndex]
if refTid != null:
    roleObj = new Iom_jObject("REF", null) or writer-compatible ref object
    roleObj.setobjectrefoid(refTid)
    if refBid != null:
        roleObj.setobjectrefbid(refBid)
    owner.addattrobj(roleName, roleObj)
```

Die konkrete IOM-Codierung wird gegen iox-ili-Tests verifiziert; keine handgeschriebene XML-Logik.

# 11. Transfer Reader Abstraction

## 11.1 Interface

```java
public interface InterlisTransferReader extends AutoCloseable {
  InterlisTransferEvent next() throws InterlisReadException;
  InterlisModelContext modelContext();
  @Override void close() throws InterlisReadException;
}
```

## 11.2 `XtfTransferReader`

Felder:

```java
private IoxReader reader;
private InterlisModelContext modelContext;
private String currentBasketId;
private String currentTopic;
private boolean transferStarted;
```

### Open

```java
public static XtfTransferReader open(
    Path file,
    InterlisModelContext model,
    InterlisReadOptions options)
```

Intern wird ein aktueller `ReaderFactory`/`IoxIliReader`-Pfad verwendet und `setModel(td)` gesetzt, wo erforderlich.

### Event loop

```text
event = reader.read()

StartTransferEvent -> START_TRANSFER
StartBasketEvent   -> remember bid/topic; START_BASKET
ObjectEvent        -> copy IomObject -> Iom_jObject; OBJECT envelope
DeleteObjectEvent  -> DELETE_OBJECT envelope
EndBasketEvent     -> END_BASKET; clear basket context
EndTransferEvent   -> END_TRANSFER
```

Die exakten Event-Klassen sind an iox-ili 1.24.4 anzupassen.

# 12. Transfer Writer Abstraction

```java
public interface InterlisTransferWriter extends AutoCloseable {
  void startTransfer(InterlisTransferMetadata metadata);
  void startBasket(InterlisBasketMetadata basket);
  void writeObject(InterlisObjectEnvelope object);
  void writeDelete(InterlisObjectEnvelope delete);
  void endBasket();
  void endTransfer();
}
```

`XtfTransferWriter` verwendet IOX-Writer-Events und übernimmt keine XML-Stringerzeugung selbst.

# 13. Hop Transform Pattern

Alle Transforms folgen dem im bestehenden GeoTools-Plugin etablierten Hop-2.18-Muster:

```java
@Transform(...)
public class XMeta extends BaseTransformMeta<X, XData> {
  @Override public void setDefault() { ... }
  @Override public void getFields(...) throws HopTransformException { ... }
  @Override public void check(...) { ... }
}

public class XData extends BaseTransformData {
  // runtime state
}

public class X extends BaseTransform<XMeta, XData> {
  public X(TransformMeta transformMeta, XMeta meta, XData data,
           int copyNr, PipelineMeta pipelineMeta, Pipeline pipeline) { ... }
  @Override public boolean processRow() throws HopException { ... }
  @Override public void dispose() { ... }
}

public class XDialog extends BaseTransformDialog {
  @Override public String open() { ... }
}
```

`@HopMetadataProperty` wird für persistente Meta-Felder verwendet.

# 14. Transform: INTERLIS Input

## 14.1 Plugin-ID

```java
@Transform(
    id = "INTERLIS_INPUT",
    name = "INTERLIS Input",
    description = "Read one INTERLIS class as typed rows",
    image = ".../interlis-input.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "reader"})
```

## 14.2 `InterlisInputMeta`

Persistente Felder:

```java
@HopMetadataProperty private String fileName;
@HopMetadataProperty private String modelNames;
@HopMetadataProperty private String modelDirectories;
@HopMetadataProperty private String metaConfig;
@HopMetadataProperty private String className;
@HopMetadataProperty private String selectedFieldsJson;
@HopMetadataProperty private boolean includeTid;
@HopMetadataProperty private boolean includeBid;
@HopMetadataProperty private boolean includeClassName;
@HopMetadataProperty private boolean includeTopicName;
@HopMetadataProperty private String singleStructureMode;
@HopMetadataProperty private String roleMode;
@HopMetadataProperty private String defaultSrid;
@HopMetadataProperty private boolean validate;
@HopMetadataProperty private String validationConfig;
```

Methoden:

```java
public ProjectionOptions projectionOptions();
public InterlisModelRequest modelRequest(IVariables variables);
public Optional<Integer> resolvedDefaultSrid(IVariables variables);
```

### `getFields(...)`

Algorithmus:

```text
resolve file/model/class config
if unresolved variables prevent model load:
    return without throwing design-time fatal error
load model context
find class descriptor
create RowSchemaPlan
append each plan.valueMeta to rowMeta
```

Wichtig: Design-Time-Probe-Fehler sollen wie beim GeoTools-Dialog die Pipeline-Datei nicht unöffnbar machen. Runtime bleibt strict.

### `check(...)`

Prüft:

- Datei gesetzt.
- Format unterstützt.
- Modell auflösbar, soweit Variablen resolved.
- Klasse existiert.
- keine Output-Namenskollision.
- ausgewählte Properties existieren.
- Geometry Value Type verfügbar.

## 14.3 `InterlisInputData`

```java
public class InterlisInputData extends BaseTransformData {
  boolean initialized;
  InterlisTransferReader reader;
  InterlisModelContext modelContext;
  RowSchemaPlan schemaPlan;
  IRowMeta outputRowMeta;
  IomToRowMapper mapper;
  long readObjects;
  long emittedObjects;
}
```

## 14.4 `InterlisInput.processRow()`

```text
if !initialized:
    initialize()

loop:
    event = reader.next()
    if event == null:
        close; setOutputDone; return false

    if event is OBJECT:
        if class matches configured class:
            row = mapper.map(event.envelope, plan)
            putRow(outputRowMeta, row)
            return true
        else:
            continue

    if event is DELETE_OBJECT and configured policy emits it:
        if matching class:
            map technical fields; user fields null as appropriate
            putRow(...)
            return true

    otherwise:
        continue
```

Dadurch liest der Transform streaming und erzeugt pro `processRow` maximal eine Output-Row.

# 15. Transform: INTERLIS Output

## 15.1 Zweck

Komfort-Writer für einen normalen typisierten Input-Stream und genau eine konfigurierte Klasse.

## 15.2 Meta

```java
fileName
modelNames
modelDirectories
metaConfig
className
basketPolicy
basketId
basketIdField
objectIdField
operationField
validate
validationConfig
overwrite
```

## 15.3 `getFields`

Output Writer verändert das Schema normalerweise nicht.

## 15.4 Runtime

Beim ersten Input-Row:

1. Modell laden.
2. RowSchemaPlan für Write erstellen.
3. Binding gegen `getInputRowMeta()` erstellen.
4. Writer öffnen.
5. StartTransfer schreiben.
6. Basket-Verwaltung initialisieren.

Pro Row:

1. BID bestimmen.
2. Wenn Basket wechselt und Policy dies erlaubt: bisherigen Basket schliessen, neuen öffnen.
3. Row -> Envelope.
4. Object/Delete schreiben.

Am Ende:

1. Basket schliessen.
2. Transfer schliessen.
3. Writer close.
4. optional Validierung durchführen bzw. integrierten Validator finalisieren.

## 15.5 Sortieranforderung für mehrere Baskets

Ein streaming Writer kann Baskets nicht beliebig verschachteln. Bei `GROUP_BY_BID` muss Input nach BID gruppiert sein.

GUI-Hinweis:

```text
Input rows must be grouped by Basket ID. If the same BID appears again
later after another basket was closed, the transform fails.
```

Eine spätere buffered mode ist möglich, aber nicht Default.

# 16. Transform: INTERLIS Transfer Input

## 16.1 Plugin-ID

```text
INTERLIS_TRANSFER_INPUT
```

## 16.2 Output RowMeta

Konstant (zentral definiert in `InterlisEnvelopeRowLayout`):

```text
_ili_event_type   String
_ili_model        String
_ili_topic        String
_ili_bid          String
_ili_class        String
_ili_tid          String
_ili_operation    String
_ili_object       INTERLIS Object
_ili_line         Integer
_ili_column       Integer
```

`getFields` braucht daher kein konkretes Class-Schema.

## 16.3 Modes

```java
public enum TransferInputMode {
  OBJECTS,
  EVENTS
}
```

OBJECTS filtert Transfer-/Basket-Events, hält deren Kontext aber in den Feldern.
EVENTS reproduziert die exakte IOX-Ereignissequenz (Phase 5 implementiert beide
Modi; Delete-Objekte kommen als OBJECT-Zeilen mit `_ili_operation=DELETE`,
weil iox Object-Events mit Operation liefert).

# 17. Transform: INTERLIS Object to Row

## 17.1 Input contract

Benötigt ein `_ili_object`-Feld vom Typ `INTERLIS Object` oder explizit ausgewähltes Envelope-Feld.

## 17.2 Meta

```text
objectFieldName
className
model configuration override optional
projection options
selected fields
```

Normalerweise ist der Model Context bereits im Envelope semantisch identifizierbar, aber Design-Time-Schema benötigt eine explizite Model-Konfiguration oder eine zuverlässig auflösbare Transfer-Datei.

## 17.3 getFields

Input-Felder können wahlweise:

- ersetzt werden (`replace envelope` Default), oder
- durch typed fields ergänzt werden (`appendEnvelopeFields`).

Default für UX (Phase 5 umgesetzt):

```text
Replace envelope fields
Add typed class fields (_ili_tid/_ili_bid aus der Projektion)
```

Flattening attributierter Assoziationen puffert pro Basket (Flush bei
END-Events oder BID-Wechsel); der Envelope-Stream muss dafür basket-gruppiert
sein.

# 18. Transform: INTERLIS Row to Object

Inverse Operation (Phase 5 umgesetzt). Regenerierte Link-Objekte aus geflatteten
Assoziationsattributen (`RowToIomMapper.mapAll`) werden als zusätzliche
OBJECT-Envelope-Zeilen direkt nach ihrer Klassenzeile emittiert.

Output ist ein stabiles Envelope-Schema und kann danach via `Append Streams`/Merge zusammengeführt werden.

Beispiel:

```text
Building rows ---> Row to Object --\
                                 +--> Append Streams --> Transfer Output
Parcel rows -----> Row to Object --/
```

Das ist die zentrale Lösung für mehrere Klassen in einem XTF-Writer.

# 19. Transform: INTERLIS Transfer Output

Input contract: Envelope schema.

Verantwortlich für:

- Transfer Start/End.
- Basket Gruppierung.
- Objekt-/Delete-Events.
- Event mode, falls explizite Events angeliefert werden.
- Fehler bei ungültiger Eventreihenfolge.

**Phase 5 umgesetzt:** Object Mode (Default) leitet die Ereignisse aus den
OBJECT-Zeilen ab (Basket-Gruppierung über `_ili_bid`, explizite Event-Zeilen sind
ein Fehler); Event Mode schreibt die explizite Sequenz (State-Machine des
Writers prüft die Reihenfolge). Operationen werden auf das IOM-Objekt übertragen;
explizite Modellnamen sind Pflicht (der Envelope-Stream trägt keinen Header).

### 19.1 State machine

```text
NEW
 |
START_TRANSFER
 v
TRANSFER_OPEN
 |
START_BASKET
 v
BASKET_OPEN -- OBJECT/DELETE --> BASKET_OPEN
 |
END_BASKET
 v
TRANSFER_OPEN
 |
END_TRANSFER
 v
DONE
```

Ungültige Übergänge -> `InterlisWriteException`.

# 20. Transform: INTERLIS Structure Explode

## 20.1 Input modes

Zwei mögliche Modi:

### ENVELOPE

Input enthält `_ili_object`. Der Transform navigiert direkt im IOM-Objekt. Dies ist die präziseste Advanced-Variante.

### TYPED_PARENT_WITH_HIDDEN_STRUCTURE

Für die normale `INTERLIS Input`-UX muss die mehrwertige Struktur zugänglich bleiben. Dafür gibt es zwei Designoptionen:

A. `INTERLIS Input` kann ein optionales verstecktes/Advanced `InterlisObject`-Feld behalten.
B. `INTERLIS Structure Explode` liest direkt aus einem parallel weitergereichten Envelope-Stream.

**Festlegung (Phase 3 umgesetzt):** Variante A. `INTERLIS Input` fügt auf Wunsch
(„Keep source object for Structure Explode“) ein technisches Feld
`_ili_source_object` vom Typ `ValueMetaInterlisObject` hinzu
(`@ValueMetaPlugin`, `classLoaderGroup="sogeo-geometry"`, Phase-5-AP-5.2 wird damit
vorgezogen). Das Feld ist im normalen GUI unter „Advanced technical fields“
sichtbar; es ist ein Implementierungsdetail des Struktur-Pipelines, kein
Benutzerdatentyp (`getString()` = XML nur für Debug/Preview, keine verlustbehaftete
String-Konvertierung).

Damit bleibt der Parent-Stream einfach, ohne alle Strukturen in Java-Listen zu packen.

Hinweis Hop 2.18: Wenn dieselbe Quelle zwei Ausgänge speist (Input → Explode und
Input → Collect), verteilt Hop standardmässig Round-Robin
(`TransformMeta.distributes = true`). Für den Fan-out muss `distributes=false`
gesetzt sein (GUI: Transform-Eigenschaften), sonst wird der Stream aufgeteilt.

## 20.2 Meta

```text
sourceObjectField
parentTidField
structureAttributePath
includeParentFields[]
parentKeyFieldName
indexFieldName
flattenNestedSingleStructures
selectedChildFields[]
```

## 20.3 Output RowMeta

```text
_parent_tid       String
_ili_parent_key   String optional
_ili_index        Integer for LIST, optional for BAG
<selected child fields...>
```

Option `include parent fields` kann z.B. BFS-Nummer mitkopieren, um downstream Joins zu vereinfachen.

## 20.4 Runtime-Algorithmus

Ein Input-Parent kann 0..n Output-Rows erzeugen. `processRow()` benötigt deshalb einen Pending-Iterator in `Data`.

```text
if pending child iterator has next:
    emit next child
    return true

parentRow = getRow()
if parentRow == null:
    setOutputDone; return false

sourceObj = read source object
children = locate structure attribute
prepare child iterator

if no children:
    continue to next parent
else:
    emit first child
    return true
```

# 21. Transform: INTERLIS Structure Collect

Hop-Transforms mit zwei Input-Streams benötigen eine klare Semantik. Für Phase 3 wird der Transform als **sorted merge/streaming collect** spezifiziert.

Inputs müssen nach Parent-Key sortiert sein:

```text
Parent stream: _ili_tid ascending
Child stream:  _parent_tid ascending, _ili_index ascending
```

Meta:

```text
parentInputTransform
childInputTransform
parentKeyField
childParentKeyField
indexField
structureAttributePath
strictOrdering
failOnDuplicateIndex
failOnChildWithoutParent
sourceObjectField
```

Output kann:

- Envelope-Parent mit eingesammelter Struktur sein, oder
- typed Parent + aktualisiertes `_ili_source_object`.

**Festlegung (Phase 3 umgesetzt):** typed Parent + aktualisiertes
`_ili_source_object`. Die Envelope-Ausgabe braucht Row-to-Object/Transfer-Output
und folgt mit Phase 5; bis dahin schreibt der erweiterte `INTERLIS Output`
(Overlay auf den Träger) den Roundtrip.

Die gesammelte Struktur **ersetzt** immer den Strukturinhalt des Trägers: der
Child-Strom ist das Ergebnis der Downstream-Transformation, von Filtern entfernte
Kinder dürfen nicht aus dem Träger wieder auftauchen.

Eine alternative buffered Implementierung kann später hinzukommen.

# 22. Transform: INTERLIS Role Join

## 22.1 Hop-Implementierung

Zwei Input-Streams (Hop 2.x: beide via `findInputRowSet`/`getRowFrom`, analog
StreamLookup):

```text
main stream
lookup stream
```

Meta kennt:

```text
mainInputTransform
lookupInputTransform
rolePath (Rolle der Main-Klasse)
mainReferenceField      (Default <role>_ref)
lookupTidField          (Default _ili_tid)
lookupFields[]          (Default: alle Attribute der Zielklasse)
prefix                  (Default <role>_)
failOnMissingMandatoryReference
failOnDuplicateTid
maxLookupRows
```

Data:

```java
Map<String, Object[]> lookupByTid;
IRowMeta mainMeta;
IRowMeta lookupMeta;
IRowMeta outputMeta;
int mainRefIndex;
int lookupTidIndex;
int[] copiedLookupIndexes;
```

Lookup wird einmal geladen. Bei Überschreitung `maxLookupRows` wird mit klarer Meldung abgebrochen.

Die Rolle wird über das Modell aufgelöst (Main-Klasse → Rolle → Zielklasse),
`getFields()` liefert die getypten Zielklassen-Felder (Geometrie als Hop-Geometry).
Hinweis: Hop 2.18 hat auf `IValueMeta` keinen Attribute-Kanal; die
INTERLIS-Rollenmetadaten (ili.kind/target/min/max) leben im Plan/Deskriptor.

# 23. Transform: INTERLIS Validate

## 23.1 Phase-6 File Mode (umgesetzt)

Ein Input-Transform ohne Row-Input validiert eine XTF-Datei mit dem
iox-ili-Streaming-Validator und gibt Validation Error Rows aus.

Meta:

```text
fileName
modelNames/modelDirectories
configFile          (lokale TOML-Config; ilidata/MetaConfig eingeschränkt)
validateMultiplicity
maxErrors
stopOnFirstError
includeWarnings / includeInfo
failOnErrors
```

Output RowMeta siehe Architektur-Dokument (`InterlisValidationRowLayout`).

## 23.2 Stream mode später

Envelope-Stream validieren ist komplexer, weil Constraints u.U. globale/zweipassige Informationen benötigen. Deshalb:

- Phase 6: robuste File-Validation.
- Phase 7/8: Stream Validation mit `PipelinePool` und korrekter Second-Pass-Semantik.

# 24. Transform: INTERLIS Enumerations

Inputloser Transform (Phase 6 umgesetzt).

Meta:

```text
model configuration
enumerationFilter optional
includeNonLeafValues
```

Output (umgesetzte Feldnamen, siehe `InterlisEnumerationsMeta`):

```text
enum_definition
enum_value
enum_path
parent_value
depth
is_leaf
```

(ITF-Enum-Codes folgen mit Phase 7.)

# 25. GUI Support Services

Keine Dialogklasse darf selbst ili2c-Metamodelllogik implementieren.

## 25.1 `InterlisUiModelService`

```java
public final class InterlisUiModelService {
  public ModelProbeResult probe(
      InterlisModelUiRequest request);
}
```

`ModelProbeResult` enthält entweder Deskriptoren oder eine design-time freundliche Fehlermeldung.

## 25.2 `InterlisSchemaPreviewFormatter`

```java
public String formatClass(InterlisClassDescriptor descriptor, ProjectionOptions options);
public String formatStructure(...);
public String formatAssociation(...);
```

## 25.3 `InterlisModelTreeBuilder`

Erzeugt UI-neutral Baumknoten:

```java
public record ModelTreeNode(
    NodeKind kind,
    String label,
    String scopedName,
    String typeLabel,
    List<ModelTreeNode> children) {}
```

SWT-Code rendert nur diese Struktur.

# 26. Dialog-Klassen

Jeder Transform bekommt eine `*Dialog extends BaseTransformDialog`.

Wiederverwendbare SWT-Composites:

```text
InterlisModelSourceComposite
InterlisModelBrowserComposite
InterlisProjectionOptionsComposite
InterlisFieldSelectionComposite
InterlisValidationOptionsComposite
InterlisBasketOptionsComposite
InterlisAdvancedOptionsComposite
InterlisSchemaPreviewComposite
```

Das verhindert acht leicht unterschiedliche Model-Directory-Dialoge.

# 27. Design-Time Robustheit

Verbindliche Regel aus dem bestehenden GeoTools-Pattern:

> Eine fehlgeschlagene Schema-Probe darf das Öffnen eines gespeicherten Transform-Dialogs nicht verhindern.

Deshalb:

```text
Design time:
- unresolved ${VAR} -> informative preview message
- unavailable repository -> preview error, dialog remains usable
- missing file -> preview error

Runtime:
- same conditions -> hard error
```

# 28. Metadata Persistence

Persistente Einstellungen werden mit `@HopMetadataProperty` gespeichert. Komplexe Listen können als verschachtelte Bean-Properties oder, wenn Hop-Metadataserialisierung dies nicht sauber abbildet, als versioniertes JSON-Feld gespeichert werden.

Für `selectedFieldsJson` gilt:

```json
{
  "version": 1,
  "fields": [
    {"path":"Name","output":"Name","enabled":true},
    {"path":"Address.Street","output":"Address_Street","enabled":true}
  ]
}
```

Versionierung ist Pflicht, damit spätere GUI-Erweiterungen alte `.hpl`-Dateien weiter öffnen können.

# 29. `check(...)`-Strategie aller Meta-Klassen

Check-Resultate in drei Kategorien:

```text
ERROR   Transform kann so nicht korrekt laufen.
WARNING Konfiguration ist technisch möglich, aber riskant/unvollständig.
OK      Basiskonfiguration gültig.
```

Beispiele:

- ERROR: className existiert nicht.
- ERROR: Output-Feldnamen kollidieren.
- ERROR: `LIST OF` wurde als Flatten angefordert.
- WARNING: Default SRID gesetzt, aber Modell liefert kein bestätigtes CRS Mapping.
- WARNING: Validate aus.
- WARNING: Role Join lookup limit sehr gross.
- OK: Model loaded, class resolved, 14 fields projected.

# 30. Logging

Basic:

```text
Loaded INTERLIS model(s): ...
Reading XTF: ...
Selected class: ...
Opened basket: ... (nur detailed)
Finished: objects read X, emitted Y
```

Debug:

```text
resolved model repositories
schema plan
field bindings
geometry kind conversion
association mapping decision
```

Row-level darf Werte protokollieren, aber sensible Daten nicht standardmässig in Basic/Debug ausgeben.

# 31. Resource Lifecycle

Jeder IO-Transform implementiert deterministisches Close:

```text
normal EOF -> close -> setOutputDone
exception  -> close in finally/dispose
pipeline stop -> dispose closes reader/writer
```

`dispose()` muss idempotent sein.

# 31a. Threading-Policy (Phase 8)

Für jeden Transform ist die Parallel-Copy-Unterstützung explizit:

| Transform | parallel copies | Begründung |
|---|---|---|
| `INTERLIS Input` | **nein** | gleiche Datei mehrfach lesen = doppelte Rows |
| `INTERLIS Transfer Input` | **nein** | dito |
| `INTERLIS Output` | **nein** | konkurrierendes Schreiben auf dieselbe Datei korrumpiert |
| `INTERLIS Transfer Output` | **nein** | dito |
| `INTERLIS Validate` | **nein** | doppelte Error-Rows pro Kopie |
| `INTERLIS Enumerations` | **nein** | doppelte Rows pro Kopie |
| `INTERLIS Structure Explode` | ja | row-stateless |
| `INTERLIS Structure Collect` | ja | row-stateless |
| `INTERLIS Role Join` | ja | row-stateless (Lookup read-only) |
| `INTERLIS Object to Row` | ja | row-stateless |
| `INTERLIS Row to Object` | ja | row-stateless |

Datei-Transforms brechen bei parallelen Kopien mit einer klaren
Fehlermeldung ab („Set Number of copies back to 1"); sie scheitern nie
stillschweigend. Zentrale Hilfsklasse: `InterlisParallelCopies`.

Geteilte Dienste: `InterlisModelService` ist thread-sicher (statischer
Cache, serialisierte Compiles); Mapper und Pläne sind nach der
Initialisierung unveränderlich; Reader/Writer werden nie zwischen
Transform-Kopien geteilt.

# 32. Backward compatibility

Ab erster öffentlicher 0.x-Version gelten stabile Plugin IDs. Namen dürfen übersetzt/verbessert werden, IDs nicht.

Konfigurations-JSONs tragen Versionsnummern.

Bei Schema-Änderungen braucht es Migration in Meta-Klassen oder tolerant lesbare Defaults.

# 33. Sicherheits-/Qualitätsregeln für die Implementierung

- Keine XML-Manipulation mit String-Replacement.
- Keine Reflection auf private iox-ili-Felder, ausser nach dokumentierter Entscheidung und Test.
- Kein swallowing von Mapping Exceptions.
- Keine implizite Geometry-Linearisierung.
- Keine Date/Decimal-Konvertierung über Locale defaults.
- Keine Modellkompilierung pro Row.
- Keine Feldsuche per Name pro Row, wenn sie vorab gebunden werden kann.
- Kein zweites gebündeltes `jts-core`.
- Keine SWT-Abhängigkeit in zentralen Mappern.

