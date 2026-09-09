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
 * <p>This is the single implementation shared by the inverse class mapper ({@link RowToIomMapper})
 * and the structure collector: both map {@link InterlisFieldPlan} values back into IOM structures,
 * the only difference being the owner (class object vs. structure child). Flattened single
 * structures are created once per structure and existing structures are reused, so overlaying
 * values onto a carrier object never duplicates structures.
 */
public final class IomFieldWriter {

  private final InterlisPrimitiveCodec primitiveCodec = new InterlisPrimitiveCodec();
  private final InterlisGeometryMapper geometryMapper = new InterlisGeometryMapper();

  /**
   * Writes a primitive, geometry or flattened-structure field value into an owner object.
   *
   * @param root the object receiving the value
   * @param field the projected field
   * @param value the Hop value; {@code null} removes the projected attribute
   * @param options write options controlling strictness
   * @param structureCache cache of created/reused single structures keyed by dotted path relative
   *     to {@code root}; may be {@code null} for a fresh run
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
              "Field <"
                  + field.hopFieldName()
                  + "> is not an attribute field (source "
                  + field.source()
                  + ")");
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
      owner.setattrundefined(field.attributeDescriptor().name());
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
      owner.setattrundefined(field.attributeDescriptor().name());
      return;
    }
    if (!(value instanceof Geometry geometry)) {
      throw new InterlisMappingException(
          "Expected a Geometry value for field "
              + field.hopFieldName()
              + " but got "
              + value.getClass().getName());
    }
    InterlisAttributeDescriptor descriptor = field.attributeDescriptor();
    int dimension = requiredDimension(descriptor);
    try {
      IomObject iomGeometry =
          geometryMapper.toIomGeometry(
              geometry, descriptor.geometryKind(), dimension, descriptor.geometryEncoding());
      owner.setattrundefined(descriptor.name());
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
    Iom_jObject owner = root;
    for (int i = 0; i < segments.size(); i++) {
      String key = String.join(".", segments.subList(0, i + 1));
      Iom_jObject child = structureCache == null ? null : structureCache.get(key);
      if (child == null) {
        child = reuseExistingStructure(owner, segments.get(i));
        if (child == null) {
          if (value == null) return;
          InterlisAttributeDescriptor structureAttribute =
              field.structurePath().isEmpty()
                  ? findStructureAttribute(rootProperties, segments.get(i), contextName)
                  : field.structurePath().get(i);
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
    if (value == null) {
      owner.setattrundefined(leaf.name());
      return;
    }
    if (leaf.kind().isGeometry()) {
      if (!(value instanceof Geometry geometry)) {
        throw new InterlisMappingException(
            "Expected a Geometry value for field "
                + field.hopFieldName()
                + " but got "
                + value.getClass().getName());
      }
      try {
        int dimension = requiredDimension(leaf);
        owner.setattrundefined(leaf.name());
        owner.addattrobj(
            leaf.name(),
            geometryMapper.toIomGeometry(
                geometry, leaf.geometryKind(), dimension, leaf.geometryEncoding()));
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

  /** Finalize projected structures, then check mandatory leaves independently of field order. */
  public void finish(
      Iom_jObject root, List<InterlisFieldPlan> fields, RowWriteOptions options, String contextName)
      throws InterlisMappingException {
    // Remove empty optional ancestors bottom-up, retaining any unprojected content.
    for (InterlisFieldPlan field : fields) {
      List<InterlisAttributeDescriptor> path = field.structurePath();
      for (int depth = path.size() - 1; depth >= 0; depth--) {
        IomObject owner = root;
        for (int i = 0; i < depth && owner != null; i++)
          owner = owner.getattrobj(path.get(i).name(), 0);
        if (owner == null) continue;
        var attribute = path.get(depth);
        IomObject child = owner.getattrobj(attribute.name(), 0);
        if (child != null && child.getattrcount() == 0 && !attribute.mandatory()) {
          owner.setattrundefined(attribute.name());
        }
      }
    }
    if (!options.strict() || options.isDelete()) return;
    for (InterlisFieldPlan field : fields) {
      if (field.source() != InterlisFieldSource.PRIMITIVE_ATTRIBUTE
          && field.source() != InterlisFieldSource.GEOMETRY_ATTRIBUTE
          && field.source() != InterlisFieldSource.FLATTENED_STRUCTURE_ATTRIBUTE) continue;
      IomObject owner = root;
      for (var attribute : field.structurePath()) {
        IomObject next = owner.getattrobj(attribute.name(), 0);
        if (next == null && attribute.mandatory()) {
          throw new InterlisMappingException(
              "Mandatory structure "
                  + attribute.name()
                  + " has no value for "
                  + contextName
                  + " (TID "
                  + root.getobjectoid()
                  + ")");
        }
        owner = next;
        if (owner == null) break;
      }
      var attribute = field.attributeDescriptor();
      if (owner != null
          && attribute != null
          && attribute.mandatory()
          && owner.getattrvaluecount(attribute.name()) == 0) {
        throw new InterlisMappingException(
            "Mandatory attribute "
                + field.propertyPath().dotted()
                + " is null for "
                + contextName
                + " (TID "
                + root.getobjectoid()
                + ")");
      }
    }
  }

  public static int requiredDimension(InterlisAttributeDescriptor attribute)
      throws InterlisMappingException {
    Integer dimension = attribute.coordDimension();
    if (dimension == null || (dimension != 2 && dimension != 3)) {
      throw new InterlisMappingException(
          "Cannot resolve coordinate dimension for "
              + attribute.scopedName()
              + "; expected a concrete 2D or 3D coordinate domain");
    }
    return dimension;
  }
}
