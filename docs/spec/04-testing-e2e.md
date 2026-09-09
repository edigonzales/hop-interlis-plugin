# hop-interlis-plugin – Teststrategie, Integrations- und E2E-Tests

## 1. Ziel dieses Dokuments

Dieses Dokument definiert die verbindliche Teststrategie für `hop-interlis-plugin`.

Das Plugin sitzt an einer anspruchsvollen Systemgrenze:

- INTERLIS-Metamodell (`ili2c`),
- INTERLIS-Transfer (`iox-ili`),
- XTF/ITF-Dateien,
- Geometrien inklusive Kreisbögen,
- Apache-Hop-Row-Metadaten,
- Apache-Hop-Laufzeit,
- SWT-GUI,
- Plugin-Classloader und Distribution.

Ein Test, der nur eine einzelne Mapper-Methode mit drei primitiven Feldern prüft, reicht deshalb nicht. Die Teststrategie muss nachweisen, dass **dieselbe Modellsemantik durch alle Schichten hindurch korrekt bleibt**.

Die wichtigsten Qualitätsziele sind:

1. Das vom INTERLIS-Modell abgeleitete Hop-Schema ist deterministisch und korrekt.
2. `IomObject -> Object[]` und `Object[] -> IomObject` sind semantisch invers, soweit der gewählte Mapping-Modus dies zulässt.
3. XTF-Lesen und -Schreiben funktioniert mit echten Dateien.
4. Mehrere Geometrieattribute und Kreisbögen bleiben erhalten.
5. Strukturen und Assoziationen verhalten sich wie in der Architektur spezifiziert.
6. Baskets, OIDs, Referenzen, Reihenfolgen und Operationen gehen nicht verloren.
7. Fehler führen zu verständlichen Hop-Fehlern und nicht zu stiller Datenkorruption.
8. Die installierbare ZIP-Datei funktioniert in einer realen Apache-Hop-Installation.
9. Der lokale Entwicklungsworkflow ist reproduzierbar.
10. Regressionen werden möglichst früh durch kleine, schnelle Tests gefunden.

---

## 2. Testpyramide

Die Tests werden in fünf Ebenen gegliedert.

```text
                         +-------------------------+
                         |  E2E mit echtem Hop     |
                         |  hop-run / Plugin ZIP   |
                         +------------+------------+
                                      |
                     +----------------+----------------+
                     | Pipeline-/Transform-Integration |
                     | echte Hop Runtime               |
                     +----------------+----------------+
                                      |
                  +-------------------+-------------------+
                  | iox-/ili2c-Integration                |
                  | echte ILI/XTF-Dateien                 |
                  +-------------------+-------------------+
                                      |
               +----------------------+----------------------+
               | Mapper / Schema / Geometry / Model Services |
               | schnelle Java-Tests                         |
               +----------------------+----------------------+
                                      |
                         +------------+------------+
                         | kleine Unit Tests       |
                         +-------------------------+
```

### 2.1 Grundsatz

Je tiefer die Ebene, desto:

- schneller,
- isolierter,
- zahlreicher,
- präziser bei der Fehlerlokalisierung.

Je höher die Ebene, desto:

- realistischer,
- langsamer,
- näher am Benutzererlebnis,
- wichtiger für Release-Freigaben.

**Kein Release darf allein auf Unit-Tests beruhen.**

---

# 3. Testmodule im Maven-Projekt

Empfohlene Struktur:

```text
hop-interlis-plugin/
├── hop-interlis-core/
│   └── src/test/java/...
│
├── hop-interlis-transforms/
│   └── src/test/java/...
│
├── hop-interlis-ui/
│   └── src/test/java/...
│
├── hop-interlis-it/
│   ├── pom.xml
│   ├── src/test/java/...
│   └── src/test/resources/...
│
├── e2e/
│   ├── pipelines/
│   ├── expected/
│   ├── input/
│   ├── scripts/
│   └── README.md
│
└── test-models/
    ├── src/main/resources/models/
    ├── src/main/resources/data/
    └── README.md
```

`hop-interlis-it` enthält Tests, die bewusst mehrere Produktmodule miteinander verdrahten.

Die E2E-Dateien unter `e2e/` werden nicht als versteckte Java-Fixtures behandelt, sondern sollen auch manuell in Hop GUI geöffnet werden können.

---

# 4. Testmodelle

## 4.1 Nicht nur reale Grossmodelle verwenden

Für Kernsemantik werden kleine, gezielt konstruierte INTERLIS-Modelle benötigt.

Vorteile:

- Fehler sind leichter verständlich.
- Jede Spracheigenschaft kann isoliert geprüft werden.
- Golden Files bleiben klein.
- Tests sind unabhängig von externen Modelländerungen.

Reale Modelle werden zusätzlich als Kompatibilitäts- und Belastungstests verwendet.

---

## 4.2 Verbindliche Testmodell-Suite

```text
test-models/models/
├── HopIli_Primitives_V1.ili
├── HopIli_Geometry_V1.ili
├── HopIli_Structures_V1.ili
├── HopIli_Associations_V1.ili
├── HopIli_Inheritance_V1.ili
├── HopIli_Baskets_V1.ili
├── HopIli_Enums_V1.ili
├── HopIli_Operations_V1.ili
├── HopIli_InvalidCases_V1.ili
└── HopIli_Itf_V1.ili            # erst in ITF-Phase aktiv
```

### `HopIli_Primitives_V1.ili`

Muss mindestens abdecken:

- `TEXT`
- `MTEXT`
- `NAME`
- `URI`
- `BOOLEAN`
- Ganzzahlwerte
- Dezimalwerte
- numerische Werte mit unterschiedlichen Genauigkeiten
- Datum
- Zeitstempel
- Enumeration
- optionale Attribute
- obligatorische Attribute
- Werte an Grenzen der Domäne
- Unicode-Text
- leere Strings, soweit transferseitig unterscheidbar

### `HopIli_Geometry_V1.ili`

Muss mindestens abdecken:

- `COORD 2D`
- `COORD 3D`
- `MULTICOORD`
- `POLYLINE`
- Polyline nur mit Geraden
- Polyline mit `ARC`
- gemischte Gerade/Bogen-Geometrie
- `SURFACE`
- `SURFACE` mit Loch
- `SURFACE` mit Bogen
- `AREA`
- `MULTIPOLYLINE`
- `MULTISURFACE`
- mehrere Geometrieattribute in derselben Klasse

### `HopIli_Structures_V1.ili`

Muss enthalten:

- Struktur `1`
- Struktur `0..1`
- verschachtelte Struktur
- `BAG OF`
- `LIST OF`
- leere Liste
- Liste mit einem Element
- Liste mit mehreren Elementen
- Struktur mit Geometrieattribut
- Struktur mit Enumeration
- Struktur in Struktur

### `HopIli_Associations_V1.ili`

Muss enthalten:

- einfache `1:n`-Beziehung
- optionale Rolle
- obligatorische Rolle
- Assoziationsattribute
- `m:n`
- n-äre Assoziation
- geordnete Rolle
- Assoziation mit OID, soweit modellseitig sinnvoll
- eingebettete und nicht eingebettete Transferdarstellung
- Referenz in anderen Basket, soweit zulässig

### `HopIli_Inheritance_V1.ili`

Muss enthalten:

- abstrakte Basisklasse
- konkrete Subklasse
- mehrere Spezialisierungen
- geerbte primitive Attribute
- geerbte Geometrie
- geerbte Rolle
- überschreibbare bzw. spezialisierte Domänen, soweit INTERLIS dies erlaubt

---

# 5. Testdatenkonvention

Für jedes Testmodell gibt es mindestens:

```text
models/HopIli_X_V1.ili
data/HopIli_X_V1_valid.xtf
data/HopIli_X_V1_edgecases.xtf
```

Bei absichtlich fehlerhaften Daten:

```text
data/HopIli_X_V1_invalid_<case>.xtf
```

Beispiele:

```text
HopIli_Associations_V1_invalid_missing_ref.xtf
HopIli_Primitives_V1_invalid_mandatory.xtf
HopIli_Geometry_V1_invalid_surface.xtf
```

Jede Fixture-Datei erhält eine kurze Beschreibung in:

```text
test-models/README.md
```

Damit Tests nicht zu einer Sammlung undokumentierter XML-Dateien werden.

---

# 6. Java-Unit-Tests

## 6.1 `InterlisPrimitiveCodecTest`

Klasse:

```java
class InterlisPrimitiveCodecTest
```

Verbindliche Testfälle:

```java
text_is_mapped_to_string()
boolean_true_is_mapped_to_boolean_true()
boolean_false_is_mapped_to_boolean_false()
integer_is_mapped_to_long()
large_integer_is_not_truncated()
decimal_is_mapped_to_big_decimal_without_precision_loss()
enum_is_mapped_to_qualified_or_plain_value_according_to_policy()
xml_date_is_mapped_to_expected_java_type()
xml_datetime_is_mapped_to_expected_java_type()
null_optional_value_stays_null()
invalid_boolean_throws_mapping_exception()
invalid_decimal_contains_attribute_name_in_error()
```

### Besonders wichtig: Dezimalwerte

Kein Test darf nur Werte wie `1.0` benutzen.

Beispiel:

```text
12345678901234567890.123456789
```

Der Test muss beweisen, dass kein unbemerkter `double`-Roundtrip stattfindet.

---

# 7. Modellanalyse-Tests

## 7.1 `InterlisModelServiceTest`

Verbindliche Tests:

```java
compile_local_model_returns_transfer_description()
compile_model_with_import_resolves_dependencies()
unknown_model_fails_with_diagnostic()
syntax_error_is_reported_with_model_and_line()
model_cache_reuses_same_compilation_result()
model_cache_key_changes_when_model_configuration_changes()
```

## 7.2 `InterlisClassDescriptorExtractorTest`

Beispieltests:

```java
extracts_effective_attributes_in_stable_order()
includes_inherited_attributes()
marks_geometry_attributes()
marks_structure_attributes()
marks_list_and_bag_cardinality()
marks_role_target_and_cardinality()
marks_association_attributes()
marks_ordered_role()
marks_oid_domain()
```

### Deterministische Reihenfolge

Ein sehr wichtiger Regressionstest:

```java
assertThat(descriptor.fields())
    .extracting(InterlisPropertyDescriptor::qualifiedName)
    .containsExactly(...);
```

Das Row-Schema darf nicht von HashMap-Reihenfolgen oder JVM-Zufällen abhängen.

---

# 8. `IRowMeta`-Tests

## 8.1 `HopRowSchemaFactoryTest`

Die Tests vergleichen nicht nur Feldnamen, sondern auch ValueMeta-Typen und INTERLIS-Metadaten.

Beispiel:

```java
IRowMeta rowMeta = factory.createClassRowMeta(classDescriptor, options);

assertThat(rowMeta.size()).isEqualTo(7);
assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("_ili_tid");
assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("_ili_bid");
assertThat(rowMeta.getValueMeta(2).getName()).isEqualTo("name");
assertThat(rowMeta.getValueMeta(3)).isInstanceOf(ValueMetaGeometry.class);
```

Verbindliche Tests:

```java
creates_stable_schema_for_primitives()
creates_one_geometry_value_meta_per_geometry_attribute()
flattens_single_structure_with_prefix()
does_not_flatten_list_structure()
creates_role_reference_field()
adds_order_position_for_ordered_association_mapping()
preserves_decimal_as_big_number()
adds_interlis_metadata_to_value_meta()
rejects_duplicate_output_field_names()
```

---

# 9. Exakte `IomObject -> Object[]` Mapper-Tests

## 9.1 `IomToRowMapperTest`

Dieser Test ist einer der wichtigsten im Projekt.

### Prinzip

Nicht mit handgebauten Maps testen, sondern mit echten `Iom_jObject`-Instanzen.

Beispiel:

```java
Iom_jObject obj = new Iom_jObject(CLASS_NAME, "o1");
obj.setattrvalue("Name", "Haus A");
obj.setattrvalue("Hoehe", "12.50");
```

Danach:

```java
Object[] row = mapper.map(context, obj);
```

und positionsgenau prüfen.

### Testfälle

```java
maps_tid_to_reserved_tid_field()
maps_bid_from_object_context()
maps_primitive_attributes_by_precompiled_binding()
maps_optional_missing_attribute_to_null()
maps_multiple_geometry_attributes_independently()
maps_single_structure_by_flattening()
maps_role_to_reference_oid()
maps_role_reference_bid_when_enabled()
ignores_unselected_fields_in_projection_mode()
throws_on_unexpected_multiple_value_for_scalar_property()
contains_class_and_attribute_in_error_message()
```

---

# 10. `Object[] -> IomObject` Mapper-Tests

## 10.1 `RowToIomMapperTest`

Verbindliche Tests:

```java
creates_iom_object_with_configured_class_tag()
uses_tid_field_as_object_oid()
writes_primitives_using_interlis_lexical_form()
reconstructs_flattened_single_structure()
writes_role_as_reference_object()
writes_reference_bid_if_present()
writes_multiple_geometry_attributes()
does_not_write_null_optional_attributes()
rejects_null_for_mandatory_attribute_in_strict_mode()
rejects_wrong_hop_field_type_with_field_name()
```

---

# 11. Mapper-Roundtrip-Tests

Zusätzlich zu den Einzeltests:

```text
IomObject
   |
   v
Object[]
   |
   v
IomObject'
```

Die Behauptung lautet nicht zwingend:

```java
original.equals(mappedBack)
```

weil `IomObject` keine geeignete semantische Gleichheit garantieren muss.

Stattdessen wird ein eigener Comparator verwendet:

```java
final class IomSemanticComparator {
  ComparisonResult compare(IomObject expected, IomObject actual, ComparisonOptions options);
}
```

Er vergleicht:

- Tag,
- OID,
- primitive Attribute,
- Unterobjekte,
- Referenzen,
- Reihenfolge bei LIST/ORDERED,
- Geometrien semantisch,
- relevante Transfermetadaten.

---

# 12. Geometrietests

## 12.1 Ziel

Die Geometrietests müssen explizit verhindern, dass während eines Refactorings unbemerkt Kreisbögen linearisiert werden.

Primärer Pfad:

```text
IomObject
   |
   | Iox2wkb
   v
SQL/MM WKB
   |
   | hop-geometry-type
   v
org.locationtech.jts.geom.Geometry / CurveGeometry
```

und zurück:

```text
Hop Geometry
   |
   v
SQL/MM WKB
   |
   | Wkb2iox
   v
IomObject
```

iox-ili besitzt dafür bereits SQL/MM-WKB-Unterstützung für u.a. CircularString, CompoundCurve und CurvePolygon.

## 12.2 `InterlisGeometryMapperTest`

Verbindliche Testfälle:

```java
coord_2d_roundtrips()
coord_3d_roundtrips()
polyline_straight_roundtrips()
polyline_with_arc_remains_curve()
compound_curve_remains_compound_curve()
surface_with_arc_remains_curve_polygon()
surface_with_hole_roundtrips()
multicoord_roundtrips()
multipolyline_roundtrips()
multisurface_roundtrips()
multiple_geometry_fields_do_not_interfere()
null_geometry_stays_null()
```

### Keine schwachen Assertions

Nicht ausreichend:

```java
assertThat(geometry).isNotNull();
```

Mindestens prüfen:

- Geometrietyp,
- Koordinatendimension,
- Arc-/Curve-Typ,
- Anzahl Komponenten,
- Start-/Endkoordinaten,
- Stützpunkt des Bogens,
- Topologie bei Flächen.

### Roundtrip-Assertion für Kurven

Bei einem INTERLIS-ARC muss der Test nach Rückkonvertierung erneut einen ARC im IOM-Baum finden.

Das ist stärker als nur eine visuell ähnliche Liniengeometrie zu vergleichen.

---

# 13. Structure-Tests

## 13.1 Single structure / Flattening

Beispiel:

```text
Person
  Adresse
    Strasse
    Nummer
```

Erwartetes Hop-Schema:

```text
_ili_tid
Name
Adresse_Strasse
Adresse_Nummer
```

Verbindliche Tests:

```java
single_structure_is_flattened_by_default()
optional_missing_structure_produces_all_null_child_fields()
partial_structure_maps_available_values()
nested_structure_uses_deterministic_prefix()
field_name_collision_is_detected()
```

## 13.2 BAG/LIST Explode

`StructureExploderTest`:

```java
list_produces_one_row_per_structure_element()
list_preserves_index()
empty_list_produces_no_child_row()
bag_produces_one_row_per_element()
child_row_contains_parent_tid()
child_row_contains_parent_bid_when_configured()
geometry_inside_structure_is_mapped()
```

## 13.3 Collect

`StructureCollectorTest`:

```java
collects_children_by_parent_tid()
list_children_are_sorted_by_index()
rejects_duplicate_list_index_in_strict_mode()
handles_parent_without_children()
keeps_bag_without_imposing_semantic_order()
```

---

# 14. Assoziationstests

## 14.1 Simple Rollen

```java
simple_role_maps_to_ref_field()
optional_missing_role_maps_to_null()
mandatory_missing_role_is_detected_in_strict_mode()
reference_bid_roundtrips()
```

## 14.2 Assoziationsattribute

```java
embedded_association_attributes_can_be_flattened()
association_attributes_roundtrip()
```

## 14.3 m:n / komplexe Assoziation

```java
mn_association_is_exposed_as_own_row_schema()
association_row_contains_all_role_refs()
association_row_contains_association_attributes()
ordered_role_contains_order_pos()
```

## 14.4 Role Join

`InterlisRoleJoinTest`:

```java
joins_using_model_derived_role()
left_join_preserves_source_without_target()
strict_inner_mode_can_reject_missing_target()
duplicate_target_oid_is_reported()
selected_target_fields_are_prefixed()
```

---

# 15. Inheritance-Tests

Default-Mapping ist ein **effektives flaches Klassenschema**.

Verbindliche Tests:

```java
concrete_class_contains_inherited_fields()
subclass_specific_fields_are_added()
inherited_geometry_is_geometry_value_meta()
inherited_role_is_reference_field()
abstract_class_is_not_offered_as_normal_input_target_unless_advanced_option_enabled()
object_tag_is_preserved_in_generic_envelope()
```

---

# 16. Basket-Tests

Mindestens:

```java
reads_single_basket_bid()
reads_multiple_baskets()
objects_receive_correct_bid()
writer_groups_rows_by_bid()
writer_does_not_mix_topics_between_baskets()
writer_preserves_configured_basket_metadata()
```

Später für Advanced/Event Mode:

```java
start_transfer_event_roundtrips()
start_basket_event_roundtrips()
end_basket_event_roundtrips()
delete_object_event_roundtrips()
```

---

# 17. Validator-Tests

Der `INTERLIS Validate` Transform benötigt sowohl Success- als auch Failure-Tests.

```java
valid_object_goes_to_valid_output()
invalid_object_goes_to_error_output()
error_row_contains_message()
error_row_contains_tid_if_known()
error_row_contains_class_if_known()
error_row_contains_attribute_path_if_known()
error_row_contains_source_line_if_available()
fail_fast_mode_stops_pipeline()
collect_mode_continues_pipeline()
```

Keine Tests sollen sich nur auf den exakten Wortlaut einer externen ili2c-/iox-Fehlermeldung stützen. Stabilere Assertions sind:

- Severity,
- TID,
- Klassenname,
- Attributname,
- Fehlerkategorie.

---

# 18. XTF-Integrationstests

## 18.1 Echte Reader-/Writer-Kette

Test:

```text
fixture.xtf
   |
   v
InterlisTransferReader
   |
   v
Mapper
   |
   v
Rows
   |
   v
Reverse Mapper
   |
   v
InterlisTransferWriter
   |
   v
actual.xtf
```

Danach wird `actual.xtf` mit iox-ili erneut gelesen und **semantisch** gegen das Original verglichen.

### Warum keine XML-Byte-Gleichheit?

Unterschiede in:

- XML-Formatierung,
- Namespace-Reihenfolge,
- Attributreihenfolge,
- Headerdetails,

sind nicht zwingend semantisch relevant.

Dafür entsteht:

```java
final class XtfSemanticComparator {
  ComparisonResult compare(Path expected, Path actual, TransferDescription td);
}
```

---

# 19. Transform-Integrationstests mit Hop

## 19.1 Hop initialisieren

Die Tests initialisieren die echte Hop-Umgebung einmal pro Test-Suite.

Beispielkonzept:

```java
@BeforeAll
static void initHop() throws Exception {
  HopEnvironment.init();
}
```

Die konkrete Initialisierung muss an die mit Hop 2.18 verwendete Testinfrastruktur angepasst werden.

## 19.2 Pipeline programmatisch ausführen

Beispieltests:

```java
class InterlisInputPipelineIT {

  @Test
  void reads_xtf_into_typed_rows() throws Exception {
    PipelineMeta pipelineMeta = ...;
    // INTERLIS Input -> test sink
    Pipeline pipeline = new Pipeline(...);
    pipeline.prepareExecution();
    pipeline.startThreads();
    pipeline.waitUntilFinished();
    assertThat(pipeline.getErrors()).isZero();
  }
}
```

Wenn Hop-Testhilfen für Row-Sinks verfügbar sind, werden diese bevorzugt statt eigener Produktionsklassen verwendet.

---

# 20. Pipeline-Dateien als E2E-Artefakte

Die wichtigsten Benutzerpfade werden als echte `.hpl`-Pipelines eingecheckt.

```text
e2e/pipelines/
├── 01-xtf-to-csv.hpl
├── 02-xtf-to-geopackage.hpl
├── 03-geopackage-to-xtf.hpl
├── 04-xtf-roundtrip.hpl
├── 05-structure-list-explode.hpl
├── 06-structure-list-collect.hpl
├── 07-role-join.hpl
├── 08-validation-errors.hpl
├── 09-multiple-geometries.hpl
└── 10-curves-roundtrip.hpl
```

Diese Pipelines erfüllen zwei Zwecke:

1. automatisierte E2E-Tests,
2. manuell öffnende Demonstrationsbeispiele für Entwickler und Benutzer.

---

# 21. E2E mit `hop-run`

## 21.1 Ziel

Ein Release muss beweisen, dass nicht nur Java-Tests grün sind, sondern dass eine **installierte Plugin-ZIP in einer echten Hop-Distribution geladen und ausgeführt wird**.

Ablauf:

```text
Apache-Hop-Distribution
        |
        +-- install hop-geometry-type-plugin
        |
        +-- install hop-interlis-plugin
        |
        v
     hop-run.sh
        |
        v
   pipeline.hpl
        |
        v
 Output-Dateien
        |
        v
 Assertions / Validator
```

## 21.2 Script

```text
scripts/run-e2e.sh
```

Verantwortung:

1. `HOP_HOME` prüfen.
2. Plugin-ZIP installieren.
3. temporäres E2E-Verzeichnis anlegen.
4. Testdaten kopieren.
5. Pipeline mit lokalem Hop-Runner ausführen.
6. Exit-Code prüfen.
7. erzeugte XTF-Dateien mit dem Test-Comparator prüfen.
8. erzeugte CSV/GeoPackage-Dateien prüfen.
9. temporäre Dateien bei Erfolg optional entfernen.
10. bei Fehlern Logs stehen lassen.

Konzeptioneller Aufruf:

```bash
bash scripts/run-e2e.sh "$HOP_HOME"
```

Intern z.B.:

```bash
"$HOP_HOME/hop-run.sh" \
  -r local \
  -f "$PROJECT_DIR/e2e/pipelines/04-xtf-roundtrip.hpl"
```

Die konkreten Hop-Projekt-/Environment-Parameter werden nur ergänzt, wenn die jeweilige Pipeline sie benötigt.

---

# 22. Apache-Hop Pipeline Unit Tests als zusätzliche Ebene

Apache Hop besitzt eine eigene Pipeline-Unit-Test-Funktionalität mit Test-Datasets und Golden Datasets.

Diese soll dort eingesetzt werden, wo sie einen echten Mehrwert bringt:

- Mapping eines typisierten INTERLIS-Inputs auf normale Hop-Felder,
- Structure Explode,
- Role Join,
- einfache Transformationsketten.

Sie ersetzt aber nicht die Java- und `hop-run`-E2E-Tests.

Empfehlung:

```text
Hop Pipeline Unit Test
  = benutzernahe Regression

JUnit
  = präzise technische Regression

hop-run E2E
  = Installations-/Runtime-Regression
```

---

# 23. End-to-End-Szenario: XTF -> GeoPackage

Wichtiger Demo- und Regressionstest:

```text
+------------------+
| INTERLIS Input   |
| Gebaeude         |
+--------+---------+
         |
         v
+------------------+
| Vector Writer    |
| buildings.gpkg   |
+------------------+
```

Assertions:

- Pipeline Exit 0.
- erwartete Anzahl Features.
- alle ausgewählten Attribute vorhanden.
- Geometry-Feld ist vorhanden.
- SRID korrekt, soweit bekannt.
- Arc-Geometrie bleibt bei GeoPackage als Curve erhalten, sofern der gewählte GeoPackage-Pfad dies unterstützt.

Dieser Test integriert bewusst `hop-interlis-plugin` und `hop-geotools-plugin`.

---

# 24. End-to-End-Szenario: GeoPackage -> XTF

```text
+------------------+
| Vector Reader    |
+--------+---------+
         |
         v
+------------------+
| INTERLIS Output  |
| Gebaeude         |
+--------+---------+
         |
         v
      result.xtf
```

Assertions:

- XTF ist parsebar.
- XTF validiert.
- erwartete Klasse vorhanden.
- OIDs korrekt.
- alle Geometrieattribute korrekt.
- Modellkonformität.

---

# 25. End-to-End-Szenario: Structure Explode / Collect

```text
INPUT XTF
   |
   v
INTERLIS Input: Person
   |
   +------------------------------+
   |                              |
   v                              v
parents                     Structure Explode
                                  |
                                  v
                             transform child rows
                                  |
                                  v
                             Structure Collect
                                  ^
                                  |
parents --------------------------+
                                  |
                                  v
                           INTERLIS Output
                                  |
                                  v
                              output.xtf
```

Assertion:

- Anzahl LIST-Elemente identisch.
- Reihenfolge identisch.
- Werte identisch.
- Parent-Zuordnung identisch.

---

# 26. End-to-End-Szenario: Assoziationen

```text
Gebaeude -------------------+
                            |
                            v
                     INTERLIS Role Join
                            ^
                            |
Gemeinde -------------------+
                            |
                            v
                     CSV / GeoPackage
```

Assertions:

- alle Referenzen korrekt aufgelöst,
- nullbare Rollen bleiben möglich,
- Prefix-Felder stimmen,
- keine Duplikation bei eindeutigen Targets.

---

# 27. Negative E2E-Tests

Mindestens folgende reale Fehlerfälle müssen automatisiert laufen:

### Modell nicht gefunden

Erwartung:

- Pipeline fehlschlägt.
- Fehler nennt Modellname und Model Repository / Directory-Kontext.

### Klasse nicht im Modell

- Design-Time `check()` liefert Fehler.
- Runtime bricht kontrolliert ab.

### Klasse stimmt nicht mit Objekt-Tag überein

- je nach Modus warnen/überspringen/fehlschlagen.
- niemals still falsch mappen.

### Pflichtfeld fehlt

- strict write: Fehler.
- optionaler permissive Modus nur falls explizit konfiguriert.

### Ungültige Geometriecodierung

- Fehler enthält Attributname und TID.

### Unaufgelöste Pflichtreferenz

- Validator/Role Join liefert diagnostizierbaren Fehler.

---

# 28. GUI-Tests

SWT-GUI-Automation ist vergleichsweise fragil. Deshalb wird GUI-Logik so weit wie möglich aus SWT-Klassen herausgezogen.

Beispiel:

```text
InterlisInputDialog
        |
        v
InterlisInputDialogModel
        |
        +--> ModelBrowserService
        +--> SchemaPreviewService
        +--> InputMappingController
```

Dann können folgende Dinge ohne SWT automatisiert getestet werden:

```java
class InputMappingControllerTest {
  @Test void selecting_class_updates_preview();
  @Test void toggling_structure_flatten_updates_fields();
  @Test void unselecting_attribute_removes_output_field();
}
```

### 28.1 SWT-Smoke-Tests

Optional bzw. in eigener CI-Stage:

- Dialog lässt sich erzeugen.
- `getData()` lädt gespeicherte Metadaten.
- `ok()` persistiert erwartete Metadaten.
- Dialog mit ungültigem Model-Repository stürzt nicht ab.

### 28.2 Kein Pixel-perfect GUI-Test als Kernanforderung

Screenshots können als Dokumentationsregression sinnvoll sein, sind aber keine Release-Blocker.

---

# 29. Serialization-/Metadata-Tests

Hop speichert Transform-Metadaten in Pipeline-Dateien.

Darum muss für jeden Transform geprüft werden:

```text
Meta object
   |
   v
Hop pipeline serialization
   |
   v
read pipeline again
   |
   v
Meta object'
```

Beispiel:

```java
input_meta_roundtrips_all_properties()
output_meta_roundtrips_mapping_configuration()
structure_explode_meta_roundtrips()
role_join_meta_roundtrips()
```

Besonders wichtig bei verschachtelten Konfigurationen wie:

- ausgewählte Felder,
- Flatten-Optionen,
- Role Mapping,
- Advanced Options.

---

# 30. Classloader-Tests

Der gemeinsame Geometry-Classloader ist sicherheitskritisch für das Plugin-Ökosystem.

Ein Integrationstest muss prüfen:

```java
assertThat(ValueMetaGeometry.class.getClassLoader())...;
assertThat(Geometry.class.getClassLoader())...;
```

Praktisch wichtiger ist aber der End-to-End-Nachweis:

```text
INTERLIS Input
   -> Geometry
   -> GeoTools Vector Writer
```

Wenn zwei inkompatible JTS-Klassen geladen wären, würde genau dieser Pfad typischerweise scheitern.

Zusätzlich prüft `scripts/check-distribution.py`, dass die INTERLIS-ZIP **kein zweites `jts-core` und kein `hop-geometry-type`** mitliefert.

---

# 31. Distributionstests

## 31.1 `check-distribution.py`

Das Script muss unter anderem prüfen:

```text
ZIP
└── plugins/transforms/interlis/
    ├── hop-interlis-core-*.jar
    ├── hop-interlis-transforms-*.jar
    ├── hop-interlis-ui-*.jar   # falls separat
    ├── iox-ili-*.jar
    ├── ili2c-*.jar
    └── ... benötigte Runtime-Abhängigkeiten
```

Verboten:

```text
jts-core-*.jar                   # kommt vom Geometry Type Plugin
hop-geometry-type-*.jar          # kommt separat
hop-core-*.jar                   # kommt von Hop
hop-engine-*.jar                 # kommt von Hop
hop-ui-*.jar                     # kommt von Hop
```

Das Script prüft zusätzlich:

- keine `.class`-Dateien lose ausserhalb der JARs,
- keine Test-JARs,
- keine Sources-JARs,
- keine doppelten Runtime-JARs,
- erwartete Plugin-Icons vorhanden,
- erwartete Transform-Klassen im Artifact enthalten.

---

# 32. Release-Smoke-Test gegen frische Hop-Distribution

CI sollte nicht nur einen lokal vorbereiteten `$HOP_HOME` verwenden.

Release-Pipeline:

```text
Download Apache Hop distribution
            |
            v
unpack into temp directory
            |
            v
install geometry plugin release/build
            |
            v
install hop-interlis-plugin ZIP
            |
            v
run hop-run E2E suite
            |
            v
release asset
```

Damit werden Fehler erkannt wie:

- vergessene Runtime-JAR,
- falsche ZIP-Pfade,
- Plugin wird nicht entdeckt,
- falscher Classloader Group Name,
- implizite Abhängigkeit auf lokale Maven-Artefakte.

---

# 33. CI-Matrix

Mindestens:

```text
OS:
  - Ubuntu
  - macOS
  - Windows

Java:
  - 21
```

Hop-Baseline:

```text
2.18.1   (zentral im Parent-POM gepinnt, Property hop.version)
```

Später kann zusätzlich eine Compatibility-Matrix eingeführt werden:

```text
- minimal unterstützte Hop-Version
- aktuelle unterstützte Hop-Version
```

Nicht jede Kombination muss jeden SWT-GUI-Test ausführen; Core und E2E sollen jedoch plattformübergreifend laufen, soweit Hop selbst auf der Plattform unterstützt wird.

---

# 34. GitHub Actions Jobs

Empfohlene Jobs:

```text
build-core
   |
   +--> unit-tests
   +--> mapper-tests
   +--> geometry-tests
   +--> model-tests

integration-tests
   |
   +--> iox/ili2c
   +--> Hop runtime

package
   |
   +--> create ZIP
   +--> check-distribution.py

hop-e2e
   |
   +--> fresh Hop
   +--> install plugins
   +--> hop-run pipelines

windows-e2e
macos-e2e
```

Ein Release wird erst erstellt, wenn alle verpflichtenden Jobs erfolgreich sind.

---

# 35. Testabdeckung

Code Coverage ist Hilfsmittel, kein Ziel an sich.

Empfehlung:

### Core / Mapper / Model Layer

Hohe Abdeckung anstreben, insbesondere Branch Coverage.

Besonders kritisch:

```text
InterlisPrimitiveCodec
IomToRowMapper
RowToIomMapper
HopRowSchemaFactory
InterlisGeometryMapper
StructureExploder
StructureCollector
AssociationMapper
```

Als praktische Untergrenze kann für diese Pakete später z.B. 85 % Line Coverage als CI-Regel aktiviert werden, **erst nachdem die Tests semantisch sinnvoll sind**.

Für SWT-Dialogcode ist eine niedrigere Abdeckung akzeptabel, wenn Controller/Services gut getestet sind.

---

# 36. Property-based Tests

Für einige Mapper eignen sich generative Tests.

Beispiele:

```text
BigDecimal lexical value
  -> Hop
  -> INTERLIS lexical value
```

für viele zufällige Werte.

Oder:

```text
LIST OF n elements
  -> explode
  -> collect
```

für zufällige Listenlängen und Reihenfolgen.

Mögliche Bibliothek:

- jqwik

Dies ist kein MVP-Blocker, aber sinnvoll ab der Hardening-Phase.

---

# 37. Mutation Testing

Für besonders kritische Mapper kann später PIT eingesetzt werden.

Ziel:

Tests sollen erkennen, wenn z.B. versehentlich:

```java
index++
```

entfernt oder eine Nullprüfung invertiert wird.

Mutation Testing wird nicht auf den kompletten SWT-Code angewendet.

---

# 38. Performance-Tests

Nicht als normale Unit-Tests mit engen Zeitgrenzen implementieren.

Stattdessen eigene Benchmark-/Performance-Suite.

Testfälle:

```text
10'000 Objekte
100'000 Objekte
1'000'000 einfache Objekte, falls Fixture praktikabel
viele kleine Baskets
wenige grosse Baskets
Geometrien mit vielen Punkten
viele Strukturelemente
```

Messgrössen:

- Objekte/s,
- Peak Heap,
- GC-Verhalten,
- Startzeit Modellkompilierung,
- Effekt Model Cache,
- Writer Buffering.

Wichtigster funktionaler Performance-Grundsatz:

> Der Standard-Reader darf keine komplette XTF-Datei in den Speicher laden.

Streaming muss durch Tests bzw. Profiler-Szenarien belegbar sein.

---

# 39. Determinismus-Tests

Ein mehrfacher Lauf derselben Pipeline muss dasselbe logische Ergebnis erzeugen.

Zu testen:

- identische Feldreihenfolge,
- identische LIST-Reihenfolge,
- stabile Enumerationsausgabe,
- stabile automatisch erzeugte Feldnamen,
- stabile Association-Stream-Schemas.

Bei automatisch erzeugten OIDs ist eine explizite Strategie erforderlich; wenn Zufalls-OIDs erlaubt werden, dürfen entsprechende Tests keine Byte-Gleichheit erwarten.

---

# 40. Threading-Tests

Apache Hop kann Transform Copies parallel ausführen.

Für Transforms, bei denen Parallelität gefährlich wäre, muss dies dokumentiert und technisch verhindert oder sicher gemacht werden.

Zu testen:

- Reader-Ressourcen werden nicht zwischen Copies geteilt.
- Model Cache ist threadsicher.
- Geometry Mapper besitzt keinen veränderlichen globalen Zustand.
- Writer verhindert unkontrolliertes paralleles Schreiben in dieselbe XTF-Datei.

Für `INTERLIS Output` ist wahrscheinlich eine einzelne Writer-Instanz pro Zieldatei die sichere Default-Strategie.

---

# 41. Resource-Leak-Tests

Besonders wichtig bei wiederholten Pipeline-Läufen.

Testmuster:

```java
for (int i = 0; i < 100; i++) {
  runPipeline();
}
```

Danach sicherstellen:

- Dateien können gelöscht/überschrieben werden,
- keine offenen Streams,
- Reader/Writer geschlossen,
- temporäre Dateien freigegeben.

Windows ist für solche Tests besonders wertvoll, weil offene File Handles dort schnell sichtbar werden.

---

# 42. Testfälle für Variablen

Da Hop-Konfigurationen Variablen wie `${INPUT_FILE}` enthalten können:

```java
resolved_input_file_is_used_at_runtime()
unresolved_variable_does_not_crash_design_time_preview()
resolved_model_directory_is_used()
resolved_output_file_is_used()
```

Der Dialog darf bei nicht aufgelösten Variablen keine Model-/Schema-Probe erzwingen.

---

# 43. Kompatibilitätstests mit realen Modellen

Neben synthetischen Modellen sollen reale Modelle verwendet werden.

Mindestens ein moderat komplexes INTERLIS-2-Modell mit:

- Geometrien,
- Strukturen,
- Assoziationen,
- Vererbung.

Später zusätzlich:

- DMAV-Modelle,
- weitere verbreitete Schweizer Geodatenmodelle.

Diese Tests prüfen primär:

```text
compile model
inspect all transferable classes
build all row schemas
read representative XTF
optional roundtrip
```

Sie sollen nicht für jeden einzelnen Unit-Test verwendet werden.

---

# 44. Externe Modellrepositories in Tests

Normale CI-Tests dürfen nicht von der Verfügbarkeit externer Modellrepositories abhängen.

Daher:

- benötigte kleine Testmodelle lokal einchecken,
- externe Repository-Tests separat markieren,
- Netzwerk-Kompatibilitätstests nicht als einzige Absicherung verwenden.

Optionale Nightly-/Compatibility-Tests dürfen aktuelle Modellrepositories verwenden.

---

# 45. Test Tags

JUnit Tags:

```java
@Tag("unit")
@Tag("integration")
@Tag("hop")
@Tag("e2e")
@Tag("slow")
@Tag("network")
@Tag("itf")
```

Damit sind lokale Iterationen schnell:

```bash
mvn test
```

und vollständige Builds umfassend:

```bash
mvn verify
```

---

# 46. Definition of Done für einen Transform

Ein Transform gilt erst als fertig, wenn:

- [ ] Meta-Klasse getestet ist.
- [ ] `getFields()` getestet ist.
- [ ] `check()` positive und negative Fälle hat.
- [ ] Runtime-Klasse mit echten Rows getestet ist.
- [ ] Konfiguration serialisiert/deserialisiert wird.
- [ ] Dialoglogik ausserhalb von SWT getestet ist, soweit sinnvoll.
- [ ] mindestens ein echter Pipeline-Integrationstest existiert.
- [ ] bei zentralen Transforms mindestens ein `hop-run`-E2E-Test existiert.
- [ ] Fehlerfall getestet ist.
- [ ] Ressourcenschliessung getestet ist.
- [ ] Dokumentationsbeispiel existiert.

---

# 47. Release Gate

Vor einem Release müssen mindestens erfolgreich sein:

```text
mvn clean verify
scripts/check-distribution.py
scripts/run-e2e.sh <fresh-hop-home>
```

Zusätzlich:

- Linux CI grün,
- macOS CI grün,
- Windows CI grün,
- XTF Roundtrip grün,
- Curve Roundtrip grün,
- Structure LIST Roundtrip grün,
- Association Roundtrip grün,
- installierte Plugin-ZIP erfolgreich erkannt.

---

# 48. Besonders kritische Regressionstests

Diese Tests sollten als eigene, klar benannte Tests dauerhaft sichtbar bleiben:

```text
CurveIsNotLinearizedIT
MultipleGeometryAttributesIT
ListOrderIsPreservedIT
AssociationReferenceRoundtripIT
BasketIdRoundtripIT
DecimalPrecisionRoundtripIT
PluginZipLoadsInFreshHopIT
InterlisToGeoPackageGeometryClassloaderIT
```

Sie schützen genau jene Punkte, bei denen die Architektur bewusst von einem simplen „XML-Reader“ abweicht.

---

# 49. Empfohlene erste E2E-Suite für den früh vorzeigbaren Stand

Bereits der erste vorzeigbare `INTERLIS Input` sollte automatisiert folgende Pipeline bestehen:

```text
HopIli_Geometry_V1_valid.xtf
             |
             v
     +----------------+
     | INTERLIS Input |
     | TestObject     |
     +-------+--------+
             |
             v
     +----------------+
     | CSV / test sink|
     +----------------+
```

Assertions:

- Klasse wird im GUI-/Model-Service gefunden.
- Row-Schema stimmt.
- Text, Zahl, Enum stimmen.
- zwei Geometrieattribute sind gleichzeitig vorhanden.
- Bogen ist noch ein Bogen.
- `_ili_tid` und `_ili_bid` stimmen.

Das ist klein genug für einen frühen Stand, zeigt aber bereits die eigentliche Stärke der Architektur.

---

# 50. Quellen / technische Referenzen

- Apache Hop Pipeline Unit Testing:
  https://hop.apache.org/manual/latest/pipeline/pipeline-unit-testing.html
- Apache Hop Developer Guide:
  https://hop.apache.org/dev-manual/latest/
- Apache Hop `hop-run`:
  https://hop.apache.org/manual/latest/hop-run/hop-run.html
- iox-ili:
  https://github.com/claeis/iox-ili
- bestehendes Geometry Type Plugin:
  https://github.com/edigonzales/hop-geometry-type-plugin
- bestehendes GeoTools Plugin:
  https://github.com/edigonzales/hop-geotools-plugin


## P1-Regressionen und Paketabnahme (2026-09-09)

Deterministische Regressionen prüfen Overlay/Null-Löschung, Pflichtfelder nach
vollständigem Aufbau, Referenzbestandteile, DELETE, XYZ-Roundtrips, konfigurierte
Identitäten, Append-Metadaten/-Werte und sichere bzw. abgelehnte Parallelität.
Validierungstests decken fehlende Ziele, gültige Vorwärtsreferenzen, UNIQUE,
Zweitdurchlauf genau einmal, explizite Zielprüfungs-Konfiguration, Fehlerlimits,
negative Limits und einen langsamen Verbraucher mit Queuegrösse 2 ab.

`HopIli_Associations_V1_valid.xtf` enthält vollständig gültige eingebettete Links
und alle Pflichtverknüpfungen. Die bisherige unvollständige Variante mit
absichtlich eigenständigen Linkobjekten bleibt als `HopIli_Associations_V1_mapping.xtf`
für Mapping-Regressionsfälle erhalten; sie ist kein Vollvalidierungsnachweis.

Paketpipelines 21–24 prüfen geändertes Quellobjekt, konfigurierten TID, konstanten
Basket, alle XYZ-Koordinaten, Append und vollständige Diagnoseausgabe bei erwartetem
Exit-Code 1 (unbegrenzt sowie Limit 2). Der E2E-Runner erzeugt isolierte Hop-Metadaten,
prüft jeden Exit-Code explizit und führt auch die bisherigen Pipelines aus.
`HOP_GEOTOOLS_ZIP` kann ein kompatibles vorgebautes GeoTools-Paket für den optionalen
GeoPackage-Test liefern. Ohne diese Option erfolgt der Build mit Tests.
