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
 * @param associationDescriptor association descriptor (only for flattened association attributes)
 * @param structurePath precomputed outer-to-inner descriptors of flattened single structures
 */
public record InterlisFieldPlan(
    int outputIndex,
    String hopFieldName,
    InterlisFieldSource source,
    InterlisPropertyPath propertyPath,
    InterlisAttributeDescriptor attributeDescriptor,
    InterlisRoleDescriptor roleDescriptor,
    InterlisAssociationDescriptor associationDescriptor,
    java.util.List<InterlisAttributeDescriptor> structurePath) {

  public InterlisFieldPlan {
    structurePath =
        structurePath == null ? java.util.List.of() : java.util.List.copyOf(structurePath);
  }

  public InterlisFieldPlan(
      int outputIndex,
      String hopFieldName,
      InterlisFieldSource source,
      InterlisPropertyPath propertyPath,
      InterlisAttributeDescriptor attributeDescriptor,
      InterlisRoleDescriptor roleDescriptor,
      InterlisAssociationDescriptor associationDescriptor) {
    this(
        outputIndex,
        hopFieldName,
        source,
        propertyPath,
        attributeDescriptor,
        roleDescriptor,
        associationDescriptor,
        java.util.List.of());
  }

  /** {@code true} if the field carries a geometry value. */
  public boolean isGeometry() {
    return attributeDescriptor != null
        && attributeDescriptor.kind().isGeometry()
        && (source == InterlisFieldSource.GEOMETRY_ATTRIBUTE
            || source == InterlisFieldSource.FLATTENED_STRUCTURE_ATTRIBUTE);
  }
}
