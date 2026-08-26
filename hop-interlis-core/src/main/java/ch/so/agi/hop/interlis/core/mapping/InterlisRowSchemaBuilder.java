package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisStructureDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the {@link InterlisRowMappingPlan} for a class: the stable field list with output
 * indexes, value sources and property paths.
 *
 * <p>Field order is fixed: reserved INTERLIS fields first ({@code _ili_tid}, {@code _ili_bid},
 * {@code _ili_class}, {@code _ili_topic}, {@code _ili_operation}), then the effective model
 * properties in model order; flattened structure fields appear at the position of their
 * structure attribute in depth-first order.
 */
public final class InterlisRowSchemaBuilder {

  public InterlisRowMappingPlan build(
      InterlisSchemaDescriptor schema,
      InterlisClassDescriptor classDescriptor,
      ProjectionOptions options)
      throws InterlisMappingException {
    List<InterlisFieldPlan> fields = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> usedNames = new HashSet<>();

    int index = 0;
    if (options.includeTid()) {
      index = addTechnical(fields, usedNames, index, "_ili_tid", InterlisFieldSource.OBJECT_ID);
    }
    if (options.includeBid()) {
      index = addTechnical(fields, usedNames, index, "_ili_bid", InterlisFieldSource.BASKET_ID);
    }
    if (options.includeClassName()) {
      index = addTechnical(fields, usedNames, index, "_ili_class", InterlisFieldSource.CLASS_NAME);
    }
    if (options.includeTopicName()) {
      index = addTechnical(fields, usedNames, index, "_ili_topic", InterlisFieldSource.TOPIC_NAME);
    }
    if (options.includeOperation()) {
      index = addTechnical(fields, usedNames, index, "_ili_operation", InterlisFieldSource.OPERATION);
    }

    for (InterlisPropertyDescriptor property : classDescriptor.effectiveProperties()) {
      if (property.inherited() && !options.includeInheritedProperties()) {
        continue;
      }
      if (!options.isPropertySelected(property.name())) {
        continue;
      }
      if (property instanceof InterlisAttributeDescriptor attribute) {
        index = addAttribute(schema, fields, warnings, usedNames, index, attribute,
            List.of(), options, classDescriptor.scopedName());
      } else if (property instanceof InterlisRoleDescriptor role) {
        index = addRole(fields, warnings, usedNames, index, role, options);
      }
    }

    return new InterlisRowMappingPlan(classDescriptor, fields, warnings, options.defaultSrid());
  }

  /**
   * Builds the flattened child field list for a structure root. Used by INTERLIS Structure
   * Explode and INTERLIS Structure Collect so the child row schema is produced by the same
   * projection rules as class schemas (flattened single structures, collision checks, warnings
   * for nested multi-valued structures).
   *
   * @param schema the model schema
   * @param structure the structure whose attributes are projected
   * @param options projection options (separator, selected paths, ...)
   * @return the child fields in stable order with output indexes starting at 0
   */
  public InterlisStructureChildProjection buildStructureChildFields(
      InterlisSchemaDescriptor schema,
      InterlisStructureDescriptor structure,
      ProjectionOptions options)
      throws InterlisMappingException {
    List<InterlisFieldPlan> fields = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> usedNames = new HashSet<>();

    int index = 0;
    for (InterlisAttributeDescriptor attribute : structure.attributes()) {
      index =
          addAttribute(
              schema, fields, warnings, usedNames, index, attribute, List.of(), options,
              structure.scopedName());
    }
    return new InterlisStructureChildProjection(fields, warnings);
  }

  private int addTechnical(
      List<InterlisFieldPlan> fields,
      Set<String> usedNames,
      int index,
      String name,
      InterlisFieldSource source) {
    usedNames.add(name);
    fields.add(new InterlisFieldPlan(index, name, source, InterlisPropertyPath.root(name), null, null));
    return index + 1;
  }

  private int addAttribute(
      InterlisSchemaDescriptor schema,
      List<InterlisFieldPlan> fields,
      List<String> warnings,
      Set<String> usedNames,
      int index,
      InterlisAttributeDescriptor attribute,
      List<String> pathSegments,
      ProjectionOptions options,
      String classScopedName)
      throws InterlisMappingException {
    if (attribute.kind() == InterlisValueKind.STRUCTURE) {
      if (!attribute.cardinality().isSingleValued()) {
        warnings.add(
            "Property "
                + attribute.name()
                + " of class "
                + classScopedName
                + " is a multi-valued structure ("
                + attribute.cardinality()
                + "); it is not part of the scalar row schema and must be handled with "
                + "INTERLIS Structure Explode");
        return index;
      }
      Optional<InterlisStructureDescriptor> structure =
          schema.findStructure(attribute.structureScopedName());
      if (structure.isEmpty()) {
        warnings.add(
            "Structure "
                + attribute.structureScopedName()
                + " of attribute "
                + attribute.name()
                + " is not part of the compiled model; the attribute is skipped");
        return index;
      }
      List<String> childPath = new ArrayList<>(pathSegments);
      childPath.add(attribute.name());
      for (InterlisAttributeDescriptor child : structure.get().attributes()) {
        index =
            addAttribute(
                schema, fields, warnings, usedNames, index, child, childPath, options,
                classScopedName);
      }
      return index;
    }

    if (!options.isPropertySelected(dotted(pathSegments, attribute.name()))) {
      return index;
    }

    String hopFieldName =
        pathSegments.isEmpty()
            ? attribute.name()
            : String.join(options.structureSeparator(), pathSegments)
                + options.structureSeparator()
                + attribute.name();
    if (!usedNames.add(hopFieldName)) {
      throw new InterlisMappingException(
          "Duplicate output field name <"
              + hopFieldName
              + "> while projecting class "
              + classScopedName);
    }

    InterlisFieldSource source =
        attribute.kind().isGeometry()
            ? InterlisFieldSource.GEOMETRY_ATTRIBUTE
            : InterlisFieldSource.PRIMITIVE_ATTRIBUTE;
    if (!pathSegments.isEmpty()) {
      source = InterlisFieldSource.FLATTENED_STRUCTURE_ATTRIBUTE;
    }
    fields.add(
        new InterlisFieldPlan(
            index,
            hopFieldName,
            source,
            new InterlisPropertyPath(pathSegments, attribute.name()),
            attribute,
            null));
    return index + 1;
  }

  private int addRole(
      List<InterlisFieldPlan> fields,
      List<String> warnings,
      Set<String> usedNames,
      int index,
      InterlisRoleDescriptor role,
      ProjectionOptions options) {
    if (!role.cardinality().isSingleValued()) {
      warnings.add(
          "Role "
              + role.name()
              + " with cardinality "
              + role.cardinality()
              + " cannot be projected as a single reference field; it must be handled as an "
              + "association");
      return index;
    }
    String hopFieldName = role.name() + "_ref";
    if (!usedNames.add(hopFieldName)) {
      warnings.add("Duplicate output field name <" + hopFieldName + "> for role " + role.name());
      return index;
    }
    fields.add(
        new InterlisFieldPlan(
            index,
            hopFieldName,
            InterlisFieldSource.ROLE_REFERENCE,
            InterlisPropertyPath.root(role.name()),
            null,
            role));
    return index + 1;
  }

  private String dotted(List<String> segments, String leaf) {
    return segments.isEmpty() ? leaf : String.join(".", segments) + "." + leaf;
  }
}
