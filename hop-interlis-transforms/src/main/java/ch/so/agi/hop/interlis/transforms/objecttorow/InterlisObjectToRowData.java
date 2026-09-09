package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import java.util.Iterator;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisObjectToRow} transform. */
public class InterlisObjectToRowData extends BaseTransformData {
  public ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings envelopeBindings;

  boolean initialized;
  InterlisProjectionResult projection;
  InterlisRowMappingPlan plan;
  DefaultInterlisObjectToRowMapper mapper;
  IRowMeta outputRowMeta;
  InterlisObjectToRowOutputPlan outputPlan;

  /** True when flattened association attributes require per-basket row buffering. */
  boolean buffering;

  ch.so.agi.hop.interlis.core.mapping.InterlisBasketProjectionBuffer<Object[]> basketBuffer;
  Iterator<Object[]> pendingOutput;
  long rowsMapped;
}
