package ch.so.agi.hop.interlis.transforms.validate;

import java.util.Iterator;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisValidate} transform. */
public class InterlisValidateData extends BaseTransformData {

  boolean initialized;
  Iterator<Object[]> pendingRows;
  long rowsEmitted;
}
