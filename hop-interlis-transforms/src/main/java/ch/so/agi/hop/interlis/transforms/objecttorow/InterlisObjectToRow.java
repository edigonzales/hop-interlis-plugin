package ch.so.agi.hop.interlis.transforms.objecttorow;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisEventType;
import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;
import ch.so.agi.hop.interlis.core.mapping.DefaultInterlisObjectToRowMapper;
import ch.so.agi.hop.interlis.core.mapping.InterlisAssociationLinkLookup;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.model.InterlisAssociationDescriptor;
import ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Object to Row: projects the {@code _ili_object} payload of canonical envelope rows
 * onto typed rows of one class.
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
      if (data.initialized
          && data.pendingOutput != null
          && data.pendingOutput.hasNext()) {
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

      String eventType = InterlisEnvelopeRowLayout.eventType(row);
      if (InterlisEventType.END_BASKET.name().equals(eventType)
          || InterlisEventType.END_TRANSFER.name().equals(eventType)) {
        flushPending();
        continue;
      }

      IomObject object = InterlisEnvelopeRowLayout.object(row);
      if (object == null) {
        // Non-object events keep the stream flowing.
        continue;
      }

      if (data.buffering) {
        String bid = InterlisEnvelopeRowLayout.basketId(row);
        if (data.currentBid == null) {
          data.currentBid = bid;
        } else if (!java.util.Objects.equals(data.currentBid, bid)) {
          flushPending();
          data.currentBid = bid;
        }
      }

      String className = InterlisEnvelopeRowLayout.className(row);
      if (data.buffering && data.neededAssociations.contains(className)) {
        bufferLink(row, object);
      }
      if (className != null && className.equals(data.plan.root().scopedName())) {
        if (data.buffering) {
          data.pendingRows.add(row);
        } else {
          Object[] mapped = mapRow(row, object, null);
          data.rowsMapped++;
          putRow(data.outputRowMeta, mapped);
          return true;
        }
      }
      // Other classes are skipped by this projection.
    }
  }

  /** Buffers one association link object for later resolution of flattened fields. */
  private void bufferLink(Object[] row, IomObject link) throws HopException {
    InterlisAssociationDescriptor association =
        data.associationsByScopedName.get(InterlisEnvelopeRowLayout.className(row));
    if (association == null) {
      return;
    }
    String bid = InterlisEnvelopeRowLayout.basketId(row);
    for (ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor role : association.roles()) {
      if (link.getattrvaluecount(role.name()) == 0) {
        continue;
      }
      IomObject member = link.getattrobj(role.name(), 0);
      if (member == null || member.getobjectrefoid() == null) {
        continue;
      }
      data.associationLinks.put(
          linkKey(bid, member.getobjectrefoid(), association.scopedName()), link);
    }
  }

  private Object[] mapRow(
      Object[] row, IomObject object, InterlisAssociationLinkLookup lookup) throws HopException {
    InterlisObjectEnvelope envelope = InterlisEnvelopeRowLayout.fromRow(row);
    try {
      return data.mapper.map(envelope, data.plan, lookup);
    } catch (Exception e) {
      throw new HopException(e.getMessage(), e);
    }
  }

  private void flushPending() throws HopException {
    if (data.pendingRows.isEmpty()) {
      data.pendingOutput = null;
      data.associationLinks.clear();
      return;
    }
    InterlisAssociationLinkLookup lookup =
        (objectTid, roleName) -> {
          InterlisAssociationDescriptor association = data.plan.linkResolvedRoles().get(roleName);
          if (association == null) {
            return null;
          }
          return data.associationLinks.get(
              linkKey(data.currentBid, objectTid, association.scopedName()));
        };
    List<Object[]> rows = new ArrayList<>(data.pendingRows.size());
    for (Object[] pending : data.pendingRows) {
      rows.add(mapRow(pending, InterlisEnvelopeRowLayout.object(pending), lookup));
    }
    data.pendingRows.clear();
    data.associationLinks.clear();
    data.pendingOutput = rows.iterator();
  }

  private static String linkKey(String bid, String objectTid, String associationScopedName) {
    return (bid == null ? "" : bid) + "\u0000" + objectTid + "\u0000" + associationScopedName;
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
      data.objectFieldIndex = inputRowMeta.indexOfValue(resolve(meta.getObjectFieldName()));
      if (data.objectFieldIndex < 0) {
        throw new HopException(
            "Object field <" + resolve(meta.getObjectFieldName()) + "> not found in the input");
      }

      data.buffering = data.plan.hasLinkResolvedRoles();
      data.pendingRows = new ArrayList<>();
      data.associationLinks = new java.util.HashMap<>();
      data.associationsByScopedName = new java.util.HashMap<>();
      data.neededAssociations = new java.util.HashSet<>();
      if (data.buffering) {
        for (InterlisAssociationDescriptor association : data.plan.linkResolvedRoles().values()) {
          data.associationsByScopedName.put(association.scopedName(), association);
          data.neededAssociations.add(association.scopedName());
        }
      }

      data.outputRowMeta =
          meta.isAppendEnvelopeFields()
              ? appendSchema(inputRowMeta)
              : new HopRowSchemaFactory().createRowMeta(data.plan);

      if (isBasic()) {
        logBasic(
            "Mapping object payloads to class " + data.plan.root().scopedName()
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

  private IRowMeta appendSchema(IRowMeta inputRowMeta) throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addRowMeta(inputRowMeta);
    var typed = new HopRowSchemaFactory().createRowMeta(data.plan);
    for (int i = 0; i < typed.size(); i++) {
      var valueMeta = typed.getValueMeta(i);
      if (rowMeta.indexOfValue(valueMeta.getName()) < 0) {
        rowMeta.addValueMeta(valueMeta);
      }
    }
    return rowMeta;
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(","))
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .toList();
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return Arrays.stream(resolved.split(";"))
        .map(String::trim)
        .filter(d -> !d.isEmpty())
        .toList();
  }
}
