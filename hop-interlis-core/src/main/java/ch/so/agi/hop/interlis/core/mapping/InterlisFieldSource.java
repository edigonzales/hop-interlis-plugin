package ch.so.agi.hop.interlis.core.mapping;

/** Where a Hop field of the typed row projection gets its value from. */
public enum InterlisFieldSource {
  /** Transfer object identifier (TID/OID). */
  OBJECT_ID,
  /** Basket identifier. */
  BASKET_ID,
  /** Qualified class name of the transferred object. */
  CLASS_NAME,
  /** Qualified topic name. */
  TOPIC_NAME,
  /** Transfer operation of the object. */
  OPERATION,
  /** Primitive, enum or temporal attribute at the object root. */
  PRIMITIVE_ATTRIBUTE,
  /** Geometry attribute at the object root. */
  GEOMETRY_ATTRIBUTE,
  /** Attribute below one or more flattened single structures; the leaf may be
      primitive, enum or geometry. */
  FLATTENED_STRUCTURE_ATTRIBUTE,
  /** Reference role projected as a TID reference field. */
  ROLE_REFERENCE
}
