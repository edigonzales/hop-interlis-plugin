package ch.so.agi.hop.interlis.transforms.explode;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionResult;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionService;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata of the INTERLIS Structure Explode transform: explodes one multi-valued structure
 * attribute of a typed parent row (carried by a hidden source-object field) into one child row
 * per element.
 */
@Transform(
    id = "INTERLIS_STRUCTURE_EXPLODE",
    name = "INTERLIS Structure Explode",
    description = "Explode a LIST/BAG OF structure into child rows",
    image = "ch/so/agi/hop/interlis/transforms/explode/icons/interlis-structure-explode.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "structure", "explode"})
public class InterlisStructureExplodeMeta
    extends BaseTransformMeta<InterlisStructureExplode, InterlisStructureExplodeData> {

  public static final String DEFAULT_SOURCE_OBJECT_FIELD = "_ili_source_object";
  public static final String DEFAULT_PARENT_TID_FIELD = "_ili_tid";
  public static final String DEFAULT_PARENT_BID_FIELD = "_ili_bid";
  public static final String DEFAULT_PARENT_KEY_FIELD = "_ili_parent_tid";
  public static final String DEFAULT_PARENT_BID_KEY_FIELD = "_ili_parent_bid";
  public static final String DEFAULT_INDEX_FIELD = "_ili_index";

  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String className;
  @HopMetadataProperty private String sourceObjectField;
  @HopMetadataProperty private String parentTidField;
  @HopMetadataProperty private String parentBidField;
  @HopMetadataProperty private boolean emitParentBid;
  @HopMetadataProperty private String structureAttributePath;
  @HopMetadataProperty private String parentKeyFieldName;
  @HopMetadataProperty private String parentBidKeyFieldName;
  @HopMetadataProperty private String indexFieldName;
  @HopMetadataProperty private boolean emitIndexForBag;
  @HopMetadataProperty private List<String> selectedChildFields;
  @HopMetadataProperty private List<String> includeParentFields;

  public InterlisStructureExplodeMeta() {
    super();
  }

  @Override
  public void setDefault() {
    modelNames = "";
    modelDirectories = "";
    className = "";
    sourceObjectField = DEFAULT_SOURCE_OBJECT_FIELD;
    parentTidField = DEFAULT_PARENT_TID_FIELD;
    parentBidField = DEFAULT_PARENT_BID_FIELD;
    emitParentBid = true;
    structureAttributePath = "";
    parentKeyFieldName = DEFAULT_PARENT_KEY_FIELD;
    parentBidKeyFieldName = DEFAULT_PARENT_BID_KEY_FIELD;
    indexFieldName = DEFAULT_INDEX_FIELD;
    emitIndexForBag = true;
    selectedChildFields = new ArrayList<>();
    includeParentFields = new ArrayList<>();
  }

  /**
   * Tries to build the structure projection for the current configuration; returns empty if the
   * configuration is incomplete (e.g. unresolved variables) and throws if models or the class
   * cannot be resolved.
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
    if (resolve(variables, className).isBlank() || resolve(variables, structureAttributePath).isBlank()) {
      return Optional.empty();
    }
    InterlisModelRequest request = new InterlisModelRequest(null, resolvedModels, resolvedDirs);
    return Optional.of(
        new InterlisStructureProjectionService()
            .project(
                request,
                resolve(variables, className),
                resolve(variables, structureAttributePath),
                projectionOptions()));
  }

  /** Builds the projection options from the persisted configuration. */
  public ProjectionOptions projectionOptions() {
    return new ProjectionOptions(
        false,
        false,
        false,
        false,
        false,
        true,
        "_",
        null,
        new LinkedHashSet<>(selectedChildFields == null ? List.of() : selectedChildFields));
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
      Optional<InterlisStructureProjectionResult> projection = tryStructurePlan(variables);
      if (projection.isEmpty()) {
        return;
      }
      InterlisStructurePlan plan = projection.get().plan();

      IRowMeta inputFields = new RowMeta();
      inputFields.addRowMeta(rowMeta);
      rowMeta.clear();

      rowMeta.addValueMeta(
          new org.apache.hop.core.row.value.ValueMetaString(resolvedParentKeyFieldName()));
      if (emitParentBid) {
        rowMeta.addValueMeta(
            new org.apache.hop.core.row.value.ValueMetaString(resolvedParentBidKeyFieldName()));
      }
      if (plan.ordered() || emitIndexForBag) {
        rowMeta.addValueMeta(
            new org.apache.hop.core.row.value.ValueMetaInteger(resolvedIndexFieldName()));
      }
      var schemaFactory = new ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory();
      for (var field : plan.childFields()) {
        rowMeta.addValueMeta(schemaFactory.createValueMeta(field));
      }
      for (String parentFieldName : includeParentFields == null
          ? List.<String>of()
          : includeParentFields) {
        IValueMeta parentField = inputFields.searchValueMeta(parentFieldName);
        if (parentField != null) {
          rowMeta.addValueMeta(parentField.clone());
        }
      }
      for (String warning : plan.warnings()) {
        log.logBasic(origin + ": " + warning);
      }
    } catch (Exception e) {
      if (isDebug()) {
        logDebug("Unable to probe INTERLIS structure schema for design-time metadata: "
            + e.getMessage());
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
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS class must be selected", transformMeta));
      return;
    }
    if (resolve(variables, structureAttributePath).isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "A multi-valued structure attribute must be selected", transformMeta));
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
              "INTERLIS Structure Explode is configured: structure "
                  + projection.plan().attributeName()
                  + " ("
                  + projection.plan().structure().scopedName()
                  + ") explodes into "
                  + projection.plan().childFields().size()
                  + " child fields",
              transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS structure check failed: " + e.getMessage(), transformMeta));
    }
  }

  /** Names of the available multi-valued structure paths for the configured class. */
  public List<String> multiValuedStructurePaths(IVariables variables) throws Exception {
    return tryStructurePlan(variables)
        .map(r -> List.<String>of())
        .orElseGet(() -> List.of());
  }

  private List<String> resolveModelNames(IVariables variables) {
    String resolved = resolve(variables, modelNames);
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(","))
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .toList();
  }

  private List<String> resolveModelDirectories(IVariables variables) {
    String resolved = resolve(variables, modelDirectories);
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(";"))
        .map(String::trim)
        .filter(d -> !d.isEmpty())
        .toList();
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }

  public String resolvedParentKeyFieldName() {
    return parentKeyFieldName == null || parentKeyFieldName.isBlank()
        ? DEFAULT_PARENT_KEY_FIELD
        : parentKeyFieldName;
  }

  public String resolvedParentBidKeyFieldName() {
    return parentBidKeyFieldName == null || parentBidKeyFieldName.isBlank()
        ? DEFAULT_PARENT_BID_KEY_FIELD
        : parentBidKeyFieldName;
  }

  public String resolvedIndexFieldName() {
    return indexFieldName == null || indexFieldName.isBlank() ? DEFAULT_INDEX_FIELD : indexFieldName;
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

  public String getSourceObjectField() {
    return sourceObjectField;
  }

  public void setSourceObjectField(String sourceObjectField) {
    this.sourceObjectField = sourceObjectField;
  }

  public String getParentTidField() {
    return parentTidField;
  }

  public void setParentTidField(String parentTidField) {
    this.parentTidField = parentTidField;
  }

  public String getParentBidField() {
    return parentBidField;
  }

  public void setParentBidField(String parentBidField) {
    this.parentBidField = parentBidField;
  }

  public boolean isEmitParentBid() {
    return emitParentBid;
  }

  public void setEmitParentBid(boolean emitParentBid) {
    this.emitParentBid = emitParentBid;
  }

  public String getStructureAttributePath() {
    return structureAttributePath;
  }

  public void setStructureAttributePath(String structureAttributePath) {
    this.structureAttributePath = structureAttributePath;
  }

  public String getParentKeyFieldName() {
    return parentKeyFieldName;
  }

  public void setParentKeyFieldName(String parentKeyFieldName) {
    this.parentKeyFieldName = parentKeyFieldName;
  }

  public String getParentBidKeyFieldName() {
    return parentBidKeyFieldName;
  }

  public void setParentBidKeyFieldName(String parentBidKeyFieldName) {
    this.parentBidKeyFieldName = parentBidKeyFieldName;
  }

  public String getIndexFieldName() {
    return indexFieldName;
  }

  public void setIndexFieldName(String indexFieldName) {
    this.indexFieldName = indexFieldName;
  }

  public boolean isEmitIndexForBag() {
    return emitIndexForBag;
  }

  public void setEmitIndexForBag(boolean emitIndexForBag) {
    this.emitIndexForBag = emitIndexForBag;
  }

  public List<String> getSelectedChildFields() {
    return selectedChildFields;
  }

  public void setSelectedChildFields(List<String> selectedChildFields) {
    this.selectedChildFields = selectedChildFields;
  }

  public List<String> getIncludeParentFields() {
    return includeParentFields;
  }

  public void setIncludeParentFields(List<String> includeParentFields) {
    this.includeParentFields = includeParentFields;
  }

  public Set<String> selectedChildFieldSet() {
    return new LinkedHashSet<>(selectedChildFields == null ? List.of() : selectedChildFields);
  }
}
