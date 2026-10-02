package ch.so.agi.hop.interlis.core.update;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import java.util.*;

/** Precomputed edits of business attributes only; never writes identities or relationships. */
public record InterlisPatchPlan(
    String context, List<InterlisFieldPlan> fields, List<InterlisPropertyDescriptor> properties) {
  public InterlisPatchPlan {
    fields = List.copyOf(fields);
    properties = List.copyOf(properties);
    if (fields.isEmpty())
      throw new IllegalArgumentException("Select at least one attribute for " + context);
    for (var field : fields) {
      if (!canEdit(field))
        throw new IllegalArgumentException(
            "INTERLIS Update cannot change " + context + "." + field.hopFieldName());
    }
  }

  public static boolean canEdit(InterlisFieldPlan field) {
    return field.source() == InterlisFieldSource.PRIMITIVE_ATTRIBUTE
        || field.source() == InterlisFieldSource.GEOMETRY_ATTRIBUTE
        || field.source() == InterlisFieldSource.FLATTENED_STRUCTURE_ATTRIBUTE;
  }

  public void apply(Iom_jObject target, Object[] values) throws InterlisMappingException {
    if (values.length != fields.size())
      throw new InterlisMappingException("Invalid patch width for " + context);
    var writer = new IomFieldWriter();
    var cache = new HashMap<String, Iom_jObject>();
    for (var field : fields)
      writer.write(
          target,
          field,
          values[field.outputIndex()],
          RowWriteOptions.defaults(),
          cache,
          properties,
          context);
    writer.finish(target, fields, RowWriteOptions.defaults(), context);
  }
}
