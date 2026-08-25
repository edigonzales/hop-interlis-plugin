package ch.so.agi.hop.interlis.transforms.test;

import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;

/**
 * Minimal experimental transform used to verify plugin discovery, the shared geometry classloader
 * group and programmatic pipeline execution.
 *
 * <p>This transform is a Phase-0 placeholder; it is replaced by the real {@code INTERLIS Input}
 * transform in Phase 1.
 */
@Transform(
    id = "INTERLIS_TEST",
    name = "INTERLIS Test",
    description = "Emits test rows with a Hop geometry field (Phase 0 spike)",
    image = "ch/so/agi/hop/interlis/transforms/test/icons/interlis-test.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "test"})
public class InterlisTestMeta extends BaseTransformMeta<InterlisTest, InterlisTestData> {

  @HopMetadataProperty private String prefix;

  public InterlisTestMeta() {
    super();
  }

  @Override
  public void setDefault() {
    prefix = "interlis";
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
      // Merge the sogeo-geometry classloader group so ValueMetaGeometry resolves at runtime.
      InterlisRuntimeSupport.initialize();
    } catch (org.apache.hop.core.exception.HopException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
    rowMeta.addValueMeta(new ValueMetaString("_ili_tid"));
    rowMeta.addValueMeta(new ValueMetaString("_ili_bid"));
    rowMeta.addValueMeta(new ValueMetaString("name"));
    rowMeta.addValueMeta(new ValueMetaGeometry("geom"));
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
        new CheckResult(ICheckResult.TYPE_RESULT_OK, "INTERLIS Test transform is configured", transformMeta));
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }
}
