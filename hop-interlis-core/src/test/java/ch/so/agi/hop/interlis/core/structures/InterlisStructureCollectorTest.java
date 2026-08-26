package ch.so.agi.hop.interlis.core.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureCollector.StructureCollectOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class InterlisStructureCollectorTest {

  private static InterlisSchemaDescriptor schema;
  private static Map<String, IomObject> persons;

  private final InterlisStructureCollector collector = new InterlisStructureCollector();
  private final InterlisStructureLocator locator = new InterlisStructureLocator();

  @BeforeAll
  static void setUp() throws Exception {
    schema = StructuresTestSupport.schema();
    persons = StructuresTestSupport.personsByTid();
  }

  private InterlisStructurePlan plan(String path) throws Exception {
    return locator.locate(
        schema, StructuresTestSupport.person(schema), path, ProjectionOptions.defaults());
  }

  private static InterlisStructureCollector.StructureChild child(int index, Object... values) {
    return new InterlisStructureCollector.StructureChild(index, values);
  }

  @Test
  void collects_list_children_sorted_by_index_onto_fresh_carrier() throws Exception {
    IomObject carrier = persons.get("p3"); // no Addresses at all
    IomObject result =
        collector.collect(
            carrier,
            List.of(
                child(1, "Second", "2", null, null, null),
                child(0, "First", "1", null, null, null)),
            plan("Addresses"),
            StructureCollectOptions.defaults());

    assertThat(result.getattrvaluecount("Addresses")).isEqualTo(2);
    assertThat(result.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("First");
    assertThat(result.getattrobj("Addresses", 1).getattrvalue("Street")).isEqualTo("Second");
    // The carrier itself is not mutated.
    assertThat(carrier.getattrvaluecount("Addresses")).isZero();
  }

  @Test
  void replaces_existing_structure_content() throws Exception {
    IomObject carrier = persons.get("p1"); // 3 addresses
    IomObject result =
        collector.collect(
            carrier,
            List.of(child(0, "Only", "1", null, null, null)),
            plan("Addresses"),
            StructureCollectOptions.defaults());

    assertThat(result.getattrvaluecount("Addresses")).isEqualTo(1);
    assertThat(result.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("Only");
  }

  @Test
  void writes_nested_single_structure_and_geometry_children() throws Exception {
    IomObject carrier = persons.get("p3");
    GeometryFactory factory = new GeometryFactory();
    Object[] values = {
      "Main Street", "10", factory.createPoint(new Coordinate(2600000, 1200000)), "4600", "Olten"
    };
    IomObject result =
        collector.collect(carrier, List.of(child(0, values)), plan("Addresses"),
            StructureCollectOptions.defaults());

    IomObject address = result.getattrobj("Addresses", 0);
    assertThat(address.getobjecttag()).isEqualTo("HopIli_Structures_V1.Data.Address");
    assertThat(address.getattrvaluecount("Location")).isEqualTo(1);
    assertThat(address.getattrobj("Location", 0).getobjecttag()).isEqualTo("COORD");
    IomObject postCode = address.getattrobj("PostCode", 0);
    assertThat(postCode.getobjecttag()).isEqualTo("HopIli_Structures_V1.Data.PostCode");
    assertThat(postCode.getattrvalue("Code")).isEqualTo("4600");
    assertThat(postCode.getattrvalue("Town")).isEqualTo("Olten");
  }

  @Test
  void collects_bag_in_stream_order_ignoring_indexes() throws Exception {
    IomObject carrier = persons.get("p3");
    IomObject result =
        collector.collect(
            carrier,
            List.of(
                child(7, "phone", "+41 1"),
                child(0, "email", "a@b.ch"),
                child(7, "fax", "+41 2")),
            plan("Contacts"),
            StructureCollectOptions.defaults());

    assertThat(result.getattrvaluecount("Contacts")).isEqualTo(3);
    assertThat(result.getattrobj("Contacts", 0).getattrvalue("Kind")).isEqualTo("phone");
    assertThat(result.getattrobj("Contacts", 1).getattrvalue("Kind")).isEqualTo("email");
    assertThat(result.getattrobj("Contacts", 2).getattrvalue("Kind")).isEqualTo("fax");
  }

  @Test
  void rejects_duplicate_list_index() throws Exception {
    assertThatThrownBy(
            () ->
                collector.collect(
                    persons.get("p3"),
                    List.of(child(0, "A", "1", null, null, null),
                        child(0, "B", "2", null, null, null)),
                    plan("Addresses"),
                    StructureCollectOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Duplicate index 0");
  }

  @Test
  void rejects_missing_list_index_when_strict() throws Exception {
    StructureCollectOptions lenientOnDuplicates =
        new StructureCollectOptions(true, false, RowWriteOptions.defaults());
    assertThatThrownBy(
            () ->
                collector.collect(
                    persons.get("p3"),
                    List.of(child(1, "A", "1", null, null, null),
                        child(3, "B", "2", null, null, null)),
                    plan("Addresses"),
                    lenientOnDuplicates))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("missing index");
  }

  @Test
  void tolerates_gaps_when_not_strict() throws Exception {
    StructureCollectOptions lenient =
        new StructureCollectOptions(false, true, RowWriteOptions.defaults());
    IomObject result =
        collector.collect(
            persons.get("p3"),
            List.of(child(0, "A", "1", null, null, null),
                child(2, "B", "2", null, null, null)),
            plan("Addresses"),
            lenient);

    assertThat(result.getattrvaluecount("Addresses")).isEqualTo(2);
    assertThat(result.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("A");
    assertThat(result.getattrobj("Addresses", 1).getattrvalue("Street")).isEqualTo("B");
  }

  @Test
  void rejects_null_carrier() throws Exception {
    assertThatThrownBy(() -> collector.collect(null, List.of(), plan("Addresses"),
        StructureCollectOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("null");
  }

  @Test
  void rejects_null_mandatory_child_field_in_strict_mode() throws Exception {
    assertThatThrownBy(
            () ->
                collector.collect(
                    persons.get("p3"),
                    List.of(child(0, null, "1", null, null, null)),
                    plan("Addresses"),
                    StructureCollectOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Mandatory attribute Street");
  }

  @Test
  void collects_into_structure_below_single_structures() throws Exception {
    // p3 has no Home at all: the chain Home -> Place must be created on the carrier.
    IomObject carrier = persons.get("p3");
    IomObject result =
        collector.collect(
            carrier,
            List.of(child(0, "phone", "+41 79 1")),
            plan("Home.Place.Phones"),
            StructureCollectOptions.defaults());

    IomObject home = result.getattrobj("Home", 0);
    assertThat(home.getobjecttag()).isEqualTo("HopIli_Structures_V1.Data.Home");
    IomObject place = home.getattrobj("Place", 0);
    assertThat(place.getobjecttag()).isEqualTo("HopIli_Structures_V1.Data.Place");
    assertThat(place.getattrvaluecount("Phones")).isEqualTo(1);
    assertThat(place.getattrobj("Phones", 0).getattrvalue("Kind")).isEqualTo("phone");
  }
}
