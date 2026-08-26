package ch.so.agi.hop.interlis.core.mapping;

import java.util.List;

/**
 * Result of projecting the attributes of a structure into child row fields.
 *
 * @param fields the projected fields in stable order (output indexes start at 0)
 * @param warnings projection warnings, e.g. skipped nested multi-valued structures
 */
public record InterlisStructureChildProjection(
    List<InterlisFieldPlan> fields, List<String> warnings) {

  public InterlisStructureChildProjection {
    fields = fields == null ? List.of() : List.copyOf(fields);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
