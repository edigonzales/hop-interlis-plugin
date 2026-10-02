package ch.so.agi.hop.interlis.transforms.update;

import ch.so.agi.hop.interlis.transforms.mapping.*;
import java.util.*;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.pipeline.transform.*;

@Transform(
    id = "INTERLIS_UPDATE",
    name = "INTERLIS Update",
    description =
        "Apply selected object or structure changes while preserving an original XTF transfer",
    image = "ch/so/agi/hop/interlis/transforms/output/icons/interlis-output.svg",
    categoryDescription = "Geospatial",
    isIncludeJdbcDrivers = true,
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "update", "structure"})
public class InterlisUpdateMeta extends BaseTransformMeta<InterlisUpdate, InterlisUpdateData>
    implements MappedSinkSettings {
  @HopMetadataProperty private String originalFile = "";

  public String getOriginalFile() {
    return originalFile;
  }

  public void setOriginalFile(String value) {
    originalFile = value;
  }

  @HopMetadataProperty private String fileName = "";

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String value) {
    fileName = value;
  }

  @HopMetadataProperty private String modelNames = "%DATA";

  public String getModelNames() {
    return modelNames;
  }

  public void setModelNames(String value) {
    modelNames = value;
  }

  @HopMetadataProperty
  private String modelDirectories =
      ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;

  public String getModelDirectories() {
    return modelDirectories;
  }

  public void setModelDirectories(String value) {
    modelDirectories = value;
  }

  @HopMetadataProperty private boolean overwrite = false;

  public boolean isOverwrite() {
    return overwrite;
  }

  public void setOverwrite(boolean value) {
    overwrite = value;
  }

  @HopMetadataProperty private boolean validateBeforePublish = true;

  public boolean isValidateBeforePublish() {
    return validateBeforePublish;
  }

  public void setValidateBeforePublish(boolean value) {
    validateBeforePublish = value;
  }

  @HopMetadataProperty private String validationConfigFile = "";

  public String getValidationConfigFile() {
    return validationConfigFile;
  }

  public void setValidationConfigFile(String value) {
    validationConfigFile = value;
  }

  @HopMetadataProperty private long bufferMemoryMiB = 64;

  public long getBufferMemoryMiB() {
    return bufferMemoryMiB;
  }

  public void setBufferMemoryMiB(long value) {
    bufferMemoryMiB = value;
  }

  @HopMetadataProperty private long maxSpillMiB = 0;

  public long getMaxSpillMiB() {
    return maxSpillMiB;
  }

  public void setMaxSpillMiB(long value) {
    maxSpillMiB = value;
  }

  @HopMetadataProperty private String spillDirectory = "";

  public String getSpillDirectory() {
    return spillDirectory;
  }

  public void setSpillDirectory(String value) {
    spillDirectory = value;
  }

  @HopMetadataProperty(groupKey = "inputs", key = "input")
  private List<InterlisMappedInput> inputs = new ArrayList<>();

  public List<InterlisMappedInput> getInputs() {
    return inputs;
  }

  public void setInputs(List<InterlisMappedInput> value) {
    inputs = new ArrayList<>(value);
    super.resetTransformIoMeta();
  }

  @Override
  public void setDefault() {}

  @Override
  public Object clone() {
    var copy = (InterlisUpdateMeta) super.clone();
    copy.inputs =
        inputs.stream()
            .map(InterlisMappedInput::new)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    copy.setTransformIOMeta(null);
    return copy;
  }

  @Override
  public ITransformIOMeta getTransformIOMeta() {
    var io = super.getTransformIOMeta(false);
    if (io == null) {
      io = MappedInputStreams.create(inputs);
      setTransformIOMeta(io);
    }
    return io;
  }

  @Override
  public void loadXml(
      org.w3c.dom.Node node, org.apache.hop.metadata.api.IHopMetadataProvider provider)
      throws org.apache.hop.core.exception.HopXmlException {
    super.loadXml(node, provider);
    super.resetTransformIoMeta();
  }

  @Override
  public void resetTransformIoMeta() {}

  @Override
  public void searchInfoAndTargetTransforms(List<TransformMeta> transforms) {
    MappedInputStreams.resolve(getTransformIOMeta(), inputs, transforms);
  }

  @Override
  public void getFields(
      org.apache.hop.core.row.IRowMeta row,
      String origin,
      org.apache.hop.core.row.IRowMeta[] info,
      TransformMeta next,
      org.apache.hop.core.variables.IVariables vars,
      org.apache.hop.metadata.api.IHopMetadataProvider provider) {
    row.clear();
  }
}
