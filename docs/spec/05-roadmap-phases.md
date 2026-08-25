# hop-interlis-plugin – Umsetzungsphasen und Arbeitspakete

## 1. Ziel der Phasenplanung

Das Ziel ist ausdrücklich **nicht**, zuerst monatelang eine vollständige INTERLIS-Abstraktionsschicht zu bauen und erst am Ende etwas in Hop zu sehen.

Die Umsetzung folgt stattdessen drei Regeln:

1. **Vertikale Schnitte:** Jede Phase endet mit einem funktionierenden, ausführbaren Zwischenstand.
2. **Semantischer Unterbau zuerst dort, wo er sofort benutzt wird:** keine Framework-Klassen „auf Vorrat“ ohne Test und Benutzerpfad.
3. **Früh vorzeigbar, danach verbreitern:** der erste sinnvolle XTF-Input soll früh in Hop GUI sichtbar sein; Schreiben, Strukturen, Assoziationen und Advanced Mode werden danach schrittweise ergänzt.

Jede Phase hat:

- fachliches Ziel,
- Benutzerergebnis,
- Java-/Hop-Arbeitspakete,
- Tests,
- Demo-Szenario,
- Definition of Done.

Eine Phase darf erst als abgeschlossen gelten, wenn ihre Tests und ihr Demo-Szenario funktionieren.

---

# 2. Zielbild nach allen Phasen

```text
Apache Hop / Kategorie "INTERLIS"

+---------------------------+
| INTERLIS Input            |
+---------------------------+
| INTERLIS Output           |
+---------------------------+
| INTERLIS Transfer Input   |
+---------------------------+
| INTERLIS Object to Row    |
+---------------------------+
| INTERLIS Row to Object    |
+---------------------------+
| INTERLIS Transfer Output  |
+---------------------------+
| INTERLIS Structure Explode|
+---------------------------+
| INTERLIS Structure Collect|
+---------------------------+
| INTERLIS Role Join        |
+---------------------------+
| INTERLIS Validate         |
+---------------------------+
| INTERLIS Enumerations     |
+---------------------------+
```

mit:

- XTF 2.x vollständig als primärer Modus,
- mehreren Geometrieattributen,
- Kreisbögen ohne implizite Linearisierung,
- Vererbung,
- Strukturen,
- Assoziationen,
- Baskets,
- OIDs/Referenzen,
- Validation,
- Advanced/Event Mode,
- später ITF inklusive AREA/SURFACE-Spezialfällen.

---

# 3. Phase 0 – Repository, Build, Testharness und technischer Spike

## 3.1 Ziel

Ein sauberer Plugin-Rahmen, der lokal und in CI reproduzierbar gebaut, installiert und von Hop geladen wird.

Noch kein umfassender Benutzertransform, aber bereits ein **vollständiger technischer Pfad** von INTERLIS-Modell und Geometrie bis in die Hop-Laufzeit.

## 3.2 Benutzer-/Entwicklerergebnis

Nach Phase 0 muss funktionieren:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

Das Script:

```text
buildet Geometry Type Plugin
        |
        v
buildet hop-interlis-plugin
        |
        v
führt Tests aus
        |
        v
installiert Plugin ZIP
        |
        v
startet Hop GUI neu
```

Hop GUI muss mindestens einen experimentellen INTERLIS-Transform erkennen.

---

## 3.3 Arbeitspaket 0.1 – Maven Multi-Module Projekt

Anlegen:

```text
hop-interlis-plugin/
├── pom.xml
├── hop-interlis-core/
├── hop-interlis-transforms/
├── hop-interlis-ui/            # optional separat; Entscheidung in Phase 0
├── hop-interlis-it/
├── assemblies/
│   └── assemblies-hop-interlis/
├── test-models/
├── e2e/
└── scripts/
```

### Akzeptanzkriterien

- `mvn clean verify` läuft.
- Java 21.
- Hop-Version zentral im Parent-POM.
- iox-ili/ili2c-Version zentral im Parent-POM.
- `hop-geometry-type` und `jts-core` sind `provided`.
- keine doppelte JTS-Version im Plugin-ZIP.

---

## 3.4 Arbeitspaket 0.2 – Plugin-Classloader

Vorgabe:

```java
classLoaderGroup = "sogeo-geometry"
```

für alle Transforms, die `ValueMetaGeometry` bzw. JTS-Geometrien verwenden.

### Test

Ein minimales Plugin lädt `ValueMetaGeometry` und erzeugt ein Geometry-Feld.

---

## 3.5 Arbeitspaket 0.3 – `InterlisModelService`

Minimal implementieren:

```java
public interface InterlisModelService {
  CompiledInterlisModel compile(ModelSource source, ModelCompileOptions options)
      throws InterlisModelException;

  List<InterlisClassDescriptor> listTransferableClasses(CompiledInterlisModel model);

  Optional<InterlisClassDescriptor> findClass(
      CompiledInterlisModel model, String qualifiedName);
}
```

Phase-0-Funktionalität:

- lokale `.ili`-Dateien,
- Model directories,
- Modellname,
- TransferDescription,
- Cache.

Repository-Auflösung kann zunächst auf die von ili2c/ilirepository bereitgestellten Mechanismen gestützt werden.

---

## 3.6 Arbeitspaket 0.4 – Geometry Bridge Spike

Implementieren:

```java
final class InterlisGeometryMapper {
  Geometry toHopGeometry(IomObject iomGeometry, InterlisGeometryDescriptor descriptor);

  IomObject toIomGeometry(Geometry geometry, InterlisGeometryDescriptor descriptor);
}
```

Primärer Mechanismus:

```text
IOM -> Iox2wkb -> SQL/MM WKB -> hop-geometry-type
```

und zurück:

```text
hop-geometry-type -> SQL/MM WKB -> Wkb2iox -> IOM
```

### Verbindlicher Spike-Test

Eine Polyline mit INTERLIS-ARC muss:

```text
IOM ARC
  -> Hop Geometry
  -> IOM ARC
```

als ARC zurückkommen.

Wenn dieser Test nicht gelingt, darf nicht still auf eine linearisierende Architektur umgestellt werden. Das Problem ist explizit zu lösen oder zu dokumentieren.

---

## 3.7 Arbeitspaket 0.5 – Testmodelle

Mindestens:

```text
HopIli_Primitives_V1.ili
HopIli_Geometry_V1.ili
```

mit kleinen XTF-Fixtures.

---

## 3.8 Arbeitspaket 0.6 – Dev-Sync Script

Anlegen:

```text
scripts/dev-sync-hop-plugin.sh
scripts/dev-install-and-run.sh
scripts/check-distribution.py
```

Muster analog zum bestehenden GeoTools-Plugin.

Details siehe `06-development-deployment.md`.

---

## 3.9 Tests Phase 0

Pflicht:

- Model Compilation Tests.
- Class Descriptor Smoke Tests.
- Geometry straight line roundtrip.
- Geometry ARC roundtrip.
- Distribution checker.
- Plugin load smoke test.

---

## 3.10 Definition of Done Phase 0

- [ ] Multi-Module Build grün.
- [ ] Geometry Plugin wird nicht doppelt paketiert.
- [ ] INTERLIS-Modell kann kompiliert werden.
- [ ] Transferable classes können aufgelistet werden.
- [ ] Kreisbogen-Geometry-Bridge funktioniert im Test.
- [ ] Plugin-ZIP wird erstellt.
- [ ] Dev-Sync Script installiert das Plugin.
- [ ] Hop GUI startet mit geladenem Plugin.

---

# 4. Phase 1 – Vorzeigbarer `INTERLIS Input` für XTF

## 4.1 Ziel

Der erste fachlich überzeugende Benutzerpfad:

```text
XTF
 |
 v
INTERLIS Input
 |
 v
normale typisierte Hop Rows
```

Diese Phase soll bereits zeigen, warum das Plugin besser ist als ein generischer XML-Reader.

---

## 4.2 Unterstützte Semantik

In Phase 1:

- XTF 2.x lesen,
- Modell aus Datei bzw. `%DATA`/Konfiguration bestimmen,
- Klasse auswählen,
- primitive Attribute,
- OID/TID,
- BID,
- Enumeration als String,
- Vererbung als effektives flaches Klassenschema,
- ein oder mehrere Geometrieattribute,
- Kurven,
- einfache `0..1`/`1`-Strukturen flatten,
- einfache Rollen als Referenzfelder kann bereits aufgenommen werden, sofern ohne zusätzliche Association-Infrastruktur möglich.

Noch nicht zwingend:

- BAG/LIST explode,
- komplexe Assoziationen,
- Writer,
- Advanced Envelope.

---

# 5. Arbeitspaket 1.1 – Modell-Descriptor vollständig für Klassen

Implementieren:

```java
record InterlisClassDescriptor(...)
record InterlisAttributeDescriptor(...)
record InterlisGeometryDescriptor(...)
record InterlisStructureDescriptor(...)
record InterlisRoleDescriptor(...)
```

Methoden u.a.:

```java
List<InterlisPropertyDescriptor> effectiveProperties();
List<InterlisGeometryDescriptor> geometryAttributes();
List<InterlisRoleDescriptor> roles();
boolean isAbstract();
String qualifiedName();
```

---

# 6. Arbeitspaket 1.2 – `HopRowSchemaFactory`

Implementieren:

```java
public final class HopRowSchemaFactory {
  public IRowMeta createClassRowMeta(
      InterlisClassDescriptor classDescriptor,
      RowMappingOptions options);
}
```

Schema-Reihenfolge verbindlich:

```text
1. reservierte INTERLIS-Felder
2. Modellattribute in stabiler effektiver Reihenfolge
3. geflattete Strukturfelder an Position des Strukturattributes
4. Rollen nach Modellreihenfolge
```

Default reservierte Felder:

```text
_ili_tid
_ili_bid
```

`_ili_class` nur wenn für den Typed Input gewünscht/konfiguriert; im Advanced Envelope ist es obligatorisch.

---

# 7. Arbeitspaket 1.3 – `IomToRowMapper`

Implementieren wie in `02-java-hop-implementation.md` spezifiziert.

Wichtig:

- Mapping Plan einmal beim Start vorberechnen.
- `processRow()` darf keine Metamodell-Suche pro Objekt durchführen.
- präzise Fehlerkontexte.

---

# 8. Arbeitspaket 1.4 – `INTERLIS Input` Runtime

Klassen:

```text
InterlisInputMeta
InterlisInputData
InterlisInput
InterlisInputDialog
```

Hop-Schnittstellen:

```java
@Transform(...)
class InterlisInputMeta
    extends BaseTransformMeta<InterlisInput, InterlisInputData>
```

```java
class InterlisInput
    extends BaseTransform<InterlisInputMeta, InterlisInputData>
```

---

# 9. Arbeitspaket 1.5 – Model Browser GUI

Dies ist **Teil des MVP**, nicht spätere Kosmetik.

Mindestens:

```text
+--------------------------------------------------------------+
| INTERLIS Input                                               |
+--------------------------------------------------------------+
| File       [ data.xtf                              ] [Browse] |
| Models     [ %DATA                                 ]          |
| Model dirs [ ${MODEL_DIR};https://...             ] [...]    |
|                                                              |
| Class      [ DMAV....Gebaeude                    ] [Select]  |
|                                                              |
| +----------------------------------------------------------+ |
| | Model browser                                            | |
| |  v Model                                                 | |
| |    v Topic                                               | |
| |      v Gebaeude                                          | |
| |        [x] _ili_tid                                      | |
| |        [x] Art                         TEXT               | |
| |        [x] Geometrie                   SURFACE            | |
| |        v Adresse                      STRUCTURE 0..1       | |
| |          [x] Strasse                                      | |
| |          [x] Nummer                                       | |
| |        -> Gemeinde                    ROLE {1}             | |
| +----------------------------------------------------------+ |
|                                                              |
| [Schema preview]                                  [OK][Cancel]|
+--------------------------------------------------------------+
```

Detaillierte GUI-Regeln siehe `03-gui-ux.md`.

---

# 10. Arbeitspaket 1.6 – Design-Time `getFields()`

Beim Verbinden des Transforms mit nachfolgenden Hop-Transforms muss das Schema bereits bekannt sein.

Verhalten:

```text
Dialog gespeichert
       |
       v
InterlisInputMeta.getFields(...)
       |
       v
compile model
       |
       v
find selected class
       |
       v
HopRowSchemaFactory
       |
       v
IRowMeta
```

Fehler bei Design-Time-Probe dürfen den Dialog nicht unbenutzbar machen; sie werden diagnostisch behandelt wie beim bestehenden GeoTools Reader.

---

# 11. Demo Phase 1

```text
INTERLIS Input
HopIli_Geometry_V1.TestObject
          |
          v
Preview Rows
```

Preview muss zeigen:

```text
_ili_tid | _ili_bid | name | center | boundary
------------------------------------------------
o1       | b1       | A    | POINT... | CURVEPOLYGON...
```

Zusätzliche Demonstration:

```text
INTERLIS Input -> Vector Writer -> GeoPackage
```

mit bestehendem GeoTools Plugin.

---

# 12. Tests Phase 1

Pflicht:

- alle Primitive,
- Nullwerte,
- BigDecimal-Präzision,
- Enumeration,
- Vererbung,
- zwei Geometrieattribute,
- ARC,
- 3D-Koordinate,
- single structure flatten,
- Design-Time Schema,
- echte XTF-Datei,
- Hop pipeline integration,
- `hop-run` E2E.

---

# 13. Definition of Done Phase 1

- [ ] `INTERLIS Input` in Hop-Palette sichtbar.
- [ ] Klasse per GUI auswählbar.
- [ ] Model Browser funktioniert.
- [ ] Schema Preview funktioniert.
- [ ] `getFields()` liefert typisierte Metadaten.
- [ ] mehrere Geometry-Felder funktionieren.
- [ ] Arc bleibt Arc.
- [ ] Single Structure kann geflattet werden.
- [ ] XTF -> Preview funktioniert.
- [ ] XTF -> GeoPackage funktioniert.
- [ ] automatisierter E2E-Test grün.

**Das ist der erste bewusst vorzeigbare Meilenstein.**

---

# 14. Phase 2 – `INTERLIS Output` und vollständiger Typed Roundtrip

## 14.1 Ziel

```text
normaler Hop Row Stream
          |
          v
   INTERLIS Output
          |
          v
       output.xtf
```

und:

```text
XTF -> INTERLIS Input -> INTERLIS Output -> XTF
```

für die in Phase 1 unterstützte Semantik.

---

# 15. Arbeitspaket 2.1 – `RowToIomMapper`

Implementieren:

```java
public final class RowToIomMapper {
  public IomObject map(
      RowMappingPlan plan,
      IRowMeta rowMeta,
      Object[] row,
      RowToIomContext context)
      throws InterlisMappingException;
}
```

Pflicht:

- OID,
- primitive Typen,
- Geometry,
- single structures,
- einfache Rollen,
- Nullregeln,
- mandatory checking im Strict Mode.

---

# 16. Arbeitspaket 2.2 – Writer Service

Abstraktion:

```java
public interface InterlisTransferWriter extends AutoCloseable {
  void startTransfer(TransferHeader header);
  void startBasket(BasketDescriptor basket);
  void writeObject(IomObject object);
  void endBasket();
  void endTransfer();
}
```

XTF-Implementierung kapselt iox-ili.

---

# 17. Arbeitspaket 2.3 – `INTERLIS Output`

Klassen:

```text
InterlisOutputMeta
InterlisOutputData
InterlisOutput
InterlisOutputDialog
```

GUI muss eingehendes Hop-Schema gegen gewählte INTERLIS-Klasse abgleichen.

---

# 18. Output Mapping GUI

```text
+-------------------------------------------------------------------+
| INTERLIS Output                                                   |
+-------------------------------------------------------------------+
| File       [ result.xtf                                ] [Browse] |
| Model      [ HopIli_Geometry_V1                       ] [Select] |
| Class      [ HopIli_Geometry_V1.Data.TestObject       ] [Select] |
|                                                                   |
| Field mapping                                                     |
| +----------------------+------------------------+----------------+ |
| | INTERLIS property   | Hop field              | Status         | |
| +----------------------+------------------------+----------------+ |
| | TID                 | _ili_tid               | OK             | |
| | Name                | name                   | OK             | |
| | Center              | center                 | Geometry       | |
| | Boundary            | boundary               | Geometry       | |
| +----------------------+------------------------+----------------+ |
|                                                                   |
| Basket                                                            |
|   BID field          [ _ili_bid                         ]          |
|   Default BID        [ b1                               ]          |
|                                                                   |
| [Validate mapping]                                      [OK][Cancel]|
+-------------------------------------------------------------------+
```

---

# 19. Phase-2 Roundtrip

Verbindliches Demo-Szenario:

```text
input.xtf
   |
   v
INTERLIS Input
   |
   v
Select Values / Calculator
   |
   v
INTERLIS Output
   |
   v
output.xtf
```

Anschliessend:

```bash
ilivalidator output.xtf
```

bzw. programmgesteuert derselbe Validator-Pfad.

---

# 20. Tests Phase 2

- Row -> IOM alle primitiven Typen.
- Geometry -> IOM inkl. ARC.
- Single Structure reconstruction.
- OID/BID.
- Output Meta Serialization.
- XTF Writer Integration.
- XTF semantic roundtrip.
- Invalid mandatory field.
- Wrong Hop type.
- Multiple baskets basic.
- Hop-run roundtrip E2E.

---

# 21. Definition of Done Phase 2

- [ ] Typed `INTERLIS Output` funktioniert.
- [ ] Field Mapping GUI funktioniert.
- [ ] Input -> Output Roundtrip semantisch korrekt.
- [ ] Arc Roundtrip korrekt.
- [ ] Mehrere Geometry-Felder korrekt.
- [ ] Ausgabe kann validiert werden.
- [ ] Negative Writer-Fälle getestet.

---

# 22. Phase 3 – Strukturen umfassend

## 22.1 Ziel

INTERLIS-Strukturen werden sowohl bequem als auch verlustfrei behandelt.

Semantik:

```text
0..1 / 1 structure
    -> standardmässig flatten

BAG/LIST OF structure
    -> Structure Explode / Collect
```

---

# 23. Arbeitspaket 3.1 – rekursives Single-Structure Flattening

Erweitern:

```text
Adresse.Strasse -> Adresse_Strasse
Adresse.Ort.PLZ -> Adresse_Ort_PLZ
```

Regeln:

- stabiler Separator,
- konfigurierbarer Prefix,
- Kollisionsprüfung,
- maximale Rekursion nicht künstlich auf eine Ebene beschränken.

---

# 24. Arbeitspaket 3.2 – `INTERLIS Structure Explode`

Klassen:

```text
InterlisStructureExplodeMeta
InterlisStructureExplodeData
InterlisStructureExplode
InterlisStructureExplodeDialog
```

Input:

```text
Typed parent row + hidden/raw structure carrier OR advanced object context
```

Die genaue technische Carrier-Strategie wird gemäss Architektur umgesetzt; die Benutzeroberfläche zeigt nur das Strukturattribut.

Output:

```text
_ili_parent_tid
_ili_parent_bid
_ili_index
<structure fields>
```

Bei `LIST` ist `_ili_index` semantisch.

---

# 25. Arbeitspaket 3.3 – `INTERLIS Structure Collect`

Zwei Inputs:

```text
Parent stream ------+
                    |
                    v
             Structure Collect
                    ^
                    |
Child stream -------+
```

Konfiguration:

- Parent TID field,
- Child parent TID field,
- Index field,
- Strukturdefinition.

---

# 26. Demo Phase 3

```text
INTERLIS Input: Person
        |
        +---------------------------> parent
        |
        v
Structure Explode: Adressen
        |
        v
Filter / Mapper
        |
        v
Structure Collect
        ^
        |
parent--+
        |
        v
INTERLIS Output
```

---

# 27. Tests Phase 3

- 0..1 missing.
- nested single structure.
- LIST empty/one/many.
- LIST order.
- BAG.
- child geometry.
- duplicate index error.
- collect with missing child.
- full XTF LIST roundtrip.

---

# 28. Definition of Done Phase 3

- [ ] Single structures vollständig unterstützt.
- [ ] LIST/BAG explode.
- [ ] LIST/BAG collect.
- [ ] LIST-Reihenfolge erhalten.
- [ ] Struktur-GUI modellbewusst.
- [ ] E2E Roundtrip grün.

---

# 29. Phase 4 – Assoziationen und Role Join

## 29.1 Ziel

Assoziationen sollen sich für Hop-Benutzer wie verständliche relationale Beziehungen verhalten, ohne XTF-Transferdetails zu verlangen.

---

# 30. Arbeitspaket 4.1 – Simple Role Mapping

Verbindliche Darstellung:

```text
gemeinde_ref : String
```

optional:

```text
gemeinde_ref_bid : String
```

Hop ValueMeta erhält INTERLIS-Metadaten:

```text
ili.kind   = ROLE
ili.target = Model.Topic.Gemeinde
ili.min    = 1
ili.max    = 1
```

---

# 31. Arbeitspaket 4.2 – Assoziationsattribute

Für einfach einbettbare Beziehungen kann GUI Flattening anbieten:

```text
eigentuemer_ref
eigentuemer_Anteil
```

Komplexe Beziehungen bleiben eigene Rows.

---

# 32. Arbeitspaket 4.3 – Association Rows

m:n / n-äre / komplexe Assoziation:

```text
_ili_tid              # falls Assoziation eigene OID besitzt
person_ref
organisation_ref
Funktion
Eintritt
_ili_order_pos         # falls relevant
```

Diese Assoziationen müssen im Model Browser als eigene auswählbare Transfer-Viewables erscheinen.

---

# 33. Arbeitspaket 4.4 – `INTERLIS Role Join`

Modellbewusster Join:

```text
source --------+
               |
               v
        INTERLIS Role Join
               ^
               |
target --------+
```

Der Benutzer wählt:

```text
Role: gemeinde -> Gemeinde {1}
```

nicht manuell:

```text
gemeinde_ref = _ili_tid
```

---

# 34. GUI Role Join

```text
+----------------------------------------------------------------+
| INTERLIS Role Join                                             |
+----------------------------------------------------------------+
| Source stream        [ Gebaeude                           ]     |
| Target stream        [ Gemeinde                            ]     |
|                                                                |
| Role                 [ gemeinde -> Gemeinde {1}          v]     |
|                                                                |
| Join type            [ Left                              v]     |
| Output prefix        [ gemeinde_                          ]     |
|                                                                |
| Target fields                                                  |
|   [x] Name                                                     |
|   [x] BFSNr                                                    |
|   [ ] Geometrie                                                |
|                                                                |
| Model-derived join                                             |
|   source: gemeinde_ref                                         |
|   target: _ili_tid                                             |
|                                                                |
|                                                    [OK][Cancel] |
+----------------------------------------------------------------+
```

---

# 35. Tests Phase 4

- simple roles.
- optional roles.
- ref BID.
- association attrs.
- m:n.
- ordered role.
- n-ary.
- role join success.
- missing target.
- duplicate target.
- XTF roundtrip.

---

# 36. Definition of Done Phase 4

- [ ] einfache Rollen als normale Felder nutzbar.
- [ ] komplexe Assoziationen als eigene Streams.
- [ ] Association Attributes unterstützt.
- [ ] ORDERED erhalten.
- [ ] Role Join GUI modellbewusst.
- [ ] Assoziations-Roundtrip E2E grün.

---

# 37. Phase 5 – Advanced Envelope und generische Transfer-Transforms

## 37.1 Ziel

Neben dem komfortablen Typed Workflow wird der vollständige generische Backbone öffentlich nutzbar.

```text
INTERLIS Transfer Input
       |
       v
InterlisObjectEnvelope
       |
       +--> Object to Row
       |
       +--> Filter / Router
       |
       v
Row to Object
       |
       v
INTERLIS Transfer Output
```

---

# 38. Arbeitspaket 5.1 – `InterlisObjectEnvelope`

Verbindlicher Datentyp:

```java
public record InterlisObjectEnvelope(
    InterlisEventType eventType,
    String className,
    String topicName,
    String basketId,
    String objectId,
    InterlisObjectOperation operation,
    IomObject object,
    TransferContext transferContext) {}
```

Für normalen OBJECT-Mode ist `object` gesetzt.

Event Mode kann später zusätzliche Events transportieren.

---

# 39. Arbeitspaket 5.2 – eigener Value Type

Implementieren:

```java
@ValueMetaPlugin(...)
public final class ValueMetaInterlisObject extends ValueMetaBase
```

Anforderungen:

- clone,
- serialization,
- readable `getString()` nur für Debug/Preview,
- keine implizite verlustbehaftete String-Konvertierung,
- klarer Type ID.

Alternativ darf in einem Spike geprüft werden, ob Hop `TYPE_SERIALIZABLE` robust genug wäre. Für ein langfristig umfassendes Plugin wird ein expliziter Value Type bevorzugt.

---

# 40. Arbeitspaket 5.3 – `INTERLIS Transfer Input`

Output-Schema stabil:

```text
_ili_event
_ili_class
_ili_topic
_ili_bid
_ili_tid
_ili_operation
_ili_object
```

Damit können beliebige Klassen im selben Stream transportiert werden, weil das Row-Schema konstant bleibt.

---

# 41. Arbeitspaket 5.4 – `INTERLIS Object to Row`

Konfiguration:

- Model Source,
- Class,
- Feldprojektion,
- Structure Mapping,
- Role Mapping.

Input:

```text
_ili_object : InterlisObject
```

Output:

```text
typed class row
```

---

# 42. Arbeitspaket 5.5 – `INTERLIS Row to Object`

Inverse Operation.

Output wieder Envelope-Schema.

Dadurch können mehrere Klassen nach `Row to Object` wieder mit normalen Hop-Merge-Transforms zusammengeführt werden.

---

# 43. Arbeitspaket 5.6 – `INTERLIS Transfer Output`

Liest Envelope Rows und schreibt Transfer.

Normaler Object Mode:

- gruppiert Baskets,
- erzeugt Transfer-/Basket-Events.

Advanced Event Mode:

- respektiert explizite Event-Reihenfolge.

---

# 44. Demo Phase 5

```text
                         +--> Object to Row: Gebaeude --> ... --+
                         |                                      |
Transfer Input --> Router                                        +--> Row to Object --+
                         |                                      |                    |
                         +--> Object to Row: Gemeinde  --> ... --+                    v
                                                                             Transfer Output
```

Damit ist das Gegenstück zu ili2fme-artigem generischem Arbeiten vorhanden, aber Hop-gerecht.

---

# 45. Tests Phase 5

- ValueMeta serialization.
- Envelope schema.
- mixed classes.
- routing.
- merge after RowToObject.
- basket preservation.
- delete operation.
- transfer roundtrip.
- plugin serialization under Hop.

---

# 46. Definition of Done Phase 5

- [ ] Advanced generic stream funktioniert.
- [ ] Mixed classes im Envelope möglich.
- [ ] ObjectToRow/RowToObject symmetrisch.
- [ ] TransferInput/Output roundtrip.
- [ ] Typed Input/Output bleiben einfache Fassade.

---

# 47. Phase 6 – Validation, Enumerationen, Transferkontrolle und UX-Härtung

Diese Phase bündelt Funktionen, die aus einem guten Reader/Writer ein wirklich umfassendes INTERLIS-Plugin machen.

---

# 48. Arbeitspaket 6.1 – `INTERLIS Validate`

Modi:

```text
Fail pipeline
Route errors
Annotate rows
```

Empfohlener Default:

```text
Route errors + optional fail after completion
```

Outputs:

```text
valid
errors
```

Error-Schema:

```text
_ili_severity
_ili_message
_ili_tid
_ili_bid
_ili_class
_ili_attribute
_ili_line
_ili_column
```

---

# 49. Arbeitspaket 6.2 – Validator Config / MetaConfig

Unterstützen:

- lokale Config-Datei,
- `ilidata:`-Referenz, soweit Bibliotheken dies unterstützen,
- MetaConfig,
- Multiplicity option,
- Unique OID option.

Typed Input/Output erhalten einen einfachen Validation-Bereich; der separate Validator bleibt für ETL-Ketten verfügbar.

---

# 50. Arbeitspaket 6.3 – `INTERLIS Enumerations`

Transform kann Modell-Enumerationen als Rows ausgeben:

```text
enum_type
enum_value
enum_code
enum_parent
```

Nützlich für:

- Lookup-Tabellen,
- Qualitätsprüfungen,
- Join mit Beschriftungen.

---

# 51. Arbeitspaket 6.4 – Basket-Metadaten

Ausbauen:

- Basket topic,
- BID,
- consistency,
- kind,
- start state/end state soweit relevant,
- domains/signatures soweit von XTF/iox verfügbar und sinnvoll.

Normale Benutzeroberfläche zeigt nur häufig benötigte Werte; Advanced Section enthält den Rest.

---

# 52. Arbeitspaket 6.5 – Delete / Update Transfer

Advanced Mode:

```text
_ili_operation = INSERT / UPDATE / DELETE
```

bzw. Werte gemäss iox/IOM-Konstanten.

Typed Output erhält später optional ein Operation Field.

---

# 53. Arbeitspaket 6.6 – UX Polish

- konsistente Tooltips,
- model-aware warnings,
- Schema status badges,
- bessere Fehlertexte,
- Preview limit,
- Suche im Model Browser,
- Filter nach Topic/Class/Association,
- zuletzt verwendete Modellquellen soweit Hop-konform,
- sensible Defaults.

---

# 54. Tests Phase 6

- Validator valid/error paths.
- config serialization.
- MetaConfig.
- enum extraction.
- basket metadata.
- delete operations.
- negative UI controller tests.

---

# 55. Definition of Done Phase 6

- [ ] Validator integriert.
- [ ] Fehlerrows brauchbar.
- [ ] Enumerationen als Rows.
- [ ] Basket Metadaten vollständig genug für Roundtrip.
- [ ] Delete/Operation Advanced Mode.
- [ ] zentrale Dialoge UX-poliert.

---

# 56. Phase 7 – ITF / INTERLIS 1

## 56.1 Ziel

INTERLIS 1 wird bewusst nach dem stabilen XTF-Kern umgesetzt, weil AREA/SURFACE und Linetables besondere Semantik besitzen.

---

# 57. Arbeitspaket 7.1 – ITF Reader/Writer

ReaderFactory-/iox-basierter Pfad, soweit geeignet.

Zusätzliche Konfiguration:

```text
ITF mode:
  Polygon
  Raw
  Polygon + Raw
```

Analog zu etablierten ili2fme-Konzepten, aber Hop-gerecht benannt.

---

# 58. Arbeitspaket 7.2 – AREA/SURFACE

Modi:

### Polygon

```text
AREA/SURFACE -> Geometry
```

### Raw

```text
main table rows
line table rows
```

### Polygon + Raw

beides verfügbar.

Die genaue Ausgabe soll nicht versuchen, zwei verschiedene Row-Schemas in einen Stream zu mischen.

---

# 59. Arbeitspaket 7.3 – Linetable Transforms

Falls für UX nötig, eigene Hilfstransforms:

```text
INTERLIS ITF Line Table Explode
INTERLIS ITF Polygon Build
```

Dies wird erst nach Spike entschieden; kein unnötiger Transform nur zur Nachbildung von ili2fme.

---

# 60. Arbeitspaket 7.4 – ITF Enumeration Codes

Default:

```text
Enumeration element name
```

Legacy-Option:

```text
raw ITF enum code
```

nur Advanced.

---

# 61. Tests Phase 7

- einfache ITF-Klasse.
- Enum.
- Referenz.
- AREA polygon.
- SURFACE polygon.
- raw linetable.
- invalid polygon building.
- roundtrip.
- bekannte Problemfälle mit Überlappungen als Regression.

---

# 62. Definition of Done Phase 7

- [ ] ITF Reader.
- [ ] ITF Writer.
- [ ] AREA/SURFACE Default-Workflow.
- [ ] Raw-Modus für Fehleranalyse.
- [ ] automatisierte ITF-E2E-Tests.

---

# 63. Phase 8 – Hardening, Performance und Kompatibilität

## 63.1 Ziel

Produktionsreife erhöhen, ohne die API unnötig aufzublähen.

---

# 64. Arbeitspaket 8.1 – Performance

- Streaming prüfen.
- MappingPlan caching.
- Model cache.
- weniger Allocation pro Row.
- Geometry conversion profiling.
- grosse XTF-Testdateien.

Keine Optimierung darf die Klarheit der Semantik gefährden.

---

# 65. Arbeitspaket 8.2 – Threading

Für jeden Transform explizit festlegen:

```text
parallel copies supported: yes/no
thread-safe shared services: yes/no
```

Writer standardmässig nicht parallel auf dieselbe Datei.

---

# 66. Arbeitspaket 8.3 – Hop-Kompatibilität

Testen gegen:

- minimale unterstützte Hop-Version,
- aktuelle freigegebene Version.

Bei API-Änderungen Compatibility Layer statt Copy/Paste-Divergenz.

---

# 67. Arbeitspaket 8.4 – Modellrepository-Resilienz

- Cache,
- Timeouts,
- Proxy,
- verständliche Diagnostics,
- offline behavior,
- lokale Overrides.

---

# 68. Arbeitspaket 8.5 – Dokumentation und Beispiele

Beispiele:

```text
examples/
├── xtf-to-gpkg/
├── gpkg-to-xtf/
├── structures/
├── associations/
├── validation/
├── advanced-envelope/
└── itf/
```

---

# 69. Definition of Done Phase 8

- [ ] Performance-Baseline dokumentiert.
- [ ] grosse Datei ohne Vollspeicher-Laden.
- [ ] Classloader-/Resource-Leaks geprüft.
- [ ] Compatibility Matrix in CI.
- [ ] Beispiele vollständig.
- [ ] Releaseprozess automatisiert.

---

# 70. Reihenfolge der Benutzertransforms

Empfohlene Implementierungsreihenfolge:

```text
1  INTERLIS Input
2  INTERLIS Output
3  INTERLIS Structure Explode
4  INTERLIS Structure Collect
5  INTERLIS Role Join
6  INTERLIS Transfer Input
7  INTERLIS Object to Row
8  INTERLIS Row to Object
9  INTERLIS Transfer Output
10 INTERLIS Validate
11 INTERLIS Enumerations
12 ITF-spezifische Erweiterungen
```

Warum?

- `Input` bringt den schnellsten sichtbaren Nutzen.
- `Output` macht daraus einen echten ETL-Roundtrip.
- Structures/Associations lösen danach die grössten INTERLIS-Modellierungsfälle.
- der generische Advanced Layer ist wichtig, aber kein Grund, den einfachen Benutzerpfad zu verzögern.

---

# 71. PR-/Issue-Grösse

Arbeiten sollen nicht als „Implement Phase 3“ in einen riesigen Pull Request gepackt werden.

Beispiel Phase 1:

```text
#101 Add model compilation service
#102 Add class descriptor extraction
#103 Add Hop row schema factory
#104 Add primitive IOM mapper
#105 Add geometry mapping
#106 Add InterlisInput runtime skeleton
#107 Add model browser service
#108 Add InterlisInput dialog
#109 Add design-time getFields
#110 Add XTF -> Hop pipeline integration tests
#111 Add XTF -> GeoPackage E2E
```

Jeder PR:

- kompiliert,
- hat Tests,
- zerstört keinen bestehenden Demo-Pfad.

---

# 72. Vertical Slice Rule

Neue Features möglichst vertikal implementieren.

Beispiel „Enumeration“:

Nicht:

```text
PR 1: 20 neue Descriptor-Klassen
PR 2: Mapper irgendwann später
PR 3: GUI irgendwann später
```

Sondern:

```text
Descriptor
   + Schema
   + Mapper
   + GUI Preview
   + Test
```

in einem überschaubaren Feature-Slice.

---

# 73. Architektur-Guardrails über alle Phasen

Diese Regeln dürfen nicht aus Bequemlichkeit aufgeweicht werden:

### 73.1 Kein heterogenes Typed Row-Schema

Nicht:

```text
row 1 = Gebaeude schema
row 2 = Gemeinde schema
```

im selben Hop-Stream.

Dafür existiert der Advanced Envelope.

### 73.2 Keine implizite Curve-Linearisierung

Wenn ein Downstream-Format keine Kurven kann, muss die Linearisation am entsprechenden Format-Boundary explizit stattfinden, nicht beim INTERLIS Input.

### 73.3 Keine versteckte Stringifizierung von Strukturen

`BAG/LIST` nicht als JSON/XML-String in normale Hop-Felder packen.

### 73.4 Keine HashMap-gesteuerte Feldreihenfolge

Schema immer modell- und konfigurationsdeterministisch.

### 73.5 XTF-Codierungsdetails nicht zur UX machen

Der normale Benutzer arbeitet mit:

```text
class
attribute
structure
role
basket
```

nicht mit `IomObject`, `EmbeddedLinkStruct` oder Event-Klassen.

---

# 74. Definition of Done für jede Phase

Jede Phase braucht zusätzlich zu ihren eigenen Kriterien:

- [ ] `mvn clean verify` grün.
- [ ] keine Testdeaktivierung ohne dokumentierten Grund.
- [ ] `check-distribution.py` grün.
- [ ] lokale Dev-Sync Installation funktioniert.
- [ ] mindestens ein realer Hop-Pipeline-Test für neue Hauptfunktion.
- [ ] zentrale negative Fälle getestet.
- [ ] README/Docs aktualisiert.
- [ ] Beispielpipeline eingecheckt.
- [ ] kein bekannter Datenverlust im dokumentierten Funktionsumfang.

---

# 75. Wann ein Feature bewusst verschoben werden darf

Ein Feature darf in die nächste Phase verschoben werden, wenn:

1. der aktuelle Zwischenstand fachlich geschlossen bleibt,
2. das Verschieben keine stille Semantikänderung erzeugt,
3. die Einschränkung in GUI und Dokumentation sichtbar ist,
4. ein konkreter Test bzw. Issue für die spätere Funktion existiert.

Beispiel akzeptabel:

```text
Phase 1 unterstützt BAG/LIST noch nicht.
GUI zeigt:
  "Use Structure Explode – available in a later phase"
```

Nicht akzeptabel:

```text
BAG/LIST wird still ignoriert.
```

---

# 76. Empfohlener Startpunkt für einen Coding Agent

Ein Coding Agent sollte **nicht** mit „implementiere das ganze Plugin“ gestartet werden.

Erster Auftrag:

```text
Implementiere Phase 0 vollständig.

Stoppe nach erfolgreicher Phase 0.
Führe alle Tests aus.
Dokumentiere die tatsächlich gewählten API-Versionen und Abweichungen.
Beginne Phase 1 erst, wenn Phase 0 grün ist.
```

Danach jeweils eine Phase bzw. ein kleines Arbeitspaket.

---

# 77. Fortschrittsnachweis

Für jede Phase kann eine Datei geführt werden:

```text
docs/progress/phase-01.md
```

mit:

```text
Status
Implemented
Tests
Known limitations
Demo pipeline
Decisions / ADR links
```

Das hilft besonders bei längerer agentischer Implementierung und verhindert, dass bereits getroffene Architekturentscheidungen wieder verloren gehen.

---

# 78. Architekturentscheidungen als ADR

Wichtige Entscheidungen als kleine ADR-Dateien festhalten:

```text
docs/adr/
├── 0001-typed-rows-and-envelope.md
├── 0002-shared-geometry-value-type.md
├── 0003-curves-via-sqlmm-wkb.md
├── 0004-structure-mapping.md
├── 0005-association-mapping.md
├── 0006-model-cache.md
└── 0007-itf-after-xtf.md
```

Damit bleibt die Begründung hinter dem Code nachvollziehbar.

---

# 79. Zusammenfassung der Lieferfolge

```text
PHASE 0
Build + Model + Curve Spike + Dev Install
        |
        v
PHASE 1   <---- erster klar vorzeigbarer Stand
INTERLIS Input + gute GUI + XTF -> Hop/GeoPackage
        |
        v
PHASE 2
INTERLIS Output + XTF Roundtrip
        |
        v
PHASE 3
Structures vollständig
        |
        v
PHASE 4
Associations + Role Join
        |
        v
PHASE 5
Advanced Envelope + Transfer I/O
        |
        v
PHASE 6
Validation + Enums + Basket/Operations + UX
        |
        v
PHASE 7
ITF / INTERLIS 1
        |
        v
PHASE 8
Hardening + Performance + Compatibility
```

Diese Reihenfolge liefert früh sichtbaren Nutzen, ohne die langfristig umfassende Architektur zu verbauen.
