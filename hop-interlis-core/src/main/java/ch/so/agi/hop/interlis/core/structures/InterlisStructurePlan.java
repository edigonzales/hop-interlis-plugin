package ch.so.agi.hop.interlis.core.structures;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisStructureDescriptor;
import java.util.List;

/**
 * Precomputed plan for exploding or collecting one multi-valued structure attribute
 * ({@code LIST}/{@code BAG OF} structure).
 *
 * @param parentClass the class the structure attribute belongs to
 * @param pathAttributes single-valued structure attributes leading from the class object to the
 *     multi-valued attribute; empty when the attribute is directly on the class
 * @param structureAttribute the multi-valued structure attribute
 * @param structure the structure type
 * @param childFields the projected child row fields, relative to each structure child
 * @param warnings projection warnings (e.g. skipped nested multi-valued structures)
 */
public record InterlisStructurePlan(
    InterlisClassDescriptor parentClass,
    List<InterlisAttributeDescriptor> pathAttributes,
    InterlisAttributeDescriptor structureAttribute,
    InterlisStructureDescriptor structure,
    List<InterlisFieldPlan> childFields,
    List<String> warnings) {

  public InterlisStructurePlan {
    pathAttributes = pathAttributes == null ? List.of() : List.copyOf(pathAttributes);
    childFields = childFields == null ? List.of() : List.copyOf(childFields);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /** Attribute names of the single-structure path, empty for class-level attributes. */
  public List<String> pathSegments() {
    return pathAttributes.stream().map(InterlisAttributeDescriptor::name).toList();
  }

  /** The attribute name holding the structure children on the owning object. */
  public String attributeName() {
    return structureAttribute.name();
  }

  /** {@code true} if the structure is a {@code LIST} (order is semantic). */
  public boolean ordered() {
    return structureAttribute.ordered();
  }

  /** {@code true} if at least one element is required by the model. */
  public boolean mandatory() {
    return structureAttribute.mandatory();
  }
}
