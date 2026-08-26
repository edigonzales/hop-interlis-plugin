package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPlanRoot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The precomputed projection of one INTERLIS class or association onto typed Hop rows.
 *
 * <p>A plan is built once per transform initialization and shared by {@code getFields()}, the
 * runtime mapper and the GUI schema preview. No model navigation happens at row level.
 *
 * @param root the projected class or association
 * @param fields output fields in row order
 * @param warnings design-time warnings, e.g. multi-valued structures that are not part of the
 *     scalar row schema
 * @param defaultSrid SRID applied to mapped geometries, {@code null} keeps SRID 0
 * @param linkResolvedRoles roles whose reference and association attributes are resolved from
 *     the association link object instead of an embedded reference; maps role name to the
 *     association
 */
public record InterlisRowMappingPlan(
    InterlisPlanRoot root,
    List<InterlisFieldPlan> fields,
    List<String> warnings,
    Integer defaultSrid,
    Map<String, InterlisAssociationDescriptor> linkResolvedRoles) {

  public InterlisRowMappingPlan {
    fields = fields == null ? List.of() : List.copyOf(fields);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    linkResolvedRoles =
        linkResolvedRoles == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(linkResolvedRoles));
  }

  /** The projected class, or {@code null} when the plan projects an association. */
  public InterlisClassDescriptor classDescriptor() {
    return root instanceof InterlisClassDescriptor classDescriptor ? classDescriptor : null;
  }

  public int fieldCount() {
    return fields.size();
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /** {@code true} when at least one field is resolved from an association link object. */
  public boolean hasLinkResolvedRoles() {
    return !linkResolvedRoles.isEmpty();
  }
}
