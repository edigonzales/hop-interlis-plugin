package ch.so.agi.hop.interlis.transforms;

/** One row displayed by an INTERLIS dialog preview table. */
public record InterlisPreviewRow(String fieldName, String hopType, String source) {

  public InterlisPreviewRow {
    fieldName = fieldName == null ? "" : fieldName;
    hopType = hopType == null ? "" : hopType;
    source = source == null ? "" : source;
  }
}
