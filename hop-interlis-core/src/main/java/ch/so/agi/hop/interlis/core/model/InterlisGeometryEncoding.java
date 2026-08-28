package ch.so.agi.hop.interlis.core.model;

/** IOM representation used by a geometry attribute. */
public enum InterlisGeometryEncoding {
  /** Native INTERLIS geometry object such as {@code MULTISURFACE}. */
  NATIVE,
  /** CHLV95 V1 structure {@code MultiSurface -> Surfaces -> SurfaceStructure -> Surface}. */
  CHLV95_V1_MULTISURFACE,
  /** CHLV95 V1 structure {@code MultiLine -> Lines -> LineStructure -> Line}. */
  CHLV95_V1_MULTILINE,
  /** CHLV95 V1 structure {@code MultiDirectedLine -> Lines -> DirectedLineStructure -> Line}. */
  CHLV95_V1_MULTIDIRECTED_LINE
}
