package ch.so.agi.hop.interlis.transforms.transferoutput;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
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
      InterlisObjectEnvelope envelope = data.envelopeBindings.fromRow(row);
      if (meta.isEventMode()) {
        writeEvent(envelope);
      } else {
        writeObjectMode(envelope);
      }
    } catch (HopException e) {
      closeAfterFailure(e);
      throw e;
    } catch (Exception e) {
      closeAfterFailure(e);
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
          if (!data.completedBids.add(bid))
            throw new HopException("Basket <" + bid + "> reappears after it was completed");
          data.currentTopic = requiredTopic(envelope);
          data.currentBasketMetadata = envelope.basket();
          data.writer.startBasket(data.currentTopic, bid, envelope.basket());
          data.currentBid = bid;
        } else if (!data.currentBid.equals(bid)) {
          data.writer.endBasket();
          if (!data.completedBids.add(bid))
            throw new HopException("Basket <" + bid + "> reappears after it was completed");
          data.currentTopic = requiredTopic(envelope);
          data.currentBasketMetadata = envelope.basket();
          data.writer.startBasket(data.currentTopic, bid, envelope.basket());
          data.currentBid = bid;
        }
        if (!java.util.Objects.equals(data.currentTopic, requiredTopic(envelope))
            || !java.util.Objects.equals(data.currentBasketMetadata, envelope.basket()))
          throw new HopException("Conflicting topic or metadata in basket <" + bid + ">");
        writeObject(envelope);
      }
      case START_TRANSFER, START_BASKET, END_BASKET, END_TRANSFER ->
          throw new HopException(
              "Explicit "
                  + envelope.eventType()
                  + " event in object mode; enable event mode "
                  + "or feed only OBJECT rows into INTERLIS Transfer Output");
    }
  }

  /** Event mode: write the explicit event sequence. */
  private void writeEvent(InterlisObjectEnvelope envelope)
      throws HopException, InterlisWriteException {
    switch (envelope.eventType()) {
      case START_TRANSFER -> data.writer.startTransfer(envelope.transferMetadata());
      case START_BASKET -> {
        if (envelope.topicName() == null) {
          throw new HopException("START_BASKET event without a topic");
        }
        data.writer.startBasket(envelope.topicName(), envelope.basketId(), envelope.basket());
        data.currentBid = envelope.basketId();
        data.currentTopic = envelope.topicName();
        data.currentBasketMetadata = envelope.basket();
      }
      case OBJECT -> {
        if (envelope.object() == null) {
          throw new HopException(
              "Envelope row of class <" + envelope.className() + "> carries no INTERLIS object");
        }
        if ((envelope.basketId() != null
                && !java.util.Objects.equals(data.currentBid, envelope.basketId()))
            || (envelope.topicName() != null
                && !java.util.Objects.equals(data.currentTopic, envelope.topicName()))
            || (envelope.basket() != null
                && !java.util.Objects.equals(data.currentBasketMetadata, envelope.basket())))
          throw new HopException(
              "Object <"
                  + envelope.objectId()
                  + "> has conflicting basket context: row basket <"
                  + envelope.basketId()
                  + ">, open basket <"
                  + data.currentBid
                  + ">");
        writeObject(envelope);
      }
      case END_BASKET -> {
        data.writer.endBasket();
        data.currentBid = null;
        data.currentTopic = null;
        data.currentBasketMetadata = null;
      }
      case END_TRANSFER -> data.writer.endTransfer();
    }
  }

  private void writeObject(InterlisObjectEnvelope envelope)
      throws HopException, InterlisWriteException {
    IomObject object = new ch.interlis.iom_j.Iom_jObject(envelope.object());
    if (envelope.className() != null && !envelope.className().equals(object.getobjecttag()))
      throw new HopException("Envelope class contradicts object class: " + envelope.className());
    if (envelope.objectId() != null) object.setobjectoid(envelope.objectId());
    if (envelope.operation() == InterlisObjectOperation.DELETE)
      object = new ch.interlis.iom_j.Iom_jObject(object.getobjecttag(), object.getobjectoid());
    object.setobjectoperation(envelope.operation().toIom());
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
    ch.so.agi.hop.interlis.transforms.InterlisParallelCopies.requireSingleCopy(
        getTransformMeta(), this, "file processing or enumeration emission requires one copy");
    InterlisRuntimeSupport.initialize();
    data.envelopeBindings =
        ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings.bind(
            getInputRowMeta(), InterlisEnvelopeRowLayout.OBJECT, meta.isEventMode());

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
        throw new HopException("Explicit model names are required to compile the writer schema");
      }
      ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
          new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
              .compile(
                  new ch.so.agi.hop.interlis.core.model.ModelSource(
                      List.of(), modelNames, resolveModelDirectories()),
                  ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
      data.writer = XtfTransferWriter.open(file, model.transferDescription(), modelNames);
      if (!meta.isEventMode()) {
        data.writer.startTransfer("hop-interlis-plugin");
      }
      if (isBasic()) {
        logBasic(
            "Writing INTERLIS transfer to " + file + " (event mode " + meta.isEventMode() + ")");
      }
      data.initialized = true;
    } catch (HopException e) {
      closeAfterFailure(e);
      throw e;
    } catch (Exception e) {
      closeAfterFailure(e);
      throw new HopException("Failed to initialize INTERLIS Transfer Output: " + e.getMessage(), e);
    }
  }

  private void finishWriting() throws HopException {
    if (data.writer == null) {
      if (meta.isEventMode())
        throw new HopException("Empty event stream: START_TRANSFER and END_TRANSFER required");
      return;
    }
    try {
      if (!meta.isEventMode()) {
        if (data.currentBid != null) {
          data.writer.endBasket();
        }
        data.writer.endTransfer();
      } else {
        data.writer.requireComplete();
      }
      closeWriterChecked();
    } catch (Exception e) {
      closeAfterFailure(e);
      throw new HopException("Failed to finish INTERLIS transfer: " + e.getMessage(), e);
    }
  }

  private void closeWriterChecked() throws InterlisWriteException {
    var writer = data.writer;
    data.writer = null;
    if (writer != null) writer.close();
  }

  private void closeAfterFailure(Exception failure) {
    try {
      closeWriterChecked();
    } catch (InterlisWriteException close) {
      failure.addSuppressed(close);
    }
  }

  private void closeWriter() {
    try {
      closeWriterChecked();
    } catch (InterlisWriteException e) {
      logError("Failed to close INTERLIS writer", e);
      setErrors(getErrors() + 1);
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
