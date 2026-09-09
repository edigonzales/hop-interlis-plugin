package ch.so.agi.hop.interlis.transforms.generic;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
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

  InterlisOutputMeta output() {
    var meta = new InterlisOutputMeta();
    meta.setDefault();
    meta.setFileName("result.xtf");
    meta.setModelNames("HopIli_P1_V1");
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName(plan.root().scopedName());
    return meta;
  }

  @Test
  void configured_fields_are_checked_consistently_and_constant_basket_needs_no_column()
      throws Exception {
    var input = new HopRowSchemaFactory().createRowMeta(plan);
    var meta = output();
    meta.setObjectIdField("custom");
    assertThatThrownBy(() -> InterlisOutputBindings.bind(input, plan, meta, VARS))
        .hasMessageContaining("custom");
    var remarks = new ArrayList<ICheckResult>();
    meta.check(
        remarks,
        new PipelineMeta(),
        new TransformMeta("output", meta),
        input,
        new String[] {"source"},
        new String[0],
        null,
        VARS,
        null);
    assertThat(remarks)
        .anyMatch(
            r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR && r.getText().contains("custom"));
    input.getValueMeta(input.indexOfValue("_ili_tid")).setName("custom");
    meta.setBasketIdField("missing");
    assertThatThrownBy(() -> InterlisOutputBindings.bind(input, plan, meta, VARS))
        .hasMessageContaining("missing");
    meta.setBasketIdField("");
    meta.setBasketId("constant");
    input.removeValueMeta(input.indexOfValue("_ili_bid"));
    var binding = InterlisOutputBindings.bind(input, plan, meta, VARS);
    assertThat(binding.values(new Object[input.size()])[binding.basketIndex()])
        .isEqualTo("constant");
    assertThat(new InterlisOutputDialogController().mapping(plan, meta, VARS))
        .anySatisfy(
            m -> {
              assertThat(m.property()).isEqualTo("@TID");
              assertThat(m.hopField()).isEqualTo("custom");
            });
    meta.setBasketIdField("basket");
    input.addValueMeta(new ValueMetaString("basket"));
    binding = InterlisOutputBindings.bind(input, plan, meta, VARS);
    Object[] row = new Object[input.size()];
    row[input.indexOfValue("basket")] = " ";
    assertThat(binding.values(row)[binding.basketIndex()]).isEqualTo("constant");
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
    var meta = output();
    meta.setObjectIdField("");
    assertThat(input.indexOfValue("_ili_tid")).isNegative();
    assertThatCode(() -> InterlisOutputBindings.bind(input, association, meta, VARS))
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
