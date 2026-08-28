package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata of the INTERLIS Output transform: writes one INTERLIS class from typed Hop rows to an
 * XTF file.
 */
@Transform(
    id = "INTERLIS_OUTPUT",
    name = "INTERLIS Output",
    description = "Write one INTERLIS class as typed rows to an XTF file",
    image = "ch/so/agi/hop/interlis/transforms/output/icons/interlis-output.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "writer"})
public class InterlisOutputMeta extends BaseTransformMeta<InterlisOutput, InterlisOutputData> {

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String className;
  @HopMetadataProperty private String objectIdField;
  @HopMetadataProperty private String basketIdField;
  @HopMetadataProperty private String basketId;
  @HopMetadataProperty private String operationField;
  @HopMetadataProperty private String sourceObjectField;
  @HopMetadataProperty private boolean overwrite;

  public InterlisOutputMeta() {
    super();
  }

  @Override
  public void setDefault() {
    fileName = "";
    modelNames = "";
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    className = "";
    objectIdField = "_ili_tid";
    basketIdField = "_ili_bid";
    basketId = "b1";
    operationField = "";
    sourceObjectField = "";
    overwrite = false;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    // The output transform does not change the schema.
  }

  /**
   * Tries to build the projection for the current configuration; returns empty if the
   * configuration is incomplete (e.g. unresolved variables) and throws if models or the class
   * cannot be resolved.
   */
  public Optional<InterlisProjectionResult> tryProject(IVariables variables)
      throws ch.so.agi.hop.interlis.core.model.InterlisModelException,
          ch.so.agi.hop.interlis.core.mapping.InterlisMappingException {
    String resolvedFile = resolve(variables, fileName);
    if (resolvedFile.isBlank() || resolvedFile.contains("${")) {
      return Optional.empty();
    }
    List<String> resolvedDirs = resolveModelDirectories(variables);
    if (resolvedDirs.stream().anyMatch(d -> d.contains("${"))) {
      return Optional.empty();
    }
    if (resolve(variables, className).isBlank()) {
      return Optional.empty();
    }
    List<String> resolvedModels = resolveModelNames(variables);
    if (resolvedModels.isEmpty()) {
      throw new ch.so.agi.hop.interlis.core.model.InterlisModelException(
          "INTERLIS Output requires explicit model names; %DATA cannot be used because no "
              + "transfer file is read");
    }
    InterlisModelRequest request =
        new InterlisModelRequest(null, resolvedModels, resolvedDirs);
    return Optional.of(
        new InterlisProjectionService()
            .project(request, resolve(variables, className), projectionOptions(variables)));
  }

  /** Builds the projection options from the persisted configuration. */
  public ProjectionOptions projectionOptions(IVariables variables) {
    return new ProjectionOptions(
        true, true, false, false, false, true, "_", null, java.util.Set.of());
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (fileName == null || fileName.isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS output file is required", transformMeta));
      return;
    }
    if (className == null || className.isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS class must be selected", transformMeta));
      return;
    }
    if (objectIdField == null || objectIdField.isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "Object ID (TID) field is required", transformMeta));
      return;
    }
    try {
      InterlisProjectionResult projection = tryProject(variables).orElse(null);
      if (projection == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                "INTERLIS model cannot be resolved yet (variables or repositories unresolved)",
                transformMeta));
        return;
      }
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              "INTERLIS Output is configured: writing class "
                  + className
                  + " to "
                  + resolve(variables, fileName),
              transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS model check failed: " + e.getMessage(), transformMeta));
    }
  }

  private List<String> resolveModelNames(IVariables variables) {
    String resolved = resolve(variables, modelNames);
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories(IVariables variables) {
    String resolved = resolve(variables, modelDirectories);
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }

  // -- accessors -----------------------------------------------------------

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getModelNames() {
    return modelNames;
  }

  public void setModelNames(String modelNames) {
    this.modelNames = modelNames;
  }

  public String getModelDirectories() {
    return modelDirectories;
  }

  public void setModelDirectories(String modelDirectories) {
    this.modelDirectories = modelDirectories;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getObjectIdField() {
    return objectIdField;
  }

  public void setObjectIdField(String objectIdField) {
    this.objectIdField = objectIdField;
  }

  public String getBasketIdField() {
    return basketIdField;
  }

  public void setBasketIdField(String basketIdField) {
    this.basketIdField = basketIdField;
  }

  public String getBasketId() {
    return basketId;
  }

  public void setBasketId(String basketId) {
    this.basketId = basketId;
  }

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean overwrite) {
    this.overwrite = overwrite;
  }

  public String getSourceObjectField() {
    return sourceObjectField;
  }

  public void setSourceObjectField(String sourceObjectField) {
    this.sourceObjectField = sourceObjectField;
  }

  public String getOperationField() {
    return operationField;
  }

  public void setOperationField(String operationField) {
    this.operationField = operationField;
  }
}
