package ch.so.agi.hop.interlis.core.model;

/**
 * Describes a role of an INTERLIS class or association.
 *
 * @param name role name
 * @param scopedName qualified name
 * @param cardinality multiplicity
 * @param inherited whether the role is contributed by a base class
 * @param targetClassScopedName qualified name of the role's target class
 * @param ordered whether the role is declared ORDERED
 * @param associationScopedName qualified name of the association the role belongs to, or
 *     {@code null} if unknown
 */
public record InterlisRoleDescriptor(
    String name,
    String scopedName,
    InterlisCardinality cardinality,
    boolean inherited,
    String targetClassScopedName,
    boolean ordered,
    String associationScopedName)
    implements InterlisPropertyDescriptor {}
