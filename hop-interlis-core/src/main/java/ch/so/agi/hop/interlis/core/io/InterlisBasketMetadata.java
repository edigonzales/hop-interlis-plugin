package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomConstants;

/**
 * Transfer-relevant basket metadata: consistency, kind and lifecycle states.
 *
 * <p>Values are exposed as readable strings ({@code COMPLETE}, {@code FULL}, ...); {@code null}
 * means "not set in the transfer". The IOM integer codes are mapped centrally here.
 */
public record InterlisBasketMetadata(
    String consistency, String kind, String startState, String endState) {

  public InterlisBasketMetadata {
    consistency = normalize(consistency);
    kind = normalize(kind);
    startState = normalize(startState);
    endState = normalize(endState);
  }

  public static InterlisBasketMetadata fromIom(
      int consistency, int kind, String startState, String endState) {
    return new InterlisBasketMetadata(
        consistencyName(consistency), kindName(kind), startState, endState);
  }

  /** The IOM consistency code (or -1 when unset). */
  public int consistencyIom() {
    return switch (consistency == null ? "" : consistency) {
      case "COMPLETE" -> IomConstants.IOM_COMPLETE;
      case "INCOMPLETE" -> IomConstants.IOM_INCOMPLETE;
      case "INCONSISTENT" -> IomConstants.IOM_INCONSISTENT;
      case "ADAPTED" -> IomConstants.IOM_ADAPTED;
      default -> -1;
    };
  }

  /** The IOM kind code (or -1 when unset). */
  public int kindIom() {
    return switch (kind == null ? "" : kind) {
      case "FULL" -> IomConstants.IOM_FULL;
      case "UPDATE" -> IomConstants.IOM_UPDATE;
      case "INITIAL" -> IomConstants.IOM_INITIAL;
      default -> -1;
    };
  }

  /** {@code true} when no metadata is present. */
  public boolean isEmpty() {
    return consistency == null && kind == null && startState == null && endState == null;
  }

  private static String consistencyName(int consistency) {
    return switch (consistency) {
      case IomConstants.IOM_INCOMPLETE -> "INCOMPLETE";
      case IomConstants.IOM_INCONSISTENT -> "INCONSISTENT";
      case IomConstants.IOM_ADAPTED -> "ADAPTED";
      // IOM_COMPLETE (0) is the default and is not emitted in XTF.
      default -> null;
    };
  }

  private static String kindName(int kind) {
    return switch (kind) {
      case IomConstants.IOM_UPDATE -> "UPDATE";
      case IomConstants.IOM_INITIAL -> "INITIAL";
      // IOM_FULL (0) is the default and is not emitted in XTF.
      default -> null;
    };
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
