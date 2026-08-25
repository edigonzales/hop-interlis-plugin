package ch.so.agi.hop.interlis.core.model;

/** Signals a failure to compile, load or interpret an INTERLIS model. */
public class InterlisModelException extends Exception {

  public InterlisModelException(String message) {
    super(message);
  }

  public InterlisModelException(String message, Throwable cause) {
    super(message, cause);
  }
}
