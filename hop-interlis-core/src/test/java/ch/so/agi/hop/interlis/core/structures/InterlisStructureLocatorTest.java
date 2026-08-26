package ch.so.agi.hop.interlis.core.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisStructureLocatorTest {

  private static InterlisSchemaDescriptor schema;
  private static InterlisClassDescriptor person;

  private final InterlisStructureLocator locator = new InterlisStructureLocator();

  @BeforeAll
  static void setUp() throws Exception {
    schema = StructuresTestSupport.schema();
    person = StructuresTestSupport.person(schema);
  }

  @Test
  void locates_class_level_list_with_child_fields() throws Exception {
    InterlisStructurePlan plan =
        locator.locate(schema, person, "Addresses", ProjectionOptions.defaults());

    assertThat(plan.structureAttribute().name()).isEqualTo("Addresses");
    assertThat(plan.structure().scopedName()).isEqualTo("HopIli_Structures_V1.Data.Address");
    assertThat(plan.ordered()).isTrue();
    assertThat(plan.mandatory()).isFalse();
    assertThat(plan.pathSegments()).isEmpty();
    assertThat(plan.childFields())
        .extracting(f -> f.hopFieldName())
        .containsExactly("Street", "Number", "Location", "PostCode_Code", "PostCode_Town");
    assertThat(plan.childFields().get(2).attributeDescriptor().kind())
        .isEqualTo(InterlisValueKind.GEOMETRY);
  }

  @Test
  void locates_bag_without_order_semantics() throws Exception {
    InterlisStructurePlan plan =
        locator.locate(schema, person, "Contacts", ProjectionOptions.defaults());

    assertThat(plan.structureAttribute().name()).isEqualTo("Contacts");
    assertThat(plan.ordered()).isFalse();
    assertThat(plan.childFields()).extracting(f -> f.hopFieldName())
        .containsExactly("Kind", "Value");
  }

  @Test
  void locates_multi_valued_structure_below_single_structures() throws Exception {
    InterlisStructurePlan plan =
        locator.locate(schema, person, "Home.Place.Phones", ProjectionOptions.defaults());

    assertThat(plan.structureAttribute().name()).isEqualTo("Phones");
    assertThat(plan.pathSegments()).containsExactly("Home", "Place");
    assertThat(plan.childFields()).extracting(f -> f.hopFieldName())
        .containsExactly("Kind", "Value");
  }

  @Test
  void rejects_single_valued_structure_as_explode_target() {
    assertThatThrownBy(
            () -> locator.locate(schema, person, "Home", ProjectionOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("not a multi-valued");
  }

  @Test
  void rejects_single_valued_leaf_at_end_of_path() {
    assertThatThrownBy(
            () -> locator.locate(schema, person, "Home.Place.Country", ProjectionOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("not a multi-valued");
  }

  @Test
  void rejects_unknown_attribute() {
    assertThatThrownBy(
            () -> locator.locate(schema, person, "NoSuchThing", ProjectionOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("NoSuchThing")
        .hasMessageContaining("not found");
  }

  @Test
  void rejects_non_structure_attribute() {
    assertThatThrownBy(
            () -> locator.locate(schema, person, "Name", ProjectionOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("not a structure");
  }

  @Test
  void rejects_blank_path() {
    assertThatThrownBy(
            () -> locator.locate(schema, person, " ", ProjectionOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("No structure attribute selected");
  }

  @Test
  void skips_unselected_child_fields() throws Exception {
    ProjectionOptions options =
        new ProjectionOptions(
            true, true, false, false, false, true, "_", null,
            java.util.Set.of("Street", "PostCode.Code"));
    InterlisStructurePlan plan = locator.locate(schema, person, "Addresses", options);

    assertThat(plan.childFields())
        .extracting(f -> f.hopFieldName())
        .containsExactly("Street", "PostCode_Code");
  }

  @Test
  void lists_multi_valued_structure_paths_in_model_order() throws Exception {
    assertThat(locator.multiValuedStructurePaths(schema, person))
        .containsExactly("Home.Place.Phones", "Addresses", "Contacts");
  }
}
