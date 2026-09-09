package ch.so.agi.hop.interlis.transforms.transferoutput;

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

/**
 * Metadata of the INTERLIS Transfer Output transform: writes canonical envelope rows back to an XTF
 * transfer.
 *
 * <p>In object mode (default) the transfer/basket events are derived from the OBJECT rows (basket
 * grouping by {@code _ili_bid}); in event mode the explicit event sequence of the stream is
 * written, so a full event stream can be reproduced losslessly.
 */
@Transform(
    id = "INTERLIS_TRANSFER_OUTPUT",
    name = "INTERLIS Transfer Output",
    description = "Write canonical envelope rows as an INTERLIS transfer",
    image = "ch/so/agi/hop/interlis/transforms/transferoutput/icons/interlis-transfer-output.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"interlis", "xtf", "ili", "envelope", "transfer", "writer"})
public class InterlisTransferOutputMeta
    extends BaseTransformMeta<InterlisTransferOutput, InterlisTransferOutputData> {

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String modelNames;
  @HopMetadataProperty private String modelDirectories;
  @HopMetadataProperty private boolean overwrite;
  @HopMetadataProperty private boolean eventMode;

  public InterlisTransferOutputMeta() {
    super();
  }

  @Override
  public void setDefault() {
    fileName = "";
    modelNames = "";
    modelDirectories = InterlisModelSourceSupport.DEFAULT_MODEL_DIRECTORIES;
    overwrite = false;
    eventMode = false;
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
    // The output transform does not change the schema.
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
    if (modelNames == null || modelNames.isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Explicit model names are required to compile the writer schema",
              transformMeta));
      return;
    }
    try {
      if (prev != null && !prev.isEmpty())
        ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings.bind(
            prev, ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout.OBJECT, eventMode);
    } catch (Exception e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            "INTERLIS Transfer Output is configured (event mode " + eventMode + ")",
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

  public boolean isEventMode() {
    return eventMode;
  }

  public void setEventMode(boolean eventMode) {
    this.eventMode = eventMode;
  }
}
