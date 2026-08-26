package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import java.util.List;

/**
 * Default {@link InterlisObjectToRowMapper} implementation.
 *
 * <p>The mapper is stateless: all per-field work is driven by the precomputed plan. Attribute
 * values are read by the shared {@link IomFieldReader}, which is also used by the structure
 * exploder, so class and structure fields map identically.
 */
public final class DefaultInterlisObjectToRowMapper implements InterlisObjectToRowMapper {

  private final IomFieldReader fieldReader = new IomFieldReader();

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
      case PRIMITIVE_ATTRIBUTE, GEOMETRY_ATTRIBUTE, FLATTENED_STRUCTURE_ATTRIBUTE ->
          fieldReader.read(envelope.object(), field, plan.defaultSrid());
      case ROLE_REFERENCE -> readRoleReference(envelope.object(), field.propertyPath().leafName());
    };
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
}
