package ch.so.agi.hop.interlis.transforms.transferoutput;

import ch.so.agi.hop.interlis.core.io.InterlisTransferWriter;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisTransferOutput} transform. */
public class InterlisTransferOutputData extends BaseTransformData {
  public final java.util.Set<String> completedBids = new java.util.HashSet<>();
  public String currentTopic;
  public ch.so.agi.hop.interlis.core.io.InterlisBasketMetadata currentBasketMetadata;
  public ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings envelopeBindings;

  boolean initialized;
  InterlisTransferWriter writer;
  String currentBid;
  long objectsWritten;
}
