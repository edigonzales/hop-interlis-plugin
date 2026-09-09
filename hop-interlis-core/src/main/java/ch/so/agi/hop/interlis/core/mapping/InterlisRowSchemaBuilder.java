package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPlanRoot;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisStructureDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the {@link InterlisRowMappingPlan} for a class or association: the stable field list with
 * output indexes, value sources and property paths.
 *
 * <p>Field order is fixed: reserved INTERLIS fields first ({@code _ili_tid} when the root is
 * identifiable, {@code _ili_bid}, {@code _ili_class}, {@code _ili_topic}, {@code _ili_operation}),
 * then the effective model properties in model order; flattened structure fields appear at the
 * position of their structure attribute in depth-first order.
 *
 * <p>Roles are projected as {@code <role>_ref} fields (optionally with {@code <role>_ref_bid} and,
 * on association rows, {@code <role>_order_pos} for ORDERED roles). Attributes of uniquely
 * embeddable attributed associations are flattened as {@code <role>_<attribute>} fields and are
 * resolved from the association link object at runtime.
 */
public final class InterlisRowSchemaBuilder {

  public InterlisRowMappingPlan build(
      InterlisSchemaDescriptor schema, InterlisPlanRoot root, ProjectionOptions options)
      throws InterlisMappingException {
    List<InterlisFieldPlan> fields = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> usedNames = new HashSet<>();
    Map<String, InterlisAssociationDescriptor> linkResolvedRoles = new LinkedHashMap<>();

    int index = 0;
    if (options.includeTid() && isIdentifiable(root)) {
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
      index =
          addTechnical(fields, usedNames, index, "_ili_operation", InterlisFieldSource.OPERATION);
    }

    boolean association = root instanceof InterlisAssociationDescriptor;
    for (InterlisPropertyDescriptor property : root.effectiveProperties()) {
      if (property.inherited() && !options.includeInheritedProperties()) {
        continue;
      }
      if (!options.isPropertySelected(property.name())) {
        continue;
      }
      if (property instanceof InterlisAttributeDescriptor attribute) {
        index =
            addAttribute(
                schema, fields, warnings, usedNames, index, attribute, List.of(), options,
                root.scopedName());
      } else if (property instanceof InterlisRoleDescriptor role) {
        if (association) {
          index = addAssociationRole(fields, warnings, usedNames, index, role, options, root.scopedName());
        } else {
          index =
              addClassRole(
                  schema, fields, warnings, usedNames, index, role, options, root.scopedName(),
                  linkResolvedRoles);
        }
      }
    }

    return new InterlisRowMappingPlan(root, fields, warnings, options.defaultSrid(),
        linkResolvedRoles);
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

  private boolean isIdentifiable(InterlisPlanRoot root) {
    return !(root instanceof InterlisAssociationDescriptor association)
        || association.identifiable();
  }

  private int addTechnical(
      List<InterlisFieldPlan> fields,
      Set<String> usedNames,
      int index,
      String name,
      InterlisFieldSource source) {
    usedNames.add(name);
    fields.add(new InterlisFieldPlan(index, name, source, InterlisPropertyPath.root(name), null,
        null, null));
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
      String rootScopedName)
      throws InterlisMappingException {
    if (attribute.kind() == InterlisValueKind.STRUCTURE) {
      if (!attribute.cardinality().isSingleValued()) {
        warnings.add(
            "Property "
                + attribute.name()
                + " of "
                + rootScopedName
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
                rootScopedName);
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
              + "> while projecting "
              + rootScopedName);
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
            null,
            null,
            structurePath(schema, rootScopedName, pathSegments)));
    return index + 1;
  }

  private List<InterlisAttributeDescriptor> structurePath(
      InterlisSchemaDescriptor schema, String rootName, List<String> segments) {
    if (segments.isEmpty()) return List.of();
    List<? extends InterlisPropertyDescriptor> properties =
        schema
            .findPlanRoot(rootName)
            .map(InterlisPlanRoot::effectiveProperties)
            .orElseGet(
                () -> new ArrayList<>(schema.findStructure(rootName).orElseThrow().attributes()));
    List<InterlisAttributeDescriptor> path = new ArrayList<>();
    for (String segment : segments) {
      InterlisAttributeDescriptor attribute =
          properties.stream()
              .filter(p -> p.name().equals(segment))
              .map(p -> (InterlisAttributeDescriptor) p)
              .findFirst()
              .orElseThrow();
      path.add(attribute);
      properties = schema.findStructure(attribute.structureScopedName()).orElseThrow().attributes();
    }
    return path;
  }

  /** One reference member per role, plus optional external-basket and order position fields. */
  private int addAssociationRole(
      List<InterlisFieldPlan> fields,
      List<String> warnings,
      Set<String> usedNames,
      int index,
      InterlisRoleDescriptor role,
      ProjectionOptions options,
      String rootScopedName) {
    index =
        addRoleField(
            fields,
            usedNames,
            index,
            role.name() + "_ref",
            InterlisFieldSource.ROLE_REFERENCE,
            role,
            rootScopedName,
            warnings);
    if (options.includeRoleRefBid()) {
      index = addRoleField(fields, usedNames, index, role.name() + "_ref_bid",
          InterlisFieldSource.ROLE_REFERENCE_BID, role, rootScopedName, warnings);
    }
    if (role.ordered()) {
      index = addRoleField(fields, usedNames, index, role.name() + "_order_pos",
          InterlisFieldSource.ROLE_ORDER_POS, role, rootScopedName, warnings);
    }
    return index;
  }

  /**
   * Class-side roles: plain single-valued roles become {@code <role>_ref} fields; attributes of
   * uniquely embeddable attributed associations are flattened as {@code <role>_<attribute>}
   * fields resolved from the association link object.
   */
  private int addClassRole(
      InterlisSchemaDescriptor schema,
      List<InterlisFieldPlan> fields,
      List<String> warnings,
      Set<String> usedNames,
      int index,
      InterlisRoleDescriptor role,
      ProjectionOptions options,
      String rootScopedName,
      Map<String, InterlisAssociationDescriptor> linkResolvedRoles)
      throws InterlisMappingException {
    Optional<InterlisAssociationDescriptor> association =
        role.associationScopedName() == null
            ? Optional.empty()
            : schema.findAssociation(role.associationScopedName());
    boolean attributed =
        association.isPresent() && !association.get().attributes().isEmpty();

    if (attributed) {
      if (!options.flattenAssociationAttributes()) {
        warnings.add(
            "Role "
                + role.name()
                + " of "
                + rootScopedName
                + " belongs to association "
                + association.get().scopedName()
                + " with attributes; association attributes are not flattened and the role is "
                + "available as association rows");
        return index;
      }
      if (!isUniquelyEmbeddable(association.get(), role)) {
        warnings.add(
            "Role "
                + role.name()
                + " of "
                + rootScopedName
                + " belongs to association "
                + association.get().scopedName()
                + " whose attributes cannot be flattened (association is not uniquely determined "
                + "per object); use association rows");
        return index;
      }
      linkResolvedRoles.put(role.name(), association.get());
      index =
          addRoleField(
              fields,
              usedNames,
              index,
              role.name() + "_ref",
              InterlisFieldSource.ROLE_REFERENCE,
              role,
              rootScopedName,
              warnings);
      if (options.includeRoleRefBid()) {
        index = addRoleField(fields, usedNames, index, role.name() + "_ref_bid",
            InterlisFieldSource.ROLE_REFERENCE_BID, role, rootScopedName, warnings);
      }
      for (InterlisAttributeDescriptor attribute : association.get().attributes()) {
        String hopFieldName = role.name() + "_" + attribute.name();
        if (!usedNames.add(hopFieldName)) {
          throw new InterlisMappingException(
              "Duplicate output field name <" + hopFieldName + "> while projecting "
                  + rootScopedName);
        }
        fields.add(
            new InterlisFieldPlan(
                index,
                hopFieldName,
                InterlisFieldSource.ASSOCIATION_ATTRIBUTE,
                InterlisPropertyPath.root(attribute.name()),
                attribute,
                role,
                association.get()));
        index++;
      }
      return index;
    }

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
    index =
        addRoleField(
            fields,
            usedNames,
            index,
            role.name() + "_ref",
            InterlisFieldSource.ROLE_REFERENCE,
            role,
            rootScopedName,
            warnings);
    if (options.includeRoleRefBid()) {
      index = addRoleField(fields, usedNames, index, role.name() + "_ref_bid",
          InterlisFieldSource.ROLE_REFERENCE_BID, role, rootScopedName, warnings);
    }
    return index;
  }

  private int addRoleField(
      List<InterlisFieldPlan> fields,
      Set<String> usedNames,
      int index,
      String hopFieldName,
      InterlisFieldSource source,
      InterlisRoleDescriptor role,
      String rootScopedName,
      List<String> warnings) {
    if (!usedNames.add(hopFieldName)) {
      warnings.add(
          "Duplicate output field name <" + hopFieldName + "> for role " + role.name()
              + " while projecting " + rootScopedName + "; the role field is skipped");
      return index;
    }
    fields.add(
        new InterlisFieldPlan(
            index,
            hopFieldName,
            source,
            InterlisPropertyPath.root(role.name()),
            null,
            role,
            null));
    return index + 1;
  }

  /**
   * A binary association is uniquely embeddable on the given role when the role is single-valued
   * on the class and every other role has multiplicity exactly 1..1.
   */
  private boolean isUniquelyEmbeddable(
      InterlisAssociationDescriptor association, InterlisRoleDescriptor role) {
    if (association.roles().size() != 2) {
      return false;
    }
    if (!role.cardinality().isSingleValued()) {
      return false;
    }
    for (InterlisRoleDescriptor other : association.roles()) {
      if (other.name().equals(role.name())) {
        continue;
      }
      if (!(other.cardinality().min() == 1 && other.cardinality().max() == 1)) {
        return false;
      }
    }
    return true;
  }

  private String dotted(List<String> segments, String leaf) {
    return segments.isEmpty() ? leaf : String.join(".", segments) + "." + leaf;
  }
}
