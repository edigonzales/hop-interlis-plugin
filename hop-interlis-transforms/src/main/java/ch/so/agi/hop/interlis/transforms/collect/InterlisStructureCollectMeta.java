package ch.so.agi.hop.interlis.transforms.collect;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionResult;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionService;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
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
 * Metadata of the INTERLIS Structure Collect transform: collects child rows back into the
 * multi-valued structure of a parent row's source object.
 *
 * <p>Both input streams must be sorted: the parent stream by parent key and the child stream by
 * parent key plus index.
 */
@Transform(
    id = "INTERLIS_STRUCTURE_COLLECT",
    name = "INTERLIS Structure Collect",
    description = "Collect child rows back into a LIST/BAG OF structure",
    image = "ch/so/agi/hop/interlis/transforms/collect/icons/interlis-structure-collect.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "structure", "collect"})
public class InterlisStructureCollectMeta
    extends BaseTransformMeta<InterlisStructureCollect, InterlisStructureCollectData> {

  public static final String DEFAULT_SOURCE_OBJECT_FIELD = "_ili_source_object";
  public static final String DEFAULT_PARENT_KEY_FIELD = "_ili_tid";
  public static final String DEFAULT_CHILD_PARENT_KEY_FIELD = "_ili_parent_tid";
  public static final String DEFAULT_CHILD_INDEX_FIELD = "_ili_index";

  @HopMetadataProperty private String parentInputTransform;
  @HopMetadataProperty private String childInputTransform;
  @HopMetadataProperty private String parentKeyField;
  @HopMetadataProperty private String childParentKeyField;
  @HopMetadataProperty private String childIndexField;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String className;
  @HopMetadataProperty private String structureAttributePath;
  @HopMetadataProperty private String sourceObjectField;
  @HopMetadataProperty private boolean strictOrdering;
  @HopMetadataProperty private boolean failOnDuplicateIndex;
  @HopMetadataProperty private boolean failOnChildWithoutParent;

  public InterlisStructureCollectMeta() {
    super();
  }

  @Override
  public void setDefault() {
    parentInputTransform = "";
    childInputTransform = "";
    parentKeyField = DEFAULT_PARENT_KEY_FIELD;
    childParentKeyField = DEFAULT_CHILD_PARENT_KEY_FIELD;
    childIndexField = DEFAULT_CHILD_INDEX_FIELD;
    modelNames = "";
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    className = "";
    structureAttributePath = "";
    sourceObjectField = DEFAULT_SOURCE_OBJECT_FIELD;
    strictOrdering = true;
    failOnDuplicateIndex = true;
    failOnChildWithoutParent = true;
  }

  /**
   * Tries to build the structure projection for the current configuration; returns empty if the
   * configuration is incomplete and throws if models or the class cannot be resolved.
   */
  public Optional<InterlisStructureProjectionResult> tryStructurePlan(IVariables variables)
      throws InterlisModelException, InterlisMappingException {
    List<String> resolvedModels = resolveModelNames(variables);
    if (resolvedModels.isEmpty()) {
      return Optional.empty();
    }
    List<String> resolvedDirs = resolveModelDirectories(variables);
    if (resolvedDirs.stream().anyMatch(d -> d.contains("${"))) {
      return Optional.empty();
    }
    if (resolve(variables, className).isBlank()
        || resolve(variables, structureAttributePath).isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new InterlisStructureProjectionService()
            .project(
                new InterlisModelRequest(null, resolvedModels, resolvedDirs),
                resolve(variables, className),
                resolve(variables, structureAttributePath),
                projectionOptions()));
  }

  /** Builds the projection options from the persisted configuration. */
  public ProjectionOptions projectionOptions() {
    return new ProjectionOptions(
        false, false, false, false, false, true, "_", null, java.util.Set.of());
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
    // The parent stream passes through unchanged; the collected structure is written into the
    // technical source-object carrier field.
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
    if (resolve(variables, childInputTransform).isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "The child input transform must be selected", transformMeta));
      return;
    }
    if (resolve(variables, className).isBlank() || resolve(variables, structureAttributePath).isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS class and structure must be selected", transformMeta));
      return;
    }
    try {
      InterlisStructureProjectionResult projection = tryStructurePlan(variables).orElse(null);
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
              "INTERLIS Structure Collect is configured: structure "
                  + projection.plan().attributeName()
                  + " collected into "
                  + resolve(variables, sourceObjectField),
              transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS structure check failed: " + e.getMessage(), transformMeta));
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

  public String getParentInputTransform() {
    return parentInputTransform;
  }

  public void setParentInputTransform(String parentInputTransform) {
    this.parentInputTransform = parentInputTransform;
  }

  public String getChildInputTransform() {
    return childInputTransform;
  }

  public void setChildInputTransform(String childInputTransform) {
    this.childInputTransform = childInputTransform;
  }

  public String getParentKeyField() {
    return parentKeyField;
  }

  public void setParentKeyField(String parentKeyField) {
    this.parentKeyField = parentKeyField;
  }

  public String getChildParentKeyField() {
    return childParentKeyField;
  }

  public void setChildParentKeyField(String childParentKeyField) {
    this.childParentKeyField = childParentKeyField;
  }

  public String getChildIndexField() {
    return childIndexField;
  }

  public void setChildIndexField(String childIndexField) {
    this.childIndexField = childIndexField;
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

  public String getStructureAttributePath() {
    return structureAttributePath;
  }

  public void setStructureAttributePath(String structureAttributePath) {
    this.structureAttributePath = structureAttributePath;
  }

  public String getSourceObjectField() {
    return sourceObjectField;
  }

  public void setSourceObjectField(String sourceObjectField) {
    this.sourceObjectField = sourceObjectField;
  }

  public boolean isStrictOrdering() {
    return strictOrdering;
  }

  public void setStrictOrdering(boolean strictOrdering) {
    this.strictOrdering = strictOrdering;
  }

  public boolean isFailOnDuplicateIndex() {
    return failOnDuplicateIndex;
  }

  public void setFailOnDuplicateIndex(boolean failOnDuplicateIndex) {
    this.failOnDuplicateIndex = failOnDuplicateIndex;
  }

  public boolean isFailOnChildWithoutParent() {
    return failOnChildWithoutParent;
  }

  public void setFailOnChildWithoutParent(boolean failOnChildWithoutParent) {
    this.failOnChildWithoutParent = failOnChildWithoutParent;
  }
}
