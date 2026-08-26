package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.geometry.InterlisGeometryMapper;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisGeometryKind;
import java.util.List;
import org.locationtech.jts.geom.Geometry;

/**
 * Default {@link InterlisObjectToRowMapper} implementation.
 *
 * <p>The mapper is stateless: all per-field work is driven by the precomputed plan. Geometry
 * values go through the SQL/MM WKB bridge, so arcs are preserved.
 */
public final class DefaultInterlisObjectToRowMapper implements InterlisObjectToRowMapper {

  private final InterlisPrimitiveCodec primitiveCodec = new InterlisPrimitiveCodec();
  private final InterlisGeometryMapper geometryMapper = new InterlisGeometryMapper();

  @Override
  public Object[] map(InterlisObjectEnvelope envelope, InterlisRowMappingPlan mappingPlan)
      throws InterlisMappingException {
    if (envelope == null || envelope.object() == null) {
      throw new InterlisMappingException("Cannot map a null INTERLIS object envelope");
    }

    Object[] row = new Object[mappingPlan.fieldCount()];
    for (InterlisFieldPlan field : mappingPlan.fields()) {
      try {
        row[field.outputIndex()] = readField(envelope, mappingPlan, field);
      } catch (Exception e) {
        throw new InterlisMappingException(
            "Failed to map field <"
                + field.hopFieldName()
                + "> of class "
                + envelope.className()
                + " (TID "
                + envelope.objectId()
                + ", BID "
                + envelope.basketId()
                + "): "
                + e.getMessage(),
            e);
      }
    }
    return row;
  }

  private Object readField(
      InterlisObjectEnvelope envelope,
      InterlisRowMappingPlan plan,
      InterlisFieldPlan field)
      throws InterlisMappingException {
    return switch (field.source()) {
      case OBJECT_ID -> envelope.objectId();
      case BASKET_ID -> envelope.basketId();
      case CLASS_NAME -> envelope.className();
      case TOPIC_NAME -> envelope.topicName();
      case OPERATION -> envelope.operation() == null ? null : envelope.operation().name();
      case PRIMITIVE_ATTRIBUTE ->
          readPrimitive(envelope.object(), List.of(), field.attributeDescriptor());
      case GEOMETRY_ATTRIBUTE ->
          readGeometry(envelope.object(), List.of(), field.attributeDescriptor(), plan.defaultSrid());
      case FLATTENED_STRUCTURE_ATTRIBUTE -> {
        IomObject owner = navigateSingleStructure(envelope.object(), field.propertyPath().segments());
        if (owner == null) {
          yield null;
        }
        if (field.attributeDescriptor().kind().isGeometry()) {
          yield readGeometry(owner, List.of(), field.attributeDescriptor(), plan.defaultSrid());
        }
        yield readPrimitive(owner, List.of(), field.attributeDescriptor());
      }
      case ROLE_REFERENCE -> readRoleReference(envelope.object(), field.propertyPath().leafName());
    };
  }

  private Object readPrimitive(
      IomObject owner, List<String> pathSegments, InterlisAttributeDescriptor descriptor)
      throws InterlisMappingException {
    String raw = owner.getattrvalue(descriptor.name());
    if (raw == null) {
      return null;
    }
    return primitiveCodec.parse(raw, descriptor);
  }

  private Object readGeometry(
      IomObject owner,
      List<String> pathSegments,
      InterlisAttributeDescriptor descriptor,
      Integer defaultSrid)
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
      Geometry geometry =
          geometryMapper.toHopGeometry(geometryObject, geometryKind, dimension);
      if (geometry != null && defaultSrid != null) {
        geometry.setSRID(defaultSrid);
      }
      return geometry;
    } catch (Exception e) {
      throw new InterlisMappingException(
          "Failed to map geometry attribute " + descriptor.name() + ": " + e.getMessage(), e);
    }
  }

  private String readRoleReference(IomObject owner, String roleName)
      throws InterlisMappingException {
    if (owner.getattrvaluecount(roleName) == 0) {
      return null;
    }
    IomObject reference = owner.getattrobj(roleName, 0);
    if (reference == null) {
      return null;
    }
    String oid = reference.getobjectrefoid();
    return "".equals(oid) ? null : oid;
  }

  /**
   * Navigates through single-structure attributes; returns the owning structure or {@code null}
   * if the (optional) structure is absent.
   */
  private IomObject navigateSingleStructure(IomObject root, List<String> segments)
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
        throw new InterlisMappingException(
            "Expected structure object for attribute " + segment);
      }
      current = next;
    }
    return current;
  }
}
