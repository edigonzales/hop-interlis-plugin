package ch.so.agi.hop.interlis.transforms.output;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Writes mapped INTERLIS class streams to an XTF file. */
public class InterlisOutput extends BaseTransform<InterlisOutputMeta, InterlisOutputData> {

  public InterlisOutput(
      TransformMeta transformMeta,
      InterlisOutputMeta meta,
      InterlisOutputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    try {
      MappedOutputWriter.run(this, meta);
    } catch (Exception e) {
      throw new HopException("INTERLIS output failed: " + e.getMessage(), e);
    }
    setOutputDone();
    return false;
  }
}
