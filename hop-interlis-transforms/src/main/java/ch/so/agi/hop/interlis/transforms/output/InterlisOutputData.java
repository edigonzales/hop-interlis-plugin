package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.core.io.InterlisTransferWriter;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.mapping.RowToIomMapper;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisOutput} transform. */
public class InterlisOutputData extends BaseTransformData {

  boolean initialized;
  InterlisProjectionResult projection;
  InterlisRowMappingPlan plan;
  InterlisTransferWriter writer;
  RowToIomMapper mapper;
  int[] inputIndexes;
  int objectIdFieldIndex;
  int basketIdFieldIndex;
  int sourceObjectFieldIndex;
  int operationFieldIndex;
  String currentBid;
  long writtenObjects;
}
