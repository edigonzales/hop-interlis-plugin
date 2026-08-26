package ch.so.agi.hop.interlis.transforms.testutil;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Runtime of the {@link SyntheticChildRowsMeta} test transform. */
public class SyntheticChildRows
    extends BaseTransform<SyntheticChildRowsMeta, SyntheticChildRowsData> {

  private boolean first = true;

  public SyntheticChildRows(
      TransformMeta transformMeta,
      SyntheticChildRowsMeta meta,
      SyntheticChildRowsData data,
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
        putRow(
            outputRowMeta,
            new Object[] {row[0], row[1], row[2], row[3], null, null, null});
      }
    }
    setOutputDone();
    return false;
  }
}
