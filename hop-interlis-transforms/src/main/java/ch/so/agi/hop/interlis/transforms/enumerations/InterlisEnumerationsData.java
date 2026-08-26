package ch.so.agi.hop.interlis.transforms.enumerations;

import java.util.Iterator;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisEnumerations} transform. */
public class InterlisEnumerationsData extends BaseTransformData {

  boolean initialized;
  Iterator<Object[]> pendingRows;
  IRowMeta outputRowMeta;
}
