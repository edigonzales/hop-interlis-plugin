package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomObject;

/**
 * Canonical INTERLIS envelope: one transfer event plus its object payload.
 *
 * <p>This is the stable intermediate representation between the transfer I/O layer and the typed
 * row mapping layer. For object events {@link #object()} is set; for transfer/basket events it is
 * {@code null}.
 *
 * @param eventType transfer event kind
 * @param modelName model of the object/topic (derived from the topic name)
 * @param topicName qualified topic name, e.g. {@code Model.Topic}
 * @param basketId basket identifier
 * @param className qualified class name (the XTF object tag)
 * @param objectId object identifier (TID/OID)
 * @param operation transfer operation
 * @param object the INTERLIS object as delivered by the reader
 */
public record InterlisObjectEnvelope(
    InterlisEventType eventType,
    String modelName,
    String topicName,
    String basketId,
    String className,
    String objectId,
    InterlisObjectOperation operation,
    IomObject object) {}
