package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import java.util.List;

/**
 * The precomputed projection of one INTERLIS class onto typed Hop rows.
 *
 * <p>A plan is built once per transform initialization and shared by {@code getFields()}, the
 * runtime mapper and the GUI schema preview. No model navigation happens at row level.
 *
 * @param classDescriptor the projected class
 * @param fields output fields in row order
 * @param warnings design-time warnings, e.g. multi-valued structures that are not part of the
 *     scalar row schema
 * @param defaultSrid SRID applied to mapped geometries, {@code null} keeps SRID 0
 */
public record InterlisRowMappingPlan(
    InterlisClassDescriptor classDescriptor,
    List<InterlisFieldPlan> fields,
    List<String> warnings,
    Integer defaultSrid) {

  public InterlisRowMappingPlan {
    fields = fields == null ? List.of() : List.copyOf(fields);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public int fieldCount() {
    return fields.size();
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }
}
