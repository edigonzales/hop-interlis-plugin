package ch.so.agi.hop.interlis.core.model;

import java.util.List;

/**
 * Describes an INTERLIS STRUCTURE.
 *
 * @param name structure name
 * @param scopedName qualified name
 * @param attributes effective attributes in model order
 */
public record InterlisStructureDescriptor(
    String name,
    String scopedName,
    List<InterlisAttributeDescriptor> attributes,
    String baseStructure,
    boolean abstractType) {

  public InterlisStructureDescriptor(
      String name,
      String scopedName,
      List<InterlisAttributeDescriptor> attributes,
      String baseStructure) {
    this(name, scopedName, attributes, baseStructure, false);
  }

  public InterlisStructureDescriptor(
      String name, String scopedName, List<InterlisAttributeDescriptor> attributes) {
    this(name, scopedName, attributes, null);
  }

  public InterlisStructureDescriptor {
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }
}
