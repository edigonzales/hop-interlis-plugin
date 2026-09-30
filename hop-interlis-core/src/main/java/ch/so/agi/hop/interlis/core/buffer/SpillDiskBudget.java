package ch.so.agi.hop.interlis.core.buffer;

import java.util.IdentityHashMap;
import java.util.Map;

/** One aggregate disk cap shared by all stores owned by a transform. */
final class SpillDiskBudget {
  private final long limit;
  private final Map<Object, Long> bytes = new IdentityHashMap<>();

  SpillDiskBudget(long limit) {
    this.limit = limit;
  }

  synchronized void measure(Object store, long size, String directory) {
    bytes.put(store, size);
    long total = bytes.values().stream().mapToLong(Long::longValue).sum();
    if (limit > 0 && total > limit)
      throw new IllegalStateException(
          "INTERLIS spill disk limit exceeded in "
              + directory
              + " (combined "
              + total
              + " bytes, limit "
              + limit
              + ")");
  }

  synchronized void release(Object store) {
    bytes.remove(store);
  }
}
