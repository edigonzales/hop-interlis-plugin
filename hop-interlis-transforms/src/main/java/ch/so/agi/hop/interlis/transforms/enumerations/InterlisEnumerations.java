package ch.so.agi.hop.interlis.transforms.enumerations;

import ch.so.agi.hop.interlis.core.model.InterlisEnumerationExtractor;
import ch.so.agi.hop.interlis.core.model.InterlisEnumerationRow;
import ch.so.agi.hop.interlis.transforms.InterlisParallelCopies;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Enumerations: lists the enumeration values of an INTERLIS model as lookup rows
 * ({@code enum_definition}, {@code enum_value}, {@code enum_path}, {@code parent_value},
 * {@code depth}, {@code is_leaf}), including sub-enumerations.
 */
public class InterlisEnumerations
    extends BaseTransform<InterlisEnumerationsMeta, InterlisEnumerationsData> {

  public InterlisEnumerations(
      TransformMeta transformMeta,
      InterlisEnumerationsMeta meta,
      InterlisEnumerationsData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.initialized) {
      doInitialize();
    }

    if (data.pendingRows.hasNext()) {
      putRow(data.outputRowMeta, data.pendingRows.next());
      return true;
    }

    setOutputDone();
    return false;
  }

  private void doInitialize() throws HopException {
    InterlisParallelCopies.rejectParallelCopies(getCopy(), getTransformName());
    InterlisRuntimeSupport.initialize();

    try {
      List<InterlisEnumerationRow> rows =
          new InterlisEnumerationExtractor().extract(meta.compileModel(this));
      List<Object[]> output = new ArrayList<>(rows.size());
      for (InterlisEnumerationRow row : rows) {
        output.add(
            new Object[] {
              row.definition(),
              row.value(),
              row.path(),
              row.parentValue(),
              (long) row.depth(),
              row.isLeaf()
            });
      }
      data.pendingRows = output.iterator();

      data.outputRowMeta = new RowMeta();
      meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);

      if (isBasic()) {
        logBasic("Listing " + rows.size() + " enumeration values of " + meta.getModelNames());
      }
      data.initialized = true;
    } catch (Exception e) {
      throw new HopException("Failed to initialize INTERLIS Enumerations: " + e.getMessage(), e);
    }
  }
}
