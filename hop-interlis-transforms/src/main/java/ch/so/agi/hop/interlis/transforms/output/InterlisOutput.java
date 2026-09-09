package ch.so.agi.hop.interlis.transforms.output;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.XtfTransferWriter;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.RowToIomMapper;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Output: writes typed Hop rows of one INTERLIS class to an XTF file.
 *
 * <p>Input rows must be grouped by basket ID; a changed BID opens a new basket. The row schema is
 * not modified, so downstream transforms receive the rows unchanged.
 */
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
    Object[] row = getRow();
    if (row == null) {
      finishWriting();
      setOutputDone();
      if (isBasic()) {
        logBasic("Finished writing INTERLIS transfer: objects written " + data.writtenObjects);
      }
      return false;
    }

    if (!data.initialized) {
      initialize();
    }

    try {
      Object[] values = data.bindings.values(row);
      handleBasket(values);
      ch.so.agi.hop.interlis.core.io.InterlisObjectOperation operation =
          ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.NONE;
      if (data.operationFieldIndex >= 0) {
        Object operationValue = row[data.operationFieldIndex];
        if (operationValue != null && !operationValue.toString().isBlank()) {
          operation =
              ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.valueOf(
                  operationValue.toString().trim());
        }
      }
      RowWriteOptions writeOptions = new RowWriteOptions(true, data.currentBid, operation);
      RowToIomMapper.InterlisWriteResult result;
      if (data.sourceObjectFieldIndex >= 0 && !writeOptions.isDelete()) {
        Object carrier = row[data.sourceObjectFieldIndex];
        if (carrier == null) {
          throw new HopException(
              "Source object field <"
                  + resolve(meta.getSourceObjectField())
                  + "> is null; INTERLIS Input must be configured with \"Keep source object for"
                  + " Structure Explode\" or the field must be updated by INTERLIS Structure"
                  + " Collect");
        }
        if (!(carrier instanceof IomObject carrierObject)) {
          throw new HopException(
              "Source object field <"
                  + resolve(meta.getSourceObjectField())
                  + "> does not contain an INTERLIS object but "
                  + carrier.getClass().getName());
        }
        result = data.mapper.mapAll(carrierObject, values, data.plan, writeOptions);
      } else {
        result = data.mapper.mapAll(values, data.plan, writeOptions);
      }
      for (IomObject object : result.allObjects()) {
        if (data.operationFieldIndex >= 0
            && object == result.object()
            && operation != ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.NONE) {
          object.setobjectoperation(operation.toIom());
        }
        data.writer.writeObject(object);
        data.writtenObjects++;
      }
    } catch (Exception e) {
      closeWriter();
      throw new HopException("Failed to write INTERLIS object: " + e.getMessage(), e);
    }

    putRow(getInputRowMeta(), row);
    if (checkFeedback(getLinesWritten()) && isBasic()) {
      logBasic("Wrote " + data.writtenObjects + " objects of class " + meta.getClassName());
    }
    return true;
  }

  private void initialize() throws HopException {
    ch.so.agi.hop.interlis.transforms.InterlisParallelCopies.requireSingleCopy(
        getTransformMeta(), this, "file processing or enumeration emission requires one copy");
    InterlisRuntimeSupport.initialize();

    String resolvedFile = resolve(meta.getFileName());
    if (resolvedFile.isBlank()) {
      throw new HopException("INTERLIS output file is not configured");
    }
    Path file = Path.of(resolvedFile);
    if (!meta.isOverwrite() && java.nio.file.Files.exists(file)) {
      throw new HopException(
          "INTERLIS output file already exists: " + file + " (enable overwrite to replace it)");
    }

    try {
      data.projection =
          new InterlisProjectionService()
              .project(
                  new InterlisModelRequest(null, resolveModelNames(), resolveModelDirectories()),
                  resolve(meta.getClassName()),
                  meta.projectionOptions(this));
      data.plan = data.projection.plan();
      data.mapper = new RowToIomMapper();
      data.bindings = InterlisOutputBindings.bind(getInputRowMeta(), data.plan, meta, this);

      String sourceObjectField =
          resolve(meta.getSourceObjectField() == null ? "" : meta.getSourceObjectField());
      data.sourceObjectFieldIndex =
          sourceObjectField.isBlank() ? -1 : getInputRowMeta().indexOfValue(sourceObjectField);
      if (!sourceObjectField.isBlank() && data.sourceObjectFieldIndex < 0) {
        throw new HopException(
            "Source object field <" + sourceObjectField + "> not found in the input");
      }

      String operationField =
          resolve(meta.getOperationField() == null ? "" : meta.getOperationField());
      data.operationFieldIndex =
          operationField.isBlank() ? -1 : getInputRowMeta().indexOfValue(operationField);
      if (!operationField.isBlank() && data.operationFieldIndex < 0) {
        throw new HopException("Operation field <" + operationField + "> not found in the input");
      }

      data.writer =
          XtfTransferWriter.open(
              file, data.projection.model().transferDescription(), data.projection.modelNames());
      data.writer.startTransfer("hop-interlis-plugin");

      if (isBasic()) {
        logBasic(
            "Writing INTERLIS class "
                + data.plan.root().scopedName()
                + " to "
                + file
                + " (models "
                + data.projection.modelNames()
                + ")");
      }
      data.initialized = true;
    } catch (HopException e) {
      closeWriter();
      throw e;
    } catch (Exception e) {
      closeWriter();
      throw new HopException("Failed to initialize INTERLIS Output: " + e.getMessage(), e);
    }
  }

  private void handleBasket(Object[] values) throws Exception {
    String bid =
        data.bindings.basketIndex() < 0
            ? data.bindings.constantBasket()
            : values[data.bindings.basketIndex()].toString().trim();

    if (data.currentBid == null) {
      data.writer.startBasket(data.plan.root().topicScopedName(), bid);
      data.currentBid = bid;
    } else if (!data.currentBid.equals(bid)) {
      data.writer.endBasket();
      data.writer.startBasket(data.plan.root().topicScopedName(), bid);
      data.currentBid = bid;
    }
  }

  private void finishWriting() throws HopException {
    if (data.writer == null) {
      return;
    }
    try {
      if (data.currentBid != null) {
        data.writer.endBasket();
      }
      data.writer.endTransfer();
    } catch (Exception e) {
      throw new HopException("Failed to finish INTERLIS transfer: " + e.getMessage(), e);
    } finally {
      closeWriter();
    }
  }

  private void closeWriter() {
    if (data.writer != null) {
      try {
        data.writer.close();
      } catch (Exception e) {
        if (isDebug()) {
          logDebug("Failed to close INTERLIS writer: " + e.getMessage());
        }
      } finally {
        data.writer = null;
      }
    }
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }

  @Override
  public void dispose() {
    closeWriter();
    super.dispose();
  }
}
