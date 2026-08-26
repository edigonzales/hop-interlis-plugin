package ch.so.agi.hop.interlis.core.io;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.iom_j.xtf.XtfModel;
import ch.interlis.iom_j.xtf.XtfWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming INTERLIS XTF writer backed by iox-ili.
 *
 * <p>The writer receives the model description so the XTF header declares the models; the model
 * names/versions are taken from the compiled {@link TransferDescription}, filtered to the
 * requested model names in header order.
 */
public final class XtfTransferWriter implements InterlisTransferWriter {

  private final Path file;
  private final XtfWriter writer;
  private boolean transferStarted;
  private boolean basketOpen;
  private boolean closed;

  private XtfTransferWriter(Path file, XtfWriter writer) {
    this.file = file;
    this.writer = writer;
  }

  public static XtfTransferWriter open(
      Path file, TransferDescription transferDescription, List<String> modelNames)
      throws InterlisWriteException {
    try {
      XtfWriter writer = new XtfWriter(file.toFile(), transferDescription);
      writer.setModels(extractModels(transferDescription, modelNames));
      return new XtfTransferWriter(file, writer);
    } catch (Exception e) {
      throw new InterlisWriteException(
          "Failed to open XTF writer for " + file + ": " + e.getMessage(), e);
    }
  }

  private static XtfModel[] extractModels(
      TransferDescription transferDescription, List<String> modelNames) {
    List<XtfModel> models = new ArrayList<>();
    for (String modelName : modelNames == null ? List.<String>of() : modelNames) {
      for (java.util.Iterator<ch.interlis.ili2c.metamodel.Model> it =
              transferDescription.iterator();
          it.hasNext(); ) {
        ch.interlis.ili2c.metamodel.Model model = it.next();
        if (modelName.equals(model.getName())) {
          String version = model.getModelVersion();
          String issuer = model.getIssuer();
          models.add(
              new XtfModel(
                  model.getName(),
                  issuer == null ? "" : issuer,
                  version == null ? "" : version));
        }
      }
    }
    return models.toArray(new XtfModel[0]);
  }

  @Override
  public void startTransfer(String sender) throws InterlisWriteException {
    if (transferStarted) {
      throw new InterlisWriteException("Transfer was already started for " + file);
    }
    StartTransferEvent event = new StartTransferEvent(sender);
    write(event);
    transferStarted = true;
  }

  @Override
  public void startBasket(String topicScopedName, String bid) throws InterlisWriteException {
    ensureTransferStarted();
    if (basketOpen) {
      throw new InterlisWriteException("A basket is already open for " + file);
    }
    write(new StartBasketEvent(topicScopedName, bid));
    basketOpen = true;
  }

  @Override
  public void startBasket(String topicScopedName, String bid, InterlisBasketMetadata metadata)
      throws InterlisWriteException {
    ensureTransferStarted();
    if (basketOpen) {
      throw new InterlisWriteException("A basket is already open for " + file);
    }
    if (metadata != null) {
      boolean nonFullKind = metadata.kind() != null && !"FULL".equals(metadata.kind());
      // The iox-ili 2.4 writer writes startstate/endstate without null guards for non-FULL
      // baskets; fail with a clear message instead of an NPE.
      if (nonFullKind && (metadata.endState() == null
          || ("UPDATE".equals(metadata.kind()) && metadata.startState() == null))) {
        throw new InterlisWriteException(
            "XTF 2.4 baskets with kind "
                + metadata.kind()
                + " require an end state"
                + ("UPDATE".equals(metadata.kind()) ? " and a start state" : "")
                + "; cannot write basket <" + bid + "> of " + topicScopedName);
      }
    }
    StartBasketEvent event = new StartBasketEvent(topicScopedName, bid);
    if (metadata != null) {
      if (metadata.consistency() != null) {
        event.setConsistency(metadata.consistencyIom());
      }
      if (metadata.kind() != null) {
        event.setKind(metadata.kindIom());
      }
      if (metadata.startState() != null) {
        event.setStartstate(metadata.startState());
      }
      if (metadata.endState() != null) {
        event.setEndstate(metadata.endState());
      }
    }
    write(event);
    basketOpen = true;
  }

  @Override
  public void writeObject(IomObject object) throws InterlisWriteException {
    ensureBasketOpen();
    write(new ObjectEvent(object));
  }

  @Override
  public void endBasket() throws InterlisWriteException {
    ensureBasketOpen();
    write(new EndBasketEvent());
    basketOpen = false;
  }

  @Override
  public void endTransfer() throws InterlisWriteException {
    ensureTransferStarted();
    if (basketOpen) {
      throw new InterlisWriteException("Cannot end transfer while a basket is open for " + file);
    }
    write(new EndTransferEvent());
    transferStarted = false;
  }

  private void write(ch.interlis.iox.IoxEvent event) throws InterlisWriteException {
    try {
      writer.write(event);
    } catch (IoxException e) {
      throw new InterlisWriteException(
          "Failed to write XTF event to " + file + ": " + e.getMessage(), e);
    }
  }

  private void ensureTransferStarted() throws InterlisWriteException {
    if (!transferStarted) {
      throw new InterlisWriteException("Transfer was not started for " + file);
    }
  }

  private void ensureBasketOpen() throws InterlisWriteException {
    ensureTransferStarted();
    if (!basketOpen) {
      throw new InterlisWriteException("No basket is open for " + file);
    }
  }

  @Override
  public void close() throws InterlisWriteException {
    if (closed) {
      return;
    }
    try {
      writer.flush();
      writer.close();
    } catch (IoxException e) {
      throw new InterlisWriteException(
          "Failed to close XTF writer for " + file + ": " + e.getMessage(), e);
    } finally {
      closed = true;
    }
  }
}
