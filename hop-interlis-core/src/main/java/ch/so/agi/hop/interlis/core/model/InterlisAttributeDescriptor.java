package ch.so.agi.hop.interlis.core.model;

/**
 * Describes one attribute of an INTERLIS class or structure.
 *
 * @param name attribute name
 * @param scopedName qualified name, e.g. {@code Model.Topic.Class.Attr}
 * @param cardinality multiplicity
 * @param mandatory whether at least one value is required
 * @param kind attribute kind from the Hop perspective
 * @param typeName human-readable INTERLIS type, e.g. {@code TEXT*80} or {@code 0 .. 999}
 * @param inherited whether the attribute is contributed by a base class
 * @param geometryKind geometry kind for {@link InterlisAttributeKind#GEOMETRY}, else {@code null}
 * @param coordDimension 2 or 3 for coordinate-based geometries, else {@code null}
 * @param allowsArcs whether the geometry domain allows circular arcs
 * @param structureScopedName qualified name of the structure type, else {@code null}
 */
public record InterlisAttributeDescriptor(
    String name,
    String scopedName,
    InterlisCardinality cardinality,
    boolean mandatory,
    InterlisAttributeKind kind,
    String typeName,
    boolean inherited,
    InterlisGeometryKind geometryKind,
    Integer coordDimension,
    boolean allowsArcs,
    String structureScopedName)
    implements InterlisPropertyDescriptor {}
