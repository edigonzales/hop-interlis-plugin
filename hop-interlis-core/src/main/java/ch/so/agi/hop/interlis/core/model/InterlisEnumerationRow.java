package ch.so.agi.hop.interlis.core.model;

/**
 * One enumeration value of a model enumeration, flattened for the INTERLIS Enumerations
 * transform.
 *
 * @param definition scoped name of the enumeration type, e.g. {@code Model.Topic.Kind}
 * @param value element name of the enumeration value, e.g. {@code one}
 * @param path scoped path of the value, e.g. {@code Kind.one} or {@code Kind.two.two_a}
 * @param parentValue parent element name, {@code null} for top-level values
 * @param depth hierarchy depth of the value (0 = top level)
 * @param isLeaf {@code true} when the value has no sub-values
 */
public record InterlisEnumerationRow(
    String definition,
    String value,
    String path,
    String parentValue,
    int depth,
    boolean isLeaf) {}
