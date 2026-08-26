package ch.so.agi.hop.interlis.transforms.testutil;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Runtime of the {@link SyntheticLookupRowsMeta} test transform. */
public class SyntheticLookupRows
    extends BaseTransform<SyntheticLookupRowsMeta, SyntheticLookupRowsData> {

  private boolean first = true;

  public SyntheticLookupRows(
      TransformMeta transformMeta,
      SyntheticLookupRowsMeta meta,
      SyntheticLookupRowsData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (first) {
      first = false;
      RowMeta outputRowMeta = new RowMeta();
      meta.getFields(outputRowMeta, getTransformName(), null, null, this, metadataProvider);
      for (Object[] row : meta.getRows()) {
        putRow(outputRowMeta, new Object[] {row[0], row[1]});
      }
    }
    setOutputDone();
    return false;
  }
}
