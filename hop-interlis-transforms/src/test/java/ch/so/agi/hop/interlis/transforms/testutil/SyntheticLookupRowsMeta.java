package ch.so.agi.hop.interlis.transforms.testutil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Test-only inputless transform emitting String rows for Role Join pipeline tests. */
@Transform(
    id = "INTERLIS_TEST_LOOKUP_ROWS",
    name = "INTERLIS Test Lookup Rows",
    description = "Emits synthetic lookup rows (test support)",
    image = "ch/so/agi/hop/interlis/transforms/input/icons/interlis-input.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "test"})
public class SyntheticLookupRowsMeta
    extends BaseTransformMeta<SyntheticLookupRows, SyntheticLookupRowsData> {

  private List<String> fields = List.of("_ili_tid", "Street");
  private final List<Object[]> rows = new ArrayList<>();

  public SyntheticLookupRowsMeta() {
    super();
  }

  @Override
  public void setDefault() {
    rows.clear();
  }

  public void setFields(String... names) {
    fields = List.of(names);
  }

  public void addRow(String... values) {
    rows.add(Arrays.copyOf(values, values.length, Object[].class));
  }

  public List<Object[]> getRows() {
    return rows;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    for (String field : fields) {
      rowMeta.addValueMeta(new ValueMetaString(field));
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK, "Synthetic lookup rows transform", transformMeta));
  }
}
