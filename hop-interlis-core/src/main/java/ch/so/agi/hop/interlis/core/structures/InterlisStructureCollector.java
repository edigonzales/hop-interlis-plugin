package ch.so.agi.hop.interlis.core.structures;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
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
 * child stream is the result of the downstream transformation, so children removed by a filter
 * must not reappear from the carrier. The carrier itself is never mutated; the result is a deep
 * copy with the structure replaced.
 *
 * <p>Order rules: {@code LIST} children are validated (missing/duplicate indexes, ascending order
 * when strict) and written in index order. {@code BAG} children keep their stream order; the
 * index is purely technical and ignored.
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

    List<StructureChild> ordered = validateAndOrder(children, plan, options);
    Iom_jObject copy = new Iom_jObject(carrier);
    Iom_jObject owner = navigateOrCreate(copy, plan);

    String attributeName = plan.attributeName();
    int existing = owner.getattrvaluecount(attributeName);
    for (int i = existing - 1; i >= 0; i--) {
      owner.deleteattrobj(attributeName, i);
    }

    for (StructureChild child : ordered) {
      Iom_jObject childObject = new Iom_jObject(plan.structure().scopedName(), null);
      Map<String, Iom_jObject> structureCache = new HashMap<>();
      List<InterlisPropertyDescriptor> structureProperties =
          new ArrayList<>(plan.structure().attributes());
      for (InterlisFieldPlan field : plan.childFields()) {
        Object value =
            child.values() == null || child.values().length <= field.outputIndex()
                ? null
                : child.values()[field.outputIndex()];
        fieldWriter.write(
            childObject,
            field,
            value,
            options.rowWriteOptions(),
            structureCache,
            structureProperties,
            plan.structure().scopedName());
      }
      fieldWriter.finish(
          childObject,
          plan.childFields(),
          options.rowWriteOptions(),
          plan.structure().scopedName());
      owner.addattrobj(attributeName, childObject);
    }
    return copy;
  }

  private List<StructureChild> validateAndOrder(
      List<StructureChild> children, InterlisStructurePlan plan, StructureCollectOptions options)
      throws InterlisMappingException {
    if (children == null || children.isEmpty()) {
      if (options.rowWriteOptions().strict() && plan.mandatory()) {
        throw new InterlisMappingException(
            "Mandatory structure " + plan.attributeName() + " of class "
                + plan.parentClass().scopedName() + " has no children");
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
              "Duplicate index " + child.index() + " for LIST structure " + plan.attributeName()
                  + " of class " + plan.parentClass().scopedName());
        }
      }
    }
    if (options.strictOrdering()) {
      for (int i = 0; i < sorted.size(); i++) {
        if (sorted.get(i).index() != i) {
          throw new InterlisMappingException(
              "LIST structure " + plan.attributeName() + " of class "
                  + plan.parentClass().scopedName()
                  + " has a missing index (expected index " + i + " but got "
                  + sorted.get(i).index() + ")");
        }
      }
    }
    return sorted;
  }

  /**
   * Navigates the single-structure path of the plan, creating missing (optional) structures on
   * the carrier copy so the collected children can be attached.
   */
  private Iom_jObject navigateOrCreate(Iom_jObject root, InterlisStructurePlan plan)
      throws InterlisMappingException {
    Iom_jObject owner = root;
    for (InterlisAttributeDescriptor pathAttribute : plan.pathAttributes()) {
      String name = pathAttribute.name();
      int count = owner.getattrvaluecount(name);
      if (count == 0) {
        Iom_jObject created =
            new Iom_jObject(pathAttribute.structureScopedName(), null);
        owner.addattrobj(name, created);
        owner = created;
      } else if (count == 1) {
        IomObject existing = owner.getattrobj(name, 0);
        if (!(existing instanceof Iom_jObject existingObject)) {
          throw new InterlisMappingException(
              "Expected structure object for attribute " + name + " of class "
                  + plan.parentClass().scopedName());
        }
        owner = existingObject;
      } else {
        throw new InterlisMappingException(
            "Structure attribute " + name + " of class " + plan.parentClass().scopedName()
                + " is multi-valued and cannot be part of a collect path");
      }
    }
    return owner;
  }

  /** One collected child row. */
  public record StructureChild(int index, Object[] values) {}

  /**
   * Collect options.
   *
   * @param strictOrdering require LIST children in ascending contiguous index order
   * @param failOnDuplicateIndex reject duplicate LIST indexes
   * @param rowWriteOptions write strictness for the child values
   */
  public record StructureCollectOptions(
      boolean strictOrdering, boolean failOnDuplicateIndex, RowWriteOptions rowWriteOptions) {

    public static StructureCollectOptions defaults() {
      return new StructureCollectOptions(true, true, RowWriteOptions.defaults());
    }
  }
}
