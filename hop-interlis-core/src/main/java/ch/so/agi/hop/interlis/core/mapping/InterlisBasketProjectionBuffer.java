package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.buffer.*;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import java.util.*;

/** One basket, with both payloads and lookup indexes bounded by a spill budget. */
public final class InterlisBasketProjectionBuffer<T> {
  public record Entry<T>(InterlisObjectEnvelope envelope, T context)
      implements java.io.Serializable {}

  public final class Batch implements AutoCloseable {
    private final SpillStore<Entry<T>> batchRows;
    private final SpillStore<IomObject> batchLinks;
    private boolean closed;

    private Batch(SpillStore<Entry<T>> rows, SpillStore<IomObject> links) {
      batchRows = rows;
      batchLinks = links;
    }

    public List<Entry<T>> rows() {
      return new AbstractList<>() {
        public int size() {
          return Math.toIntExact(batchRows.size());
        }

        public Iterator<Entry<T>> iterator() {
          return batchRows.iterator(false);
        }

        public Entry<T> get(int i) {
          Objects.checkIndex(i, size());
          var it = iterator();
          while (i-- > 0) it.next();
          return it.next();
        }
      };
    }

    public InterlisAssociationLinkLookup lookup() {
      return (tid, role) -> {
        var association = plan.linkResolvedRoles().get(role);
        return association == null
            ? null
            : batchLinks.get(key(tid, association.scopedName(), role));
      };
    }

    public long peakDiskBytes() {
      return batchRows.peakDiskBytes() + batchLinks.peakDiskBytes();
    }

    public long peakMemoryBytes() {
      return batchRows.peakMemoryBytes() + batchLinks.peakMemoryBytes();
    }

    public void close() {
      if (closed) return;
      closed = true;
      try {
        batchRows.close();
      } finally {
        batchLinks.close();
        batches.remove(this);
      }
    }
  }

  private final InterlisRowMappingPlan plan;
  private final RecordCodec<Entry<T>> codec;
  private final SpillOptions options;
  private final Map<String, InterlisAssociationDescriptor> associations = new HashMap<>();
  private final Set<Batch> batches = new HashSet<>();
  private SpillStore<Entry<T>> rows;
  private SpillStore<IomObject> links;
  private String bid;
  private boolean started;

  public InterlisBasketProjectionBuffer(InterlisRowMappingPlan plan) {
    this(plan, new JavaRecordCodec<>(), SpillOptions.defaults());
  }

  public InterlisBasketProjectionBuffer(
      InterlisRowMappingPlan plan, RecordCodec<Entry<T>> codec, SpillOptions options) {
    this.plan = plan;
    this.codec = codec;
    this.options = options;
    for (var association : plan.linkResolvedRoles().values())
      associations.put(association.scopedName(), association);
    reset();
  }

  private void reset() {
    rows = new SpillStore<>(codec, options.divided(2));
    links = new SpillStore<>(new JavaRecordCodec<>(), options.divided(2));
    bid = null;
    started = false;
  }

  public boolean startsNewBasket(InterlisObjectEnvelope envelope) {
    return started && !Objects.equals(bid, envelope.basketId());
  }

  public void add(InterlisObjectEnvelope envelope, T context) {
    if (startsNewBasket(envelope))
      throw new IllegalStateException("Drain previous basket before adding " + envelope.basketId());
    started = true;
    bid = envelope.basketId();
    var association = associations.get(envelope.className());
    if (association != null)
      for (var projected : plan.linkResolvedRoles().entrySet()) {
        if (!projected.getValue().scopedName().equals(association.scopedName())) continue;
        // Index the owner end for this projected role. The other end can legitimately
        // be shared by multiple objects; self-associations also need role-specific keys.
        var ownerRole =
            association.roles().stream()
                .filter(role -> !role.name().equals(projected.getKey()))
                .findFirst()
                .orElseThrow();
        var member = envelope.object().getattrobj(ownerRole.name(), 0);
        if (member != null && member.getobjectrefoid() != null) {
          String key = key(member.getobjectrefoid(), association.scopedName(), projected.getKey());
          IomObject existing = links.get(key);
          if (existing != null && !existing.toString().equals(envelope.object().toString()))
            throw new IllegalStateException(
                "Ambiguous association link for "
                    + association.scopedName()
                    + ", TID "
                    + member.getobjectrefoid()
                    + ", basket "
                    + bid);
          links.putIfAbsent(key, envelope.object());
        }
      }
    if (plan.root().scopedName().equals(envelope.className()))
      rows.append(new Entry<>(envelope, context));
  }

  public Batch drain() {
    var batch = new Batch(rows, links);
    batches.add(batch);
    reset();
    return batch;
  }

  public void clear() {
    RuntimeException failure = null;
    var resources = new ArrayList<AutoCloseable>();
    resources.add(rows);
    resources.add(links);
    resources.addAll(batches);
    for (var resource : resources)
      try {
        resource.close();
      } catch (Exception e) {
        var error = e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
        if (failure == null) failure = error;
        else failure.addSuppressed(error);
      }
    reset();
    if (failure != null) throw failure;
  }

  int bufferedRowCount() {
    return Math.toIntExact(rows.size());
  }

  int bufferedLinkCount() {
    return Math.toIntExact(links.size());
  }

  private static String key(String tid, String association, String role) {
    return tid + "\u0000" + association + "\u0000" + role;
  }
}
