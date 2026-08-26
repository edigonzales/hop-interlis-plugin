package ch.so.agi.hop.interlis.core.structures;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowSchemaBuilder;
import ch.so.agi.hop.interlis.core.mapping.InterlisStructureChildProjection;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisStructureDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates a multi-valued structure attribute within a class and precomputes the child row
 * projection.
 *
 * <p>The structure attribute may sit directly on the class ({@code Addresses}) or below a chain
 * of single-valued structure attributes ({@code Home.Contact.Addresses}); the same dotted path
 * notation as for flattened single-structure fields is used.
 */
public final class InterlisStructureLocator {

  private final InterlisRowSchemaBuilder schemaBuilder = new InterlisRowSchemaBuilder();

  /**
   * Locates the structure attribute and builds its child projection.
   *
   * @param schema the model schema
   * @param classDescriptor the parent class
   * @param structurePath dotted path from the class to the multi-valued structure attribute,
   *     e.g. {@code Addresses} or {@code Home.Contact.Addresses}
   * @param options projection options for the child fields
   * @throws InterlisMappingException if the path cannot be resolved to a multi-valued structure
   */
  public InterlisStructurePlan locate(
      InterlisSchemaDescriptor schema,
      InterlisClassDescriptor classDescriptor,
      String structurePath,
      ProjectionOptions options)
      throws InterlisMappingException {
    if (structurePath == null || structurePath.isBlank()) {
      throw new InterlisMappingException(
          "No structure attribute selected for class " + classDescriptor.scopedName());
    }

    String[] segments = structurePath.split("\\.");
    List<InterlisAttributeDescriptor> pathAttributes = new ArrayList<>();
    List<InterlisPropertyDescriptor> currentProperties = classDescriptor.effectiveProperties();
    String currentContext = classDescriptor.scopedName();

    InterlisAttributeDescriptor target = null;
    for (String segment : segments) {
      if (segment.isBlank()) {
        throw new InterlisMappingException(
            "Invalid structure path <" + structurePath + "> for class "
                + classDescriptor.scopedName());
      }
      InterlisAttributeDescriptor attribute =
          findAttribute(currentProperties, segment, currentContext);
      if (!attribute.kind().isStructure()) {
        throw new InterlisMappingException(
            "Path element <" + segment + "> of <" + structurePath + "> is not a structure "
                + "attribute in " + currentContext + " (" + attribute.typeName() + ")");
      }
      target = attribute;
      if (attribute.cardinality().isSingleValued()) {
        // Intermediate hop: descend into the single-valued structure.
        pathAttributes.add(attribute);
        Optional<InterlisStructureDescriptor> structure =
            schema.findStructure(attribute.structureScopedName());
        if (structure.isEmpty()) {
          throw new InterlisMappingException(
              "Structure " + attribute.structureScopedName() + " of attribute " + attribute.name()
                  + " is not part of the compiled model");
        }
        currentProperties = new ArrayList<>(structure.get().attributes());
        currentContext = structure.get().scopedName();
      }
    }

    if (target == null || target.cardinality().isSingleValued()) {
      throw new InterlisMappingException(
          "Structure path <" + structurePath + "> of class " + classDescriptor.scopedName()
              + " is not a multi-valued (LIST/BAG OF) structure attribute");
    }

    String structureScopedName = target.structureScopedName();
    String targetName = target.name();
    InterlisStructureDescriptor structure =
        schema
            .findStructure(structureScopedName)
            .orElseThrow(
                () ->
                    new InterlisMappingException(
                        "Structure " + structureScopedName + " of attribute "
                            + targetName + " is not part of the compiled model"));

    InterlisStructureChildProjection projection =
        schemaBuilder.buildStructureChildFields(schema, structure, options);
    return new InterlisStructurePlan(
        classDescriptor, pathAttributes, target, structure, projection.fields(),
        projection.warnings());
  }

  /**
   * Lists the dotted paths of all multi-valued ({@code LIST}/{@code BAG OF}) structure attributes
   * of a class, including those below single-valued structure attributes
   * (e.g. {@code Home.Place.Phones}). The result follows the model order.
   */
  public List<String> multiValuedStructurePaths(
      InterlisSchemaDescriptor schema, InterlisClassDescriptor classDescriptor) {
    List<String> paths = new ArrayList<>();
    collectMultiValuedPaths(
        schema, classDescriptor.effectiveProperties(), List.of(), paths);
    return paths;
  }

  private void collectMultiValuedPaths(
      InterlisSchemaDescriptor schema,
      List<InterlisPropertyDescriptor> properties,
      List<String> prefix,
      List<String> paths) {
    for (InterlisPropertyDescriptor property : properties) {
      if (!(property instanceof InterlisAttributeDescriptor attribute)
          || !attribute.kind().isStructure()) {
        continue;
      }
      List<String> path = new ArrayList<>(prefix);
      path.add(attribute.name());
      if (attribute.cardinality().isSingleValued()) {
        schema
            .findStructure(attribute.structureScopedName())
            .ifPresent(
                structure ->
                    collectMultiValuedPaths(
                        schema, new ArrayList<>(structure.attributes()), path, paths));
      } else {
        paths.add(String.join(".", path));
      }
    }
  }

  private InterlisAttributeDescriptor findAttribute(
      List<InterlisPropertyDescriptor> properties, String name, String contextName)
      throws InterlisMappingException {
    for (InterlisPropertyDescriptor property : properties) {
      if (property instanceof InterlisAttributeDescriptor attribute
          && attribute.name().equals(name)) {
        return attribute;
      }
    }
    throw new InterlisMappingException(
        "Attribute <" + name + "> not found in " + contextName);
  }
}
