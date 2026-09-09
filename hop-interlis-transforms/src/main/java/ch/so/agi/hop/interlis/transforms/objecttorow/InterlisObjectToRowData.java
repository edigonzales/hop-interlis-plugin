package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

/** Runtime state of the {@link InterlisObjectToRow} transform. */
public class InterlisObjectToRowData extends BaseTransformData {

  boolean initialized;
  InterlisProjectionResult projection;
  InterlisRowMappingPlan plan;
  DefaultInterlisObjectToRowMapper mapper;
  IRowMeta outputRowMeta;
  InterlisObjectToRowOutputPlan outputPlan;
  int objectFieldIndex;
  /** True when flattened association attributes require per-basket row buffering. */
  boolean buffering;
  Map<String, IomObject> associationLinks;
  Map<String, InterlisAssociationDescriptor> associationsByScopedName;
  Set<String> neededAssociations;
  String currentBid;
  List<Object[]> pendingRows;
  Iterator<Object[]> pendingOutput;
  long rowsMapped;
}
