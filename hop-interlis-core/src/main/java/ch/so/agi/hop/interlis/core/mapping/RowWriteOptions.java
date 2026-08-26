package ch.so.agi.hop.interlis.core.mapping;

/**
 * Options controlling the inverse mapping (typed row values → IOM object).
 *
 * @param strict reject missing mandatory values instead of leaving them undefined
 * @param basketId basket identifier applied when no basket field is projected
 */
public record RowWriteOptions(boolean strict, String basketId) {

  public static RowWriteOptions defaults() {
    return new RowWriteOptions(true, "b1");
  }
}
