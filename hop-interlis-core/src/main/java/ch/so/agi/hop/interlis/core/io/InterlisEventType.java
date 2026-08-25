package ch.so.agi.hop.interlis.core.io;

/** Events of the INTERLIS transfer stream. */
public enum InterlisEventType {
  START_TRANSFER,
  START_BASKET,
  OBJECT,
  DELETE_OBJECT,
  END_BASKET,
  END_TRANSFER
}
