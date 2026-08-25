package ch.so.agi.hop.interlis.core.model;

/**
 * A property of an INTERLIS class or structure: an attribute or a role.
 *
 * <p>{@code inherited} marks properties contributed by a base class; effective property lists
 * contain inherited and declared properties in stable model order.
 */
public sealed interface InterlisPropertyDescriptor
    permits InterlisAttributeDescriptor, InterlisRoleDescriptor {

  String name();

  String scopedName();

  InterlisCardinality cardinality();

  boolean inherited();
}
