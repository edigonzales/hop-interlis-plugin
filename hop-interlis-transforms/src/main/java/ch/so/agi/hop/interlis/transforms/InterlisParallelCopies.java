package ch.so.agi.hop.interlis.transforms;

import org.apache.hop.core.exception.HopException;

/**
 * Threading policy of the INTERLIS transforms.
 *
 * <p>Transforms with per-row stateless processing (Structure Explode/Collect, Role Join, Object
 * to Row, Row to Object) support parallel copies. Transforms with transfer side effects or
 * duplicate emission semantics (file readers, file writers, validation, enumerations) must run as
 * a single copy: reading the same file from several copies duplicates rows, writing the same file
 * from several copies corrupts the output. These transforms fail fast with an actionable message
 * when configured with parallel copies.
 */
public final class InterlisParallelCopies {

  private InterlisParallelCopies() {}

  /**
   * Fails when the transform runs as copy {@code n} of more than one copy.
   *
   * @param copyNr the copy number of the running transform instance
   * @param transformName the transform name for the error message
   */
  public static void rejectParallelCopies(int copyNr, String transformName)
      throws HopException {
    if (copyNr > 0) {
      throw new HopException(
          "INTERLIS transform <"
              + transformName
              + "> does not support parallel copies: running it with more than one copy would "
              + "duplicate rows or corrupt the target file. Set \"Number of copies\" back to 1.");
    }
  }
}
