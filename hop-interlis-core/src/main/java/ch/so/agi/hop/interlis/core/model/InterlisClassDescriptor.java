package ch.so.agi.hop.interlis.core.model;

import java.util.List;

/**
 * Describes a transferable INTERLIS class.
 *
 * @param name class name
 * @param scopedName qualified name, e.g. {@code Model.Topic.Class}
 * @param topicScopedName qualified topic name, e.g. {@code Model.Topic}
 * @param isAbstract whether the class is abstract
 * @param declaredProperties properties declared on this class in model order
 * @param effectiveProperties all properties including inherited ones, in stable model order
 */
public record InterlisClassDescriptor(
    String name,
    String scopedName,
    String topicScopedName,
    boolean isAbstract,
    List<InterlisPropertyDescriptor> declaredProperties,
    List<InterlisPropertyDescriptor> effectiveProperties) {

  public InterlisClassDescriptor {
    declaredProperties =
        declaredProperties == null ? List.of() : List.copyOf(declaredProperties);
    effectiveProperties =
        effectiveProperties == null ? List.of() : List.copyOf(effectiveProperties);
  }

  /** All effective attributes in model order. */
  public List<InterlisAttributeDescriptor> attributes() {
    return effectiveProperties.stream()
        .filter(InterlisAttributeDescriptor.class::isInstance)
        .map(InterlisAttributeDescriptor.class::cast)
        .toList();
  }

  /** All effective roles in model order. */
  public List<InterlisRoleDescriptor> roles() {
    return effectiveProperties.stream()
        .filter(InterlisRoleDescriptor.class::isInstance)
        .map(InterlisRoleDescriptor.class::cast)
        .toList();
  }

  /** All effective geometry attributes in model order. */
  public List<InterlisAttributeDescriptor> geometryAttributes() {
    return attributes().stream()
        .filter(a -> a.kind() == InterlisAttributeKind.GEOMETRY)
        .toList();
  }
}
