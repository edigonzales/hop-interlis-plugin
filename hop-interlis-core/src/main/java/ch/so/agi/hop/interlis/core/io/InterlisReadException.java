package ch.so.agi.hop.interlis.core.io;

/** Signals a failure while reading an INTERLIS transfer file. */
public class InterlisReadException extends Exception {

  public InterlisReadException(String message) {
    super(message);
  }

  public InterlisReadException(String message, Throwable cause) {
    super(message, cause);
  }
}
