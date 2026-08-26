package ch.so.agi.hop.interlis.transforms.input;

import ch.so.agi.hop.interlis.core.io.InterlisTransferReader;
import ch.so.agi.hop.interlis.core.mapping.InterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
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
  long readObjects;
  long emittedObjects;
}
