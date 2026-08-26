package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisRowSchemaBuilderAssociationTest {

  private static InterlisSchemaDescriptor schema;

  private final InterlisRowSchemaBuilder builder = new InterlisRowSchemaBuilder();

  @BeforeAll
  static void setUp() throws Exception {
    schema = AssociationsTestSupport.schema();
  }

  @Test
  void projects_association_rows_with_role_refs_and_attributes() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema, AssociationsTestSupport.root(schema, AssociationsTestSupport.ASSOC_MEMBERSHIP),
            ProjectionOptions.defaults());

    // Non-identifiable association: no _ili_tid; roles + attributes in model order.
    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_bid", "Person_ref", "Organisation_ref", "Function", "Entry");
    assertThat(plan.fields().get(1).source()).isEqualTo(InterlisFieldSource.ROLE_REFERENCE);
    assertThat(plan.fields().get(1).propertyPath().leafName()).isEqualTo("Person");
    assertThat(plan.linkResolvedRoles()).isEmpty();
    assertThat(plan.hasLinkResolvedRoles()).isFalse();
  }

  @Test
  void projects_ordered_roles_with_order_pos() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema, AssociationsTestSupport.root(schema, AssociationsTestSupport.ASSOC_PERSON_TASK),
            ProjectionOptions.defaults());

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_bid", "Person_ref", "Task_ref", "Task_order_pos");
    assertThat(plan.fields().get(3).source()).isEqualTo(InterlisFieldSource.ROLE_ORDER_POS);
  }

  @Test
  void role_ref_bid_is_optional() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema, AssociationsTestSupport.root(schema, AssociationsTestSupport.ASSOC_MEMBERSHIP),
            new ProjectionOptions(
                true, true, false, false, false, true, "_", null, java.util.Set.of(), true, true));

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .contains("Person_ref", "Person_ref_bid", "Organisation_ref", "Organisation_ref_bid");
  }

  @Test
  void flattens_uniquely_embeddable_association_attributes_on_class_rows() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema, AssociationsTestSupport.root(schema, AssociationsTestSupport.CLASS_PERSON),
            ProjectionOptions.defaults());

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_bid", "Name", "Address_ref", "Address_Share");
    assertThat(plan.linkResolvedRoles()).containsKey("Address");
    assertThat(plan.hasLinkResolvedRoles()).isTrue();

    InterlisFieldPlan share = plan.fields().get(4);
    assertThat(share.source()).isEqualTo(InterlisFieldSource.ASSOCIATION_ATTRIBUTE);
    assertThat(share.attributeDescriptor().name()).isEqualTo("Share");
    assertThat(share.roleDescriptor().name()).isEqualTo("Address");
  }

  @Test
  void warns_for_non_flattenable_attributed_association_roles() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema, AssociationsTestSupport.root(schema, AssociationsTestSupport.CLASS_PERSON),
            ProjectionOptions.defaults());

    // Person's role Organisation belongs to Membership (attributes, {0..*}): not flattenable.
    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .doesNotContain("Organisation_ref");
    assertThat(plan.warnings())
        .anySatisfy(
            w -> {
              assertThat(w).contains("Organisation").contains("association rows");
            });
  }

  @Test
  void flattening_can_be_disabled() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema, AssociationsTestSupport.root(schema, AssociationsTestSupport.CLASS_PERSON),
            new ProjectionOptions(
                true, true, false, false, false, true, "_", null, java.util.Set.of(), false, false));

    assertThat(plan.fields())
        .extracting(InterlisFieldPlan::hopFieldName)
        .containsExactly("_ili_tid", "_ili_bid", "Name");
    assertThat(plan.hasLinkResolvedRoles()).isFalse();
  }
}
