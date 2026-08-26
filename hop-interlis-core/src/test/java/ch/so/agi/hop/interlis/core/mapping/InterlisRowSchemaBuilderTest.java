package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.interlis.core.TestResources;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisRowSchemaBuilderTest {

  private static InterlisSchemaDescriptor geometry;
  private static InterlisSchemaDescriptor spike;
  private static InterlisSchemaDescriptor primitives;

  private final InterlisRowSchemaBuilder builder = new InterlisRowSchemaBuilder();

  @BeforeAll
  static void compile() throws InterlisModelException {
    InterlisModelService service = new InterlisModelServiceImpl();
    InterlisSchemaExtractor extractor = new InterlisSchemaExtractor();
    geometry = extractor.extract(compile(service, "HopIli_Geometry_V1.ili", "2.4").transferDescription());
    spike = extractor.extract(compile(service, "HopIli_Spike_V1.ili", "2.3").transferDescription());
    primitives = extractor.extract(compile(service, "HopIli_Primitives_V1.ili", "2.4").transferDescription());
  }

  private static CompiledInterlisModel compile(
      InterlisModelService service, String modelFile, String iliVersion)
      throws InterlisModelException {
    return service.compile(
        new ModelSource(List.of(TestResources.path("/models/" + modelFile)), List.of(), List.of()),
        new ModelCompileOptions(iliVersion));
  }

  private InterlisClassDescriptor classOf(InterlisSchemaDescriptor schema, String name) {
    return schema.findClass(name).orElseThrow();
  }

  @Test
  void creates_stable_schema_with_reserved_fields_and_model_order() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(geometry, classOf(geometry, "HopIli_Geometry_V1.Data.TestObject"),
            ProjectionOptions.defaults());

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly(
            "_ili_tid", "_ili_bid", "Name", "Center", "Points", "Axis", "Axes", "Boundary",
            "Area", "Surfaces");
    assertThat(plan.fields().get(0).source()).isEqualTo(InterlisFieldSource.OBJECT_ID);
    assertThat(plan.fields().get(1).source()).isEqualTo(InterlisFieldSource.BASKET_ID);
    assertThat(plan.warnings()).isEmpty();
  }

  @Test
  void marks_geometry_fields() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(geometry, classOf(geometry, "HopIli_Geometry_V1.Data.TestObject"),
            ProjectionOptions.defaults());

    assertThat(plan.fields()).filteredOn(InterlisFieldPlan::isGeometry)
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("Center", "Points", "Axis", "Axes", "Boundary", "Area", "Surfaces");
  }

  @Test
  void reserved_fields_are_configurable() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            geometry,
            classOf(geometry, "HopIli_Geometry_V1.Data.TestObject"),
            new ProjectionOptions(true, false, true, true, true, true, "_", null, java.util.Set.of("Name")));

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_class", "_ili_topic", "_ili_operation", "Name");
  }

  @Test
  void flattens_single_structure_with_prefix_at_property_position() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(spike, classOf(spike, "HopIli_Spike_V1.Data.Building"),
            ProjectionOptions.defaults());

    // Structure fields appear at the position of the structure attribute; inherited
    // properties and the embedded role follow in model order.
    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly(
            "_ili_tid", "_ili_bid", "Code", "Location", "Address_Street", "Address_Number",
            "Municipality_ref", "Note");

    InterlisFieldPlan street = plan.fields().stream()
        .filter(f -> f.hopFieldName().equals("Address_Street")).findFirst().orElseThrow();
    assertThat(street.source()).isEqualTo(InterlisFieldSource.FLATTENED_STRUCTURE_ATTRIBUTE);
    assertThat(street.propertyPath().dotted()).isEqualTo("Address.Street");
    assertThat(plan.fields().stream()
        .filter(f -> f.hopFieldName().equals("Address_Street")).findFirst().orElseThrow()
        .propertyPath().segments()).containsExactly("Address");
  }

  @Test
  void projects_simple_role_as_reference_field() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(spike, classOf(spike, "HopIli_Spike_V1.Data.Building"),
            ProjectionOptions.defaults());

    InterlisFieldPlan role = plan.fields().stream()
        .filter(f -> f.source() == InterlisFieldSource.ROLE_REFERENCE).findFirst().orElseThrow();
    assertThat(role.hopFieldName()).isEqualTo("Municipality_ref");
    assertThat(role.roleDescriptor().name()).isEqualTo("Municipality");
  }

  @Test
  void selected_property_paths_restrict_the_projection() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            geometry,
            classOf(geometry, "HopIli_Geometry_V1.Data.TestObject"),
            new ProjectionOptions(true, true, false, false, false, true, "_", null,
                java.util.Set.of("Name", "Boundary")));

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Boundary");
  }

  @Test
  void duplicate_output_field_names_are_rejected() throws Exception {
    InterlisSchemaDescriptor mapping =
        new InterlisSchemaExtractor()
            .extract(
                new InterlisModelServiceImpl()
                    .compile(
                        new ModelSource(
                            List.of(TestResources.path("/models/HopIli_Mapping_V1.ili")),
                            List.of(),
                            List.of()),
                        ModelCompileOptions.defaults())
                    .transferDescription());

    assertThatThrownBy(
            () ->
                builder.build(
                    mapping,
                    classOf(mapping, "HopIli_Mapping_V1.Data.Collision"),
                    ProjectionOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Address_Street");
  }

  @Test
  void multi_valued_structure_produces_warning_and_is_skipped() throws Exception {
    InterlisSchemaDescriptor mapping =
        new InterlisSchemaExtractor()
            .extract(
                new InterlisModelServiceImpl()
                    .compile(
                        new ModelSource(
                            List.of(TestResources.path("/models/HopIli_Mapping_V1.ili")),
                            List.of(),
                            List.of()),
                        ModelCompileOptions.defaults())
                    .transferDescription());

    InterlisRowMappingPlan plan =
        builder.build(
            mapping,
            classOf(mapping, "HopIli_Mapping_V1.Data.MultiStruct"),
            ProjectionOptions.defaults());

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_bid", "Name");
    assertThat(plan.warnings())
        .anySatisfy(w -> {
          assertThat(w).contains("Qualities").contains("Structure Explode");
        });
  }

  @Test
  void multi_valued_role_is_not_exposed_as_class_role() throws Exception {
    // A {0..*} role is transferred as a separate association object, not as an embedded role
    // of the class; ili2c therefore does not expose it in the class properties at all.
    InterlisSchemaDescriptor mapping =
        new InterlisSchemaExtractor()
            .extract(
                new InterlisModelServiceImpl()
                    .compile(
                        new ModelSource(
                            List.of(TestResources.path("/models/HopIli_Mapping_V1.ili")),
                            List.of(),
                            List.of()),
                        ModelCompileOptions.defaults())
                    .transferDescription());

    InterlisRowMappingPlan plan =
        builder.build(
            mapping,
            classOf(mapping, "HopIli_Mapping_V1.Data.Owner"),
            ProjectionOptions.defaults());

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_bid", "Name");
    assertThat(plan.fields())
        .noneMatch(f -> f.source() == InterlisFieldSource.ROLE_REFERENCE);
  }
}
