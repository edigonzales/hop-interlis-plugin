package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.io.InterlisTransferReader;
import ch.so.agi.hop.interlis.core.mapping.InterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import java.util.Iterator;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisInput} transform. */
public class InterlisInputData extends BaseTransformData {

  boolean initialized;
  InterlisTransferReader reader;
  InterlisProjectionResult projection;
  InterlisRowMappingPlan plan;
  IRowMeta outputRowMeta;
  InterlisObjectToRowMapper mapper;
  boolean keepSourceObject;

  /** True when flattened association attributes require per-basket row buffering. */
  boolean buffering;

  ch.so.agi.hop.interlis.core.mapping.InterlisBasketProjectionBuffer<Void> basketBuffer;
  Iterator<Object[]> pendingOutput;
  long readObjects;
  long emittedObjects;
}
