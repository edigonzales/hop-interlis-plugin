package ch.so.agi.hop.interlis.transforms.testutil;

import java.util.*;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transform.*;

/** Configurable test-only source; never packaged in the plugin. */
@Transform(
    id = "INTERLIS_REGRESSION_ROWS",
    name = "INTERLIS Regression Rows",
    description = "Test rows")
public class RegressionRowsMeta extends BaseTransformMeta<RegressionRows, RegressionRowsData> {
  public IRowMeta schema;
  public List<Object[]> rows = new ArrayList<>();

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta next,
      IVariables variables,
      IHopMetadataProvider provider) {
    rowMeta.clear();
    rowMeta.addRowMeta(schema);
  }
}
