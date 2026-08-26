package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import java.util.HashMap;
import java.util.Map;

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
 *
 * <p>When a base (carrier) object is given, the row values are overlaid onto a deep copy of it:
 * structures already present on the carrier are reused, and multi-valued structures collected
 * downstream (see {@code InterlisStructureCollector}) are preserved. If the row TID differs from
 * the carrier TID, a new object with the row TID is created and the carrier's content is copied.
 */
public final class RowToIomMapper {

  private final IomFieldWriter fieldWriter = new IomFieldWriter();

  /**
   * Maps row values (in plan field order) to a new IOM object.
   *
   * @param values values per plan field, in plan field order
   * @param plan the projection the values were produced with
   * @param options write options
   * @return the INTERLIS object
   */
  public IomObject map(Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    String tid = requireTid(values, plan);
    Iom_jObject object = new Iom_jObject(plan.classDescriptor().scopedName(), tid);
    return mapInto(object, values, plan, options);
  }

  /**
   * Overlays row values onto a carrier object (e.g. the {@code _ili_source_object} kept by
   * INTERLIS Input and updated by INTERLIS Structure Collect).
   *
   * <p>The carrier is never mutated; the result is a deep copy with the row values applied.
   *
   * @param carrier the base object, must not be {@code null}
   * @param values values per plan field, in plan field order
   * @param plan the projection the values were produced with
   * @param options write options
   * @return the merged INTERLIS object
   */
  public IomObject map(
      IomObject carrier, Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    if (carrier == null) {
      throw new InterlisMappingException(
          "Source INTERLIS object is null; cannot write class "
              + plan.classDescriptor().scopedName());
    }
    String tid = requireTid(values, plan);
    Iom_jObject target;
    if (tid.equals(carrier.getobjectoid())) {
      target = new Iom_jObject(carrier);
    } else {
      target = new Iom_jObject(plan.classDescriptor().scopedName(), tid);
      copyChildren(carrier, target);
    }
    return mapInto(target, values, plan, options);
  }

  private IomObject mapInto(
      Iom_jObject object, Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    String tid = object.getobjectoid();
    Map<String, Iom_jObject> structureCache = new HashMap<>();

    for (InterlisFieldPlan field : plan.fields()) {
      Object value = values[field.outputIndex()];
      switch (field.source()) {
        case OBJECT_ID, BASKET_ID, CLASS_NAME, TOPIC_NAME, OPERATION ->
            /* consumed above or handled by the caller */ { }
        case PRIMITIVE_ATTRIBUTE, GEOMETRY_ATTRIBUTE, FLATTENED_STRUCTURE_ATTRIBUTE ->
            fieldWriter.write(
                object,
                field,
                value,
                options,
                structureCache,
                plan.classDescriptor().effectiveProperties(),
                plan.classDescriptor().scopedName());
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

  private String requireTid(Object[] values, InterlisRowMappingPlan plan)
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
    return tid;
  }

  /** Deep-copies all children of the source into a target that already has a fresh TID. */
  private void copyChildren(IomObject source, Iom_jObject target) {
    for (int i = 0; i < source.getattrcount(); i++) {
      String name = source.getattrname(i);
      int count = source.getattrvaluecount(name);
      for (int j = 0; j < count; j++) {
        IomObject child = source.getattrobj(name, j);
        if (child != null) {
          target.addattrobj(name, new Iom_jObject(child));
        } else {
          target.addattrvalue(name, source.getattrprim(name, j));
        }
      }
    }
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
}
