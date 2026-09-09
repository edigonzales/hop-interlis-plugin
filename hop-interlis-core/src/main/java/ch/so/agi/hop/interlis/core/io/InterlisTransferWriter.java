package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomObject;

/**
 * Streaming writer over an INTERLIS transfer file.
 *
 * <p>The event order is enforced by the caller: {@code startTransfer → startBasket → (writeObject)*
 * → endBasket → endTransfer}.
 *
 * <p>Writers are single-use; {@link #close()} must be called exactly once.
 */
public interface InterlisTransferWriter extends AutoCloseable {

  /** Writes the transfer header. Must be called exactly once, before any basket. */
  void startTransfer(String sender) throws InterlisWriteException;

  /** Writes preserved header semantics. */
  void startTransfer(InterlisTransferMetadata metadata) throws InterlisWriteException;

  /** Rejects EOF before the transfer has been completed. */
  void requireComplete() throws InterlisWriteException;

  /**
   * Opens a basket.
   *
   * @param topicScopedName qualified topic name, e.g. {@code Model.Topic}
   * @param bid basket identifier
   */
  void startBasket(String topicScopedName, String bid) throws InterlisWriteException;

  /**
   * Opens a basket with transfer metadata (consistency, kind, lifecycle states). Values set to
   * {@code null} are omitted.
   *
   * @param topicScopedName qualified topic name, e.g. {@code Model.Topic}
   * @param bid basket identifier
   * @param metadata basket metadata, may be {@code null}
   */
  default void startBasket(String topicScopedName, String bid, InterlisBasketMetadata metadata)
      throws InterlisWriteException {
    startBasket(topicScopedName, bid);
  }

  /** Writes one transfer object into the current basket. */
  void writeObject(IomObject object) throws InterlisWriteException;

  /** Closes the current basket. */
  void endBasket() throws InterlisWriteException;

  /** Writes the transfer footer. Must be called exactly once, after all baskets. */
  void endTransfer() throws InterlisWriteException;

  @Override
  void close() throws InterlisWriteException;
}
