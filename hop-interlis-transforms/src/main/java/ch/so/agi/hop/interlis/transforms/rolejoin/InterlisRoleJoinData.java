package ch.so.agi.hop.interlis.transforms.rolejoin;

import org.apache.hop.core.IRowSet;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisRoleJoin} transform. */
public class InterlisRoleJoinData extends BaseTransformData {
  ch.so.agi.hop.interlis.core.buffer.SpillOptions storageOptions;
  InterlisRoleJoinBindings.Main mainBindings;
  InterlisRoleJoinBindings.Lookup lookupBindings;

  boolean initialized;
  boolean mainBound;
  boolean lookupBound;
  InterlisRoleJoinProbeResult probe;
  IRowSet mainRowSet;
  IRowSet lookupRowSet;
  ch.so.agi.hop.interlis.core.buffer.SpillStore<Object[]> lookupByTid;
  ch.so.agi.hop.interlis.transforms.buffer.FairInputReader inputs;
  IRowMeta outputRowMeta;
}
