package ch.so.agi.hop.interlis.core.mapping;

import ch.interlis.iom.IomObject;

/**
 * Resolves association link objects for flattened association attributes.
 *
 * <p>INTERLIS Input buffers the link objects of attributed associations while streaming the
 * transfer and passes a lookup to the row mapper, so class rows can carry {@code <role>_ref} and
 * {@code <role>_<attribute>} values even though XTF encodes attributed associations as separate
 * link objects.
 */
@FunctionalInterface
public interface InterlisAssociationLinkLookup {

  /** Link lookup without matches (returns {@code null} for every key). */
  InterlisAssociationLinkLookup EMPTY = (objectTid, roleName) -> null;

  /**
   * Finds the association link object for a class object.
   *
   * @param objectTid TID of the class object the row is mapped from
   * @param roleName role name of the flattenable association role on the class
   * @return the link object, or {@code null} if there is none
   */
  IomObject find(String objectTid, String roleName);
}
