package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Role Join: appends the fields of a role's target class to the main stream.
 *
 * <p>Both streams are read fairly through explicit input rowsets. The lookup stream is loaded into
 * a spill-backed index once (bounded by {@code maxLookupRows}), keyed by BID and TID. Missing
 * targets are null fields or an error for mandatory roles, depending on {@code
 * failOnMissingMandatoryReference}.
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
    Object[] mainRow = readMainRow();
    if (!isStopped() && data.mainBindings == null && data.mainRowSet.getRowMeta() != null) {
      data.mainBindings =
          InterlisRoleJoinBindings.main(data.mainRowSet.getRowMeta(), data.probe, meta, this);
    }
    if (mainRow == null || isStopped()) {
      setOutputDone();
      if (isBasic()) {
        logBasic("Finished joining role " + meta.getRoleName());
      }
      return false;
    }

    String reference = identifier(data.mainBindings.reference().read(mainRow));
    String explicitBid = identifier(data.mainBindings.referenceBid().read(mainRow));
    String bid =
        explicitBid == null ? identifier(data.mainBindings.bid().read(mainRow)) : explicitBid;
    if (explicitBid != null && reference == null) {
      throw new HopException(referenceContext(null, bid) + ": reference BID requires a target TID");
    }
    if (reference != null && bid == null) {
      throw new HopException(
          referenceContext(reference, null) + ": main row requires _ili_bid for a local reference");
    }
    Object[] lookupRow =
        reference == null || data.lookupIndex == null
            ? null
            : data.lookupIndex.get(lookupKey(bid, reference));
    if (lookupRow == null) {
      if (data.probe.role().cardinality().min() >= 1 && meta.isFailOnMissingMandatoryReference()) {
        throw new HopException(referenceContext(reference, bid) + ": mandatory target not found");
      }
      if (isDebug()) {
        logDebug(referenceContext(reference, bid) + ": target not found");
      }
    }

    IRowMeta output = data.mainBindings.output();
    Object[] outputRow = new Object[output.size()];
    System.arraycopy(mainRow, 0, outputRow, 0, mainRow.length);
    if (lookupRow != null)
      System.arraycopy(lookupRow, 0, outputRow, mainRow.length, lookupRow.length);

    putRow(output, outputRow);
    if (checkFeedback(getLinesWritten()) && isBasic()) {
      logBasic("Joined " + getLinesWritten() + " rows of role " + meta.getRoleName());
    }
    return true;
  }

  private String referenceContext(String tid, String bid) {
    return "Role "
        + data.probe.role().name()
        + " of "
        + data.probe.mainClass().scopedName()
        + " in lookup <"
        + data.lookupRowSet.getOriginTransformName()
        + ">, TID <"
        + tid
        + ">"
        + (bid == null ? "" : ", BID <" + bid + ">");
  }

  private static String identifier(Object value) {
    String id = (String) value;
    return id == null || id.isBlank() ? null : id;
  }

  private static String lookupKey(String bid, String tid) {
    // The length delimits the BID even if a key contains a colon.
    return bid.length() + ":" + bid + tid;
  }

  private Object[] readMainRow() throws HopException {
    try {
      return data.inputs.next(0);
    } catch (Exception e) {
      throw new HopException("Failed to read main stream: " + e.getMessage(), e);
    }
  }

  private void doInitialize() throws HopException {
    ch.so.agi.hop.interlis.transforms.InterlisParallelCopies.requireSingleCopy(
        getTransformMeta(), this, "independent input streams require a single shared collector");
    InterlisRuntimeSupport.initialize();
    data.storageOptions = meta.spillOptions(this);

    try {
      data.probe = meta.probeRole(this);

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

      if (meta.getMaxLookupRows() < 0)
        throw new HopException("maxLookupRows must be zero (unlimited) or positive");
      data.inputs =
          new ch.so.agi.hop.interlis.transforms.buffer.FairInputReader(
              this, data.storageOptions.divided(2), data.mainRowSet, data.lookupRowSet);
      loadLookup();
      if (data.lookupRowSet.getRowMeta() != null) {
        bindLookupRowMeta();
      }
      if (isBasic()) {
        logBasic(
            "Joining role "
                + data.probe.role().name()
                + " -> "
                + data.probe.target().scopedName()
                + " (lookup rows "
                + (data.lookupIndex == null ? 0 : data.lookupIndex.size())
                + ")");
      }
      data.initialized = true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to initialize INTERLIS Role Join: " + e.getMessage(), e);
    }
  }

  /** Loads the lookup stream into the spill-backed index before producing joined rows. */
  private void loadLookup() throws HopException {
    long count = 0;
    try {
      Object[] lookupRow;
      while ((lookupRow = data.inputs.next(1)) != null) {
        if (meta.getMaxLookupRows() > 0 && count >= meta.getMaxLookupRows()) {
          throw new HopException(
              "Lookup stream exceeds the configured maximum of "
                  + meta.getMaxLookupRows()
                  + " rows; increase the limit or use a smaller lookup stream");
        }
        bindLookupRowMeta();
        String tid = identifier(data.lookupBindings.tid().read(lookupRow));
        if (tid != null) {
          String bid = identifier(data.lookupBindings.bid().read(lookupRow));
          if (bid == null) {
            throw new HopException(
                referenceContext(tid, null) + ": lookup row requires an _ili_bid value");
          }
          if (!data.lookupIndex.putIfAbsent(
                  lookupKey(bid, tid), data.lookupBindings.fields().values(lookupRow))
              && meta.isFailOnDuplicateKey()) {
            throw new HopException(referenceContext(tid, bid) + ": duplicate lookup key");
          }
        }
        count++;
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to load the lookup stream: " + e.getMessage(), e);
    }
  }

  private void bindLookupRowMeta() throws HopException {
    if (data.lookupBindings != null) {
      return;
    }
    IRowMeta lookupRowMeta = data.lookupRowSet.getRowMeta();
    if (lookupRowMeta == null) {
      throw new HopException("Lookup stream of INTERLIS Role Join has no row metadata");
    }
    data.lookupBindings = InterlisRoleJoinBindings.lookup(lookupRowMeta, data.probe, meta, this);
    data.lookupIndex =
        new ch.so.agi.hop.interlis.core.buffer.SpillStore<>(
            new ch.so.agi.hop.interlis.transforms.buffer.HopRowCodec(
                data.lookupBindings.fields().normalRowMeta()),
            data.storageOptions.divided(2));
  }

  @Override
  public void dispose() {
    try {
      if (data.lookupIndex != null) data.lookupIndex.close();
    } finally {
      if (data.inputs != null) data.inputs.close();
      data.lookupIndex = null;
      data.inputs = null;
    }
    super.dispose();
  }
}
