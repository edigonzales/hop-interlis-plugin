package ch.so.agi.hop.interlis.transforms.explode;

import ch.so.agi.hop.interlis.core.structures.InterlisStructureExploder;
import ch.so.agi.hop.interlis.core.structures.InterlisStructurePlan;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionResult;
import java.util.Iterator;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisStructureExplode} transform. */
public class InterlisStructureExplodeData extends BaseTransformData {
  InterlisStructureExplodeBindings bindings;

  boolean initialized;
  InterlisStructureProjectionResult projection;
  InterlisStructurePlan plan;
  InterlisStructureExploder exploder;
  IRowMeta outputRowMeta;

  /** Pending child rows of the current parent row; 0..n rows are emitted per parent. */
  Iterator<Object[]> pendingChildren;
}
