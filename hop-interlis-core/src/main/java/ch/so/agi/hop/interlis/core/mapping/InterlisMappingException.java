package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisValueKind;

/** Signals a failure while mapping INTERLIS values to Hop values or back. */
public class InterlisMappingException extends Exception {

  public InterlisMappingException(String message) {
    super(message);
  }

  public InterlisMappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
