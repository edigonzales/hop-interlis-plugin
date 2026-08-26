package ch.so.agi.hop.interlis.transforms.transferoutput;

import ch.so.agi.hop.interlis.core.io.InterlisTransferWriter;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisTransferOutput} transform. */
public class InterlisTransferOutputData extends BaseTransformData {

  boolean initialized;
  InterlisTransferWriter writer;
  String currentBid;
  long objectsWritten;
}
