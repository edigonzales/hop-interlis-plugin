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
    while (!isStopped()) {
      if (data.pendingChildren != null && data.pendingChildren.hasNext()) {
        try {
          putRow(
              data.outputRowMeta,
              buildChildRow(data.pendingParentRow, data.pendingChildren.next()));
        } catch (ch.so.agi.hop.interlis.core.mapping.InterlisMappingException e) {
          throw new HopException(e);
        }
        return true;
      }
      data.pendingChildren = null;
      data.pendingParentRow = null;
      Object[] parentRow = getRow();
      if (parentRow == null) {
        setOutputDone();
        return false;
      }
      if (!data.initialized) doInitialize();
      Object carrier = data.bindings.carrier().read(parentRow);
      if (!(carrier instanceof IomObject source))
        throw new HopException("Missing INTERLIS source object for parent " + parentKey(parentRow));
      try {
        data.pendingChildren = data.exploder.cursor(source, data.plan);
      } catch (Exception e) {
        throw new HopException(
            "Failed to explode " + meta.getStructureAttributePath() + ": " + e.getMessage(), e);
      }
      data.pendingParentRow = parentRow;
    }
    return false;
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
    if (meta.isKeepChildSourceObject() && !data.plan.primitive()) values.add(child.source());
    for (var field : data.bindings.parentFields()) {
      values.add(parentRow[field.sourceIndex()]);
    }
    return values.toArray();
  }

  @Override
  public void dispose() {
    data.pendingChildren = null;
    data.pendingParentRow = null;
    super.dispose();
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
                + data.plan.childTypeName()
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
