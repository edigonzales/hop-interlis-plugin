package ch.so.agi.hop.interlis.core.structures;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.IomFieldReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Explodes the children of a multi-valued structure attribute of a source INTERLIS object into one
 * child row per element.
 *
 * <p>The index is the position within the structure (0-based). For {@code LIST} it is semantic; for
 * {@code BAG} it is only a deterministic technical ordering.
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
    var result = new ArrayList<ExplodedChild>();
    var cursor = cursor(source, plan);
    while (cursor.hasNext()) result.add(cursor.next());
    return result;
  }

  public interface Cursor {
    boolean hasNext();

    ExplodedChild next() throws InterlisMappingException;
  }

  public Cursor cursor(IomObject source, InterlisStructurePlan plan)
      throws InterlisMappingException {
    if (source == null)
      throw new InterlisMappingException("Source object is null for " + plan.attributeName());
    IomObject owner = fieldReader.navigateSingleStructure(source, plan.pathSegments());
    int count = owner == null ? 0 : owner.getattrvaluecount(plan.attributeName());
    return new Cursor() {
      int index;

      public boolean hasNext() {
        return index < count;
      }

      public ExplodedChild next() throws InterlisMappingException {
        if (!hasNext()) throw new java.util.NoSuchElementException();
        int position = index++;
        IomObject child;
        if (plan.primitive()) {
          var wrapper = new ch.interlis.iom_j.Iom_jObject("value", null);
          String raw = owner.getattrprim(plan.attributeName(), position);
          if (raw != null) wrapper.setattrvalue(plan.attributeName(), raw);
          else {
            var object = owner.getattrobj(plan.attributeName(), position);
            if (object == null)
              throw new InterlisMappingException("Undefined collection element " + position);
            wrapper.addattrobj(plan.attributeName(), object);
          }
          child = wrapper;
        } else {
          child = owner.getattrobj(plan.attributeName(), position);
          if (child == null || !plan.allowedChildTypes().contains(child.getobjecttag()))
            throw new InterlisMappingException(
                "Incompatible structure child of " + plan.attributeName() + " at " + position);
        }
        Object[] values = new Object[plan.childFields().size()];
        for (var field : plan.childFields())
          values[field.outputIndex()] = fieldReader.read(child, field, null);
        return new ExplodedChild(
            position, values, plan.primitive() ? null : new ch.interlis.iom_j.Iom_jObject(child));
      }
    };
  }

  public record ExplodedChild(int index, Object[] values, IomObject source) {
    public ExplodedChild(int index, Object[] values) {
      this(index, values, null);
    }
  }
}
