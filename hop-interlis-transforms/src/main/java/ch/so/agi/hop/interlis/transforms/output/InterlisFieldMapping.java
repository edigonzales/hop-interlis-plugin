package ch.so.agi.hop.interlis.transforms.output;

/**
 * One row of the INTERLIS Output mapping grid.
 *
 * @param property INTERLIS property (technical field or attribute/role path)
 * @param hopField auto-mapped Hop field name
 * @param type INTERLIS type label
 * @param status mapping status
 */
public record InterlisFieldMapping(
    String property, String hopField, String type, String status) {}
