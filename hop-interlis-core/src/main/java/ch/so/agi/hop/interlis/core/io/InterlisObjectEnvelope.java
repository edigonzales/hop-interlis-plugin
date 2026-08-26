package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomObject;

/**
 * Canonical INTERLIS envelope: one transfer event plus its object payload and basket context.
 *
 * <p>This is the stable intermediate representation between the transfer I/O layer and the typed
 * row mapping layer. For object events {@link #object()} is set; for transfer/basket events it is
 * {@code null}. Basket metadata (consistency, kind, lifecycle states) is carried on basket and
 * object envelopes so it can survive a lossless roundtrip.
 *
 * @param eventType transfer event kind
 * @param modelName model of the object/topic (derived from the topic name)
 * @param topicName qualified topic name, e.g. {@code Model.Topic}
 * @param basketId basket identifier
 * @param className qualified class name (the XTF object tag)
 * @param objectId object identifier (TID/OID)
 * @param operation transfer operation
 * @param object the INTERLIS object as delivered by the reader
 * @param basket basket metadata of the containing basket, or {@code null}
 */
public record InterlisObjectEnvelope(
    InterlisEventType eventType,
    String modelName,
    String topicName,
    String basketId,
    String className,
    String objectId,
    InterlisObjectOperation operation,
    IomObject object,
    InterlisBasketMetadata basket) {

  /** Backwards-compatible constructor without basket metadata. */
  public InterlisObjectEnvelope(
      InterlisEventType eventType,
      String modelName,
      String topicName,
      String basketId,
      String className,
      String objectId,
      InterlisObjectOperation operation,
      IomObject object) {
    this(eventType, modelName, topicName, basketId, className, objectId, operation, object, null);
  }
}
