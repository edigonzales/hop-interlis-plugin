package ch.so.agi.hop.interlis.core.mapping;

import java.util.Set;

/**
 * Options controlling the typed row projection of an INTERLIS class.
 *
 * @param includeTid emit {@code _ili_tid}
 * @param includeBid emit {@code _ili_bid}
 * @param includeClassName emit {@code _ili_class}
 * @param includeTopicName emit {@code _ili_topic}
 * @param includeOperation emit {@code _ili_operation}
 * @param includeInheritedProperties include properties contributed by base classes
 * @param structureSeparator separator for flattened structure field names, default {@code _}
 * @param defaultSrid SRID assigned to mapped geometries; {@code null} keeps SRID 0
 * @param selectedPropertyPaths dotted paths of the properties to project. Null selects all
 *     supported properties and an empty set selects no properties.
 * @param includeRoleRefBid emit {@code <role>_ref_bid} for all projected roles. External roles
 *     always have that field.
 * @param flattenAssociationAttributes flatten attributes of uniquely embeddable attributed
 *     associations onto the class rows ({@code <role>_<attribute>})
 */
public record ProjectionOptions(
    boolean includeTid,
    boolean includeBid,
    boolean includeClassName,
    boolean includeTopicName,
    boolean includeOperation,
    boolean includeInheritedProperties,
    String structureSeparator,
    Integer defaultSrid,
    Set<String> selectedPropertyPaths,
    boolean includeRoleRefBid,
    boolean flattenAssociationAttributes) {

  public static ProjectionOptions selected(Set<String> paths) {
    return new ProjectionOptions(
        true, true, false, false, false, true, "_", null, paths, true, true);
  }

  public static ProjectionOptions defaults() {
    return new ProjectionOptions(
        true, true, false, false, false, true, "_", null, null, false, true);
  }

  public ProjectionOptions {
    structureSeparator =
        structureSeparator == null || structureSeparator.isEmpty() ? "_" : structureSeparator;
    selectedPropertyPaths =
        selectedPropertyPaths == null ? null : Set.copyOf(selectedPropertyPaths);
  }

  /** Whether a path can contain a selected descendant. Does not select sibling leaves. */
  public boolean shouldTraverse(String dottedPath) {
    return isPropertySelected(dottedPath)
        || selectedPropertyPaths.stream()
            .anyMatch(selected -> selected.startsWith(dottedPath + "."));
  }

  public boolean isPropertySelected(String dottedPath) {
    if (selectedPropertyPaths == null) {
      return true;
    }
    // Selecting a structure selects all flattened fields below it.
    return selectedPropertyPaths.stream()
        .anyMatch(selected -> dottedPath.equals(selected) || dottedPath.startsWith(selected + "."));
  }
}
