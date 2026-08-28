package ch.so.agi.hop.interlis.transforms.rowtoobject;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.List;
import java.util.Optional;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata of the INTERLIS Row to Object transform: maps typed class rows back to canonical
 * envelope rows (stable envelope schema), so several classes can be merged into one stream for
 * INTERLIS Transfer Output.
 */
@Transform(
    id = "INTERLIS_ROW_TO_OBJECT",
    name = "INTERLIS Row to Object",
    description = "Map typed class rows back to canonical envelope rows",
    image = "ch/so/agi/hop/interlis/transforms/rowtoobject/icons/interlis-row-to-object.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "envelope", "object", "map"})
public class InterlisRowToObjectMeta
    extends BaseTransformMeta<InterlisRowToObject, InterlisRowToObjectData> {

  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String className;
  @HopMetadataProperty private String basketIdField;

  public InterlisRowToObjectMeta() {
    super();
  }

  @Override
  public void setDefault() {
    modelNames = "";
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    className = "";
    basketIdField = "_ili_bid";
  }

  /**
   * Tries to build the projection for the current configuration; returns empty if the
   * configuration is incomplete and throws if models or the class cannot be resolved.
   */
  public Optional<InterlisProjectionResult> tryProject(IVariables variables)
      throws InterlisModelException, InterlisMappingException {
    List<String> resolvedModels = resolveModelNames(variables);
    if (resolvedModels.isEmpty()) {
      return Optional.empty();
    }
    List<String> resolvedDirs = resolveModelDirectories(variables);
    if (resolvedDirs.stream().anyMatch(d -> d.contains("${"))) {
      return Optional.empty();
    }
    if (resolve(variables, className).isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new InterlisProjectionService()
            .project(
                new InterlisModelRequest(null, resolvedModels, resolvedDirs),
                resolve(variables, className),
                projectionOptions()));
  }

  /** Builds the projection options from the persisted configuration. */
  public ProjectionOptions projectionOptions() {
    return new ProjectionOptions(true, true, false, false, false, true, "_", null,
        java.util.Set.of());
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
    // The output is the constant envelope schema.
    try {
      rowMeta.clear();
      rowMeta.addRowMeta(InterlisEnvelopeSchemaFactory.createRowMeta());
    } catch (HopException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
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
    if (resolve(variables, className).isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS class must be selected", transformMeta));
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
              "INTERLIS Row to Object is configured for class " + className, transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS model check failed: " + e.getMessage(), transformMeta));
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

  public String getBasketIdField() {
    return basketIdField;
  }

  public void setBasketIdField(String basketIdField) {
    this.basketIdField = basketIdField;
  }
}
