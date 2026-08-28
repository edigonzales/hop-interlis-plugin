package ch.so.agi.hop.interlis.transforms.transferinput;

import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import java.util.List;
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
 * Metadata of the INTERLIS Transfer Input transform: reads the complete XTF event stream and
 * emits canonical envelope rows with a constant schema, so any number of classes can travel in
 * one Hop stream.
 */
@Transform(
    id = "INTERLIS_TRANSFER_INPUT",
    name = "INTERLIS Transfer Input",
    description = "Read a full INTERLIS transfer as canonical envelope rows",
    image = "ch/so/agi/hop/interlis/transforms/transferinput/icons/interlis-transfer-input.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "envelope", "transfer", "reader"})
public class InterlisTransferInputMeta
    extends BaseTransformMeta<InterlisTransferInput, InterlisTransferInputData> {

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private String mode;

  public InterlisTransferInputMeta() {
    super();
  }

  @Override
  public void setDefault() {
    fileName = "";
    modelNames = ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta.MODELS_FROM_DATA;
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    mode = TransferInputMode.OBJECTS.name();
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
    if (fileName == null || fileName.isBlank()) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS transfer file is required", transformMeta));
      return;
    }
    String resolved = variables.resolve(fileName);
    if (!resolved.contains("${") && !java.nio.file.Files.exists(java.nio.file.Path.of(resolved))) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, "INTERLIS transfer file does not exist: " + resolved, transformMeta));
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            "INTERLIS Transfer Input is configured (mode " + mode + ")", transformMeta));
  }

  /** The resolved transfer input mode, defaulting to OBJECTS. */
  public TransferInputMode resolvedMode() {
    if (mode == null || mode.isBlank()) {
      return TransferInputMode.OBJECTS;
    }
    try {
      return TransferInputMode.valueOf(mode);
    } catch (IllegalArgumentException e) {
      return TransferInputMode.OBJECTS;
    }
  }

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

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }
}
