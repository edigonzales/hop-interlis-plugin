package ch.so.agi.hop.interlis.transforms.collect;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureCollector;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureCollector.StructureChild;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureCollector.StructureCollectOptions;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionService;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Structure Collect: merges a sorted child stream back into the multi-valued structure
 * of the parent row's source object.
 *
 * <p>Streaming sorted merge: the parent stream must be sorted by the parent key, the child
 * stream by (parent key, index). Each parent row is emitted once with an updated carrier
 * ({@code _ili_source_object}); the collected structure replaces the carrier's structure content.
 */
public class InterlisStructureCollect
    extends BaseTransform<InterlisStructureCollectMeta, InterlisStructureCollectData> {

  public InterlisStructureCollect(
      TransformMeta transformMeta,
      InterlisStructureCollectMeta meta,
      InterlisStructureCollectData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.initialized) {
      doInitialize();
    }

    if (data.pendingParentRow == null) {
      Object[] parentRow;
      try {
        parentRow = getRowFrom(data.parentRowSet);
      } catch (Exception e) {
        throw new HopException("Failed to read parent stream: " + e.getMessage(), e);
      }
      if (parentRow == null) {
        handleRemainingChildren();
        setOutputDone();
        if (isBasic()) {
          logBasic("Finished collecting structure " + meta.getStructureAttributePath());
        }
        return false;
      }
      data.pendingParentRow = parentRow;
      bindParentRowMeta();
      String parentKey = key(data.pendingParentRow[data.parentKeyFieldIndex]);
      if (data.lastParentKey != null && parentKey.compareTo(data.lastParentKey) < 0) {
        throw new HopException(
            "Parent stream of INTERLIS Structure Collect must be sorted by parent key <"
                + res(meta.getParentKeyField())
                + "> ascending; got "
                + parentKey
                + " after "
                + data.lastParentKey);
      }
      data.lastParentKey = parentKey;
    }

    try {
      List<StructureChild> children = collectChildrenFor(key(data.pendingParentRow[data.parentKeyFieldIndex]));

      Object carrier = data.pendingParentRow[data.sourceObjectFieldIndex];
      if (carrier == null) {
        throw new HopException(
            "Source object field <"
                + res(meta.getSourceObjectField())
                + "> is null for parent "
                + data.lastParentKey
                + "; INTERLIS Input must be configured with \"Keep source object for Structure Explode\"");
      }
      if (!(carrier instanceof IomObject carrierObject)) {
        throw new HopException(
            "Source object field <"
                + res(meta.getSourceObjectField())
                + "> does not contain an INTERLIS object but "
                + carrier.getClass().getName());
      }

      IomObject updated =
          data.collector.collect(
              carrierObject, children, data.plan, collectOptions());

      Object[] outputRow = data.pendingParentRow.clone();
      outputRow[data.sourceObjectFieldIndex] = updated;
      data.pendingParentRow = null;
      putRow(data.parentRowSet.getRowMeta(), outputRow);
      return true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Failed to collect structure "
              + meta.getStructureAttributePath()
              + " for parent "
              + data.lastParentKey
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private List<StructureChild> collectChildrenFor(String parentKey) throws HopException {
    List<StructureChild> children = new ArrayList<>();
    long lastIndex = -1;
    boolean first = true;

    while (true) {
      Object[] childRow = data.pendingChildRow;
      data.pendingChildRow = null;
      if (childRow == null) {
        if (data.childStreamExhausted) {
          // Hop removes a finished input rowset from the transform on the first exhausted
          // read; reading it again would trip the internal rowset bookkeeping.
          return children;
        }
        try {
          childRow = getRowFrom(data.childRowSet);
        } catch (Exception e) {
          throw new HopException("Failed to read child stream: " + e.getMessage(), e);
        }
      }
      if (childRow == null) {
        data.childStreamExhausted = true;
        return children;
      }
      bindChildRowMeta();

      String childKey = key(childRow[data.childParentKeyFieldIndex]);
      int comparison = childKey.compareTo(parentKey);
      if (comparison < 0) {
        // The child's parent never appeared (streams are sorted ascending).
        if (meta.isFailOnChildWithoutParent()) {
          throw new HopException(
              "Child row with parent key "
                  + childKey
                  + " has no matching parent row (parent stream of INTERLIS Structure Collect "
                  + "is sorted by parent key); configure the transform or reorder the streams");
        }
        if (isBasic()) {
          logBasic(
              "INTERLIS Structure Collect: skipping child row with unknown parent " + childKey);
        }
        continue;
      }
      if (comparison > 0) {
        data.pendingChildRow = childRow;
        return children;
      }

      long index = childIndex(childRow, parentKey);
      if (data.plan.ordered() && meta.isStrictOrdering()) {
        if (!first && index <= lastIndex) {
          throw new HopException(
              "Child stream of INTERLIS Structure Collect must be sorted by index ascending "
                  + "within parent "
                  + parentKey
                  + "; got index "
                  + index
                  + " after "
                  + lastIndex);
        }
      }
      lastIndex = index;
      first = false;
      children.add(new StructureChild((int) index, childValues(childRow)));
    }
  }

  private long childIndex(Object[] childRow, String parentKey) throws HopException {
    if (!data.plan.ordered()) {
      // BAG: the index is purely technical; use the stream position.
      return 0;
    }
    Object indexValue = childRow[data.childIndexFieldIndex];
    if (indexValue == null) {
      throw new HopException(
          "Child row for parent "
              + parentKey
              + " has no index value in field <"
              + res(meta.getChildIndexField())
              + ">; LIST structures require the index field");
    }
    if (!(indexValue instanceof Number number)) {
      throw new HopException(
          "Child index field <"
              + res(meta.getChildIndexField())
              + "> must be numeric but is "
              + indexValue.getClass().getName());
    }
    return number.longValue();
  }

  private Object[] childValues(Object[] childRow) {
    Object[] values = new Object[data.plan.childFields().size()];
    for (int i = 0; i < data.childFieldIndexes.length; i++) {
      int inputIndex = data.childFieldIndexes[i];
      values[i] = inputIndex >= 0 ? childRow[inputIndex] : null;
    }
    return values;
  }

  private void handleRemainingChildren() throws HopException {
    if (data.childStreamExhausted) {
      return;
    }
    Object[] childRow = data.pendingChildRow;
    data.pendingChildRow = null;
    while (childRow != null) {
      bindChildRowMeta();
      String childKey = key(childRow[data.childParentKeyFieldIndex]);
      if (meta.isFailOnChildWithoutParent()) {
        throw new HopException(
            "Child row with parent key "
                + childKey
                + " has no matching parent row; configure INTERLIS Structure Collect or fix "
                + "the child stream");
      }
      if (isBasic()) {
        logBasic(
            "INTERLIS Structure Collect: skipping child row with unknown parent " + childKey);
      }
      try {
        childRow = getRowFrom(data.childRowSet);
      } catch (Exception e) {
        throw new HopException("Failed to read child stream: " + e.getMessage(), e);
      }
    }
  }

  private StructureCollectOptions collectOptions() {
    return new StructureCollectOptions(
        meta.isStrictOrdering(),
        meta.isFailOnDuplicateIndex(),
        new RowWriteOptions(true, null));
  }

  /** Binds the parent stream row meta once the first parent row is available. */
  private void bindParentRowMeta() throws HopException {
    if (data.parentBound) {
      return;
    }
    var parentRowMeta = data.parentRowSet.getRowMeta();
    if (parentRowMeta == null) {
      throw new HopException("Parent stream of INTERLIS Structure Collect has no row metadata");
    }
    data.parentKeyFieldIndex = parentRowMeta.indexOfValue(res(meta.getParentKeyField()));
    if (data.parentKeyFieldIndex < 0) {
      throw new HopException(
          "Parent key field <" + res(meta.getParentKeyField()) + "> not found in the parent stream");
    }
    data.sourceObjectFieldIndex =
        parentRowMeta.indexOfValue(res(meta.getSourceObjectField()));
    if (data.sourceObjectFieldIndex < 0) {
      throw new HopException(
          "Source object field <" + res(meta.getSourceObjectField()) + "> not found in the parent stream");
    }
    data.parentBound = true;
  }

  /**
   * Binds the child stream row meta the first time a child row is available. The row meta of an
   * info stream is only guaranteed after its first row was produced.
   */
  private void bindChildRowMeta() throws HopException {
    if (data.childBound) {
      return;
    }
    var childRowMeta = data.childRowSet.getRowMeta();
    if (childRowMeta == null) {
      throw new HopException("Child stream of INTERLIS Structure Collect has no row metadata");
    }
    data.childParentKeyFieldIndex =
        childRowMeta.indexOfValue(res(meta.getChildParentKeyField()));
    if (data.childParentKeyFieldIndex < 0) {
      throw new HopException(
          "Child parent key field <" + res(meta.getChildParentKeyField()) + "> not found in the child stream");
    }
    data.childIndexFieldIndex = childRowMeta.indexOfValue(res(meta.getChildIndexField()));
    if (data.plan.ordered() && data.childIndexFieldIndex < 0) {
      throw new HopException(
          "Child index field <" + res(meta.getChildIndexField()) + "> not found in the child stream");
    }

    data.childFieldIndexes = new int[data.plan.childFields().size()];
    for (int i = 0; i < data.plan.childFields().size(); i++) {
      String fieldName = data.plan.childFields().get(i).hopFieldName();
      data.childFieldIndexes[i] = childRowMeta.indexOfValue(fieldName);
      if (data.childFieldIndexes[i] < 0) {
        throw new HopException(
            "Child stream is missing field <" + fieldName + "> of structure "
                + data.plan.structure().scopedName());
      }
    }
    data.childBound = true;
  }

  private static String key(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  private void doInitialize() throws HopException {
    InterlisRuntimeSupport.initialize();

    try {
      data.projection =
          new InterlisStructureProjectionService()
              .project(
                  new InterlisModelRequest(null, resolveModelNames(), resolveModelDirectories()),
                  res(meta.getClassName()),
                  res(meta.getStructureAttributePath()),
                  meta.projectionOptions());
      data.plan = data.projection.plan();
      data.collector = new InterlisStructureCollector();

      String parentTransform = res(meta.getParentInputTransform());
      if (parentTransform.isBlank()) {
        throw new HopException("The parent input transform must be selected");
      }
      data.parentRowSet = findInputRowSet(parentTransform);
      if (data.parentRowSet == null) {
        throw new HopException("Parent input transform <" + parentTransform + "> not found");
      }

      String childTransform = res(meta.getChildInputTransform());
      data.childRowSet = findInputRowSet(childTransform);
      if (data.childRowSet == null) {
        throw new HopException("Child input transform <" + childTransform + "> not found");
      }

      if (isBasic()) {
        logBasic(
            "Collecting structure "
                + data.plan.attributeName()
                + " ("
                + data.plan.structure().scopedName()
                + ") of class "
                + data.plan.parentClass().scopedName()
                + " from child stream "
                + childTransform);
      }
      data.initialized = true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to initialize INTERLIS Structure Collect: " + e.getMessage(), e);
    }
  }

  private List<String> resolveModelNames() {
    String resolved = res(meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories() {
    String resolved = res(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }

  /** Resolves a meta field, treating {@code null} as unset. */
  private String res(String value) {
    return value == null ? "" : resolve(value);
  }
}
