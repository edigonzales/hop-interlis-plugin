package ch.so.agi.hop.interlis.transforms.mapping;

import org.apache.hop.metadata.api.HopMetadataProperty;

/** Persisted, editable mapping configuration; compiled before processing rows. */
public class InterlisFieldAssignment {
  @HopMetadataProperty private String sourceField = "";
  @HopMetadataProperty private String targetPath = "";

  public InterlisFieldAssignment() {}

  public InterlisFieldAssignment(InterlisFieldAssignment source) {
    this.sourceField = source.sourceField;
    this.targetPath = source.targetPath;
  }

  public InterlisFieldAssignment(String sourceField, String targetPath) {
    this.sourceField = sourceField;
    this.targetPath = targetPath;
  }

  public String getSourceField() {
    return sourceField;
  }

  public void setSourceField(String value) {
    sourceField = value == null ? "" : value;
  }

  public String getTargetPath() {
    return targetPath;
  }

  public void setTargetPath(String value) {
    targetPath = value == null ? "" : value;
  }
}
