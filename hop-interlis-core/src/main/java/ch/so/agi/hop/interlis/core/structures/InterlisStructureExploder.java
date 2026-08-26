package ch.so.agi.hop.interlis.core.structures;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.IomFieldReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Explodes the children of a multi-valued structure attribute of a source INTERLIS object into
 * one child row per element.
 *
 * <p>The index is the position within the structure (0-based). For {@code LIST} it is semantic;
 * for {@code BAG} it is only a deterministic technical ordering.
 */
public final class InterlisStructureExploder {

  private final IomFieldReader fieldReader = new IomFieldReader();

  /**
   * Explodes a source object.
   *
   * @param source the INTERLIS class object
   * @param plan the structure plan
   * @return one child per structure element in transfer order (may be empty)
   */
  public List<ExplodedChild> explode(IomObject source, InterlisStructurePlan plan)
      throws InterlisMappingException {
    if (source == null) {
      throw new InterlisMappingException(
          "Source INTERLIS object is null; cannot explode structure "
              + plan.attributeName()
              + " of class "
              + plan.parentClass().scopedName());
    }

    IomObject owner = fieldReader.navigateSingleStructure(source, plan.pathSegments());
    if (owner == null) {
      return List.of();
    }

    String attributeName = plan.attributeName();
    int count = owner.getattrvaluecount(attributeName);
    if (count == 0) {
      return List.of();
    }

    List<ExplodedChild> children = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      IomObject child = owner.getattrobj(attributeName, i);
      if (child == null) {
        throw new InterlisMappingException(
            "Expected structure object for attribute "
                + attributeName
                + " at index "
                + i
                + " of class "
                + plan.parentClass().scopedName());
      }
      Object[] values = new Object[plan.childFields().size()];
      for (InterlisFieldPlan field : plan.childFields()) {
        values[field.outputIndex()] = fieldReader.read(child, field, null);
      }
      children.add(new ExplodedChild(i, values));
    }
    return children;
  }

  /** One exploded structure child. */
  public record ExplodedChild(int index, Object[] values) {}
}
