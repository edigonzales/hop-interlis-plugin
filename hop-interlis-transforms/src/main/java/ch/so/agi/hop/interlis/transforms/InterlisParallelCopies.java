package ch.so.agi.hop.interlis.transforms;

import org.apache.hop.core.exception.HopException;

/** Guards transforms whose file or multi-stream state cannot be distributed across copies. */
public final class InterlisParallelCopies {

  private InterlisParallelCopies() {}

  /** Check configured copies, including copy zero, before any output or file side effects. */
  public static void requireSingleCopy(
      org.apache.hop.pipeline.transform.TransformMeta transform,
      org.apache.hop.core.variables.IVariables variables,
      String reason)
      throws HopException {
    if (transform.getCopies(variables) > 1 || transform.isPartitioned()) {
      throw new HopException(
          "INTERLIS transform <"
              + transform.getName()
              + "> does not support parallel copies because "
              + reason
              + ". Set \"Number of copies\" to 1 and disable partitioning.");
    }
  }
}
