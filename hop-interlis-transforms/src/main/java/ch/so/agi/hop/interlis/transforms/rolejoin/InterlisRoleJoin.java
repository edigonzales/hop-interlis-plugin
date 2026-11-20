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
 * a spill-backed index once (bounded by {@code maxLookupRows}), keyed by basket and TID when basket
 * fields are available. Missing targets are null fields or an error for mandatory roles, depending
 * on {@code failOnMissingMandatoryReference}.
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
    Object[] mainRow = data.initialized ? readMainRow() : doInitialize();
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
    Object[] lookupRow = findTarget(reference, bid, explicitBid != null);
    if (lookupRow == null) {
      if (data.probe.role().cardinality().min() >= 1 && meta.isFailOnMissingMandatoryReference()) {
        throw new HopException(referenceContext(reference, bid) + ": mandatory target not found");
      }
      if (isDebug()) {
        logDebug(referenceContext(reference, bid) + ": target not found");
      }
    }

    Object[] outputRow = new Object[data.outputRowMeta.size()];
    System.arraycopy(mainRow, 0, outputRow, 0, mainRow.length);
    if (lookupRow != null)
      System.arraycopy(lookupRow, 0, outputRow, mainRow.length, lookupRow.length);

    putRow(data.outputRowMeta, outputRow);
    if (checkFeedback(getLinesWritten()) && isBasic()) {
      logBasic("Joined " + getLinesWritten() + " rows of role " + meta.getRoleName());
    }
    return true;
  }

  private Object[] findTarget(String tid, String bid, boolean explicitBid) throws HopException {
    if (tid == null) {
      if (explicitBid) {
        throw new HopException(
            referenceContext(null, bid) + ": reference BID requires a target TID");
      }
      return null;
    }
    if (data.lookupBindings == null) {
      return null;
    }
    if (!data.lookupBindings.bid().present() && explicitBid) {
      throw new HopException(
          referenceContext(tid, bid) + ": lookup requires a String _ili_bid field");
    }
    if (!useBasketKeys()) {
      return data.lookupIndex.get(lookupKey(null, tid));
    }
    if (bid == null) {
      throw new HopException(
          referenceContext(tid, null) + ": main row requires _ili_bid for a local reference");
    }

    Object[] target = data.lookupIndex.get(lookupKey(bid, tid));
    if (target == null && explicitBid && data.lookupIndex.containsKey(lookupKey(null, tid))) {
      throw new HopException(
          referenceContext(tid, bid) + ": lookup row is missing its _ili_bid value");
    }
    return target;
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

  private boolean useBasketKeys() {
    return data.lookupBindings.bid().present()
        && (data.mainBindings == null
            || data.mainBindings.bid().present()
            || data.mainBindings.referenceBid().present());
  }

  private static String identifier(Object value) {
    if (value == null) {
      return null;
    }
    String id = value.toString().trim();
    return id.isEmpty() ? null : id;
  }

  private static String lookupKey(String bid, String tid) {
    // The BID length makes arbitrary String identifiers unambiguous across both key forms.
    return bid == null ? "0:" + tid : bid.length() + ":" + bid + tid;
  }

  private Object[] readMainRow() throws HopException {
    try {
      return data.inputs.next(0);
    } catch (Exception e) {
      throw new HopException("Failed to read main stream: " + e.getMessage(), e);
    }
  }

  private Object[] doInitialize() throws HopException {
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
      // Bind the effective main schema before indexing the lookup. The fair reader
      // buffers rows from either input while waiting for the first main row.
      Object[] mainRow = readMainRow();
      if (mainRow != null) {
        bindMainRowMeta();
      }
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
      return mainRow;
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
          String bid =
              useBasketKeys() ? identifier(data.lookupBindings.bid().read(lookupRow)) : null;
          String key = lookupKey(bid, tid);
          if (data.lookupIndex.containsKey(key) && meta.isFailOnDuplicateTid()) {
            throw new HopException(referenceContext(tid, bid) + ": duplicate lookup key");
          }
          data.lookupIndex.putIfAbsent(key, data.lookupBindings.fields().values(lookupRow));
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
    IRowMeta mainRowMeta = data.mainRowSet.getRowMeta();
    if (mainRowMeta == null) {
      throw new HopException("Main stream of INTERLIS Role Join has no row metadata");
    }
    data.mainBindings = InterlisRoleJoinBindings.main(mainRowMeta, data.probe, meta, this);
    data.outputRowMeta = data.mainBindings.output();
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
