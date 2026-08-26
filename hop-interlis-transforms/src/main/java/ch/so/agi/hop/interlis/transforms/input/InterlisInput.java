package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Input: reads one INTERLIS class from an XTF file and emits typed Hop rows.
 *
 * <p>The transform streams the transfer file: per {@code processRow()} call at most one row is
 * emitted; objects of other classes are skipped.
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
      InterlisObjectEnvelope envelope;
      try {
        envelope = data.reader.next();
      } catch (Exception e) {
        throw new HopException("Failed to read INTERLIS transfer: " + e.getMessage(), e);
      }
      if (envelope == null) {
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
        if (envelope.className() != null
            && envelope.className().equals(data.plan.classDescriptor().scopedName())) {
          Object[] row;
          try {
            row = data.mapper.map(envelope, data.plan);
          } catch (Exception e) {
            throw new HopException(e.getMessage(), e);
          }
          data.emittedObjects++;
          putRow(data.outputRowMeta, row);
          if (checkFeedback(getLinesWritten()) && isBasic()) {
            logBasic(
                "Read " + data.emittedObjects + " objects of class " + meta.getClassName());
          }
          return true;
        }
      }
      // Other events and objects of other classes are skipped in the typed projection.
    }
  }

  private void doInitialize() throws HopException {
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
      data.mapper = new DefaultInterlisObjectToRowMapper();
      data.outputRowMeta = new HopRowSchemaFactory().createRowMeta(data.plan);

      if (isBasic()) {
        logBasic(
            "Reading INTERLIS class "
                + data.plan.classDescriptor().scopedName()
                + " from "
                + file
                + " (models "
                + data.projection.modelNames()
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
      throw new HopException(
          "Failed to initialize INTERLIS Input: " + e.getMessage(), e);
    }
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    if (resolved.isBlank() || InterlisInputMeta.MODELS_FROM_DATA.equals(resolved.trim())) {
      return List.of();
    }
    return Arrays.stream(resolved.split(","))
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .toList();
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(";"))
        .map(String::trim)
        .filter(d -> !d.isEmpty())
        .toList();
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
    closeReader();
    super.dispose();
  }
}
