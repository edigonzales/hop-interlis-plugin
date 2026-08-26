package ch.so.agi.hop.interlis.transforms.collect;

import ch.so.agi.hop.interlis.core.structures.InterlisStructureCollector;
import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionResult;
import org.apache.hop.core.IRowSet;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisStructureCollect} transform. */
public class InterlisStructureCollectData extends BaseTransformData {

  boolean initialized;
  boolean parentBound;
  boolean childBound;
  InterlisStructureProjectionResult projection;
  InterlisStructurePlan plan;
  InterlisStructureCollector collector;
  IRowSet parentRowSet;
  IRowSet childRowSet;
  Object[] pendingParentRow;
  Object[] pendingChildRow;
  int parentKeyFieldIndex;
  int sourceObjectFieldIndex;
  int childParentKeyFieldIndex;
  int childIndexFieldIndex;
  int[] childFieldIndexes;
  boolean childStreamExhausted;
  String lastParentKey;
}
