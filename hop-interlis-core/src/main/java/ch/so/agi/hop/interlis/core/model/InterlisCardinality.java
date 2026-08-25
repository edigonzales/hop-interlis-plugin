package ch.so.agi.hop.interlis.core.model;

/**
 * Cardinality of an INTERLIS property.
 *
 * @param min minimum multiplicity
 * @param max maximum multiplicity; {@link #UNBOUNDED} for {@code *}
 */
public record InterlisCardinality(int min, long max) {

  public static final long UNBOUNDED = Long.MAX_VALUE;

  public InterlisCardinality {
    if (min < 0) {
      throw new IllegalArgumentException("min must be >= 0");
    }
    if (max < min) {
      throw new IllegalArgumentException("max must be >= min");
    }
  }

  public boolean isUnbounded() {
    return max == UNBOUNDED;
  }

  public boolean isSingleValued() {
    return max == 1;
  }

  @Override
  public String toString() {
    return min + ".." + (isUnbounded() ? "*" : Long.toString(max));
  }
}
