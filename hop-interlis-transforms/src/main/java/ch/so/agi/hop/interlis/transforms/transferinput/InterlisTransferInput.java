package ch.so.agi.hop.interlis.transforms.transferinput;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.XtfTransferReader;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisParallelCopies;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Transfer Input: streams the complete XTF event stream as canonical envelope rows.
 *
 * <p>The row schema is constant (see {@link InterlisEnvelopeRowLayout}), so transfers with any
 * number of classes fit into one Hop stream. In OBJECTS mode only object rows are emitted (with
 * transfer/basket context in the fields); in EVENTS mode the exact event sequence is reproduced.
 */
public class InterlisTransferInput
    extends BaseTransform<InterlisTransferInputMeta, InterlisTransferInputData> {

  public InterlisTransferInput(
      TransformMeta transformMeta,
      InterlisTransferInputMeta meta,
      InterlisTransferInputData data,
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
            "Finished reading INTERLIS transfer: events read "
                + data.eventsRead
                + ", rows emitted "
                + data.rowsEmitted);
      }
      return false;
    }

    data.eventsRead++;
    if (meta.resolvedMode() == TransferInputMode.OBJECTS
        && envelope.eventType() != InterlisEventType.OBJECT) {
      // Skip transfer/basket events; their context is carried in the object rows' fields.
      return processRow();
    }

    Object[] row = InterlisEnvelopeRowLayout.toRow(envelope);
    data.rowsEmitted++;
    putRow(data.outputRowMeta, row);
    return true;
  }

  private void doInitialize() throws HopException {
    InterlisParallelCopies.rejectParallelCopies(getCopy(), getTransformName());
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
      data.eventsRead++;
      if (meta.resolvedMode() == TransferInputMode.EVENTS) {
        data.outputRowMeta = InterlisEnvelopeSchemaFactory.createRowMeta();
        putRow(data.outputRowMeta, InterlisEnvelopeRowLayout.toRow(first));
        data.rowsEmitted++;
      } else {
        data.outputRowMeta = InterlisEnvelopeSchemaFactory.createRowMeta();
      }

      // The XTF 2.4 reader needs the model to resolve topics; detect models from the
      // transfer header when configured with %DATA and resolve %XTF_DIR like INTERLIS Input.
      List<String> modelNames = resolveModelNames();
      if (modelNames.isEmpty() && data.reader instanceof XtfTransferReader transferReader) {
        modelNames = transferReader.detectedModelNames();
      }
      if (!modelNames.isEmpty()) {
        List<String> modelDirectories = new java.util.ArrayList<>(resolveModelDirectories());
        if (modelDirectories.contains("%XTF_DIR")) {
          modelDirectories.replaceAll(
              d -> "%XTF_DIR".equals(d) ? file.toAbsolutePath().getParent().toString() : d);
        }
        ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
            new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
                .compile(
                    new ch.so.agi.hop.interlis.core.model.ModelSource(
                        List.of(), modelNames, modelDirectories),
                    ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
        if (data.reader instanceof XtfTransferReader transferReader) {
          transferReader.setModel(model.transferDescription());
        }
      }

      if (isBasic()) {
        logBasic(
            "Reading INTERLIS transfer from " + file + " (mode " + meta.resolvedMode() + ")");
      }
      data.initialized = true;
    } catch (HopException e) {
      closeReader();
      throw e;
    } catch (Exception e) {
      closeReader();
      throw new HopException("Failed to initialize INTERLIS Transfer Input: " + e.getMessage(), e);
    }
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    if (resolved.isBlank()
        || ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA.equals(
            resolved.trim())) {
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
