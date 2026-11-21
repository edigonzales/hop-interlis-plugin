package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.mapping.MappedClassPlan;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.*;

class P1BindingAndLayoutTest {
  static InterlisRowMappingPlan plan;
  static final Variables VARS = new Variables();

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
    plan =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(
                    null, List.of("HopIli_P1_V1"), List.of(TestData.path("/models").toString())),
                "HopIli_P1_V1.Data.Item",
                ProjectionOptions.defaults())
            .plan();
  }

  @Test
  void non_identifiable_association_has_no_tid_binding() throws Exception {
    var association =
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(
                    null,
                    List.of("HopIli_Associations_V1"),
                    List.of(TestData.path("/models").toString())),
                "HopIli_Associations_V1.Data.Membership",
                ProjectionOptions.defaults())
            .plan();
    var input = new HopRowSchemaFactory().createRowMeta(association);
    var config = TestData.mappedInput("source", association.root().scopedName());
    config.setObjectIdField("");
    assertThat(input.indexOfValue("_ili_tid")).isNegative();
    assertThatCode(
            () ->
                new MappedClassPlan(association, config, VARS, true)
                    .bind(input, config, VARS, null))
        .doesNotThrowAnyException();
  }

  @Test
  void append_preserves_input_and_reuses_only_compatible_technical_fields() throws Exception {
    var input = new RowMeta();
    input.addValueMeta(new ValueMetaString("extra"));
    input.addValueMeta(new ValueMetaString("_ili_tid"));
    var output = InterlisObjectToRowOutputPlan.create(input, plan, true);
    Object[] typed = new Object[plan.fieldCount()];
    Arrays.fill(typed, "typed");
    Object[] actual = output.values(new Object[] {"original", "tid"}, typed);
    assertThat(actual).hasSize(output.rowMeta().size());
    Object[] padded = new Object[100];
    padded[0] = "original";
    padded[1] = "tid";
    assertThat(output.values(padded, typed)).containsExactly(actual);
    assertThat(Arrays.copyOf(actual, 2)).containsExactly("original", "tid");
    assertThat(output.rowMeta().getFieldNames()).doesNotHaveDuplicates();
    assertThat(actual[output.rowMeta().indexOfValue("Name")]).isEqualTo("typed");
    assertThat(
            InterlisObjectToRowOutputPlan.create(input, plan, false)
                .values(new Object[] {"ignored"}, typed))
        .isSameAs(typed);
    input.addValueMeta(new ValueMetaString("Name"));
    assertThatThrownBy(() -> InterlisObjectToRowOutputPlan.create(input, plan, true))
        .hasMessageContaining("Name")
        .hasMessageContaining("collides");
    input.removeValueMeta(input.indexOfValue("Name"));
    input.setValueMeta(1, new ValueMetaInteger("_ili_tid"));
    assertThatThrownBy(() -> InterlisObjectToRowOutputPlan.create(input, plan, true))
        .hasMessageContaining("_ili_tid");
  }
}
