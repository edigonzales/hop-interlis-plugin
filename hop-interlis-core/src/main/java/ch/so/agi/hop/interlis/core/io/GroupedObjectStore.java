package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.buffer.*;
import java.io.Serializable;
import java.util.Iterator;

/** Disk-backed basket grouping; all registries are charged to the same shared budget. */
public final class GroupedObjectStore implements AutoCloseable {
  private record Basket(String topic, long order) implements Serializable {}

  public record ObjectInBasket(String topic, String bid, IomObject object)
      implements Serializable {}

  private final SpillStore<Basket> baskets;
  private final SpillStore<ObjectInBasket> objects;

  public GroupedObjectStore(SpillOptions options) {
    baskets = new SpillStore<>(new JavaRecordCodec<>(), options.divided(2));
    objects = new SpillStore<>(new JavaRecordCodec<>(), options.divided(2));
  }

  public void add(String topic, String bid, IomObject object) throws InterlisWriteException {
    if (bid == null || bid.isBlank())
      throw new InterlisWriteException("Empty basket ID for " + topic);
    Basket basket = baskets.get(bid);
    if (basket == null) {
      basket = new Basket(topic, baskets.size());
      baskets.putIfAbsent(bid, basket);
    }
    if (!basket.topic().equals(topic))
      throw new InterlisWriteException(
          "Basket <" + bid + "> assigned to both " + basket.topic() + " and " + topic);
    objects.appendOrdered(basket.order(), new ObjectInBasket(topic, bid, object));
  }

  public Iterator<ObjectInBasket> iterator() {
    return objects.iterator(true);
  }

  public long size() {
    return objects.size();
  }

  public boolean spilled() {
    return objects.spilled() || baskets.spilled();
  }

  public void close() {
    try {
      objects.close();
    } finally {
      baskets.close();
    }
  }
}
