package ch.so.agi.hop.interlis.core.model;

import java.util.List;
import java.util.Optional;

/**
 * Immutable, UI-friendly view of the transfer-relevant elements of a compiled INTERLIS model.
 *
 * <p>This descriptor layer exists because {@code TransferDescription} is too raw to serve as a
 * mapping and UI API. Descriptors are built once per compiled model and treated as read-only.
 *
 * @param classes transferable classes
 * @param structures STRUCTURE definitions
 * @param associations transferable associations (link objects)
 */
public record InterlisSchemaDescriptor(
    List<InterlisClassDescriptor> classes,
    List<InterlisStructureDescriptor> structures,
    List<InterlisAssociationDescriptor> associations) {

  public InterlisSchemaDescriptor {
    classes = classes == null ? List.of() : List.copyOf(classes);
    structures = structures == null ? List.of() : List.copyOf(structures);
    associations = associations == null ? List.of() : List.copyOf(associations);
  }

  /** Backwards-compatible constructor for callers without associations. */
  public InterlisSchemaDescriptor(
      List<InterlisClassDescriptor> classes, List<InterlisStructureDescriptor> structures) {
    this(classes, structures, List.of());
  }

  public Optional<InterlisClassDescriptor> findClass(String scopedName) {
    return classes.stream().filter(c -> c.scopedName().equals(scopedName)).findFirst();
  }

  public Optional<InterlisStructureDescriptor> findStructure(String scopedName) {
    return structures.stream().filter(s -> s.scopedName().equals(scopedName)).findFirst();
  }

  public Optional<InterlisAssociationDescriptor> findAssociation(String scopedName) {
    return associations.stream()
        .filter(a -> a.scopedName().equals(scopedName))
        .findFirst();
  }

  /** Classes suitable for user-facing class selectors. */
  public List<InterlisClassDescriptor> selectableClasses() {
    return classes.stream().filter(InterlisClassDescriptor::isSelectable).toList();
  }

  /** Associations suitable for user-facing class selectors. */
  public List<InterlisAssociationDescriptor> selectableAssociations() {
    return associations.stream().filter(InterlisAssociationDescriptor::isSelectable).toList();
  }

  /** Finds a class or an association by qualified name. */
  public Optional<InterlisPlanRoot> findPlanRoot(String scopedName) {
    Optional<InterlisPlanRoot> root = findClass(scopedName).map(InterlisPlanRoot.class::cast);
    if (root.isPresent()) {
      return root;
    }
    return findAssociation(scopedName).map(InterlisPlanRoot.class::cast);
  }
}
