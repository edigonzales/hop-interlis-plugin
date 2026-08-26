package ch.so.agi.hop.interlis.transforms.testutil;

import java.util.ArrayList;
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
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Test-only inputless transform emitting configurable synthetic lookup rows with the schema
 * {@code _ili_tid, Name}. Used by INTERLIS Role Join pipeline tests.
 */
@Transform(
    id = "INTERLIS_TEST_LOOKUP_ROWS",
    name = "INTERLIS Test Lookup Rows",
    description = "Emits synthetic lookup rows (test support)",
    image = "ch/so/agi/hop/interlis/transforms/test/icons/interlis-test.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "test"})
public class SyntheticLookupRowsMeta
    extends BaseTransformMeta<SyntheticLookupRows, SyntheticLookupRowsData> {

  /** [tid, name] pairs. */
  private final List<Object[]> rows = new ArrayList<>();

  public SyntheticLookupRowsMeta() {
    super();
  }

  @Override
  public void setDefault() {
    rows.clear();
  }

  public void addRow(String tid, String name) {
    rows.add(new Object[] {tid, name});
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
    rowMeta.addValueMeta(new ValueMetaString("_ili_tid"));
    rowMeta.addValueMeta(new ValueMetaString("Street"));
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
        new CheckResult(ICheckResult.TYPE_RESULT_OK, "Synthetic lookup rows transform", transformMeta));
  }
}
