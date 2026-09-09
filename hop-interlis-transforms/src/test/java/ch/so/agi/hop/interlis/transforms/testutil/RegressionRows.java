package ch.so.agi.hop.interlis.transforms.testutil;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public class RegressionRows extends BaseTransform<RegressionRowsMeta, RegressionRowsData> {
  public RegressionRows(
      TransformMeta t,
      RegressionRowsMeta m,
      RegressionRowsData d,
      int copy,
      PipelineMeta pm,
      Pipeline p) {
    super(t, m, d, copy, pm, p);
  }

  @Override
  public boolean processRow() throws HopException {
    if (data.index == meta.rows.size()) {
      setOutputDone();
      return false;
    }
    putRow(meta.schema, meta.rows.get(data.index++).clone());
    return true;
  }
}
