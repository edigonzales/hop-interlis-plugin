package ch.so.agi.hop.interlis.transforms.transferoutput;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.InterlisObjectOperation;
import ch.so.agi.hop.interlis.core.io.InterlisWriteException;
import ch.so.agi.hop.interlis.core.io.XtfTransferWriter;
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
 * INTERLIS Transfer Output: writes canonical envelope rows back to an XTF transfer.
 *
 * <p>Object mode: the transfer/basket events are derived from the OBJECT rows (baskets are opened
 * and closed on {@code _ili_bid} changes); event rows in the stream are an error. Event mode: the
 * explicit event sequence is written; the iox writer's own state machine rejects invalid event
 * orders. Object operations (INSERT/UPDATE/DELETE) are applied to the IOM object before writing.
 */
public class InterlisTransferOutput
    extends BaseTransform<InterlisTransferOutputMeta, InterlisTransferOutputData> {

  public InterlisTransferOutput(
      TransformMeta transformMeta,
      InterlisTransferOutputMeta meta,
      InterlisTransferOutputData data,
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
        logBasic("Finished writing INTERLIS transfer: objects written " + data.objectsWritten);
      }
      return false;
    }

    if (!data.initialized) {
      doInitialize();
    }

    try {
      InterlisObjectEnvelope envelope = InterlisEnvelopeRowLayout.fromRow(row);
      if (meta.isEventMode()) {
        writeEvent(envelope);
      } else {
        writeObjectMode(envelope);
      }
    } catch (HopException e) {
      closeWriter();
      throw e;
    } catch (Exception e) {
      closeWriter();
      throw new HopException("Failed to write INTERLIS transfer: " + e.getMessage(), e);
    }

    putRow(getInputRowMeta(), row);
    return true;
  }

  /** Object mode: derive transfer/basket events, reject explicit event rows. */
  private void writeObjectMode(InterlisObjectEnvelope envelope)
      throws HopException, InterlisWriteException {
    switch (envelope.eventType()) {
      case OBJECT -> {
        if (envelope.object() == null) {
          throw new HopException(
              "Envelope row of class <" + envelope.className() + "> carries no INTERLIS object");
        }
        String bid = envelope.basketId() == null ? "b1" : envelope.basketId();
        if (data.currentBid == null) {
          data.writer.startBasket(requiredTopic(envelope), bid);
          data.currentBid = bid;
        } else if (!data.currentBid.equals(bid)) {
          data.writer.endBasket();
          data.writer.startBasket(requiredTopic(envelope), bid);
          data.currentBid = bid;
        }
        writeObject(envelope);
      }
      case START_TRANSFER, START_BASKET, END_BASKET, END_TRANSFER ->
          throw new HopException(
              "Explicit " + envelope.eventType() + " event in object mode; enable event mode "
                  + "or feed only OBJECT rows into INTERLIS Transfer Output");
    }
  }

  /** Event mode: write the explicit event sequence. */
  private void writeEvent(InterlisObjectEnvelope envelope)
      throws HopException, InterlisWriteException {
    switch (envelope.eventType()) {
      case START_TRANSFER -> data.writer.startTransfer("hop-interlis-plugin");
      case START_BASKET -> {
        if (envelope.topicName() == null) {
          throw new HopException("START_BASKET event without a topic");
        }
        data.writer.startBasket(envelope.topicName(), envelope.basketId(), envelope.basket());
      }
      case OBJECT -> {
        if (envelope.object() == null) {
          throw new HopException(
              "Envelope row of class <" + envelope.className() + "> carries no INTERLIS object");
        }
        writeObject(envelope);
      }
      case END_BASKET -> data.writer.endBasket();
      case END_TRANSFER -> data.writer.endTransfer();
    }
  }

  private void writeObject(InterlisObjectEnvelope envelope)
      throws HopException, InterlisWriteException {
    IomObject object = envelope.object();
    if (envelope.operation() != InterlisObjectOperation.NONE) {
      object.setobjectoperation(envelope.operation().toIom());
    }
    data.writer.writeObject(object);
    data.objectsWritten++;
  }

  private String requiredTopic(InterlisObjectEnvelope envelope) throws HopException {
    if (envelope.topicName() != null) {
      return envelope.topicName();
    }
    if (envelope.className() == null) {
      throw new HopException("Envelope row has neither topic nor class name");
    }
    int separator = envelope.className().lastIndexOf('.');
    if (separator < 0) {
      throw new HopException("Cannot derive topic from class name <" + envelope.className() + ">");
    }
    return envelope.className().substring(0, separator);
  }

  private void doInitialize() throws HopException {
    ch.so.agi.hop.interlis.transforms.InterlisParallelCopies.rejectParallelCopies(
        getCopy(), getTransformName());
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
      List<String> modelNames = resolveModelNames();
      if (modelNames.isEmpty()) {
        throw new HopException(
            "Explicit model names are required (the envelope stream carries no header)");
      }
      ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
          new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
              .compile(
                  new ch.so.agi.hop.interlis.core.model.ModelSource(
                      List.of(), modelNames, resolveModelDirectories()),
                  ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
      data.writer =
          XtfTransferWriter.open(file, model.transferDescription(), modelNames);
      if (!meta.isEventMode()) {
        data.writer.startTransfer("hop-interlis-plugin");
      }
      if (isBasic()) {
        logBasic(
            "Writing INTERLIS transfer to " + file + " (event mode " + meta.isEventMode() + ")");
      }
      data.initialized = true;
    } catch (HopException e) {
      closeWriter();
      throw e;
    } catch (Exception e) {
      closeWriter();
      throw new HopException("Failed to initialize INTERLIS Transfer Output: " + e.getMessage(), e);
    }
  }

  private void finishWriting() throws HopException {
    if (data.writer == null) {
      return;
    }
    try {
      if (!meta.isEventMode()) {
        if (data.currentBid != null) {
          data.writer.endBasket();
        }
        data.writer.endTransfer();
      }
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
      } catch (InterlisWriteException e) {
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
