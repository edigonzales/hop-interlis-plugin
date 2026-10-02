package ch.so.agi.hop.interlis.transforms.update;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public class InterlisUpdate extends BaseTransform<InterlisUpdateMeta, InterlisUpdateData> {
  public InterlisUpdate(
      TransformMeta transform,
      InterlisUpdateMeta meta,
      InterlisUpdateData data,
      int copy,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transform, meta, data, copy, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    try {
      new InterlisUpdateRunner(this, meta).run();
    } catch (Exception e) {
      throw new HopException("INTERLIS Update failed: " + e.getMessage(), e);
    }
    setOutputDone();
    return false;
  }
}
