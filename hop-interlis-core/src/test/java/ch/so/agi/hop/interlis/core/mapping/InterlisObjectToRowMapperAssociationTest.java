package ch.so.agi.hop.interlis.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisObjectToRowMapperAssociationTest {

  private static InterlisSchemaDescriptor schema;
  private static Map<String, List<InterlisObjectEnvelope>> objects;

  private final InterlisRowSchemaBuilder builder = new InterlisRowSchemaBuilder();
  private final DefaultInterlisObjectToRowMapper mapper = new DefaultInterlisObjectToRowMapper();

  @BeforeAll
  static void setUp() throws Exception {
    schema = AssociationsTestSupport.schema();
    objects = AssociationsTestSupport.allObjects();
  }

  private InterlisRowMappingPlan plan(String scopedName) throws Exception {
    return builder.build(
        schema, AssociationsTestSupport.root(schema, scopedName), ProjectionOptions.defaults());
  }

  @Test
  void maps_association_rows_with_role_refs_and_attributes() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.ASSOC_MEMBERSHIP);
    List<InterlisObjectEnvelope> memberships = objects.get(AssociationsTestSupport.ASSOC_MEMBERSHIP);

    Object[] first = mapper.map(memberships.get(0), plan);
    // _ili_bid, Person_ref, Organisation_ref, Function, Entry
    assertThat(first[0]).isEqualTo("b1");
    assertThat(first[1]).isEqualTo("p1");
    assertThat(first[2]).isEqualTo("o1");
    assertThat(first[3]).isEqualTo("CEO");
    assertThat(
            java.time.LocalDate.ofInstant(
                    ((java.util.Date) first[4]).toInstant(), java.time.ZoneOffset.UTC)
                .toString())
        .isEqualTo("2020-01-01");

    Object[] second = mapper.map(memberships.get(1), plan);
    assertThat(second[1]).isEqualTo("p2");
    assertThat(second[3]).isEqualTo("Dev");
  }

  @Test
  void maps_ordered_role_order_positions() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.ASSOC_PERSON_TASK);
    List<InterlisObjectEnvelope> links = objects.get(AssociationsTestSupport.ASSOC_PERSON_TASK);

    Object[] first = mapper.map(links.get(0), plan);
    // _ili_bid, Person_ref, Task_ref, Task_order_pos
    assertThat(first[1]).isEqualTo("p1");
    assertThat(first[2]).isEqualTo("t1");
    assertThat(first[3]).isEqualTo(0L);

    Object[] second = mapper.map(links.get(1), plan);
    assertThat(second[3]).isEqualTo(1L);
  }

  @Test
  void maps_external_basket_references() throws Exception {
    InterlisRowMappingPlan plan =
        builder.build(
            schema,
            AssociationsTestSupport.root(schema, AssociationsTestSupport.ASSOC_PERSON_TASK),
            new ProjectionOptions(
                true, true, false, false, false, true, "_", null, java.util.Set.of(), true, true));
    List<InterlisObjectEnvelope> links = objects.get(AssociationsTestSupport.ASSOC_PERSON_TASK);

    Object[] second = mapper.map(links.get(1), plan);
    // _ili_bid, Person_ref, Person_ref_bid, Task_ref, Task_ref_bid, Task_order_pos
    assertThat(second[4]).isEqualTo("b2");
    assertThat(second[5]).isEqualTo(1L);
  }

  @Test
  void flattens_association_attributes_from_the_link_object() throws Exception {
    InterlisRowMappingPlan plan = plan(AssociationsTestSupport.CLASS_PERSON);
    List<InterlisObjectEnvelope> persons = objects.get(AssociationsTestSupport.CLASS_PERSON);

    // Link lookup: AddressOwnership link for p1.
    InterlisAssociationLinkLookup lookup =
        (objectTid, roleName) -> {
          if ("p1".equals(objectTid) && "Address".equals(roleName)) {
            return objects
                .get(AssociationsTestSupport.ASSOC_ADDRESS_OWNERSHIP)
                .get(0)
                .object();
          }
          return null;
        };

    Object[] p1 = mapper.map(persons.get(0), plan, lookup);
    assertThat(p1[3]).isEqualTo("a1"); // Address_ref resolved from the link
    assertThat(p1[4]).isEqualTo(new BigDecimal("0.5"));

    // Without a lookup the link-resolved fields stay null.
    Object[] p2 = mapper.map(persons.get(1), plan, null);
    assertThat(p2[3]).isNull();
    assertThat(p2[4]).isNull();
  }
}
