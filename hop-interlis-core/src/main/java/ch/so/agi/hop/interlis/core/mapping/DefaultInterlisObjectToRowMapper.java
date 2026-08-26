package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;

/**
 * Default {@link InterlisObjectToRowMapper} implementation.
 *
 * <p>The mapper is stateless: all per-field work is driven by the precomputed plan. Attribute
 * values are read by the shared {@link IomFieldReader}, which is also used by the structure
 * exploder, so class and structure fields map identically. Fields of flattenable attributed
 * associations are resolved from the association link object via the lookup.
 */
public final class DefaultInterlisObjectToRowMapper implements InterlisObjectToRowMapper {

  private final IomFieldReader fieldReader = new IomFieldReader();

  @Override
  public Object[] map(InterlisObjectEnvelope envelope, InterlisRowMappingPlan mappingPlan)
      throws InterlisMappingException {
    return map(envelope, mappingPlan, null);
  }

  @Override
  public Object[] map(
      InterlisObjectEnvelope envelope,
      InterlisRowMappingPlan mappingPlan,
      InterlisAssociationLinkLookup linkLookup)
      throws InterlisMappingException {
    if (envelope == null || envelope.object() == null) {
      throw new InterlisMappingException("Cannot map a null INTERLIS object envelope");
    }

    Object[] row = new Object[mappingPlan.fieldCount()];
    for (InterlisFieldPlan field : mappingPlan.fields()) {
      try {
        row[field.outputIndex()] = readField(envelope, mappingPlan, field, linkLookup);
      } catch (Exception e) {
        throw new InterlisMappingException(
            "Failed to map field <"
                + field.hopFieldName()
                + "> of "
                + mappingPlan.root().scopedName()
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
      InterlisFieldPlan field,
      InterlisAssociationLinkLookup linkLookup)
      throws InterlisMappingException {
    return switch (field.source()) {
      case OBJECT_ID -> envelope.objectId();
      case BASKET_ID -> envelope.basketId();
      case CLASS_NAME -> envelope.className();
      case TOPIC_NAME -> envelope.topicName();
      case OPERATION -> envelope.operation() == null ? null : envelope.operation().name();
      case PRIMITIVE_ATTRIBUTE, GEOMETRY_ATTRIBUTE, FLATTENED_STRUCTURE_ATTRIBUTE ->
          fieldReader.read(envelope.object(), field, plan.defaultSrid());
      case ROLE_REFERENCE ->
          readRoleReference(envelope.object(), plan, field, linkLookup, false);
      case ROLE_REFERENCE_BID ->
          readRoleReference(envelope.object(), plan, field, linkLookup, true);
      case ROLE_ORDER_POS -> readRoleOrderPos(envelope.object(), field);
      case ASSOCIATION_ATTRIBUTE -> readAssociationAttribute(envelope.object(), plan, field, linkLookup);
    };
  }

  private Object readRoleReference(
      IomObject owner,
      InterlisRowMappingPlan plan,
      InterlisFieldPlan field,
      InterlisAssociationLinkLookup linkLookup,
      boolean basketId)
      throws InterlisMappingException {
    String roleName = field.propertyPath().leafName();
    InterlisAssociationDescriptor association = plan.linkResolvedRoles().get(roleName);
    if (association != null) {
      IomObject link =
          linkLookup == null ? null : linkLookup.find(owner.getobjectoid(), roleName);
      IomObject member = link == null ? null : link.getattrobj(roleName, 0);
      if (member == null) {
        return null;
      }
      String value = basketId ? member.getobjectrefbid() : member.getobjectrefoid();
      return value == null || value.isEmpty() ? null : value;
    }
    if (owner.getattrvaluecount(roleName) == 0) {
      return null;
    }
    IomObject reference = owner.getattrobj(roleName, 0);
    if (reference == null) {
      return null;
    }
    String value = basketId ? reference.getobjectrefbid() : reference.getobjectrefoid();
    return value == null || value.isEmpty() ? null : value;
  }

  private Object readRoleOrderPos(IomObject owner, InterlisFieldPlan field)
      throws InterlisMappingException {
    String roleName = field.propertyPath().leafName();
    if (owner.getattrvaluecount(roleName) == 0) {
      return null;
    }
    IomObject reference = owner.getattrobj(roleName, 0);
    if (reference == null) {
      return null;
    }
    return reference.getobjectreforderpos();
  }

  private Object readAssociationAttribute(
      IomObject owner,
      InterlisRowMappingPlan plan,
      InterlisFieldPlan field,
      InterlisAssociationLinkLookup linkLookup)
      throws InterlisMappingException {
    String roleName = field.roleDescriptor().name();
    IomObject link =
        linkLookup == null ? null : linkLookup.find(owner.getobjectoid(), roleName);
    if (link == null) {
      return null;
    }
    return fieldReader.readAttributeFrom(link, field, plan.defaultSrid());
  }
}
