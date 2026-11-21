package ch.so.agi.hop.interlis.transforms.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisOutputMetaTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void sink_has_no_output_schema() throws Exception {
    var meta = new InterlisOutputMeta();
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isZero();
  }

  @Test
  void check_reports_errors_for_missing_configuration() {
    List<ICheckResult> remarks = new ArrayList<>();
    new InterlisOutputMeta().check(remarks, null, new TransformMeta("x", new InterlisOutputMeta()),
        null, new String[0], new String[0], null, new Variables(), null);

    assertThat(remarks)
        .anyMatch(
            r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR && r.getText().contains("required"));
  }
}
