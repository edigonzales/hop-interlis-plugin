package ch.so.agi.hop.interlis.core.io;

/** Signals a failure while writing an INTERLIS transfer file. */
public class InterlisWriteException extends Exception {

  public InterlisWriteException(String message) {
    super(message);
  }

  public InterlisWriteException(String message, Throwable cause) {
    super(message, cause);
  }
}
