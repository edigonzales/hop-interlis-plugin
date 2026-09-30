package ch.so.agi.hop.interlis.core.structures;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.IomFieldWriter;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisPropertyDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects child rows of a multi-valued structure attribute back into a carrier INTERLIS object.
 *
 * <p>The collected structure always <em>replaces</em> the structure content of the carrier: the
 * child stream is the result of the downstream transformation, so children removed by a filter must
 * not reappear from the carrier. The carrier itself is never mutated; the result is a deep copy
 * with the structure replaced.
 *
 * <p>Order rules: {@code LIST} children are validated (missing/duplicate indexes, ascending order
 * when strict) and written in index order. {@code BAG} children keep their stream order; the index
 * is purely technical and ignored.
 */
public final class InterlisStructureCollector {

  private final IomFieldWriter fieldWriter = new IomFieldWriter();

  /**
   * Replaces the target structure of a carrier object with the collected children.
   *
   * @param carrier the carrier object (e.g. {@code _ili_source_object}), must not be {@code null}
   * @param children the collected children in stream order
   * @param plan the structure plan
   * @param options collect options
   * @return a deep copy of the carrier with the structure replaced
   */
  public IomObject collect(
      IomObject carrier,
      List<StructureChild> children,
      InterlisStructurePlan plan,
      StructureCollectOptions options)
      throws InterlisMappingException {
    if (carrier == null) {
      throw new InterlisMappingException(
          "Source INTERLIS object is null; cannot collect structure "
              + plan.attributeName()
              + " of class "
              + plan.parentClass().scopedName());
    }

    return collectOrdered(
        carrier, validateAndOrder(children, plan, options).iterator(), plan, options);
  }

  /** Consumes an already ordered, possibly disk-backed child cursor without retaining rows. */
  public IomObject collectOrdered(
      IomObject carrier,
      java.util.Iterator<StructureChild> children,
      InterlisStructurePlan plan,
      StructureCollectOptions options)
      throws InterlisMappingException {
    if (carrier == null)
      throw new InterlisMappingException("Source object is null: " + plan.attributeName());
    Iom_jObject copy = new Iom_jObject(carrier);
    Iom_jObject owner = navigateOrCreate(copy, plan);
    String attributeName = plan.attributeName();
    owner.setattrundefined(attributeName);
    long count = 0;
    int previous = -1;
    while (children.hasNext()) {
      StructureChild child = children.next();
      if (child.index() < 0)
        throw new InterlisMappingException("Negative child index: " + child.index());
      if (plan.ordered()) {
        if (options.failOnDuplicateIndex() && child.index() == previous)
          throw new InterlisMappingException(
              "Duplicate index " + child.index() + " of " + attributeName);
        if (options.strictOrdering() && child.index() != count)
          throw new InterlisMappingException(
              "LIST " + attributeName + " has a missing index (expected index " + count + ")");
      }
      previous = child.index();
      count++;
      if (plan.primitive()) {
        Object value = child.values()[0];
        if (value == null)
          throw new InterlisMappingException("Null collection element: " + attributeName);
        var wrapper = new Iom_jObject("value", null);
        fieldWriter.write(
            wrapper,
            plan.childFields().getFirst(),
            value,
            options.rowWriteOptions(),
            new HashMap<>(),
            List.of(plan.structureAttribute()),
            plan.parentClass().scopedName());
        String raw = wrapper.getattrprim(attributeName, 0);
        if (raw != null) owner.addattrvalue(attributeName, raw);
        else owner.addattrobj(attributeName, wrapper.getattrobj(attributeName, 0));
        continue;
      }
      Iom_jObject childObject;
      if (options.preserve()) {
        if (child.source() == null
            || !plan.allowedChildTypes().contains(child.source().getobjecttag()))
          throw new InterlisMappingException(
              "Missing or incompatible child source object for " + attributeName);
        childObject = new Iom_jObject(child.source());
      } else {
        if (!plan.allowedChildTypes().contains(plan.structure().scopedName()))
          throw new InterlisMappingException(
              "Cannot rebuild the abstract or restricted structure "
                  + plan.structure().scopedName()
                  + "; use PRESERVE with a permitted concrete child");
        childObject = new Iom_jObject(plan.structure().scopedName(), null);
      }
      Map<String, Iom_jObject> structureCache = new HashMap<>();
      List<InterlisPropertyDescriptor> properties = new ArrayList<>(plan.structure().attributes());
      for (var field : plan.childFields()) {
        Object value = child.values()[field.outputIndex()];
        fieldWriter.write(
            childObject,
            field,
            value,
            options.rowWriteOptions(),
            structureCache,
            properties,
            plan.structure().scopedName());
      }
      fieldWriter.finish(
          childObject,
          plan.childFields(),
          options.rowWriteOptions(),
          plan.structure().scopedName());
      owner.addattrobj(attributeName, childObject);
    }
    if (count < plan.structureAttribute().cardinality().min()
        || count > plan.structureAttribute().cardinality().max())
      throw new InterlisMappingException(
          "Cardinality "
              + plan.structureAttribute().cardinality()
              + " violated by "
              + count
              + " children of "
              + plan.parentClass().scopedName()
              + "."
              + attributeName);
    return copy;
  }

  private List<StructureChild> validateAndOrder(
      List<StructureChild> children, InterlisStructurePlan plan, StructureCollectOptions options)
      throws InterlisMappingException {
    if (children == null || children.isEmpty()) {
      if (options.rowWriteOptions().strict() && plan.mandatory()) {
        throw new InterlisMappingException(
            "Mandatory structure "
                + plan.attributeName()
                + " of class "
                + plan.parentClass().scopedName()
                + " has no children");
      }
      return List.of();
    }

    if (!plan.ordered()) {
      // BAG: stream order, no index semantics.
      return List.copyOf(children);
    }

    List<StructureChild> sorted = new ArrayList<>(children);
    sorted.sort(Comparator.comparingInt(StructureChild::index));

    Set<Integer> seen = new HashSet<>();
    for (StructureChild child : children) {
      if (!seen.add(child.index())) {
        if (options.failOnDuplicateIndex()) {
          throw new InterlisMappingException(
              "Duplicate index "
                  + child.index()
                  + " for LIST structure "
                  + plan.attributeName()
                  + " of class "
                  + plan.parentClass().scopedName());
        }
      }
    }
    if (options.strictOrdering()) {
      for (int i = 0; i < sorted.size(); i++) {
        if (sorted.get(i).index() != i) {
          throw new InterlisMappingException(
              "LIST structure "
                  + plan.attributeName()
                  + " of class "
                  + plan.parentClass().scopedName()
                  + " has a missing index (expected index "
                  + i
                  + " but got "
                  + sorted.get(i).index()
                  + ")");
        }
      }
    }
    return sorted;
  }

  /**
   * Navigates the single-structure path of the plan, creating missing (optional) structures on the
   * carrier copy so the collected children can be attached.
   */
  private Iom_jObject navigateOrCreate(Iom_jObject root, InterlisStructurePlan plan)
      throws InterlisMappingException {
    Iom_jObject owner = root;
    for (InterlisAttributeDescriptor pathAttribute : plan.pathAttributes()) {
      String name = pathAttribute.name();
      int count = owner.getattrvaluecount(name);
      if (count == 0) {
        Iom_jObject created = new Iom_jObject(pathAttribute.structureScopedName(), null);
        owner.addattrobj(name, created);
        owner = created;
      } else if (count == 1) {
        IomObject existing = owner.getattrobj(name, 0);
        if (!(existing instanceof Iom_jObject existingObject)) {
          throw new InterlisMappingException(
              "Expected structure object for attribute "
                  + name
                  + " of class "
                  + plan.parentClass().scopedName());
        }
        owner = existingObject;
      } else {
        throw new InterlisMappingException(
            "Structure attribute "
                + name
                + " of class "
                + plan.parentClass().scopedName()
                + " is multi-valued and cannot be part of a collect path");
      }
    }
    return owner;
  }

  /** One collected child row. */
  public record StructureChild(int index, Object[] values, IomObject source) {
    public StructureChild(int index, Object[] values) {
      this(index, values, null);
    }
  }

  /**
   * Collect options.
   *
   * @param strictOrdering require LIST children in ascending contiguous index order
   * @param failOnDuplicateIndex reject duplicate LIST indexes
   * @param rowWriteOptions write strictness for the child values
   */
  public record StructureCollectOptions(
      boolean strictOrdering,
      boolean failOnDuplicateIndex,
      RowWriteOptions rowWriteOptions,
      boolean preserve) {
    public StructureCollectOptions(
        boolean strictOrdering, boolean failOnDuplicateIndex, RowWriteOptions rowWriteOptions) {
      this(strictOrdering, failOnDuplicateIndex, rowWriteOptions, false);
    }

    public static StructureCollectOptions defaults() {
      return new StructureCollectOptions(true, true, RowWriteOptions.defaults());
    }
  }
}
