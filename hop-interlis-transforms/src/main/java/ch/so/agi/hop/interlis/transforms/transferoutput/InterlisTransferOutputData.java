package ch.so.agi.hop.interlis.transforms.transferoutput;

import ch.so.agi.hop.interlis.core.io.InterlisTransferWriter;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisTransferOutput} transform. */
public class InterlisTransferOutputData extends BaseTransformData {
  ch.so.agi.hop.interlis.core.io.PreparedXtfOutput preparedOutput;
  ch.interlis.ili2c.metamodel.TransferDescription writerModel;
  public String currentTopic;
  public ch.so.agi.hop.interlis.core.io.InterlisBasketMetadata currentBasketMetadata;
  public ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings envelopeBindings;

  boolean initialized;
  InterlisTransferWriter writer;
  String currentBid;
  long objectsWritten;
}
