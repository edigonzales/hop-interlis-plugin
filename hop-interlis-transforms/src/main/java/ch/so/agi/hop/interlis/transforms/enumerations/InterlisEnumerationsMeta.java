package ch.so.agi.hop.interlis.transforms.enumerations;

import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata of the INTERLIS Enumerations transform: lists the enumeration values of a model as
 * lookup rows.
 */
@Transform(
    id = "INTERLIS_ENUMERATIONS",
    name = "INTERLIS Enumerations",
    description = "List the enumeration values of an INTERLIS model as rows",
    image = "ch/so/agi/hop/interlis/transforms/enumerations/icons/interlis-enumerations.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "ili", "enum", "lookup"})
public class InterlisEnumerationsMeta
    extends BaseTransformMeta<InterlisEnumerations, InterlisEnumerationsData> {

  public static final String ENUM_DEFINITION = "enum_definition";
  public static final String ENUM_VALUE = "enum_value";
  public static final String ENUM_PATH = "enum_path";
  public static final String ENUM_PARENT = "parent_value";
  public static final String ENUM_DEPTH = "depth";
  public static final String ENUM_IS_LEAF = "is_leaf";

  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;

  public InterlisEnumerationsMeta() {
    super();
  }

  @Override
  public void setDefault() {
    modelNames = "";
    modelDirectories = "";
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
    try {
      InterlisRuntimeSupport.initialize();
    } catch (HopException e) {
      throw new HopTransformException(e.getMessage(), e);
    }
    rowMeta.addValueMeta(new ValueMetaString(ENUM_DEFINITION));
    rowMeta.addValueMeta(new ValueMetaString(ENUM_VALUE));
    rowMeta.addValueMeta(new ValueMetaString(ENUM_PATH));
    rowMeta.addValueMeta(new ValueMetaString(ENUM_PARENT));
    rowMeta.addValueMeta(new ValueMetaInteger(ENUM_DEPTH));
    rowMeta.addValueMeta(new ValueMetaBoolean(ENUM_IS_LEAF));
  }

  /** Compiles the configured model; empty model names yield a friendly failure. */
  public CompiledInterlisModel compileModel(IVariables variables) throws Exception {
    List<String> modelNames = resolveModelNames(variables);
    if (modelNames.isEmpty()) {
      throw new InterlisModelMissingException("No INTERLIS models configured");
    }
    InterlisModelService modelService = new InterlisModelServiceImpl();
    return modelService.compile(
        new ModelSource(List.of(), modelNames, resolveModelDirectories(variables)),
        ModelCompileOptions.defaults());
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
    if (modelNames == null || modelNames.isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS models are required", transformMeta));
      return;
    }
    try {
      compileModel(variables);
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_OK, "INTERLIS Enumerations is configured", transformMeta));
    } catch (Exception e) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS model check failed: " + e.getMessage(), transformMeta));
    }
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

  /** Raised when the configuration lacks model names; treated as a friendly design-time message. */
  public static final class InterlisModelMissingException extends Exception {
    public InterlisModelMissingException(String message) {
      super(message);
    }
  }
}
