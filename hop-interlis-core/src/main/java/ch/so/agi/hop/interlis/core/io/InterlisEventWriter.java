package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import java.util.Objects;

/** Shared full-transfer event contract for Transfer Output and Update. */
public final class InterlisEventWriter {
  private final InterlisTransferWriter writer;
  private String bid, topic;
  private InterlisBasketMetadata basket;

  public InterlisEventWriter(InterlisTransferWriter writer) {
    this.writer = writer;
  }

  public void write(InterlisObjectEnvelope event) throws InterlisWriteException {
    switch (event.eventType()) {
      case START_TRANSFER -> writer.startTransfer(event.transferMetadata());
      case START_BASKET -> {
        if (event.topicName() == null)
          throw new InterlisWriteException("START_BASKET event without a topic");
        writer.startBasket(event.topicName(), event.basketId(), event.basket());
        bid = event.basketId();
        topic = event.topicName();
        basket = event.basket();
      }
      case OBJECT -> {
        if (event.object() == null)
          throw new InterlisWriteException(
              "Envelope carries no INTERLIS object: " + event.className());
        if ((event.basketId() != null && !Objects.equals(bid, event.basketId()))
            || (event.topicName() != null && !Objects.equals(topic, event.topicName()))
            || (event.basket() != null && !Objects.equals(basket, event.basket())))
          throw new InterlisWriteException(
              "Object <"
                  + event.objectId()
                  + "> has conflicting basket context: "
                  + event.basketId()
                  + " / "
                  + bid);
        IomObject object = new Iom_jObject(event.object());
        if (event.className() != null && !event.className().equals(object.getobjecttag()))
          throw new InterlisWriteException(
              "Envelope class contradicts object class: " + event.className());
        if (event.objectId() != null) object.setobjectoid(event.objectId());
        if (event.operation() == InterlisObjectOperation.DELETE)
          object = new Iom_jObject(object.getobjecttag(), object.getobjectoid());
        object.setobjectoperation(event.operation().toIom());
        writer.writeObject(object);
      }
      case END_BASKET -> {
        writer.endBasket();
        bid = null;
        topic = null;
        basket = null;
      }
      case END_TRANSFER -> writer.endTransfer();
      default -> throw new InterlisWriteException("Unsupported event: " + event.eventType());
    }
  }
}
