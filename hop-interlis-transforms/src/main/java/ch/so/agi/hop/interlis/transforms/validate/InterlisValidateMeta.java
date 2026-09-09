package ch.so.agi.hop.interlis.transforms.validate;

import ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata of the INTERLIS Validate transform: validates an XTF file with the iox-ili validator
 * and emits one error row per finding.
 */
@Transform(
    id = "INTERLIS_VALIDATE",
    name = "INTERLIS Validate",
    description = "Validate an INTERLIS transfer file and emit validation error rows",
    image = "ch/so/agi/hop/interlis/transforms/validate/icons/interlis-validate.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "validate", "ilivalidator"})
public class InterlisValidateMeta
    extends BaseTransformMeta<InterlisValidate, InterlisValidateData> {

  public static final long DEFAULT_MAX_ERRORS = 10_000;

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String configFile;
  @HopMetadataProperty private boolean validateMultiplicity;
  @HopMetadataProperty private long maxErrors;
  @HopMetadataProperty private boolean stopOnFirstError;
  @HopMetadataProperty private boolean includeWarnings;
  @HopMetadataProperty private boolean includeInfo;
  @HopMetadataProperty private boolean failOnErrors;

  public InterlisValidateMeta() {
    super();
  }

  @Override
  public void setDefault() {
    fileName = "";
    modelNames = ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA;
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    configFile = "";
    validateMultiplicity = true;
    maxErrors = DEFAULT_MAX_ERRORS;
    stopOnFirstError = false;
    includeWarnings = true;
    includeInfo = false;
    failOnErrors = false;
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
      ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport.initialize();
      for (String fieldName : InterlisValidationRowLayout.FIELD_NAMES) {
        if (InterlisValidationRowLayout.LINE.equals(fieldName)
            || InterlisValidationRowLayout.COLUMN.equals(fieldName)) {
          rowMeta.addValueMeta(new ValueMetaInteger(fieldName));
        } else {
          rowMeta.addValueMeta(new ValueMetaString(fieldName));
        }
      }
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
    if (maxErrors < 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "maxErrors must be zero (unlimited) or positive",
              transformMeta));
      return;
    }
    if (fileName == null || fileName.isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS transfer file is required", transformMeta));
      return;
    }
    String resolved = variables.resolve(fileName);
    if (!resolved.contains("${") && !java.nio.file.Files.exists(java.nio.file.Path.of(resolved))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "INTERLIS transfer file does not exist: " + resolved,
              transformMeta));
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK, "INTERLIS Validate is configured", transformMeta));
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

  public String getConfigFile() {
    return configFile;
  }

  public void setConfigFile(String configFile) {
    this.configFile = configFile;
  }

  public boolean isValidateMultiplicity() {
    return validateMultiplicity;
  }

  public void setValidateMultiplicity(boolean validateMultiplicity) {
    this.validateMultiplicity = validateMultiplicity;
  }

  public long getMaxErrors() {
    return maxErrors;
  }

  public void setMaxErrors(long maxErrors) {
    this.maxErrors = maxErrors;
  }

  public boolean isStopOnFirstError() {
    return stopOnFirstError;
  }

  public void setStopOnFirstError(boolean stopOnFirstError) {
    this.stopOnFirstError = stopOnFirstError;
  }

  public boolean isIncludeWarnings() {
    return includeWarnings;
  }

  public void setIncludeWarnings(boolean includeWarnings) {
    this.includeWarnings = includeWarnings;
  }

  public boolean isIncludeInfo() {
    return includeInfo;
  }

  public void setIncludeInfo(boolean includeInfo) {
    this.includeInfo = includeInfo;
  }

  public boolean isFailOnErrors() {
    return failOnErrors;
  }

  public void setFailOnErrors(boolean failOnErrors) {
    this.failOnErrors = failOnErrors;
  }
}
