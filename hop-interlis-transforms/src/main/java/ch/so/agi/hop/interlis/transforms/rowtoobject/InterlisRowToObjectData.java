package ch.so.agi.hop.interlis.transforms.rowtoobject;

import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.mapping.RowToIomMapper;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisRowToObject} transform. */
public class InterlisRowToObjectData extends BaseTransformData {
  InterlisRowToObjectBindings bindings;

  boolean initialized;
  InterlisProjectionResult projection;
  InterlisRowMappingPlan plan;
  RowToIomMapper mapper;
  IRowMeta outputRowMeta;
  java.util.Iterator<Object[]> pendingRows;
  long rowsMapped;
}
