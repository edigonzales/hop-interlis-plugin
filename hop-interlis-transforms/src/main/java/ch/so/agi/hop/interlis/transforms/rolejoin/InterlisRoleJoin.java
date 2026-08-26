package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Role Join: appends the fields of a role's target class to the main stream.
 *
 * <p>Both streams are read through explicit input rowsets. The lookup stream is loaded into
 * memory once (bounded by {@code maxLookupRows}); the join itself is a plain TID lookup on the
 * model-derived reference field. Missing targets are null fields or an error for mandatory roles,
 * depending on {@code failOnMissingMandatoryReference}.
 */
public class InterlisRoleJoin extends BaseTransform<InterlisRoleJoinMeta, InterlisRoleJoinData> {

  public InterlisRoleJoin(
      TransformMeta transformMeta,
      InterlisRoleJoinMeta meta,
      InterlisRoleJoinData data,
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

    Object[] mainRow;
    try {
      mainRow = getRowFrom(data.mainRowSet);
    } catch (Exception e) {
      throw new HopException("Failed to read main stream: " + e.getMessage(), e);
    }
    if (mainRow == null) {
      setOutputDone();
      if (isBasic()) {
        logBasic("Finished joining role " + meta.getRoleName());
      }
      return false;
    }
    bindMainRowMeta();
    bindLookupRowMeta();

    String reference = null;
    if (data.mainReferenceFieldIndex >= 0) {
      Object referenceValue = mainRow[data.mainReferenceFieldIndex];
      reference = referenceValue == null ? null : referenceValue.toString().trim();
      if (reference != null && reference.isEmpty()) {
        reference = null;
      }
    }


    Object[] lookupRow = reference == null ? null : data.lookupByTid.get(reference);

    if (lookupRow == null) {
      if (data.probe.role().cardinality().min() >= 1 && meta.isFailOnMissingMandatoryReference()) {
        throw new HopException(
            "Role "
                + data.probe.role().name()
                + " of class "
                + data.probe.mainClass().scopedName()
                + " is mandatory but no target object with TID <"
                + reference
                + "> was found in the lookup stream");
      }
      if (isDebug()) {
        logDebug(
            "Role " + data.probe.role().name() + ": no target for reference <" + reference + ">");
      }
    }

    Object[] outputRow = new Object[data.outputRowMeta.size()];
    System.arraycopy(mainRow, 0, outputRow, 0, mainRow.length);
    for (int i = 0; i < data.lookupFieldIndexes.length; i++) {
      int inputIndex = data.lookupFieldIndexes[i];
      outputRow[mainRow.length + i] = lookupRow == null || inputIndex < 0 ? null : lookupRow[inputIndex];
    }


    putRow(data.outputRowMeta, outputRow);
    if (checkFeedback(getLinesWritten()) && isBasic()) {
      logBasic("Joined " + getLinesWritten() + " rows of role " + meta.getRoleName());
    }
    return true;
  }

  private void doInitialize() throws HopException {
    InterlisRuntimeSupport.initialize();

    try {
      data.probe = meta.probeRole(this);
      data.lookupByTid = new LinkedHashMap<>();

      String mainTransform = resolve(meta.getMainInputTransform());
      String lookupTransform = resolve(meta.getLookupInputTransform());
      if (mainTransform.isBlank()) {
        throw new HopException("The main input transform must be selected");
      }
      if (lookupTransform.isBlank()) {
        throw new HopException("The lookup input transform must be selected");
      }
      data.mainRowSet = findInputRowSet(mainTransform);
      if (data.mainRowSet == null) {
        throw new HopException("Main input transform <" + mainTransform + "> not found");
      }
      data.lookupRowSet = findInputRowSet(lookupTransform);
      if (data.lookupRowSet == null) {
        throw new HopException("Lookup input transform <" + lookupTransform + "> not found");
      }

      loadLookup();

      data.outputRowMeta = new RowMeta();
      if (isBasic()) {
        logBasic(
            "Joining role "
                + data.probe.role().name()
                + " -> "
                + data.probe.target().scopedName()
                + " (lookup rows "
                + data.lookupByTid.size()
                + ")");
      }
      data.initialized = true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to initialize INTERLIS Role Join: " + e.getMessage(), e);
    }
  }

  /** Loads the lookup stream into memory once, keyed by the lookup TID field. */
  private void loadLookup() throws HopException {
    long count = 0;
    try {
      Object[] lookupRow;
      while ((lookupRow = getRowFrom(data.lookupRowSet)) != null) {
        if (count >= meta.getMaxLookupRows()) {
          throw new HopException(
              "Lookup stream exceeds the configured maximum of "
                  + meta.getMaxLookupRows()
                  + " rows; increase the limit or use a smaller lookup stream");
        }
        if (!data.lookupBound) {
          bindLookupRowMeta();
        }
        Object tidValue = lookupRow[data.lookupTidFieldIndex];
        String tid = tidValue == null ? null : tidValue.toString().trim();
        if (tid != null && !tid.isEmpty()) {
          if (data.lookupByTid.containsKey(tid) && meta.isFailOnDuplicateTid()) {
            throw new HopException(
                "Duplicate lookup TID <" + tid + "> in the lookup stream of role "
                    + data.probe.role().name());
          }
          data.lookupByTid.putIfAbsent(tid, lookupRow);
        }
        count++;
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to load the lookup stream: " + e.getMessage(), e);
    }
  }

  private void bindMainRowMeta() throws HopException {
    if (data.mainBound || data.mainRowSet == null) {
      return;
    }
    IRowMeta mainRowMeta = data.mainRowSet.getRowMeta();
    if (mainRowMeta == null) {
      throw new HopException("Main stream of INTERLIS Role Join has no row metadata");
    }
    data.outputRowMeta = new RowMeta();
    data.outputRowMeta.addRowMeta(mainRowMeta);
    String referenceField = meta.resolvedMainReferenceField(data.probe);
    data.mainReferenceFieldIndex = mainRowMeta.indexOfValue(referenceField);
    if (data.mainReferenceFieldIndex < 0) {
      throw new HopException(
          "Main reference field <" + referenceField + "> not found in the main stream");
    }
    for (String lookupField : meta.effectiveLookupFields(data.probe)) {
      var valueMeta =
          new ch.so.agi.hop.interlis.transforms.HopRowSchemaFactory()
              .createValueMeta(
                  new ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan(
                      0,
                      meta.resolvedPrefix(data.probe) + lookupField,
                      ch.so.agi.hop.interlis.core.mapping.InterlisFieldSource.PRIMITIVE_ATTRIBUTE,
                      ch.so.agi.hop.interlis.core.mapping.InterlisPropertyPath.root(lookupField),
                      data.probe.target().attributes().stream()
                          .filter(a -> a.name().equals(lookupField))
                          .findFirst()
                          .orElse(null),
                      null,
                      null));
      data.outputRowMeta.addValueMeta(valueMeta);
    }
    data.mainBound = true;
  }

  private void bindLookupRowMeta() throws HopException {
    if (data.lookupBound) {
      return;
    }
    IRowMeta lookupRowMeta = data.lookupRowSet.getRowMeta();
    if (lookupRowMeta == null) {
      throw new HopException("Lookup stream of INTERLIS Role Join has no row metadata");
    }
    data.lookupTidFieldIndex = lookupRowMeta.indexOfValue(resolve(meta.getLookupTidField()));
    if (data.lookupTidFieldIndex < 0) {
      throw new HopException(
          "Lookup TID field <" + resolve(meta.getLookupTidField()) + "> not found in the lookup stream");
    }
    List<String> fields = meta.effectiveLookupFields(data.probe);
    data.lookupFieldNames = fields.toArray(new String[0]);
    data.lookupFieldIndexes = new int[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      data.lookupFieldIndexes[i] = lookupRowMeta.indexOfValue(fields.get(i));
    }
    data.lookupBound = true;
  }

  @Override
  public void dispose() {
    data.lookupByTid = null;
    super.dispose();
  }
}
