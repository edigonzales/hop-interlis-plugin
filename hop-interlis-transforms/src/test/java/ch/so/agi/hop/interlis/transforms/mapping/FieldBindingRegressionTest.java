package ch.so.agi.hop.interlis.transforms.mapping;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.*;
import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.transforms.output.*;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.*;

class FieldBindingRegressionTest {
  static InterlisRowMappingPlan plan;

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
  void output_rejects_wrong_business_type_before_values_are_read() throws Exception {
    var input = new HopRowSchemaFactory().createRowMeta(plan);
    input.setValueMeta(input.indexOfValue("Name"), new ValueMetaInteger("Name"));
    var meta = new InterlisOutputMeta();
    meta.setDefault();
    assertThatThrownBy(() -> InterlisOutputBindings.bind(input, plan, meta, new Variables()))
        .hasMessageContaining("Name")
        .hasMessageContaining("String")
        .hasMessageContaining("Integer");
  }

  @Test
  void output_rejects_ambiguous_names() throws Exception {
    var input = new HopRowSchemaFactory().createRowMeta(plan);
    input.addValueMeta(new ValueMetaString("another"));
    input.getValueMeta(input.size() - 1).setName("NAME");
    var meta = new InterlisOutputMeta();
    meta.setDefault();
    assertThatThrownBy(() -> InterlisOutputBindings.bind(input, plan, meta, new Variables()))
        .hasMessageContaining("Ambiguous");
  }

  @Test
  void output_normalizes_lazy_string_without_mutating_input() throws Exception {
    var input = new HopRowSchemaFactory().createRowMeta(plan);
    var field = input.getValueMeta(input.indexOfValue("Name"));
    field.setStorageType(IValueMeta.STORAGE_TYPE_BINARY_STRING);
    field.setStorageMetadata(new ValueMetaString("Name"));
    var row = new Object[input.size()];
    var bytes = "lazy".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    row[input.indexOfValue("Name")] = bytes;
    var meta = new InterlisOutputMeta();
    meta.setDefault();
    var values = InterlisOutputBindings.bind(input, plan, meta, new Variables()).values(row);
    assertThat(values[input.indexOfValue("Name")]).isEqualTo("lazy");
    assertThat(row[input.indexOfValue("Name")]).isSameAs(bytes);
  }

  @Test
  void optional_constant_variable_and_case_rules_are_shared() throws Exception {
    var input = new RowMeta();
    input.addValueMeta(new ValueMetaString("Custom"));
    var vars = new Variables();
    vars.setVariable("FIELD", "custom");
    var field =
        InterlisFieldBinding.bind(
            input,
            "test stream",
            InterlisFieldBinding.resolve(vars, "${FIELD}"),
            "target",
            0,
            new ValueMetaString("target"),
            true);
    assertThat(field.read(new Object[] {"value"})).isEqualTo("value");
    assertThat(field.read(new Object[] {null})).isNull();
    assertThat(
            InterlisFieldBinding.bind(
                    input, "test", "missing", "optional", 0, new ValueMetaString("optional"), false)
                .read(new Object[1]))
        .isNull();
    assertThat(InterlisFieldBinding.constant("test", "basket", 0, "b1").read(new Object[0]))
        .isEqualTo("b1");
    assertThatThrownBy(
            () ->
                InterlisFieldBinding.bind(
                    input,
                    "lookup stream",
                    "missing",
                    "result",
                    0,
                    new ValueMetaString("result"),
                    true))
        .hasMessageContaining("lookup stream")
        .hasMessageContaining("result")
        .hasMessageContaining("missing");
  }

  @Test
  void normalized_and_indexed_values_use_a_metadata_snapshot() throws Exception {
    var meta = new ValueMetaString("code");
    meta.setStorageType(IValueMeta.STORAGE_TYPE_INDEXED);
    meta.setIndex(new Object[] {"zero", "one"});
    var input = new RowMeta();
    input.addValueMeta(meta);
    var binding =
        InterlisFieldBinding.bind(
            input, "test", "code", "code", 0, new ValueMetaString("code"), true);
    meta.setStorageType(IValueMeta.STORAGE_TYPE_NORMAL);
    assertThat(binding.read(new Object[] {1})).isEqualTo("one");
  }

  @Test
  void carrier_and_operation_are_checked_before_output() throws Exception {
    var input = new HopRowSchemaFactory().createRowMeta(plan);
    input.addValueMeta(new ValueMetaString("carrier"));
    var meta = new InterlisOutputMeta();
    meta.setDefault();
    meta.setSourceObjectField("carrier");
    assertThatThrownBy(() -> InterlisOutputBindings.bind(input, plan, meta, new Variables()))
        .hasMessageContaining("carrier")
        .hasMessageContaining("expected");
    meta.setSourceObjectField("");
    meta.setOperationField("absent");
    assertThatThrownBy(() -> InterlisOutputBindings.bind(input, plan, meta, new Variables()))
        .hasMessageContaining("absent");
  }
}
