package ch.so.agi.hop.interlis.core.io;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.iox_j.utility.ReaderFactory;
import ch.interlis.iom_j.xtf.XtfStartTransferEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Streaming reader for INTERLIS XTF files, backed by iox-ili.
 *
 * <p>The reader emits the transfer event stream as {@link InterlisObjectEnvelope} instances:
 * {@code START_TRANSFER}, {@code START_BASKET}, {@code OBJECT}, {@code END_BASKET},
 * {@code END_TRANSFER}. The XTF header is used to detect the model names.
 */
public final class XtfTransferReader implements InterlisTransferReader {

  private static final String MODEL_HEADER_TAG = "iom04.metamodel.ModelEntry";
  private static final String MODEL_HEADER_NAME_ATTR = "model";

  private final Path file;
  private final TransferDescription transferDescription;
  private IoxReader reader;
  private String currentTopic;
  private String currentBasketId;
  private InterlisBasketMetadata currentBasketMetadata;
  private List<String> detectedModelNames;
  private boolean transferEnded;

  private XtfTransferReader(Path file, TransferDescription transferDescription) {
    this.file = file;
    this.transferDescription = transferDescription;
  }

  public static XtfTransferReader open(Path file) throws InterlisReadException {
    return open(file, null);
  }

  public static XtfTransferReader open(Path file, TransferDescription transferDescription)
      throws InterlisReadException {
    XtfTransferReader transferReader =
        new XtfTransferReader(file, transferDescription);
    try {
      transferReader.reader =
          new ReaderFactory().createReader(file.toFile(), null);
      if (transferReader.reader instanceof IoxIliReader ioxIliReader
          && transferDescription != null) {
        ioxIliReader.setModel(transferDescription);
      }
      return transferReader;
    } catch (Exception e) {
      throw new InterlisReadException(
          "Failed to open XTF file " + file + ": " + e.getMessage(), e);
    }
  }

  @Override
  public InterlisObjectEnvelope next() throws InterlisReadException {
    // The XTF 2.4 reader throws on reads past END_TRANSFER instead of returning null.
    if (transferEnded) {
      return null;
    }
    try {
      IoxEvent event = reader.read();
      if (event == null) {
        return null;
      }

      if (event instanceof XtfStartTransferEvent startTransferEvent) {
        detectedModelNames = detectModelNames(startTransferEvent);
        return new InterlisObjectEnvelope(
            InterlisEventType.START_TRANSFER,
            null,
            null,
            null,
            null,
            null,
            InterlisObjectOperation.NONE,
            null);
      }
      if (event instanceof StartTransferEvent) {
        return new InterlisObjectEnvelope(
            InterlisEventType.START_TRANSFER,
            null,
            null,
            null,
            null,
            null,
            InterlisObjectOperation.NONE,
            null);
      }
      if (event instanceof StartBasketEvent startBasketEvent) {
        currentTopic = startBasketEvent.getType();
        currentBasketId = startBasketEvent.getBid();
        currentBasketMetadata =
            InterlisBasketMetadata.fromIom(
                startBasketEvent.getConsistency(),
                startBasketEvent.getKind(),
                startBasketEvent.getStartstate(),
                startBasketEvent.getEndstate());
        return new InterlisObjectEnvelope(
            InterlisEventType.START_BASKET,
            modelNameOf(currentTopic),
            currentTopic,
            currentBasketId,
            null,
            null,
            InterlisObjectOperation.NONE,
            null,
            currentBasketMetadata);
      }
      if (event instanceof ObjectEvent objectEvent) {
        IomObject object = objectEvent.getIomObject();
        return new InterlisObjectEnvelope(
            InterlisEventType.OBJECT,
            modelNameOf(currentTopic),
            currentTopic,
            currentBasketId,
            object == null ? null : object.getobjecttag(),
            object == null ? null : object.getobjectoid(),
            object == null
                ? InterlisObjectOperation.NONE
                : InterlisObjectOperation.fromIom(object.getobjectoperation()),
            object,
            currentBasketMetadata);
      }
      if (event instanceof EndBasketEvent) {
        InterlisBasketMetadata basketMetadata = currentBasketMetadata;
        return new InterlisObjectEnvelope(
            InterlisEventType.END_BASKET,
            modelNameOf(currentTopic),
            currentTopic,
            currentBasketId,
            null,
            null,
            InterlisObjectOperation.NONE,
            null,
            basketMetadata);
      }
      if (event instanceof EndTransferEvent) {
        transferEnded = true;
        return new InterlisObjectEnvelope(
            InterlisEventType.END_TRANSFER,
            null,
            null,
            null,
            null,
            null,
            InterlisObjectOperation.NONE,
            null);
      }
      throw new InterlisReadException(
          "Unexpected iox event while reading XTF " + file + ": " + event.getClass().getName());
    } catch (IoxException e) {
      throw new InterlisReadException("Failed to read XTF file " + file + ": " + e.getMessage(), e);
    }
  }

  /** Model names declared in the XTF header; empty until {@code START_TRANSFER} was read. */
  public List<String> detectedModelNames() {
    return detectedModelNames == null ? List.of() : List.copyOf(detectedModelNames);
  }

  /**
   * Sets the model description used to interpret the transfer. Needed by the XTF 2.4 reader to
   * resolve topics; safe to call after the transfer header was read.
   */
  public void setModel(TransferDescription transferDescription) {
    if (reader instanceof IoxIliReader ioxIliReader && transferDescription != null) {
      ioxIliReader.setModel(transferDescription);
    }
  }

  private static List<String> detectModelNames(XtfStartTransferEvent event) {
    return detectModelNames(event.getHeaderObjects());
  }

  /**
   * Detects the model names declared in an XTF transfer header.
   *
   * @param headerObjects the header objects of a {@code XtfStartTransferEvent}
   * @return model names in header order
   */
  public static List<String> detectModelNames(Map<String, IomObject> headerObjects) {
    if (headerObjects == null) {
      return List.of();
    }
    List<String> modelNames = new ArrayList<>();
    for (IomObject object : headerObjects.values()) {
      if (MODEL_HEADER_TAG.equals(object.getobjecttag())) {
        String name = object.getattrvalue(MODEL_HEADER_NAME_ATTR);
        if (name != null && !name.isBlank() && !modelNames.contains(name)) {
          modelNames.add(name);
        }
      }
    }
    return modelNames;
  }

  private static String modelNameOf(String topicName) {
    if (topicName == null || topicName.isBlank()) {
      return null;
    }
    int dot = topicName.indexOf('.');
    return dot < 0 ? topicName : topicName.substring(0, dot);
  }

  @Override
  public TransferDescription transferDescription() {
    return transferDescription;
  }

  @Override
  public void close() throws InterlisReadException {
    if (reader == null) {
      return;
    }
    try {
      reader.close();
    } catch (IoxException e) {
      throw new InterlisReadException(
          "Failed to close XTF reader for " + file + ": " + e.getMessage(), e);
    } finally {
      reader = null;
    }
  }
}
