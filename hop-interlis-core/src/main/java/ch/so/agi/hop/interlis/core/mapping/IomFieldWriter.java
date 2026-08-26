package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.geometry.InterlisGeometryMapper;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;

/**
 * Writes attribute field values into an IOM owner object.
 *
 * <p>This is the single implementation shared by the inverse class mapper
 * ({@link RowToIomMapper}) and the structure collector: both map {@link InterlisFieldPlan} values
 * back into IOM structures, the only difference being the owner (class object vs. structure
 * child). Flattened single structures are created once per structure and existing structures are
 * reused, so overlaying values onto a carrier object never duplicates structures.
 */
public final class IomFieldWriter {

  private final InterlisPrimitiveCodec primitiveCodec = new InterlisPrimitiveCodec();
  private final InterlisGeometryMapper geometryMapper = new InterlisGeometryMapper();

  /**
   * Writes a primitive, geometry or flattened-structure field value into an owner object.
   *
   * @param root the object receiving the value
   * @param field the projected field
   * @param value the Hop value; {@code null} leaves the attribute undefined
   * @param options write options controlling strictness
   * @param structureCache cache of created/reused single structures keyed by dotted path
   *     relative to {@code root}; may be {@code null} for a fresh run
   * @param rootProperties properties of {@code root} used to resolve structure attribute types
   * @param contextName class or structure name used in error messages
   */
  public void write(
      Iom_jObject root,
      InterlisFieldPlan field,
      Object value,
      RowWriteOptions options,
      Map<String, Iom_jObject> structureCache,
      List<InterlisPropertyDescriptor> rootProperties,
      String contextName)
      throws InterlisMappingException {
    switch (field.source()) {
      case PRIMITIVE_ATTRIBUTE -> writePrimitive(root, field, value, options, contextName);
      case GEOMETRY_ATTRIBUTE -> writeGeometry(root, field, value, options, contextName);
      case FLATTENED_STRUCTURE_ATTRIBUTE ->
          writeFlattened(root, field, value, options, structureCache, rootProperties, contextName);
      default ->
          throw new InterlisMappingException(
              "Field <" + field.hopFieldName() + "> is not an attribute field (source "
                  + field.source() + ")");
    }
  }

  private void writePrimitive(
      Iom_jObject owner,
      InterlisFieldPlan field,
      Object value,
      RowWriteOptions options,
      String contextName)
      throws InterlisMappingException {
    if (value == null) {
      requireNotMandatory(field, contextName, options);
      return;
    }
    String raw = primitiveCodec.format(value, field.attributeDescriptor());
    owner.setattrvalue(field.attributeDescriptor().name(), raw);
  }

  private void writeGeometry(
      Iom_jObject owner,
      InterlisFieldPlan field,
      Object value,
      RowWriteOptions options,
      String contextName)
      throws InterlisMappingException {
    if (value == null) {
      requireNotMandatory(field, contextName, options);
      return;
    }
    if (!(value instanceof Geometry geometry)) {
      throw new InterlisMappingException(
          "Expected a Geometry value for field " + field.hopFieldName() + " but got "
              + value.getClass().getName());
    }
    InterlisAttributeDescriptor descriptor = field.attributeDescriptor();
    int dimension = descriptor.coordDimension() == null ? 2 : descriptor.coordDimension();
    try {
      IomObject iomGeometry =
          geometryMapper.toIomGeometry(geometry, descriptor.geometryKind(), dimension);
      owner.addattrobj(descriptor.name(), iomGeometry);
    } catch (Exception e) {
      throw new InterlisMappingException(
          "Failed to convert geometry field " + field.hopFieldName() + ": " + e.getMessage(), e);
    }
  }

  private void writeFlattened(
      Iom_jObject root,
      InterlisFieldPlan field,
      Object value,
      RowWriteOptions options,
      Map<String, Iom_jObject> structureCache,
      List<InterlisPropertyDescriptor> rootProperties,
      String contextName)
      throws InterlisMappingException {
    List<String> segments = field.propertyPath().segments();
    boolean structureExists =
        structureCache != null && structureCache.containsKey(String.join(".", segments));

    if (value == null) {
      // A missing optional structure leaves all its children undefined. If the structure
      // already exists (another child was set), mandatory leaves are enforced in strict mode.
      if (structureExists) {
        requireNotMandatory(field, contextName, options);
      }
      return;
    }

    Iom_jObject owner = root;
    for (int i = 0; i < segments.size(); i++) {
      String key = String.join(".", segments.subList(0, i + 1));
      Iom_jObject child = structureCache == null ? null : structureCache.get(key);
      if (child == null) {
        child = reuseExistingStructure(owner, segments.get(i));
        if (child == null) {
          InterlisAttributeDescriptor structureAttribute =
              findStructureAttribute(rootProperties, segments.get(i), contextName);
          child = new Iom_jObject(structureAttribute.structureScopedName(), null);
          owner.addattrobj(segments.get(i), child);
        }
        if (structureCache != null) {
          structureCache.put(key, child);
        }
      }
      owner = child;
    }

    InterlisAttributeDescriptor leaf = field.attributeDescriptor();
    if (leaf.kind().isGeometry()) {
      if (!(value instanceof Geometry geometry)) {
        throw new InterlisMappingException(
            "Expected a Geometry value for field " + field.hopFieldName() + " but got "
                + value.getClass().getName());
      }
      try {
        int dimension = leaf.coordDimension() == null ? 2 : leaf.coordDimension();
        owner.addattrobj(
            leaf.name(), geometryMapper.toIomGeometry(geometry, leaf.geometryKind(), dimension));
      } catch (Exception e) {
        throw new InterlisMappingException(
            "Failed to convert geometry field " + field.hopFieldName() + ": " + e.getMessage(), e);
      }
    } else {
      owner.setattrvalue(leaf.name(), primitiveCodec.format(value, leaf));
    }
  }

  /** Reuses a single structure already present on the owner, so overlays do not duplicate it. */
  private Iom_jObject reuseExistingStructure(Iom_jObject owner, String attributeName) {
    if (owner.getattrvaluecount(attributeName) == 1) {
      IomObject existing = owner.getattrobj(attributeName, 0);
      if (existing instanceof Iom_jObject existingObject) {
        return existingObject;
      }
    }
    return null;
  }

  private InterlisAttributeDescriptor findStructureAttribute(
      List<InterlisPropertyDescriptor> properties, String attributeName, String contextName)
      throws InterlisMappingException {
    for (InterlisPropertyDescriptor property : properties) {
      if (property instanceof InterlisAttributeDescriptor attribute
          && attribute.name().equals(attributeName)) {
        return attribute;
      }
    }
    throw new InterlisMappingException(
        "Structure attribute " + attributeName + " not found in " + contextName);
  }

  private void requireNotMandatory(
      InterlisFieldPlan field, String contextName, RowWriteOptions options)
      throws InterlisMappingException {
    InterlisAttributeDescriptor descriptor = field.attributeDescriptor();
    if (options.strict() && !options.isDelete() && descriptor != null && descriptor.mandatory()) {
      throw new InterlisMappingException(
          "Mandatory attribute " + descriptor.name() + " is null for " + contextName);
    }
  }
}
