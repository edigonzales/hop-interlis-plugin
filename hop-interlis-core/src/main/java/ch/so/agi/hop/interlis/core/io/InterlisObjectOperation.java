package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomConstants;

/** Transfer operation of an INTERLIS object. */
public enum InterlisObjectOperation {
  INSERT,
  UPDATE,
  DELETE,
  NONE;

  /** Maps the IOM operation code to the envelope operation. */
  public static InterlisObjectOperation fromIom(int iomOperation) {
    return switch (iomOperation) {
      case IomConstants.IOM_OP_INSERT -> INSERT;
      case IomConstants.IOM_OP_UPDATE -> UPDATE;
      case IomConstants.IOM_OP_DELETE -> DELETE;
      default -> NONE;
    };
  }

  /** The IOM operation code used by iox-ili. */
  public int toIom() {
    return switch (this) {
      case INSERT -> IomConstants.IOM_OP_INSERT;
      case UPDATE -> IomConstants.IOM_OP_UPDATE;
      case DELETE -> IomConstants.IOM_OP_DELETE;
      case NONE -> IomConstants.IOM_OP_INSERT;
    };
  }
}
