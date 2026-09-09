package ch.so.agi.hop.interlis.transforms.explode;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureExploder;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionService;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Structure Explode: emits one child row per element of a multi-valued structure
 * attribute.
 *
 * <p>The structure content is read from the technical source-object carrier field kept by INTERLIS
 * Input. Each parent row produces 0..n child rows; a pending iterator in the data keeps the
 * transform streaming (one row per {@code processRow()} call).
 */
public class InterlisStructureExplode
    extends BaseTransform<InterlisStructureExplodeMeta, InterlisStructureExplodeData> {

  public InterlisStructureExplode(
      TransformMeta transformMeta,
      InterlisStructureExplodeMeta meta,
      InterlisStructureExplodeData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (data.pendingChildren != null && data.pendingChildren.hasNext()) {
      putRow(data.outputRowMeta, data.pendingChildren.next());
      return true;
    }

    Object[] parentRow = getRow();
    if (parentRow == null) {
      setOutputDone();
      if (isBasic()) {
        logBasic("Finished exploding structure " + meta.getStructureAttributePath());
      }
      return false;
    }

    if (!data.initialized) {
      doInitialize();
    }

    data.pendingChildren = explode(parentRow).iterator();
    if (!data.pendingChildren.hasNext()) {
      // No children: continue with the next parent row.
      data.pendingChildren = null;
      return processRow();
    }
    putRow(data.outputRowMeta, data.pendingChildren.next());
    return true;
  }

  private List<Object[]> explode(Object[] parentRow) throws HopException {
    Object carrier = data.bindings.carrier().read(parentRow);
    if (carrier == null) {
      throw new HopException(
          "Source object field <"
              + res(meta.getSourceObjectField())
              + "> is null for parent row "
              + parentKey(parentRow)
              + "; INTERLIS Input must be configured with \"Keep source object for Structure"
              + " Explode\"");
    }
    if (!(carrier instanceof IomObject sourceObject)) {
      throw new HopException(
          "Source object field <"
              + res(meta.getSourceObjectField())
              + "> does not contain an INTERLIS object but "
              + carrier.getClass().getName());
    }

    List<InterlisStructureExploder.ExplodedChild> children;
    try {
      children = data.exploder.explode(sourceObject, data.plan);
    } catch (Exception e) {
      throw new HopException(
          "Failed to explode structure "
              + meta.getStructureAttributePath()
              + " of class "
              + res(meta.getClassName())
              + " (parent "
              + parentKey(parentRow)
              + "): "
              + e.getMessage(),
          e);
    }

    List<Object[]> rows = new ArrayList<>(children.size());
    for (InterlisStructureExploder.ExplodedChild child : children) {
      rows.add(buildChildRow(parentRow, child));
    }
    return rows;
  }

  private Object[] buildChildRow(Object[] parentRow, InterlisStructureExploder.ExplodedChild child)
      throws HopException {
    List<Object> values = new ArrayList<>();
    values.add(data.bindings.tid().read(parentRow));
    if (meta.isEmitParentBid()) {
      values.add(data.bindings.bid().read(parentRow));
    }
    if (data.plan.ordered() || meta.isEmitIndexForBag()) {
      values.add((long) child.index());
    }
    for (Object childValue : child.values()) {
      values.add(childValue);
    }
    for (var field : data.bindings.parentFields()) {
      values.add(parentRow[field.sourceIndex()]);
    }
    return values.toArray();
  }

  private String parentKey(Object[] parentRow) throws HopException {
    Object key = data.bindings.tid().read(parentRow);
    return key == null ? "<null>" : key.toString();
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
      data.exploder = new InterlisStructureExploder();

      data.bindings =
          InterlisStructureExplodeBindings.bind(getInputRowMeta(), data.plan, meta, this);
      data.outputRowMeta = data.bindings.output();

      if (isBasic()) {
        logBasic(
            "Exploding structure "
                + data.plan.attributeName()
                + " ("
                + data.plan.structure().scopedName()
                + ") of class "
                + data.plan.parentClass().scopedName());
      }
      for (String warning : data.plan.warnings()) {
        logBasic("INTERLIS Structure Explode warning: " + warning);
      }
      data.initialized = true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          "Failed to initialize INTERLIS Structure Explode: " + e.getMessage(), e);
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
