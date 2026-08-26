package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps typed row values back to INTERLIS IOM objects according to a precomputed
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
 *       fails unless the root is a non-identifiable association (XTF link objects carry no TID);
 *   <li>{@code null} values leave optional attributes undefined; in strict mode a {@code null}
 *       value for a mandatory attribute fails;
 *   <li>flattened single structures are re-created once per structure when at least one child
 *       has a value; an entirely empty mandatory top-level structure fails in strict mode;
 *   <li>role reference fields become IOM reference objects ({@code REF} semantics), including
 *       external basket references and ORDERED order positions;
 *   <li>association rows produce a link object; class rows with flattened association attributes
 *       additionally produce the association link object when any link value is present;
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

  /** The written object plus any additionally generated link objects. */
  public record InterlisWriteResult(IomObject object, List<IomObject> additionalObjects) {

    public InterlisWriteResult {
      additionalObjects =
          additionalObjects == null ? List.of() : List.copyOf(additionalObjects);
    }

    /** All objects to write, primary first. */
    public List<IomObject> allObjects() {
      List<IomObject> all = new ArrayList<>(additionalObjects.size() + 1);
      all.add(object);
      all.addAll(additionalObjects);
      return all;
    }
  }

  /**
   * Maps row values (in plan field order) to a new IOM object plus any association link objects.
   *
   * @param values values per plan field, in plan field order
   * @param plan the projection the values were produced with
   * @param options write options
   * @return the INTERLIS object and generated link objects
   */
  public InterlisWriteResult mapAll(
      Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    String tid = requireTid(values, plan);
    Iom_jObject object = new Iom_jObject(plan.root().scopedName(), tid);
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
   * @return the merged INTERLIS object and generated link objects
   */
  public InterlisWriteResult mapAll(
      IomObject carrier, Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    if (carrier == null) {
      throw new InterlisMappingException(
          "Source INTERLIS object is null; cannot write " + plan.root().scopedName());
    }
    String tid = requireTid(values, plan);
    Iom_jObject target;
    if (tid == null || tid.equals(carrier.getobjectoid())) {
      target = new Iom_jObject(carrier);
      if (tid != null && !tid.equals(carrier.getobjectoid())) {
        target.setobjectoid(tid);
      }
    } else {
      target = new Iom_jObject(plan.root().scopedName(), tid);
      copyChildren(carrier, target);
    }
    return mapInto(target, values, plan, options);
  }

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
    return mapAll(values, plan, options).object();
  }

  /**
   * Overlays row values onto a carrier object.
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
    return mapAll(carrier, values, plan, options).object();
  }

  private InterlisWriteResult mapInto(
      Iom_jObject object, Object[] values, InterlisRowMappingPlan plan, RowWriteOptions options)
      throws InterlisMappingException {
    String tid = object.getobjectoid();
    Map<String, Iom_jObject> structureCache = new HashMap<>();
    Map<String, LinkBuilder> linkBuilders = new HashMap<>();
    List<IomObject> additionalObjects = new ArrayList<>();

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
                plan.root().effectiveProperties(),
                plan.root().scopedName());
        case ROLE_REFERENCE ->
            writeRoleReference(plan, object, field, value, linkBuilders);
        case ROLE_REFERENCE_BID ->
            writeRoleReferenceBid(plan, object, field, value, linkBuilders);
        case ROLE_ORDER_POS ->
            writeRoleOrderPos(object, field, value, plan.root().scopedName());
        case ASSOCIATION_ATTRIBUTE ->
            writeAssociationAttribute(plan, field, value, linkBuilders);
      }
    }

    for (LinkBuilder linkBuilder : linkBuilders.values()) {
      IomObject link = linkBuilder.build(options, tid);
      if (link != null) {
        additionalObjects.add(link);
      }
    }

    // Strict check for entirely empty mandatory top-level structures.
    if (options.strict() && !options.isDelete()) {
      for (InterlisPropertyDescriptor property : plan.root().effectiveProperties()) {
        if (property instanceof InterlisAttributeDescriptor attribute
            && attribute.kind().isStructure()
            && attribute.mandatory()
            && attribute.cardinality().isSingleValued()
            && object.getattrvaluecount(attribute.name()) == 0) {
          throw new InterlisMappingException(
              "Mandatory structure " + attribute.name() + " has no value for "
                  + plan.root().scopedName() + " (TID " + tid + ")");
        }
      }
    }

    return new InterlisWriteResult(object, additionalObjects);
  }

  /**
   * Collects the values of one flattenable association link: the target reference, the external
   * basket reference and the association attributes.
   */
  private final class LinkBuilder {
    private final InterlisAssociationDescriptor association;
    private final InterlisRoleDescriptor classSideRole;
    private final Map<String, Object> attributeValues = new HashMap<>();
    private String targetRefOid;
    private String targetRefBid;

    private LinkBuilder(
        InterlisAssociationDescriptor association, InterlisRoleDescriptor classSideRole) {
      this.association = association;
      this.classSideRole = classSideRole;
    }

    IomObject build(RowWriteOptions options, String classTid) throws InterlisMappingException {
      boolean anyAttribute = attributeValues.values().stream().anyMatch(v -> v != null);
      if (targetRefOid == null && !anyAttribute) {
        return null;
      }
      if (targetRefOid == null) {
        throw new InterlisMappingException(
            "Role " + classSideRole.name() + " of " + association.scopedName()
                + " has association values but no reference; the " + classSideRole.name()
                + "_ref field must be set (class TID " + classTid + ")");
      }
      Iom_jObject link = new Iom_jObject(association.scopedName(), null);
      for (InterlisRoleDescriptor role : association.roles()) {
        Iom_jObject member = new Iom_jObject("REF", null);
        if (role.name().equals(classSideRole.name())) {
          member.setobjectrefoid(targetRefOid);
          if (targetRefBid != null && !targetRefBid.isEmpty()) {
            member.setobjectrefbid(targetRefBid);
          }
        } else {
          member.setobjectrefoid(classTid);
        }
        link.addattrobj(role.name(), member);
      }
      for (InterlisAttributeDescriptor attribute : association.attributes()) {
        Object value = attributeValues.get(attribute.name());
        if (value == null) {
          if (options.strict() && attribute.mandatory()) {
            throw new InterlisMappingException(
                "Mandatory association attribute " + attribute.name() + " is null for "
                    + association.scopedName() + " (class TID " + classTid + ")");
          }
          continue;
        }
        fieldWriter.write(
            link,
            new InterlisFieldPlan(
                0,
                attribute.name(),
                attribute.kind().isGeometry()
                    ? InterlisFieldSource.GEOMETRY_ATTRIBUTE
                    : InterlisFieldSource.PRIMITIVE_ATTRIBUTE,
                InterlisPropertyPath.root(attribute.name()),
                attribute,
                null,
                null),
            value,
            options,
            null,
            association.effectiveProperties(),
            association.scopedName());
      }
      return link;
    }
  }

  private LinkBuilder linkBuilderFor(
      InterlisRowMappingPlan plan, InterlisFieldPlan field, Map<String, LinkBuilder> linkBuilders)
      throws InterlisMappingException {
    String roleName = field.roleDescriptor().name();
    InterlisAssociationDescriptor association = plan.linkResolvedRoles().get(roleName);
    if (association == null) {
      throw new InterlisMappingException(
          "Field " + field.hopFieldName() + " is not a link-resolved association role");
    }
    return linkBuilders.computeIfAbsent(
        roleName, name -> new LinkBuilder(association, field.roleDescriptor()));
  }

  private void writeAssociationAttribute(
      InterlisRowMappingPlan plan,
      InterlisFieldPlan field,
      Object value,
      Map<String, LinkBuilder> linkBuilders)
      throws InterlisMappingException {
    LinkBuilder builder = linkBuilderFor(plan, field, linkBuilders);
    builder.attributeValues.put(field.attributeDescriptor().name(), value);
  }

  private void writeRoleReference(
      InterlisRowMappingPlan plan,
      Iom_jObject object,
      InterlisFieldPlan field,
      Object value,
      Map<String, LinkBuilder> linkBuilders)
      throws InterlisMappingException {
    String roleName = field.propertyPath().leafName();
    if (plan.linkResolvedRoles().containsKey(roleName)) {
      LinkBuilder builder = linkBuilderFor(plan, field, linkBuilders);
      if (value != null && !value.toString().trim().isEmpty()) {
        builder.targetRefOid = value.toString().trim();
      }
      return;
    }
    if (value == null) {
      return;
    }
    String referenceTid = value.toString().trim();
    if (referenceTid.isEmpty()) {
      return;
    }
    Iom_jObject reference = new Iom_jObject("REF", null);
    reference.setobjectrefoid(referenceTid);
    object.addattrobj(roleName, reference);
  }

  private void writeRoleReferenceBid(
      InterlisRowMappingPlan plan,
      Iom_jObject object,
      InterlisFieldPlan field,
      Object value,
      Map<String, LinkBuilder> linkBuilders)
      throws InterlisMappingException {
    String roleName = field.propertyPath().leafName();
    if (value == null || value.toString().trim().isEmpty()) {
      return;
    }
    String referenceBid = value.toString().trim();
    if (plan.linkResolvedRoles().containsKey(roleName)) {
      LinkBuilder builder = linkBuilderFor(plan, field, linkBuilders);
      builder.targetRefBid = referenceBid;
      return;
    }
    Iom_jObject reference =
        object.getattrvaluecount(roleName) == 0
            ? null
            : (Iom_jObject) object.getattrobj(roleName, 0);
    if (reference == null) {
      reference = new Iom_jObject("REF", null);
      object.addattrobj(roleName, reference);
    }
    reference.setobjectrefbid(referenceBid);
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
    boolean tidOptional =
        plan.root() instanceof InterlisAssociationDescriptor association
            && !association.identifiable();
    if (tid == null && !tidOptional) {
      throw new InterlisMappingException(
          "No object identifier (TID) for " + plan.root().scopedName()
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

  private void writeRoleOrderPos(
      Iom_jObject owner, InterlisFieldPlan field, Object value, String contextName)
      throws InterlisMappingException {
    String roleName = field.propertyPath().leafName();
    if (value == null) {
      return;
    }
    Iom_jObject reference =
        owner.getattrvaluecount(roleName) == 0
            ? null
            : (Iom_jObject) owner.getattrobj(roleName, 0);
    if (reference == null) {
      throw new InterlisMappingException(
          "Role " + roleName + " of " + contextName
              + " has an order position but no reference; the role reference field must be set");
    }
    reference.setobjectreforderpos(((Number) value).longValue());
  }
}
