package ch.so.agi.hop.interlis.core.io;

import ch.interlis.ili2c.metamodel.TransferDescription;

/**
 * Streaming reader over an INTERLIS transfer file.
 *
 * <p>Readers are single-use and must be closed. Implementations must not load the whole transfer
 * into memory.
 */
public interface InterlisTransferReader extends AutoCloseable {

  /**
   * Returns the next transfer event or {@code null} at end of transfer.
   *
   * @throws InterlisReadException if the transfer cannot be read
   */
  InterlisObjectEnvelope next() throws InterlisReadException;

  /** The compiled model description used to interpret the transfer, if available. */
  TransferDescription transferDescription();

  @Override
  void close() throws InterlisReadException;
}
