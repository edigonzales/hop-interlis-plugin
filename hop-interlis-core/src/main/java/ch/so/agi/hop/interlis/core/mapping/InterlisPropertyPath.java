package ch.so.agi.hop.interlis.core.mapping;

import java.util.List;

/**
 * Path of a projected field inside the IOM object tree.
 *
 * <p>{@code segments} are single-structure attribute names leading from the object root to the
 * owning structure; {@code leafName} is the attribute or role name whose value is read. For
 * top-level properties {@code segments} is empty.
 *
 * @param segments structure attribute names (empty for top-level properties)
 * @param leafName attribute or role name holding the value
 */
public record InterlisPropertyPath(List<String> segments, String leafName) {

  public static InterlisPropertyPath root(String leafName) {
    return new InterlisPropertyPath(List.of(), leafName);
  }

  public InterlisPropertyPath {
    segments = segments == null ? List.of() : List.copyOf(segments);
  }

  /** Dotted path string, e.g. {@code Address.Street} or {@code Name}. */
  public String dotted() {
    if (segments.isEmpty()) {
      return leafName;
    }
    return String.join(".", segments) + "." + leafName;
  }
}
