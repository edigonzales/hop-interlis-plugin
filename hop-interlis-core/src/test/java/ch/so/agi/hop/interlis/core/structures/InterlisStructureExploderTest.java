package ch.so.agi.hop.interlis.core.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class InterlisStructureExploderTest {

  private static InterlisSchemaDescriptor schema;
  private static Map<String, IomObject> persons;

  private final InterlisStructureExploder exploder = new InterlisStructureExploder();
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

  @Test
  void explodes_list_with_order_and_values() throws Exception {
    List<InterlisStructureExploder.ExplodedChild> children =
        exploder.explode(persons.get("p1"), plan("Addresses"));

    assertThat(children).hasSize(3);
    assertThat(children).extracting(InterlisStructureExploder.ExplodedChild::index)
        .containsExactly(0, 1, 2);
    // Street, Number, Location, PostCode_Code, PostCode_Town
    assertThat(children.get(0).values()[0]).isEqualTo("Main Street");
    assertThat(children.get(0).values()[1]).isEqualTo("10");
    assertThat(children.get(0).values()[2]).isInstanceOf(Geometry.class);
    assertThat(children.get(0).values()[3]).isEqualTo("4600");
    assertThat(children.get(0).values()[4]).isEqualTo("Olten");
    assertThat(children.get(1).values()[0]).isEqualTo("Station Street");
    assertThat(children.get(2).values()[0]).isEqualTo("Village Road");
  }

  @Test
  void explodes_missing_nested_single_structure_inside_child_as_null() throws Exception {
    // The third address has no PostCode structure: its flattened fields must be null.
    List<InterlisStructureExploder.ExplodedChild> children =
        exploder.explode(persons.get("p1"), plan("Addresses"));

    assertThat(children.get(2).values()[3]).isNull();
    assertThat(children.get(2).values()[4]).isNull();
  }

  @Test
  void explodes_optional_missing_structure_to_no_children() throws Exception {
    // p3 has no Addresses at all.
    assertThat(exploder.explode(persons.get("p3"), plan("Addresses"))).isEmpty();
  }

  @Test
  void explodes_empty_list_to_no_children() throws Exception {
    // p2 has Addresses but no Contacts.
    assertThat(exploder.explode(persons.get("p2"), plan("Contacts"))).isEmpty();
  }

  @Test
  void explodes_bag_without_order_guarantees() throws Exception {
    List<InterlisStructureExploder.ExplodedChild> children =
        exploder.explode(persons.get("p1"), plan("Contacts"));

    assertThat(children).hasSize(2);
    assertThat(children.get(0).values()[0]).isEqualTo("phone");
    assertThat(children.get(1).values()[0]).isEqualTo("email");
  }

  @Test
  void explodes_structure_below_single_structures() throws Exception {
    List<InterlisStructureExploder.ExplodedChild> children =
        exploder.explode(persons.get("p1"), plan("Home.Place.Phones"));

    assertThat(children).hasSize(1);
    assertThat(children.get(0).index()).isZero();
    assertThat(children.get(0).values()[0]).isEqualTo("phone");
    assertThat(children.get(0).values()[1]).isEqualTo("+41 79 123 45 67");
  }

  @Test
  void missing_parent_structure_yields_no_children() throws Exception {
    // p2 has no Home at all: exploding Home.Place.Phones yields nothing.
    assertThat(exploder.explode(persons.get("p2"), plan("Home.Place.Phones"))).isEmpty();
  }

  @Test
  void rejects_null_source() throws Exception {
    assertThatThrownBy(() -> exploder.explode(null, plan("Addresses")))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("null");
  }
}
