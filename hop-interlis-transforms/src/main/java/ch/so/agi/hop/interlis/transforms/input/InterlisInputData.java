package ch.so.agi.hop.interlis.transforms.input;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.io.InterlisTransferReader;
import ch.so.agi.hop.interlis.core.mapping.InterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionResult;
import ch.so.agi.hop.interlis.core.mapping.InterlisRowMappingPlan;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  List<InterlisObjectEnvelope> pendingRows;
  Map<String, IomObject> associationLinks;
  Map<String, InterlisAssociationDescriptor> associationsByScopedName;
  Set<String> neededAssociations;
  Iterator<Object[]> pendingOutput;
  long readObjects;
  long emittedObjects;
}
