package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import java.util.*;

/** One basket's projected objects and association links; owned by one transform copy. */
public final class InterlisBasketProjectionBuffer<T> {
  public record Entry<T>(InterlisObjectEnvelope envelope, T context) {}

  public record Batch<T>(List<Entry<T>> rows, InterlisAssociationLinkLookup lookup) {}

  private final InterlisRowMappingPlan plan;
  private final Map<String, InterlisAssociationDescriptor> associations = new HashMap<>();
  private List<Entry<T>> rows = new ArrayList<>();
  private Map<String, IomObject> links = new HashMap<>();
  private String bid;
  private boolean started;

  public InterlisBasketProjectionBuffer(InterlisRowMappingPlan plan) {
    this.plan = plan;
    for (var association : plan.linkResolvedRoles().values())
      associations.put(association.scopedName(), association);
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
      for (var role : association.roles()) {
        var member = envelope.object().getattrobj(role.name(), 0);
        if (member != null && member.getobjectrefoid() != null)
          links.put(key(member.getobjectrefoid(), association.scopedName()), envelope.object());
      }
    if (plan.root().scopedName().equals(envelope.className()))
      rows.add(new Entry<>(envelope, context));
  }

  public Batch<T> drain() {
    var batchRows = List.copyOf(rows);
    var batchLinks = links;
    rows = new ArrayList<>();
    links = new HashMap<>();
    bid = null;
    started = false;
    var resolvedRoles = plan.linkResolvedRoles();
    return new Batch<>(
        batchRows,
        (tid, role) -> {
          var association = resolvedRoles.get(role);
          return association == null ? null : batchLinks.get(key(tid, association.scopedName()));
        });
  }

  /** Discard the current basket and its allocated capacity without emitting rows. */
  public void clear() {
    rows = new ArrayList<>();
    links = new HashMap<>();
    bid = null;
    started = false;
  }

  int bufferedRowCount() {
    return rows.size();
  }

  int bufferedLinkCount() {
    return links.size();
  }

  private static String key(String tid, String association) {
    return tid + "\u0000" + association;
  }
}
