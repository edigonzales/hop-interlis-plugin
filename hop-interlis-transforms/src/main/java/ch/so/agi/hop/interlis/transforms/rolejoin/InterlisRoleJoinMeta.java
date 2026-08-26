package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata of the INTERLIS Role Join transform: joins the fields of a role's target class onto
 * the main stream, driven by the model (role, target class, key fields).
 */
@Transform(
    id = "INTERLIS_ROLE_JOIN",
    name = "INTERLIS Role Join",
    description = "Join the target class fields of a reference role onto the main stream",
    image = "ch/so/agi/hop/interlis/transforms/rolejoin/icons/interlis-role-join.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "role", "join"})
public class InterlisRoleJoinMeta
    extends BaseTransformMeta<InterlisRoleJoin, InterlisRoleJoinData> {

  public static final String DEFAULT_LOOKUP_TID_FIELD = "_ili_tid";
  public static final long DEFAULT_MAX_LOOKUP_ROWS = 500_000;

  @HopMetadataProperty private String mainInputTransform;
  @HopMetadataProperty private String lookupInputTransform;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String mainClassName;
  @HopMetadataProperty private String roleName;
  @HopMetadataProperty private String mainReferenceField;
  @HopMetadataProperty private String lookupTidField;
  @HopMetadataProperty private String prefix;
  @HopMetadataProperty private List<String> lookupFields;
  @HopMetadataProperty private boolean failOnMissingMandatoryReference;
  @HopMetadataProperty private boolean failOnDuplicateTid;
  @HopMetadataProperty private long maxLookupRows;

  public InterlisRoleJoinMeta() {
    super();
  }

  @Override
  public void setDefault() {
    mainInputTransform = "";
    lookupInputTransform = "";
    modelNames = "";
    modelDirectories = "";
    mainClassName = "";
    roleName = "";
    mainReferenceField = "";
    lookupTidField = DEFAULT_LOOKUP_TID_FIELD;
    prefix = "";
    lookupFields = new java.util.ArrayList<>();
    failOnMissingMandatoryReference = true;
    failOnDuplicateTid = true;
    maxLookupRows = DEFAULT_MAX_LOOKUP_ROWS;
  }

  /**
   * Probes the model configuration: resolves the role and its target class.
   *
   * @throws InterlisModelException if models or class cannot be resolved
   * @throws InterlisMappingException if the role cannot be resolved
   */
  public InterlisRoleJoinProbeResult probeRole(IVariables variables)
      throws InterlisModelException, InterlisMappingException {
    List<String> modelNames = resolveModelNames(variables);
    if (modelNames.isEmpty()) {
      throw new InterlisModelException("No INTERLIS models configured");
    }
    ch.so.agi.hop.interlis.core.model.CompiledInterlisModel model =
        new ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl()
            .compile(
                new ch.so.agi.hop.interlis.core.model.ModelSource(
                    List.of(), modelNames, resolveModelDirectories(variables)),
                ch.so.agi.hop.interlis.core.model.ModelCompileOptions.defaults());
    ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor schema =
        new ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor()
            .extract(model.transferDescription());
    String mainClass = resolve(variables, mainClassName);
    if (mainClass.isBlank()) {
      throw new InterlisModelException("No INTERLIS class selected");
    }
    InterlisClassDescriptor classDescriptor =
        schema
            .findClass(mainClass)
            .orElseThrow(
                () ->
                    new InterlisModelException(
                        "Class " + mainClass + " was not found in models " + modelNames));
    String role = resolve(variables, roleName);
    InterlisRoleDescriptor roleDescriptor =
        classDescriptor.roles().stream()
            .filter(r -> r.name().equals(role))
            .findFirst()
            .orElseThrow(
                () ->
                    new InterlisMappingException(
                        "Role " + role + " not found on class " + mainClass
                            + "; available roles: "
                            + classDescriptor.roles().stream()
                                .map(InterlisRoleDescriptor::name)
                                .toList()));
    InterlisClassDescriptor target =
        schema
            .findClass(roleDescriptor.targetClassScopedName())
            .orElseThrow(
                () ->
                    new InterlisModelException(
                        "Target class " + roleDescriptor.targetClassScopedName()
                            + " of role " + role + " was not found in models " + modelNames));
    return new InterlisRoleJoinProbeResult(model, schema, classDescriptor, roleDescriptor, target);
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
      ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport.initialize();
      InterlisRoleJoinProbeResult result = probeRole(variables);
      String resolvedPrefix = resolvedPrefix(result);
      for (String lookupField : effectiveLookupFields(result)) {
        var field =
            result.target().attributes().stream()
                .filter(a -> a.name().equals(lookupField))
                .findFirst()
                .orElse(null);
        if (field == null) {
          continue;
        }
        var valueMeta =
            new ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory()
                .createValueMeta(
                    new ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan(
                        0,
                        resolvedPrefix + field.name(),
                        field.kind().isGeometry()
                            ? ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource
                                .GEOMETRY_ATTRIBUTE
                            : ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource
                                .PRIMITIVE_ATTRIBUTE,
                        ch.so.agi.hop.interlis.core.mapping.InterlisPropertyPath.root(field.name()),
                        field,
                        null,
                        null));
        rowMeta.addValueMeta(valueMeta);
      }
    } catch (Exception e) {
      if (isDebug()) {
        logDebug("Unable to probe INTERLIS role for design-time metadata: " + e.getMessage());
      }
    }
  }

  @Override
  public void check(
      List<org.apache.hop.core.ICheckResult> remarks,
      org.apache.hop.pipeline.PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (resolve(variables, lookupInputTransform).isBlank()) {
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_ERROR,
              "The lookup input transform must be selected",
              transformMeta));
      return;
    }
    if (resolve(variables, roleName).isBlank() || resolve(variables, mainClassName).isBlank()) {
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_ERROR,
              "INTERLIS class and role must be selected",
              transformMeta));
      return;
    }
    try {
      InterlisRoleJoinProbeResult result = probeRole(variables);
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_OK,
              "INTERLIS Role Join is configured: role "
                  + result.role().name()
                  + " -> "
                  + result.target().scopedName()
                  + " ("
                  + result.role().cardinality()
                  + ")",
              transformMeta));
    } catch (Exception e) {
      remarks.add(
          new org.apache.hop.core.CheckResult(
              org.apache.hop.core.ICheckResult.TYPE_RESULT_ERROR,
              "INTERLIS role check failed: " + e.getMessage(),
              transformMeta));
    }
  }

  /** The lookup fields to copy, defaulting to all primitive/geometry attributes of the target. */
  public List<String> effectiveLookupFields(InterlisRoleJoinProbeResult result) {
    if (lookupFields != null && !lookupFields.isEmpty()) {
      return List.copyOf(lookupFields);
    }
    return result.target().attributes().stream()
        .map(a -> a.name())
        .toList();
  }

  public String resolvedPrefix(InterlisRoleJoinProbeResult result) {
    if (prefix != null && !prefix.isBlank()) {
      return prefix;
    }
    return result.role().name() + "_";
  }

  /** The resolved main reference field, defaulting to {@code <role>_ref}. */
  public String resolvedMainReferenceField(InterlisRoleJoinProbeResult result) {
    if (mainReferenceField != null && !mainReferenceField.isBlank()) {
      return mainReferenceField;
    }
    return result.role().name() + "_ref";
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

  // -- accessors -----------------------------------------------------------

  public String getMainInputTransform() {
    return mainInputTransform;
  }

  public void setMainInputTransform(String mainInputTransform) {
    this.mainInputTransform = mainInputTransform;
  }

  public String getLookupInputTransform() {
    return lookupInputTransform;
  }

  public void setLookupInputTransform(String lookupInputTransform) {
    this.lookupInputTransform = lookupInputTransform;
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

  public String getMainClassName() {
    return mainClassName;
  }

  public void setMainClassName(String mainClassName) {
    this.mainClassName = mainClassName;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }

  public String getMainReferenceField() {
    return mainReferenceField;
  }

  public void setMainReferenceField(String mainReferenceField) {
    this.mainReferenceField = mainReferenceField;
  }

  public String getLookupTidField() {
    return lookupTidField;
  }

  public void setLookupTidField(String lookupTidField) {
    this.lookupTidField = lookupTidField;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public List<String> getLookupFields() {
    return lookupFields;
  }

  public void setLookupFields(List<String> lookupFields) {
    this.lookupFields = lookupFields;
  }

  public boolean isFailOnMissingMandatoryReference() {
    return failOnMissingMandatoryReference;
  }

  public void setFailOnMissingMandatoryReference(boolean failOnMissingMandatoryReference) {
    this.failOnMissingMandatoryReference = failOnMissingMandatoryReference;
  }

  public boolean isFailOnDuplicateTid() {
    return failOnDuplicateTid;
  }

  public void setFailOnDuplicateTid(boolean failOnDuplicateTid) {
    this.failOnDuplicateTid = failOnDuplicateTid;
  }

  public long getMaxLookupRows() {
    return maxLookupRows;
  }

  public void setMaxLookupRows(long maxLookupRows) {
    this.maxLookupRows = maxLookupRows;
  }
}
