package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisAssociationLinkLookup;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Object to Row: projects the {@code _ili_object} payload of canonical envelope rows onto
 * typed rows of one class.
 *
 * <p>Rows of other classes pass through unmapped (and are dropped); event rows keep the basket
 * context. When the projection flattens attributed association roles, the rows are buffered per
 * basket (flushed on basket end/change) so association link objects can be resolved regardless of
 * their position in the stream.
 */
public class InterlisObjectToRow
    extends BaseTransform<InterlisObjectToRowMeta, InterlisObjectToRowData> {

  public InterlisObjectToRow(
      TransformMeta transformMeta,
      InterlisObjectToRowMeta meta,
      InterlisObjectToRowData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    while (true) {
      if (data.initialized && data.pendingOutput != null && data.pendingOutput.hasNext()) {
        Object[] row = data.pendingOutput.next();
        data.rowsMapped++;
        putRow(data.outputRowMeta, row);
        return true;
      }

      Object[] row = getRow();
      if (row == null) {
        if (data.initialized) {
          flushPending();
          if (data.pendingOutput != null && data.pendingOutput.hasNext()) {
            continue;
          }
        }
        setOutputDone();
        if (isBasic()) {
          logBasic("Finished mapping " + data.rowsMapped + " rows of " + meta.getClassName());
        }
        return false;
      }

      if (!data.initialized) {
        doInitialize();
      }

      InterlisObjectEnvelope envelope = data.envelopeBindings.fromRow(row);
      if (envelope.eventType() == InterlisEventType.END_BASKET
          || envelope.eventType() == InterlisEventType.END_TRANSFER) {
        flushPending();
        continue;
      }
      if (envelope.eventType() != InterlisEventType.OBJECT) continue;
      if (data.buffering) {
        if (data.basketBuffer.startsNewBasket(envelope)) flushPending();
        data.basketBuffer.add(envelope, row);
      } else if (data.plan.root().scopedName().equals(envelope.className())) {
        Object[] mapped = mapRow(row, envelope, null);
        data.rowsMapped++;
        putRow(data.outputRowMeta, mapped);
        return true;
      }
      // Other classes are skipped by this projection.
    }
  }

  private Object[] mapRow(
      Object[] row, InterlisObjectEnvelope envelope, InterlisAssociationLinkLookup lookup)
      throws HopException {
    try {
      return data.outputPlan.values(row, data.mapper.map(envelope, data.plan, lookup));
    } catch (Exception e) {
      throw new HopException(e.getMessage(), e);
    }
  }

  private void flushPending() throws HopException {
    if (data.basketBuffer == null) return;
    var batch = data.basketBuffer.drain();
    List<Object[]> rows = new ArrayList<>(batch.rows().size());
    for (var entry : batch.rows())
      rows.add(mapRow(entry.context(), entry.envelope(), batch.lookup()));
    data.pendingOutput = rows.iterator();
  }

  private void doInitialize() throws HopException {
    InterlisRuntimeSupport.initialize();

    try {
      data.projection =
          new InterlisProjectionService()
              .project(
                  new InterlisModelRequest(null, resolveModelNames(), resolveModelDirectories()),
                  resolve(meta.getClassName()),
                  meta.projectionOptions(this));
      data.plan = data.projection.plan();
      data.mapper = new DefaultInterlisObjectToRowMapper();

      IRowMeta inputRowMeta = getInputRowMeta();
      data.envelopeBindings =
          ch.so.agi.hop.interlis.transforms.mapping.InterlisEnvelopeBindings.bind(
              inputRowMeta, resolve(meta.getObjectFieldName()), false);

      data.buffering = data.plan.hasLinkResolvedRoles();
      if (data.buffering) {
        ch.so.agi.hop.interlis.transforms.InterlisParallelCopies.requireSingleCopy(
            getTransformMeta(), this, "association resolution buffers complete baskets");
      }
      if (data.buffering)
        data.basketBuffer =
            new ch.so.agi.hop.interlis.core.mapping.InterlisBasketProjectionBuffer<>(data.plan);

      data.outputPlan =
          InterlisObjectToRowOutputPlan.create(
              inputRowMeta, data.plan, meta.isAppendEnvelopeFields());
      data.outputRowMeta = data.outputPlan.rowMeta();

      if (isBasic()) {
        logBasic(
            "Mapping object payloads to class "
                + data.plan.root().scopedName()
                + (data.buffering ? " (association links resolved per basket)" : ""));
      }
      for (String warning : data.plan.warnings()) {
        logBasic("INTERLIS Object to Row warning: " + warning);
      }
      data.initialized = true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to initialize INTERLIS Object to Row: " + e.getMessage(), e);
    }
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }
}
