package ch.so.agi.hop.interlis.transforms.transferinput;

import ch.so.agi.hop.interlis.core.io.InterlisTransferReader;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisTransferInput} transform. */
public class InterlisTransferInputData extends BaseTransformData {

  boolean initialized;
  InterlisTransferReader reader;
  IRowMeta outputRowMeta;
  long eventsRead;
  long rowsEmitted;
}
