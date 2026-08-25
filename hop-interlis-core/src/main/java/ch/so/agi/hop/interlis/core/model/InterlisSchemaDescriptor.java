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
 */
public record InterlisSchemaDescriptor(
    List<InterlisClassDescriptor> classes, List<InterlisStructureDescriptor> structures) {

  public InterlisSchemaDescriptor {
    classes = classes == null ? List.of() : List.copyOf(classes);
    structures = structures == null ? List.of() : List.copyOf(structures);
  }

  public Optional<InterlisClassDescriptor> findClass(String scopedName) {
    return classes.stream().filter(c -> c.scopedName().equals(scopedName)).findFirst();
  }

  public Optional<InterlisStructureDescriptor> findStructure(String scopedName) {
    return structures.stream().filter(s -> s.scopedName().equals(scopedName)).findFirst();
  }
}
