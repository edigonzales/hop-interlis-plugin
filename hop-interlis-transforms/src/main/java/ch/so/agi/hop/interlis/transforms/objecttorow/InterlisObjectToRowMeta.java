package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
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
 * Metadata of the INTERLIS Object to Row transform: projects the {@code _ili_object} payload of
 * envelope rows onto typed class rows.
 */
@Transform(
    id = "INTERLIS_OBJECT_TO_ROW",
    name = "INTERLIS Object to Row",
    description = "Project envelope object payloads onto typed class rows",
    image = "ch/so/agi/hop/interlis/transforms/objecttorow/icons/interlis-object-to-row.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "envelope", "object", "map"})
public class InterlisObjectToRowMeta
    extends BaseTransformMeta<InterlisObjectToRow, InterlisObjectToRowData> {

  public static final String DEFAULT_OBJECT_FIELD = "_ili_object";

  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String className;
  @HopMetadataProperty private String objectFieldName;
  @HopMetadataProperty private boolean includeTid;
  @HopMetadataProperty private boolean includeBid;
  @HopMetadataProperty private boolean appendEnvelopeFields;
  @HopMetadataProperty private String defaultSrid;

  public InterlisObjectToRowMeta() {
    super();
  }

  @Override
  public void setDefault() {
    modelNames = "";
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    className = "";
    objectFieldName = DEFAULT_OBJECT_FIELD;
    includeTid = true;
    includeBid = true;
    appendEnvelopeFields = false;
    defaultSrid = "";
  }

  /**
   * Tries to build the projection for the current configuration; returns empty if the configuration
   * is incomplete (e.g. unresolved variables) and throws if models or the class cannot be resolved.
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
                projectionOptions(variables)));
  }

  /** Builds the projection options from the persisted configuration. */
  public ProjectionOptions projectionOptions(IVariables variables) {
    return new ProjectionOptions(
        includeTid,
        includeBid,
        false,
        false,
        false,
        true,
        "_",
        resolvedDefaultSrid(variables).orElse(null),
        java.util.Set.of());
  }

  /** Resolves the configured default SRID; empty or unparsable yields {@code null}. */
  public Optional<Integer> resolvedDefaultSrid(IVariables variables) {
    String resolved = resolve(variables, defaultSrid);
    if (resolved.isBlank() || resolved.contains("${")) {
      return Optional.empty();
    }
    try {
      return Optional.of(Integer.parseInt(resolved.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
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
    // Design-time probe: failures must never make the dialog unusable.
    try {
      InterlisRuntimeSupport.initialize();
      Optional<InterlisProjectionResult> projection = tryProject(variables);
      if (projection.isEmpty()) {
        return;
      }
      var output =
          InterlisObjectToRowOutputPlan.create(
              rowMeta, projection.get().plan(), appendEnvelopeFields);
      rowMeta.clear();
      rowMeta.addRowMeta(output.rowMeta());
      for (String warning : projection.get().plan().warnings()) {
        log.logBasic(origin + ": " + warning);
      }
    } catch (HopTransformException e) {
      throw e;
    } catch (Exception e) {
      if (isDebug()) {
        logDebug("Unable to probe INTERLIS schema for design-time metadata: " + e.getMessage());
      }
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
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS class must be selected", transformMeta));
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
      if (prev != null && !prev.isEmpty()) {
        ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings.bind(
            prev, resolve(variables, objectFieldName), false);
        InterlisObjectToRowOutputPlan.create(prev, projection.plan(), appendEnvelopeFields);
      }
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              "INTERLIS Object to Row is configured: "
                  + projection.plan().fieldCount()
                  + " fields projected for class "
                  + className,
              transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "INTERLIS model check failed: " + e.getMessage(),
              transformMeta));
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

  public String getObjectFieldName() {
    return objectFieldName;
  }

  public void setObjectFieldName(String objectFieldName) {
    this.objectFieldName = objectFieldName;
  }

  public boolean isIncludeTid() {
    return includeTid;
  }

  public void setIncludeTid(boolean includeTid) {
    this.includeTid = includeTid;
  }

  public boolean isIncludeBid() {
    return includeBid;
  }

  public void setIncludeBid(boolean includeBid) {
    this.includeBid = includeBid;
  }

  public boolean isAppendEnvelopeFields() {
    return appendEnvelopeFields;
  }

  public void setAppendEnvelopeFields(boolean appendEnvelopeFields) {
    this.appendEnvelopeFields = appendEnvelopeFields;
  }

  public String getDefaultSrid() {
    return defaultSrid;
  }

  public void setDefaultSrid(String defaultSrid) {
    this.defaultSrid = defaultSrid;
  }
}
