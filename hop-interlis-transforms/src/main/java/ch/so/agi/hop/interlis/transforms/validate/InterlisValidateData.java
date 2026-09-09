package ch.so.agi.hop.interlis.transforms.validate;

import java.util.Iterator;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisValidate} transform. */
public class InterlisValidateData extends BaseTransformData {

  enum Phase {
    VALIDATE,
    EMIT,
    COMPLETE
  }

  Phase phase = Phase.VALIDATE;
  long errorCount;
  Iterator<Object[]> pendingRows;
  long rowsEmitted;
}
