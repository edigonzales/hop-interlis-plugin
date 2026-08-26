package ch.so.agi.hop.interlis.transforms.testutil;

import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Test-only inputless transform emitting configurable synthetic structure child rows, matching
 * the child row schema of INTERLIS Structure Explode for the Addresses structure of the
 * structures test model: {@code _ili_parent_tid, _ili_index, Street, Number, Location,
 * PostCode_Code, PostCode_Town}.
 *
 * <p>Used by pipeline tests of INTERLIS Structure Collect to produce orphan children, duplicate
 * indexes and modified structure content without depending on real transfer data.
 */
@Transform(
    id = "INTERLIS_TEST_CHILD_ROWS",
    name = "INTERLIS Test Child Rows",
    description = "Emits synthetic structure child rows (test support)",
    image = "ch/so/agi/hop/interlis/transforms/test/icons/interlis-test.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "test"})
public class SyntheticChildRowsMeta
    extends BaseTransformMeta<SyntheticChildRows, SyntheticChildRowsData> {

  /** [parentTid, index, street, number]; index may be null. */
  private final List<Object[]> rows = new ArrayList<>();

  public SyntheticChildRowsMeta() {
    super();
  }

  @Override
  public void setDefault() {
    rows.clear();
  }

  /** Adds a child row; {@code index} may be {@code null}. */
  public void addRow(String parentTid, Long index, String street, String number) {
    rows.add(new Object[] {parentTid, index, street, number});
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
    try {
      InterlisRuntimeSupport.initialize();
    } catch (HopException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
    rowMeta.addValueMeta(new ValueMetaString("_ili_parent_tid"));
    rowMeta.addValueMeta(new ValueMetaInteger("_ili_index"));
    rowMeta.addValueMeta(new ValueMetaString("Street"));
    rowMeta.addValueMeta(new ValueMetaString("Number"));
    rowMeta.addValueMeta(new ValueMetaGeometry("Location"));
    rowMeta.addValueMeta(new ValueMetaString("PostCode_Code"));
    rowMeta.addValueMeta(new ValueMetaString("PostCode_Town"));
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
        new CheckResult(ICheckResult.TYPE_RESULT_OK, "Synthetic child rows transform", transformMeta));
  }
}
