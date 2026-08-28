package ch.so.agi.hop.interlis.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a transferable INTERLIS association (link objects).
 *
 * <p>Associations are projected onto their own row type: one {@code <role>_ref} field per role
 * (optionally with {@code <role>_ref_bid} and {@code <role>_order_pos}), plus the association
 * attributes as regular fields.
 *
 * @param name association name
 * @param scopedName qualified name, e.g. {@code Model.Topic.Association}
 * @param topicScopedName qualified topic name
 * @param identifiable whether the association declares its own OID (rare; XTF link objects of
 *     plain associations carry no TID)
 * @param roles roles in model order
 * @param attributes association attributes in model order
 * @param modelKind kind of the owning INTERLIS model
 */
public record InterlisAssociationDescriptor(
    String name,
    String scopedName,
    String topicScopedName,
    boolean identifiable,
    List<InterlisRoleDescriptor> roles,
    List<InterlisAttributeDescriptor> attributes,
    InterlisModelKind modelKind)
    implements InterlisPlanRoot {

  /** Backwards-compatible constructor for descriptors created outside the extractor. */
  public InterlisAssociationDescriptor(
      String name,
      String scopedName,
      String topicScopedName,
      boolean identifiable,
      List<InterlisRoleDescriptor> roles,
      List<InterlisAttributeDescriptor> attributes) {
    this(
        name,
        scopedName,
        topicScopedName,
        identifiable,
        roles,
        attributes,
        InterlisModelKind.DATA);
  }

  public InterlisAssociationDescriptor {
    roles = roles == null ? List.of() : List.copyOf(roles);
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
    modelKind = modelKind == null ? InterlisModelKind.OTHER : modelKind;
  }

  public boolean isSelectable() {
    return modelKind.isSelectable();
  }

  @Override
  public List<InterlisPropertyDescriptor> effectiveProperties() {
    List<InterlisPropertyDescriptor> properties = new ArrayList<>(roles);
    properties.addAll(attributes);
    return List.copyOf(properties);
  }

  public InterlisRoleDescriptor role(String name) {
    return roles.stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
  }
}
