package ch.so.agi.hop.interlis.core.geometry;

/** Signals a failure while converting between INTERLIS and Hop geometry values. */
public class InterlisGeometryException extends Exception {

  public InterlisGeometryException(String message) {
    super(message);
  }

  public InterlisGeometryException(String message, Throwable cause) {
    super(message, cause);
  }
}
