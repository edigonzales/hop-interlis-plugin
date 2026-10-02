package ch.so.agi.hop.interlis.transforms.mapping;

import org.apache.hop.metadata.api.HopMetadataProperty;

/** Persisted, editable mapping configuration; compiled before processing rows. */
public class InterlisMappedInput {
  @HopMetadataProperty private String transformName = "";
  @HopMetadataProperty private String className = "";
  @HopMetadataProperty private String objectIdField = "_ili_tid";
  @HopMetadataProperty private String basketIdField = "_ili_bid";
  @HopMetadataProperty private String operationField = "";
  @HopMetadataProperty private String sourceObjectField = "";
  @HopMetadataProperty private String structurePath = "";
  @HopMetadataProperty private String updateReferenceField = "_ili_update_ref";

  @HopMetadataProperty(groupKey = "fields", key = "field")
  private java.util.List<InterlisFieldAssignment> fields = new java.util.ArrayList<>();

  public InterlisMappedInput() {}

  public InterlisMappedInput(InterlisMappedInput source) {
    this.transformName = source.transformName;
    this.className = source.className;
    this.objectIdField = source.objectIdField;
    this.basketIdField = source.basketIdField;
    this.operationField = source.operationField;
    this.sourceObjectField = source.sourceObjectField;
    this.structurePath = source.structurePath;
    this.updateReferenceField = source.updateReferenceField;
    fields =
        source.fields.stream()
            .map(InterlisFieldAssignment::new)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
  }

  public String getTransformName() {
    return transformName;
  }

  public void setTransformName(String value) {
    transformName = value == null ? "" : value;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String value) {
    className = value == null ? "" : value;
  }

  public String getObjectIdField() {
    return objectIdField;
  }

  public void setObjectIdField(String value) {
    objectIdField = value == null ? "" : value;
  }

  public String getBasketIdField() {
    return basketIdField;
  }

  public void setBasketIdField(String value) {
    basketIdField = value == null ? "" : value;
  }

  public String getOperationField() {
    return operationField;
  }

  public void setOperationField(String value) {
    operationField = value == null ? "" : value;
  }

  public String getSourceObjectField() {
    return sourceObjectField;
  }

  public void setSourceObjectField(String value) {
    sourceObjectField = value == null ? "" : value;
  }

  public String getStructurePath() {
    return structurePath;
  }

  public void setStructurePath(String value) {
    structurePath = value == null ? "" : value;
  }

  public String getUpdateReferenceField() {
    return updateReferenceField;
  }

  public void setUpdateReferenceField(String value) {
    updateReferenceField = value == null ? "" : value;
  }

  public java.util.List<InterlisFieldAssignment> getFields() {
    return fields;
  }

  public void setFields(java.util.List<InterlisFieldAssignment> value) {
    fields = value == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(value);
  }
}
