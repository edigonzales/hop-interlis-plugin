package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import java.util.List;
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

/** Settings for writing explicitly mapped class streams to one XTF file. */
@Transform(
    id = "INTERLIS_OUTPUT",
    name = "INTERLIS Output",
    description = "Write typed INTERLIS class streams to one XTF file",
    image = "ch/so/agi/hop/interlis/transforms/output/icons/interlis-output.svg",
    categoryDescription = "Geospatial",
    isIncludeJdbcDrivers = true,
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "writer"})
public class InterlisOutputMeta extends BaseTransformMeta<InterlisOutput, InterlisOutputData>
    implements ch.so.agi.hop.interlis.transforms.mapping.MappedSinkSettings {

  public enum BasketMode {
    PER_TOPIC,
    FROM_FIELD
  }

  @HopMetadataProperty private BasketMode basketMode = BasketMode.PER_TOPIC;

  @HopMetadataProperty(groupKey = "inputs", key = "input")
  private List<ch.so.agi.hop.interlis.transforms.mapping.InterlisMappedInput> inputs =
      new java.util.ArrayList<>();

  public BasketMode getBasketMode() {
    return basketMode;
  }

  public void setBasketMode(BasketMode value) {
    basketMode = value;
  }

  public List<ch.so.agi.hop.interlis.transforms.mapping.InterlisMappedInput> getInputs() {
    return inputs;
  }

  public void setInputs(List<ch.so.agi.hop.interlis.transforms.mapping.InterlisMappedInput> value) {
    inputs = new java.util.ArrayList<>(value);
    super.resetTransformIoMeta();
  }

  @Override
  public Object clone() {
    var copy = (InterlisOutputMeta) super.clone();
    copy.inputs =
        inputs.stream()
            .map(ch.so.agi.hop.interlis.transforms.mapping.InterlisMappedInput::new)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    copy.setTransformIOMeta(null);
    return copy;
  }

  @Override
  public void loadXml(org.w3c.dom.Node node, IHopMetadataProvider provider)
      throws org.apache.hop.core.exception.HopXmlException {
    super.loadXml(node, provider);
    super.resetTransformIoMeta();
  }

  @Override
  public org.apache.hop.pipeline.transform.ITransformIOMeta getTransformIOMeta() {
    var io = super.getTransformIOMeta(false);
    if (io == null) {
      io = ch.so.agi.hop.interlis.transforms.mapping.MappedInputStreams.create(inputs);
      setTransformIOMeta(io);
    }
    return io;
  }

  @Override
  public void resetTransformIoMeta() {}

  @Override
  public void searchInfoAndTargetTransforms(List<TransformMeta> transforms) {
    ch.so.agi.hop.interlis.transforms.mapping.MappedInputStreams.resolve(
        getTransformIOMeta(), inputs, transforms);
  }

  @HopMetadataProperty private long bufferMemoryMiB = 64;

  public long getBufferMemoryMiB() {
    return bufferMemoryMiB;
  }

  public void setBufferMemoryMiB(long value) {
    bufferMemoryMiB = value;
  }

  @HopMetadataProperty private String spillDirectory = "";

  public String getSpillDirectory() {
    return spillDirectory;
  }

  public void setSpillDirectory(String value) {
    spillDirectory = value;
  }

  @HopMetadataProperty private long maxSpillMiB = 0;

  public long getMaxSpillMiB() {
    return maxSpillMiB;
  }

  public void setMaxSpillMiB(long value) {
    maxSpillMiB = value;
  }

  @HopMetadataProperty private boolean validateBeforePublish = true;
  @HopMetadataProperty private String validationConfigFile = "";

  public boolean isValidateBeforePublish() {
    return validateBeforePublish;
  }

  public void setValidateBeforePublish(boolean value) {
    validateBeforePublish = value;
  }

  public String getValidationConfigFile() {
    return validationConfigFile;
  }

  public void setValidationConfigFile(String value) {
    validationConfigFile = value;
  }

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private boolean overwrite;

  public InterlisOutputMeta() {
    super();
  }

  @Override
  public void setDefault() {
    inputs = new java.util.ArrayList<>();
    basketMode = BasketMode.PER_TOPIC;
    validateBeforePublish = true;
    validationConfigFile = "";
    fileName = "";
    modelNames = "";
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
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
    rowMeta.clear();
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
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "INTERLIS output file is required", transformMeta));
      return;
    }
    remarks.add(
        new CheckResult(
            inputs.isEmpty() ? ICheckResult.TYPE_RESULT_ERROR : ICheckResult.TYPE_RESULT_OK,
            inputs.isEmpty()
                ? "Configure INTERLIS input mappings"
                : "INTERLIS input mappings configured",
            transformMeta));
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

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean overwrite) {
    this.overwrite = overwrite;
  }
}
