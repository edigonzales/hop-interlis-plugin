package ch.so.agi.hop.interlis.transforms.rolejoin;

import java.util.Map;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisRoleJoin} transform. */
public class InterlisRoleJoinData extends BaseTransformData {

  boolean initialized;
  boolean mainBound;
  boolean lookupBound;
  InterlisRoleJoinProbeResult probe;
  IRowSet mainRowSet;
  IRowSet lookupRowSet;
  Map<String, Object[]> lookupByTid;
  int mainReferenceFieldIndex;
  int lookupTidFieldIndex;
  int[] lookupFieldIndexes;
  String[] lookupFieldNames;
  IRowMeta outputRowMeta;
}
