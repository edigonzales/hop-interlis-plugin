package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisParallelCopies;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Input: reads one INTERLIS class or association from an XTF file and emits typed Hop
 * rows.
 *
 * <p>The transform streams the transfer file: per {@code processRow()} call at most one row is
 * emitted; objects of other classes are skipped. When the projection contains flattened association
 * attributes (attributed associations are transferred as separate link objects), the rows of the
 * selected class are buffered per basket and emitted at basket end, once every link object of the
 * basket has been seen.
 */
public class InterlisInput extends BaseTransform<InterlisInputMeta, InterlisInputData> {

  public InterlisInput(
      TransformMeta transformMeta,
      InterlisInputMeta meta,
      InterlisInputData data,
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

    while (true) {
      if (data.pendingOutput != null && data.pendingOutput.hasNext()) {
        Object[] row = data.pendingOutput.next();
        data.emittedObjects++;
        putRow(data.outputRowMeta, row);
        if (checkFeedback(getLinesWritten()) && isBasic()) {
          logBasic("Read " + data.emittedObjects + " objects of " + meta.getClassName());
        }
        return true;
      }

      data.pendingOutput = null;

      InterlisObjectEnvelope envelope;
      try {
        envelope = data.reader.next();
      } catch (Exception e) {
        throw new HopException("Failed to read INTERLIS transfer: " + e.getMessage(), e);
      }
      if (envelope == null) {
        flushPending();
        if (data.pendingOutput != null && data.pendingOutput.hasNext()) {
          continue;
        }
        clearBufferedRows();
        closeReader();
        setOutputDone();
        if (isBasic()) {
          logBasic(
              "Finished reading INTERLIS transfer: objects read "
                  + data.readObjects
                  + ", rows emitted "
                  + data.emittedObjects);
        }
        return false;
      }

      if (envelope.eventType() == InterlisEventType.OBJECT) {
        data.readObjects++;
        if (data.buffering) {
          if (data.basketBuffer.startsNewBasket(envelope)) flushPending();
          data.basketBuffer.add(envelope, null);
          continue;
        }
        if (envelope.className() != null
            && envelope.className().equals(data.plan.root().scopedName())) {
          {
            Object[] row;
            try {
              row = data.mapper.map(envelope, data.plan);
            } catch (Exception e) {
              throw new HopException(e.getMessage(), e);
            }
            if (data.keepSourceObject) {
              row = appendSourceObject(row, envelope.object());
            }
            data.emittedObjects++;
            putRow(data.outputRowMeta, row);
            if (checkFeedback(getLinesWritten()) && isBasic()) {
              logBasic("Read " + data.emittedObjects + " objects of " + meta.getClassName());
            }
            return true;
          }
        }
      } else if (envelope.eventType() == InterlisEventType.END_BASKET
          || envelope.eventType() == InterlisEventType.END_TRANSFER) {
        flushPending();
      }
      // Other events and objects of other classes are skipped in the typed projection.
    }
  }

  private void flushPending() throws HopException {
    if (data.basketBuffer == null) return;
    var batch = data.basketBuffer.drain();
    List<Object[]> rows = new ArrayList<>(batch.rows().size());
    for (var entry : batch.rows()) {
      var envelope = entry.envelope();
      Object[] row;
      try {
        row = data.mapper.map(envelope, data.plan, batch.lookup());
      } catch (Exception e) {
        throw new HopException(e.getMessage(), e);
      }
      if (data.keepSourceObject) {
        row = appendSourceObject(row, envelope.object());
      }
      rows.add(row);
    }
    data.pendingOutput = rows.iterator();
  }

  private Object[] appendSourceObject(Object[] row, ch.interlis.iom.IomObject object)
      throws HopException {
    Object[] extended = new Object[row.length + 1];
    System.arraycopy(row, 0, extended, 0, row.length);
    extended[row.length] = object;
    if (extended.length != data.outputRowMeta.size()) {
      throw new HopException(
          "INTERLIS Input row ("
              + extended.length
              + " values) does not match the output schema ("
              + data.outputRowMeta.size()
              + " fields); the source object carrier is misconfigured");
    }
    return extended;
  }

  private void doInitialize() throws HopException {
    InterlisParallelCopies.requireSingleCopy(
        getTransformMeta(), this, "file processing or enumeration emission requires one copy");
    InterlisRuntimeSupport.initialize();

    String resolvedFile = resolve(meta.getFileName());
    if (resolvedFile.isBlank()) {
      throw new HopException("INTERLIS transfer file is not configured");
    }
    Path file = Path.of(resolvedFile);
    if (!java.nio.file.Files.isRegularFile(file)) {
      throw new HopException("INTERLIS transfer file does not exist: " + file);
    }

    try {
      data.reader = XtfTransferReader.open(file);
      InterlisObjectEnvelope first = data.reader.next();
      if (first == null || first.eventType() != InterlisEventType.START_TRANSFER) {
        throw new HopException("INTERLIS transfer does not start with a START_TRANSFER event");
      }

      List<String> modelNames = resolveModelNames();
      List<String> modelDirectories = resolveModelDirectories();
      InterlisProjectionService projectionService = new InterlisProjectionService();
      data.projection =
          projectionService.project(
              new InterlisModelRequest(file, modelNames, modelDirectories),
              resolve(meta.getClassName()),
              meta.projectionOptions(this));
      data.plan = data.projection.plan();
      // The XTF 2.4 reader needs the model to resolve topics; set it after the header was read.
      if (data.reader instanceof XtfTransferReader transferReader) {
        transferReader.setModel(data.projection.model().transferDescription());
      }
      data.mapper = new DefaultInterlisObjectToRowMapper();
      data.keepSourceObject = meta.isKeepSourceObject();
      data.outputRowMeta = new HopRowSchemaFactory().createRowMeta(data.plan);
      if (data.keepSourceObject) {
        data.outputRowMeta.addValueMeta(
            new ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject(
                meta.resolvedSourceObjectFieldName()));
      }

      data.buffering = data.plan.hasLinkResolvedRoles();
      if (data.buffering)
        data.basketBuffer =
            new ch.so.agi.hop.interlis.core.mapping.InterlisBasketProjectionBuffer<>(data.plan);

      if (isBasic()) {
        logBasic(
            "Reading INTERLIS class "
                + data.plan.root().scopedName()
                + " from "
                + file
                + " (models "
                + data.projection.modelNames()
                + (data.buffering ? ", association links resolved per basket" : "")
                + ")");
      }
      for (String warning : data.plan.warnings()) {
        logBasic("INTERLIS Input warning: " + warning);
      }
      data.initialized = true;
    } catch (HopException e) {
      closeReader();
      throw e;
    } catch (Exception e) {
      closeReader();
      throw new HopException("Failed to initialize INTERLIS Input: " + e.getMessage(), e);
    }
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }

  private void closeReader() {
    if (data.reader != null) {
      try {
        data.reader.close();
      } catch (Exception e) {
        if (isDebug()) {
          logDebug("Failed to close INTERLIS reader: " + e.getMessage());
        }
      } finally {
        data.reader = null;
      }
    }
  }

  @Override
  public void dispose() {
    clearBufferedRows();
    closeReader();
    super.dispose();
  }

  private void clearBufferedRows() {
    if (data.basketBuffer != null) data.basketBuffer.clear();
    data.basketBuffer = null;
    data.pendingOutput = null;
  }
}
