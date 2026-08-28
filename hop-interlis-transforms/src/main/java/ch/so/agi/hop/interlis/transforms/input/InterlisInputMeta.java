package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelContext;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Metadata of the INTERLIS Input transform: reads one INTERLIS class from an XTF file and emits
 * typed Hop rows.
 */
@Transform(
    id = "INTERLIS_INPUT",
    name = "INTERLIS Input",
    description = "Read one INTERLIS class as typed rows",
    image = "ch/so/agi/hop/interlis/transforms/input/icons/interlis-input.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "reader"})
public class InterlisInputMeta extends BaseTransformMeta<InterlisInput, InterlisInputData> {

  /** Placeholder used by the GUI for "detect models from the transfer file". */
  public static final String MODELS_FROM_DATA = InterlisModelSourceSupport.MODELS_FROM_DATA;

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String className;
  @HopMetadataProperty private boolean includeTid;
  @HopMetadataProperty private boolean includeBid;
  @HopMetadataProperty private boolean includeClassName;
  @HopMetadataProperty private boolean includeTopicName;
  @HopMetadataProperty private boolean includeOperation;
  @HopMetadataProperty private String defaultSrid;
  @HopMetadataProperty private boolean keepSourceObject;
  @HopMetadataProperty private String sourceObjectFieldName;

  /** Default name of the technical source-object carrier field. */
  public static final String DEFAULT_SOURCE_OBJECT_FIELD = "_ili_source_object";

  public InterlisInputMeta() {
    super();
  }

  @Override
  public void setDefault() {
    fileName = "";
    modelNames = MODELS_FROM_DATA;
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    className = "";
    includeTid = true;
    includeBid = true;
    includeClassName = false;
    includeTopicName = false;
    includeOperation = false;
    defaultSrid = "";
    keepSourceObject = false;
    sourceObjectFieldName = DEFAULT_SOURCE_OBJECT_FIELD;
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
      // Merge the sogeo-geometry classloader group so ValueMetaGeometry resolves.
      InterlisRuntimeSupport.initialize();
      Optional<InterlisProjectionResult> projection = tryProject(variables);
      if (projection.isEmpty()) {
        return;
      }
      IRowMeta detected =
          new HopRowSchemaFactory().createRowMeta(projection.get().plan());
      for (int i = 0; i < detected.size(); i++) {
        rowMeta.addValueMeta(detected.getValueMeta(i));
      }
      if (keepSourceObject) {
        rowMeta.addValueMeta(
            new ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject(
                resolvedSourceObjectFieldName()));
      }
      for (String warning : projection.get().plan().warnings()) {
        log.logBasic(origin + ": " + warning);
      }
    } catch (Exception e) {
      // Unresolved variables, missing repositories or files: stay quiet at design time;
      // runtime reports the same condition as a hard error.
      if (isDebug()) {
        logDebug("Unable to probe INTERLIS schema for design-time metadata: " + e.getMessage());
      }
    }
  }

  /**
   * Tries to build the projection for the current configuration; returns empty if the
   * configuration is incomplete (e.g. unresolved variables) and throws if models or the class
   * cannot be resolved.
   */
  public Optional<InterlisProjectionResult> tryProject(IVariables variables)
      throws ch.so.agi.hop.interlis.core.model.InterlisModelException,
          ch.so.agi.hop.interlis.core.mapping.InterlisMappingException {
    String resolvedClass = resolvedClassName(variables);
    if (resolvedClass.isBlank() || resolvedClass.contains("${")) {
      return Optional.empty();
    }
    Optional<InterlisModelContext> context = tryLoadModel(variables);
    if (context.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new InterlisProjectionService()
            .project(context.get(), resolvedClass, projectionOptions(variables)));
  }

  /**
   * Tries to resolve and compile the configured model without requiring a selected class.
   *
   * <p>This is the design-time path used to populate the class selector. It returns empty only
   * when the file, model directories or one of their Hop variables is not resolved yet; model and
   * repository failures are propagated so the dialog can show their actionable diagnostics.
   */
  public Optional<InterlisModelContext> tryLoadModel(IVariables variables)
      throws ch.so.agi.hop.interlis.core.model.InterlisModelException {
    String resolvedFile = resolve(variables, fileName);
    if (resolvedFile.isBlank() || resolvedFile.contains("${")) {
      return Optional.empty();
    }
    List<String> resolvedDirs = resolveModelDirectories(variables);
    if (resolvedDirs.stream().anyMatch(d -> d.contains("${"))) {
      return Optional.empty();
    }
    InterlisModelRequest request =
        new InterlisModelRequest(
            Path.of(resolvedFile), resolveModelNames(variables), resolvedDirs);
    return Optional.of(new InterlisProjectionService().loadModel(request));
  }

  /** Returns the class name after Hop-variable resolution for the dialog controller. */
  String resolvedClassName(IVariables variables) {
    return resolve(variables, className);
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
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS transfer file is required", transformMeta));
      return;
    }
    if (className == null || className.isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS class must be selected", transformMeta));
      return;
    }
    String resolved = resolve(variables, fileName);
    if (!resolved.contains("${") && !java.nio.file.Files.exists(Path.of(resolved))) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS transfer file does not exist: " + resolved, transformMeta));
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
              "INTERLIS Input is configured: "
                  + projection.plan().fieldCount()
                  + " fields projected for class "
                  + className,
              transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS model check failed: " + e.getMessage(), transformMeta));
    }
  }

  /** Builds the projection options from the persisted configuration. */
  public ProjectionOptions projectionOptions(IVariables variables) {
    return new ProjectionOptions(
        includeTid,
        includeBid,
        includeClassName,
        includeTopicName,
        includeOperation,
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

  /** The resolved name of the source-object carrier field. */
  public String resolvedSourceObjectFieldName() {
    return sourceObjectFieldName == null || sourceObjectFieldName.isBlank()
        ? DEFAULT_SOURCE_OBJECT_FIELD
        : sourceObjectFieldName;
  }

  private List<String> resolveModelNames(IVariables variables) {
    String resolved = resolve(variables, modelNames);
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories(IVariables variables) {
    String resolved = resolve(variables, modelDirectories);
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

  public boolean isIncludeClassName() {
    return includeClassName;
  }

  public void setIncludeClassName(boolean includeClassName) {
    this.includeClassName = includeClassName;
  }

  public boolean isIncludeTopicName() {
    return includeTopicName;
  }

  public void setIncludeTopicName(boolean includeTopicName) {
    this.includeTopicName = includeTopicName;
  }

  public boolean isIncludeOperation() {
    return includeOperation;
  }

  public void setIncludeOperation(boolean includeOperation) {
    this.includeOperation = includeOperation;
  }

  public String getDefaultSrid() {
    return defaultSrid;
  }

  public void setDefaultSrid(String defaultSrid) {
    this.defaultSrid = defaultSrid;
  }

  public boolean isKeepSourceObject() {
    return keepSourceObject;
  }

  public void setKeepSourceObject(boolean keepSourceObject) {
    this.keepSourceObject = keepSourceObject;
  }

  public String getSourceObjectFieldName() {
    return sourceObjectFieldName;
  }

  public void setSourceObjectFieldName(String sourceObjectFieldName) {
    this.sourceObjectFieldName = sourceObjectFieldName;
  }
}
