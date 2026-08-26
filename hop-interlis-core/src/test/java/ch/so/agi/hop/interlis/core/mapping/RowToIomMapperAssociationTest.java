package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RowToIomMapperAssociationTest {

  private static InterlisSchemaDescriptor schema;

  private final InterlisRowSchemaBuilder builder = new InterlisRowSchemaBuilder();
  private final RowToIomMapper mapper = new RowToIomMapper();

  @BeforeAll
  static void setUp() throws Exception {
    schema = AssociationsTestSupport.schema();
  }

  private InterlisRowMappingPlan plan(String scopedName) throws Exception {
    return builder.build(
        schema, AssociationsTestSupport.root(schema, scopedName), ProjectionOptions.defaults());
  }

  @Test
  void writes_association_link_objects_without_tid() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.ASSOC_MEMBERSHIP);
    // _ili_bid, Person_ref, Organisation_ref, Function, Entry
    Object[] values = {
      "b1",
      "p1",
      "o1",
      "CEO",
      java.util.Date.from(
          java.time.LocalDate.parse("2020-01-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
    };

    IomObject link = mapper.map(values, plan, RowWriteOptions.defaults());

    assertThat(link.getobjecttag()).isEqualTo("HopIli_Associations_V1.Data.Membership");
    assertThat(link.getobjectoid()).isNull();
    assertThat(link.getattrobj("Person", 0).getobjectrefoid()).isEqualTo("p1");
    assertThat(link.getattrobj("Organisation", 0).getobjectrefoid()).isEqualTo("o1");
    assertThat(link.getattrvalue("Function")).isEqualTo("CEO");
    assertThat(link.getattrvalue("Entry")).isEqualTo("2020-01-01");
  }

  @Test
  void writes_ordered_role_positions_and_external_basket_references() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema,
            AssociationsTestSupport.root(schema, AssociationsTestSupport.ASSOC_PERSON_TASK),
            new ProjectionOptions(
                true, true, false, false, false, true, "_", null, java.util.Set.of(), true, true));
    // _ili_bid, Person_ref, Person_ref_bid, Task_ref, Task_ref_bid, Task_order_pos
    Object[] values = {"b1", "p2", null, "t2", "b2", 1L};

    IomObject link = mapper.map(values, plan, RowWriteOptions.defaults());

    IomObject task = link.getattrobj("Task", 0);
    assertThat(task.getobjectrefoid()).isEqualTo("t2");
    assertThat(task.getobjectrefbid()).isEqualTo("b2");
    assertThat(task.getobjectreforderpos()).isEqualTo(1L);
    assertThat(link.getattrobj("Person", 0).getobjectrefoid()).isEqualTo("p2");
  }

  @Test
  void class_rows_generate_the_association_link_object() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.CLASS_PERSON);
    // _ili_tid, _ili_bid, Name, Address_ref, Address_Share
    Object[] values = {"p1", "b1", "Meier", "a1", new BigDecimal("0.5")};

    RowToIomMapper.InterlisWriteResult result =
        mapper.mapAll(values, plan, RowWriteOptions.defaults());

    assertThat(result.object().getobjecttag()).isEqualTo("HopIli_Associations_V1.Data.Person");
    // The link-resolved role must not be written onto the class object.
    assertThat(result.object().getattrvaluecount("Address")).isZero();

    assertThat(result.additionalObjects()).hasSize(1);
    IomObject link = result.additionalObjects().get(0);
    assertThat(link.getobjecttag()).isEqualTo("HopIli_Associations_V1.Data.AddressOwnership");
    assertThat(link.getobjectoid()).isNull();
    assertThat(link.getattrobj("Person", 0).getobjectrefoid()).isEqualTo("p1");
    assertThat(link.getattrobj("Address", 0).getobjectrefoid()).isEqualTo("a1");
    assertThat(link.getattrvalue("Share")).isEqualTo("0.5");
  }

  @Test
  void no_link_is_generated_without_values() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.CLASS_PERSON);
    Object[] values = {"p3", "b1", "Keller", null, null};

    RowToIomMapper.InterlisWriteResult result =
        mapper.mapAll(values, plan, RowWriteOptions.defaults());

    assertThat(result.additionalObjects()).isEmpty();
    assertThat(result.object().getattrvaluecount("Address")).isZero();
  }

  @Test
  void association_values_without_reference_fail() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.CLASS_PERSON);
    Object[] values = {"p1", "b1", "Meier", null, new BigDecimal("0.5")};

    assertThatThrownBy(() -> mapper.mapAll(values, plan, RowWriteOptions.defaults()))
        .isInstanceOf(InterlisMappingException.class)
        .hasMessageContaining("Address_ref");
  }
}
