package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.geometry.InterlisGeometryMapper;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;

/**
 * Maps typed row values back to an INTERLIS IOM object according to a precomputed
 * {@link InterlisRowMappingPlan}.
 *
 * <p>The mapper is stateless and Hop-free: the row values are passed in plan field order; the
 * caller binds the incoming Hop row to that order beforehand (see
 * {@code InterlisRowBindings} in the transforms module).
 *
 * <p>Write rules:
 *
 * <ul>
 *   <li>the object identifier comes from the projected {@code OBJECT_ID} field; a missing TID
 *       fails regardless of strictness;
 *   <li>{@code null} values leave optional attributes undefined; in strict mode a {@code null}
 *       value for a mandatory attribute fails;
 *   <li>flattened single structures are re-created once per structure when at least one child
 *       has a value; an entirely empty mandatory top-level structure fails in strict mode;
 *   <li>role reference fields become IOM reference objects ({@code REF} semantics);
 *   <li>geometry values are converted through the SQL/MM WKB bridge, so arcs are preserved.
 * </ul>
 */
public final class RowToIomMapper {

  private final InterlisPrimitiveCodec primitiveCodec = new InterlisPrimitiveCodec();
  private final InterlisGeometryMapper geometryMapper = new InterlisGeometryMapper();

  /**
   * Maps row values (in plan field order) to an IOM object.
   *
   * @param values values per plan field, in plan field order
   * @param plan the projection the values were produced with
   * @param options write options
   * @return the INTERLIS object
   */
  public IomObject map(Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    if (values == null || values.length != plan.fieldCount()) {
      throw new InterlisMappingException(
          "Expected " + plan.fieldCount() + " values but got "
              + (values == null ? 0 : values.length));
    }

    String tid = null;
    for (InterlisFieldPlan field : plan.fields()) {
      if (field.source() == InterlisFieldSource.OBJECT_ID) {
        Object value = values[field.outputIndex()];
        tid = value == null ? null : value.toString().trim();
        if (tid != null && tid.isEmpty()) {
          tid = null;
        }
      }
    }
    if (tid == null) {
      throw new InterlisMappingException(
          "No object identifier (TID) for class " + plan.classDescriptor().scopedName()
              + "; the projection must include _ili_tid");
    }

    Iom_jObject object = new Iom_jObject(plan.classDescriptor().scopedName(), tid);
    Map<String, Iom_jObject> structureCache = new HashMap<>();

    for (InterlisFieldPlan field : plan.fields()) {
      Object value = values[field.outputIndex()];
      switch (field.source()) {
        case OBJECT_ID, BASKET_ID, CLASS_NAME, TOPIC_NAME, OPERATION ->
            /* consumed above or handled by the caller */ { }
        case PRIMITIVE_ATTRIBUTE ->
            writePrimitive(object, field, value, options, plan.classDescriptor().scopedName());
        case GEOMETRY_ATTRIBUTE ->
            writeGeometry(object, field, value, options, plan.classDescriptor().scopedName());
        case FLATTENED_STRUCTURE_ATTRIBUTE ->
            writeFlattened(object, field, value, options, structureCache, plan);
        case ROLE_REFERENCE ->
            writeRoleReference(object, field, value, plan.classDescriptor().scopedName());
      }
    }

    // Strict check for entirely empty mandatory top-level structures.
    if (options.strict()) {
      for (InterlisPropertyDescriptor property : plan.classDescriptor().effectiveProperties()) {
        if (property instanceof InterlisAttributeDescriptor attribute
            && attribute.kind().isStructure()
            && attribute.mandatory()
            && attribute.cardinality().isSingleValued()
            && object.getattrvaluecount(attribute.name()) == 0) {
          throw new InterlisMappingException(
              "Mandatory structure " + attribute.name() + " has no value for class "
                  + plan.classDescriptor().scopedName() + " (TID " + tid + ")");
        }
      }
    }

    return object;
  }

  private void writePrimitive(
      Iom_jObject owner,
      InterlisFieldPlan field,
      Object value,
      RowWriteOptions options,
      String className)
      throws InterlisMappingException {
    if (value == null) {
      requireNotMandatory(field, className, options);
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
      String className)
      throws InterlisMappingException {
    if (value == null) {
      requireNotMandatory(field, className, options);
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
      InterlisRowMappingPlan plan)
      throws InterlisMappingException {
    List<String> segments = field.propertyPath().segments();
    boolean structureExists =
        structureCache.containsKey(String.join(".", segments));

    if (value == null) {
      // A missing optional structure leaves all its children undefined. If the structure
      // already exists (another child was set), mandatory leaves are enforced in strict mode.
      if (structureExists) {
        requireNotMandatory(field, plan.classDescriptor().scopedName(), options);
      }
      return;
    }

    Iom_jObject owner = root;
    for (int i = 0; i < segments.size(); i++) {
      String key = String.join(".", segments.subList(0, i + 1));
      Iom_jObject child = structureCache.get(key);
      if (child == null) {
        InterlisAttributeDescriptor structureAttribute =
            findStructureAttribute(plan, segments.get(i));
        child = new Iom_jObject(structureAttribute.structureScopedName(), null);
        owner.addattrobj(segments.get(i), child);
        structureCache.put(key, child);
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
            leaf.name(),
            geometryMapper.toIomGeometry(geometry, leaf.geometryKind(), dimension));
      } catch (Exception e) {
        throw new InterlisMappingException(
            "Failed to convert geometry field " + field.hopFieldName() + ": " + e.getMessage(), e);
      }
    } else {
      owner.setattrvalue(leaf.name(), primitiveCodec.format(value, leaf));
    }
  }

  private InterlisAttributeDescriptor findStructureAttribute(
      InterlisRowMappingPlan plan, String attributeName) throws InterlisMappingException {
    for (InterlisPropertyDescriptor property : plan.classDescriptor().effectiveProperties()) {
      if (property instanceof InterlisAttributeDescriptor attribute
          && attribute.name().equals(attributeName)) {
        return attribute;
      }
    }
    throw new InterlisMappingException(
        "Structure attribute " + attributeName + " not found in class "
            + plan.classDescriptor().scopedName());
  }

  private void writeRoleReference(
      Iom_jObject owner, InterlisFieldPlan field, Object value, String className)
      throws InterlisMappingException {
    if (value == null) {
      return;
    }
    String referenceTid = value.toString().trim();
    if (referenceTid.isEmpty()) {
      return;
    }
    Iom_jObject reference = new Iom_jObject("REF", null);
    reference.setobjectrefoid(referenceTid);
    owner.addattrobj(field.propertyPath().leafName(), reference);
  }

  private void requireNotMandatory(
      InterlisFieldPlan field, String className, RowWriteOptions options)
      throws InterlisMappingException {
    InterlisAttributeDescriptor descriptor = field.attributeDescriptor();
    if (options.strict() && descriptor != null && descriptor.mandatory()) {
      throw new InterlisMappingException(
          "Mandatory attribute " + descriptor.name() + " is null for class " + className);
    }
  }
}
