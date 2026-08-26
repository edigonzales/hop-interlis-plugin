package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.geometry.InterlisGeometryMapper;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryKind;
import java.util.List;
import org.locationtech.jts.geom.Geometry;

/**
 * Reads attribute field values from an IOM owner object.
 *
 * <p>This is the single implementation shared by the class row mapper
 * ({@link DefaultInterlisObjectToRowMapper}) and the structure exploder: both map
 * {@link InterlisFieldPlan}s to Hop values, the only difference being the owner object (class
 * object vs. structure child). Geometry values go through the SQL/MM WKB bridge, so arcs are
 * preserved.
 */
public final class IomFieldReader {

  private final InterlisPrimitiveCodec primitiveCodec = new InterlisPrimitiveCodec();
  private final InterlisGeometryMapper geometryMapper = new InterlisGeometryMapper();

  /**
   * Reads a primitive, geometry or flattened-structure field value from an owner object.
   *
   * @param owner the IOM object holding the attribute
   * @param field the projected field
   * @param defaultSrid SRID assigned to mapped geometries, or {@code null} to keep SRID 0
   */
  public Object read(IomObject owner, InterlisFieldPlan field, Integer defaultSrid)
      throws InterlisMappingException {
    return switch (field.source()) {
      case PRIMITIVE_ATTRIBUTE -> readPrimitive(owner, field.attributeDescriptor());
      case GEOMETRY_ATTRIBUTE -> readGeometry(owner, field.attributeDescriptor(), defaultSrid);
      case FLATTENED_STRUCTURE_ATTRIBUTE -> {
        IomObject nestedOwner = navigateSingleStructure(owner, field.propertyPath().segments());
        if (nestedOwner == null) {
          yield null;
        }
        if (field.attributeDescriptor().kind().isGeometry()) {
          yield readGeometry(nestedOwner, field.attributeDescriptor(), defaultSrid);
        }
        yield readPrimitive(nestedOwner, field.attributeDescriptor());
      }
      default ->
          throw new InterlisMappingException(
              "Field <" + field.hopFieldName() + "> is not an attribute field (source "
                  + field.source() + ")");
    };
  }

  /**
   * Reads a primitive or geometry attribute value directly from an owner object, ignoring the
   * field source. Used for association attributes read from the link object.
   */
  public Object readAttributeFrom(IomObject owner, InterlisFieldPlan field, Integer defaultSrid)
      throws InterlisMappingException {
    InterlisAttributeDescriptor descriptor = field.attributeDescriptor();
    if (descriptor == null) {
      throw new InterlisMappingException(
          "Field " + field.hopFieldName() + " has no attribute descriptor");
    }
    if (descriptor.kind().isGeometry()) {
      return readGeometry(owner, descriptor, defaultSrid);
    }
    return readPrimitive(owner, descriptor);
  }

  private Object readPrimitive(IomObject owner, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    String raw = owner.getattrvalue(descriptor.name());
    if (raw == null) {
      return null;
    }
    return primitiveCodec.parse(raw, descriptor);
  }

  private Object readGeometry(
      IomObject owner, InterlisAttributeDescriptor descriptor, Integer defaultSrid)
      throws InterlisMappingException {
    if (owner.getattrvaluecount(descriptor.name()) == 0) {
      return null;
    }
    IomObject geometryObject = owner.getattrobj(descriptor.name(), 0);
    if (geometryObject == null) {
      return null;
    }
    InterlisGeometryKind geometryKind = descriptor.geometryKind();
    if (geometryKind == null) {
      throw new InterlisMappingException(
          "Geometry attribute " + descriptor.name() + " has no geometry kind");
    }
    int dimension = descriptor.coordDimension() == null ? 2 : descriptor.coordDimension();
    try {
      Geometry geometry = geometryMapper.toHopGeometry(geometryObject, geometryKind, dimension);
      if (geometry != null && defaultSrid != null) {
        geometry.setSRID(defaultSrid);
      }
      return geometry;
    } catch (Exception e) {
      throw new InterlisMappingException(
          "Failed to map geometry attribute " + descriptor.name() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Navigates through single-structure attributes; returns the owning structure or {@code null}
   * if the (optional) structure is absent.
   */
  public IomObject navigateSingleStructure(IomObject root, List<String> segments)
      throws InterlisMappingException {
    IomObject current = root;
    for (String segment : segments) {
      int count = current.getattrvaluecount(segment);
      if (count == 0) {
        return null;
      }
      if (count > 1) {
        throw new InterlisMappingException(
            "Cannot flatten multi-valued structure attribute " + segment);
      }
      IomObject next = current.getattrobj(segment, 0);
      if (next == null) {
        throw new InterlisMappingException("Expected structure object for attribute " + segment);
      }
      current = next;
    }
    return current;
  }
}
