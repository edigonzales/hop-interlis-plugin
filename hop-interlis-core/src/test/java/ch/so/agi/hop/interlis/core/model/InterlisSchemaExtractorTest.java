package ch.so.agi.hop.interlis.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.TestResources;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisSchemaExtractorTest {

  private static InterlisSchemaDescriptor primitives;
  private static InterlisSchemaDescriptor geometry;
  private static InterlisSchemaDescriptor spike;

  @BeforeAll
  static void extract() throws Exception {
    InterlisModelService service = new InterlisModelServiceImpl();
    InterlisSchemaExtractor extractor = new InterlisSchemaExtractor();
    primitives =
        extractor.extract(
            service
                .compile(
                    new ModelSource(
                        List.of(model("HopIli_Primitives_V1.ili")), List.of(), List.of()),
                    ModelCompileOptions.defaults())
                .transferDescription());
    geometry =
        extractor.extract(
            service
                .compile(
                    new ModelSource(
                        List.of(model("HopIli_Geometry_V1.ili")), List.of(), List.of()),
                    ModelCompileOptions.defaults())
                .transferDescription());
    spike =
        extractor.extract(
            service
                .compile(
                    new ModelSource(
                        List.of(model("HopIli_Spike_V1.ili")), List.of(), List.of()),
                    ModelCompileOptions.defaults())
                .transferDescription());
  }

  private static Path model(String name) {
    return TestResources.path("/models/" + name);
  }

  @Test
  void exposes_qualified_class_names() {
    assertThat(primitives.findClass("HopIli_Primitives_V1.Data.Primitive")).isPresent();
    assertThat(geometry.findClass("HopIli_Geometry_V1.Data.TestObject")).isPresent();
    assertThat(spike.findClass("HopIli_Spike_V1.Data.Building")).isPresent();
  }

  @Test
  void extracts_attributes_with_types_and_cardinality() {
    InterlisClassDescriptor primitive = classOf(primitives, "Primitive");

    assertThat(primitive.attributes())
        .extracting(InterlisAttributeDescriptor::name)
        .containsExactly("Text", "Mtext", "Label", "Link", "Flag", "Count", "Value", "At", "Ts",
            "Kind", "Opt");

    // INTERLIS 2 attributes are optional unless declared MANDATORY.
    InterlisAttributeDescriptor text = attributeOf(primitive, "Text");
    assertThat(text.kind()).isEqualTo(InterlisValueKind.TEXT);
    assertThat(text.typeName()).isEqualTo("TEXT*80");
    assertThat(text.textMaxLength()).isEqualTo(80);
    assertThat(text.cardinality()).isEqualTo(new InterlisCardinality(1, 1));
    assertThat(text.mandatory()).isTrue();

    InterlisAttributeDescriptor count = attributeOf(primitive, "Count");
    assertThat(count.kind()).isEqualTo(InterlisValueKind.INTEGER);
    assertThat(count.typeName()).contains("0", "9999999999");
    assertThat(count.cardinality()).isEqualTo(new InterlisCardinality(0, 1));
    assertThat(count.mandatory()).isFalse();

    InterlisAttributeDescriptor value = attributeOf(primitive, "Value");
    assertThat(value.kind()).isEqualTo(InterlisValueKind.DECIMAL);
    assertThat(value.decimalPlaces()).isEqualTo(3);

    InterlisAttributeDescriptor flag = attributeOf(primitive, "Flag");
    assertThat(flag.kind()).isEqualTo(InterlisValueKind.BOOLEAN);
    assertThat(flag.typeName()).isEqualTo("BOOLEAN");

    assertThat(attributeOf(primitive, "Label").kind()).isEqualTo(InterlisValueKind.NAME);
    assertThat(attributeOf(primitive, "Link").kind()).isEqualTo(InterlisValueKind.URI);
    assertThat(attributeOf(primitive, "Mtext").kind()).isEqualTo(InterlisValueKind.TEXT);
    assertThat(attributeOf(primitive, "At").kind()).isEqualTo(InterlisValueKind.DATE);
    assertThat(attributeOf(primitive, "Ts").kind()).isEqualTo(InterlisValueKind.TIME);
  }

  @Test
  void marks_enumeration_attributes() {
    InterlisClassDescriptor primitive = classOf(primitives, "Primitive");
    InterlisAttributeDescriptor kind = attributeOf(primitive, "Kind");

    assertThat(kind.kind()).isEqualTo(InterlisValueKind.ENUM);
    assertThat(kind.cardinality()).isEqualTo(new InterlisCardinality(1, 1));
    assertThat(kind.typeName()).isNotBlank();
  }

  @Test
  void marks_geometry_attributes_with_kind_and_dimension() {
    InterlisClassDescriptor testObject = classOf(geometry, "TestObject");

    assertThat(testObject.geometryAttributes())
        .extracting(InterlisAttributeDescriptor::name)
        .containsExactly("Center", "Points", "Axis", "Axes", "Boundary", "Area", "Surfaces");

    assertThat(attributeOf(testObject, "Center").geometryKind())
        .isEqualTo(InterlisGeometryKind.COORD);
    assertThat(attributeOf(testObject, "Center").coordDimension()).isEqualTo(2);
    assertThat(attributeOf(testObject, "Axis").geometryKind())
        .isEqualTo(InterlisGeometryKind.POLYLINE);
    assertThat(attributeOf(testObject, "Axis").allowsArcs()).isTrue();
    assertThat(attributeOf(testObject, "Boundary").geometryKind())
        .isEqualTo(InterlisGeometryKind.SURFACE);
    assertThat(attributeOf(testObject, "Area").geometryKind())
        .isEqualTo(InterlisGeometryKind.AREA);
  }

  @Test
  void includes_inherited_attributes_in_stable_order() {
    InterlisClassDescriptor building = classOf(spike, "Building");

    assertThat(building.effectiveProperties())
        .extracting(InterlisPropertyDescriptor::name)
        .containsExactly("Code", "Location", "Address", "Municipality", "Note");

    assertThat(building.attributes())
        .extracting(InterlisAttributeDescriptor::name)
        .containsExactly("Code", "Location", "Address", "Note");

    InterlisAttributeDescriptor note = attributeOf(building, "Note");
    assertThat(note.inherited()).isTrue();
    assertThat(attributeOf(building, "Code").inherited()).isFalse();
  }

  @Test
  void marks_structure_attributes() {
    InterlisClassDescriptor building = classOf(spike, "Building");
    InterlisAttributeDescriptor address = attributeOf(building, "Address");

    assertThat(address.kind()).isEqualTo(InterlisValueKind.STRUCTURE);
    assertThat(address.structureScopedName()).isEqualTo("HopIli_Spike_V1.Data.Address");

    Optional<InterlisStructureDescriptor> structure =
        spike.findStructure("HopIli_Spike_V1.Data.Address");
    assertThat(structure).isPresent();
    assertThat(structure.get().attributes())
        .extracting(InterlisAttributeDescriptor::name)
        .containsExactly("Street", "Number");
  }

  @Test
  void exposes_roles_with_target_and_cardinality() {
    InterlisClassDescriptor building = classOf(spike, "Building");

    assertThat(building.roles()).hasSize(1);
    InterlisRoleDescriptor role = building.roles().get(0);
    assertThat(role.name()).isEqualTo("Municipality");
    assertThat(role.targetClassScopedName()).isEqualTo("HopIli_Spike_V1.Data.Municipality");
    assertThat(role.cardinality()).isEqualTo(new InterlisCardinality(1, 1));
    assertThat(role.ordered()).isFalse();
    assertThat(role.associationScopedName())
        .isEqualTo("HopIli_Spike_V1.Data.BuildingMunicipality");
  }

  @Test
  void extracts_associations_with_roles_and_attributes() throws Exception {
    InterlisSchemaDescriptor associations =
        new InterlisSchemaExtractor()
            .extract(
                new InterlisModelServiceImpl()
                    .compile(
                        new ModelSource(
                            List.of(model("HopIli_Associations_V1.ili")), List.of(), List.of()),
                        ModelCompileOptions.defaults())
                    .transferDescription());

    assertThat(associations.associations())
        .extracting(InterlisAssociationDescriptor::name)
        .containsExactly("AddressOwnership", "Membership", "PersonProject", "PersonTask");
    assertThat(associations.findClass("HopIli_Associations_V1.Data.Membership")).isEmpty();

    InterlisAssociationDescriptor membership =
        associations.findAssociation("HopIli_Associations_V1.Data.Membership").orElseThrow();
    assertThat(membership.identifiable()).isFalse();
    assertThat(membership.roles())
        .extracting(InterlisRoleDescriptor::name)
        .containsExactly("Person", "Organisation");
    assertThat(membership.attributes())
        .extracting(InterlisAttributeDescriptor::name)
        .containsExactly("Function", "Entry");

    InterlisAssociationDescriptor personTask =
        associations.findAssociation("HopIli_Associations_V1.Data.PersonTask").orElseThrow();
    assertThat(personTask.role("Task").ordered()).isTrue();
    assertThat(personTask.role("Person").ordered()).isFalse();

    // Roles of classes point back to their association.
    InterlisClassDescriptor person =
        associations.findClass("HopIli_Associations_V1.Data.Person").orElseThrow();
    assertThat(person.roles())
        .extracting(InterlisRoleDescriptor::name)
        .contains("Address", "Organisation", "Task");
    InterlisRoleDescriptor address = person.role("Address");
    assertThat(address.associationScopedName())
        .isEqualTo("HopIli_Associations_V1.Data.AddressOwnership");
  }

  @Test
  void structures_are_not_listed_as_transferable_classes() {
    InterlisModelService service = new InterlisModelServiceImpl();
    CompiledInterlisModel model;
    try {
      model =
          service.compile(
              new ModelSource(List.of(model("HopIli_Spike_V1.ili")), List.of(), List.of()),
              ModelCompileOptions.defaults());
    } catch (InterlisModelException e) {
      throw new AssertionError(e);
    }

    assertThat(service.listTransferableClasses(model))
        .extracting(InterlisClassDescriptor::name)
        .containsExactly("Base", "Building", "Municipality");
  }

  private static InterlisClassDescriptor classOf(InterlisSchemaDescriptor schema, String name) {
    return schema.classes().stream()
        .filter(c -> c.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Class not found: " + name));
  }

  private static InterlisAttributeDescriptor attributeOf(
      InterlisClassDescriptor descriptor, String name) {
    return descriptor.attributes().stream()
        .filter(a -> a.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Attribute not found: " + name));
  }
}
