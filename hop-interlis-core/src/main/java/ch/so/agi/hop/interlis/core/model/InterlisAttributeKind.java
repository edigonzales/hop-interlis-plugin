package ch.so.agi.hop.interlis.core.model;

/** Kind of an INTERLIS attribute from the perspective of the Hop projection. */
public enum InterlisAttributeKind {
  /** TEXT, MTEXT, NAME, URI, BOOLEAN, numeric and date/time domains. */
  PRIMITIVE,
  /** Enumeration-valued attribute. */
  ENUM,
  /** Coordinate, polyline, surface or area geometry. */
  GEOMETRY,
  /** Single- or multi-valued structure attribute. */
  STRUCTURE
}
