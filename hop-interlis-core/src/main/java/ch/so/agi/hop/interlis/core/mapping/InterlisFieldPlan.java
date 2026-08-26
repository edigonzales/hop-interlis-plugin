package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor;

/**
 * One field of the typed row projection: its output position, name, value source and the model
 * descriptors driving the conversion.
 *
 * @param outputIndex target index in the row array
 * @param hopFieldName Hop field name
 * @param source where the value comes from
 * @param propertyPath path inside the IOM object tree
 * @param attributeDescriptor leaf attribute descriptor (null for technical fields and role refs)
 * @param roleDescriptor role descriptor (only for role/association fields)
 * @param associationDescriptor association descriptor (only for flattened association
 *     attributes)
 */
public record InterlisFieldPlan(
    int outputIndex,
    String hopFieldName,
    InterlisFieldSource source,
    InterlisPropertyPath propertyPath,
    InterlisAttributeDescriptor attributeDescriptor,
    InterlisRoleDescriptor roleDescriptor,
    InterlisAssociationDescriptor associationDescriptor) {

  /** {@code true} if the field carries a geometry value. */
  public boolean isGeometry() {
    return attributeDescriptor != null
        && attributeDescriptor.kind().isGeometry()
        && (source == InterlisFieldSource.GEOMETRY_ATTRIBUTE
            || source == InterlisFieldSource.FLATTENED_STRUCTURE_ATTRIBUTE);
  }
}
