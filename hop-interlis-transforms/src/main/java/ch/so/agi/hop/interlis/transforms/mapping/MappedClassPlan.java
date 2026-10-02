package ch.so.agi.hop.interlis.transforms.mapping;

import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import java.util.*;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;

/** Compiled explicit field mapping, shared by the writer and the update sink. */
public final class MappedClassPlan {
  public final InterlisRowMappingPlan plan;
  private final Map<String, String> sources;

  public MappedClassPlan(
      InterlisRowMappingPlan available,
      InterlisMappedInput config,
      IVariables variables,
      boolean identities)
      throws HopTransformException {
    sources = new LinkedHashMap<>();
    for (var mapping : config.getFields()) {
      String target = mapping.getTargetPath().trim();
      String source = InterlisFieldBinding.resolve(variables, mapping.getSourceField());
      if (target.isEmpty() || source.isEmpty() || sources.putIfAbsent(target, source) != null)
        throw new HopTransformException(
            "Empty or duplicate field mapping in " + config.getTransformName() + ": " + target);
    }
    var fields = new ArrayList<InterlisFieldPlan>();
    var unused = new LinkedHashSet<>(sources.keySet());
    for (var field : available.fields()) {
      if (identities
          && (field.source() == InterlisFieldSource.OBJECT_ID
              || field.source() == InterlisFieldSource.BASKET_ID)) {
        fields.add(reindex(field, fields.size()));
      } else if (unused.remove(target(field))) {
        fields.add(reindex(field, fields.size()));
      }
    }
    if (!unused.isEmpty())
      throw new HopTransformException(
          "Unknown or unsupported targets in " + config.getClassName() + ": " + unused);
    plan =
        new InterlisRowMappingPlan(
            available.root(),
            fields,
            available.warnings(),
            available.defaultSrid(),
            available.linkResolvedRoles());
  }

  public static String target(InterlisFieldPlan field) {
    if ("_ili_value".equals(field.hopFieldName())) return "_ili_value";
    return switch (field.source()) {
      case PRIMITIVE_ATTRIBUTE, GEOMETRY_ATTRIBUTE, FLATTENED_STRUCTURE_ATTRIBUTE ->
          field.propertyPath().dotted();
      default -> field.hopFieldName();
    };
  }

  public static InterlisFieldPlan reindex(InterlisFieldPlan f, int index) {
    return new InterlisFieldPlan(
        index,
        f.hopFieldName(),
        f.source(),
        f.propertyPath(),
        f.attributeDescriptor(),
        f.roleDescriptor(),
        f.associationDescriptor(),
        f.structurePath());
  }

  public InterlisRowBindings bind(
      IRowMeta input, InterlisMappedInput config, IVariables vars, String automaticBid)
      throws HopTransformException {
    var bindings = new ArrayList<InterlisFieldBinding>();
    var factory = new HopRowSchemaFactory();
    for (var f : plan.fields()) {
      String source =
          switch (f.source()) {
            case OBJECT_ID -> InterlisFieldBinding.resolve(vars, config.getObjectIdField());
            case BASKET_ID -> InterlisFieldBinding.resolve(vars, config.getBasketIdField());
            default -> sources.get(target(f));
          };
      bindings.add(
          f.source() == InterlisFieldSource.BASKET_ID && automaticBid != null
              ? InterlisFieldBinding.constant(
                  config.getTransformName(), f.hopFieldName(), f.outputIndex(), automaticBid)
              : InterlisFieldBinding.bind(
                  input,
                  config.getTransformName(),
                  source,
                  target(f),
                  f.outputIndex(),
                  factory.createValueMeta(f),
                  true));
    }
    return new InterlisRowBindings(bindings);
  }
}
