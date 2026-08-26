package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.io.InterlisObjectOperation;

/**
 * Options controlling the inverse mapping (typed row values → IOM object).
 *
 * @param strict reject missing mandatory values instead of leaving them undefined
 * @param basketId basket identifier applied when no basket field is projected
 * @param operation transfer operation of the mapped object; DELETE objects carry only their
 *     identity, so mandatory checks are skipped for them
 */
public record RowWriteOptions(boolean strict, String basketId, InterlisObjectOperation operation) {

  public static RowWriteOptions defaults() {
    return new RowWriteOptions(true, "b1", InterlisObjectOperation.NONE);
  }

  public RowWriteOptions(boolean strict, String basketId) {
    this(strict, basketId, InterlisObjectOperation.NONE);
  }

  /** {@code true} when the mapped object is a delete operation. */
  public boolean isDelete() {
    return operation == InterlisObjectOperation.DELETE;
  }
}
