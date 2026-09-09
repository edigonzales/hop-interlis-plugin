package ch.so.agi.hop.interlis.core.io;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import java.util.List;

/**
 * Central definition of the canonical envelope row layout for Hop streams.
 *
 * <p>The field names, order and value mapping are defined exactly once here; INTERLIS Transfer
 * Input, INTERLIS Row to Object, INTERLIS Transfer Output and the envelope schema factory all use
 * this layout, so the generic stream schema can never drift.
 *
 * <pre>
 * _ili_event_type            String
 * _ili_model                 String
 * _ili_topic                 String
 * _ili_bid                   String
 * _ili_class                 String
 * _ili_tid                   String
 * _ili_operation             String
 * _ili_object                InterlisObject
 * _ili_line                  Integer
 * _ili_column                Integer
 * _ili_basket_consistency    String
 * _ili_basket_kind           String
 * _ili_basket_start_state    String
 * _ili_basket_end_state      String
 * </pre>
 */
public final class InterlisEnvelopeRowLayout {

  public static final String EVENT_TYPE = "_ili_event_type";
  public static final String MODEL = "_ili_model";
  public static final String TOPIC = "_ili_topic";
  public static final String BID = "_ili_bid";
  public static final String CLASS = "_ili_class";
  public static final String TID = "_ili_tid";
  public static final String OPERATION = "_ili_operation";
  public static final String OBJECT = "_ili_object";
  public static final String LINE = "_ili_line";
  public static final String COLUMN = "_ili_column";
  public static final String BASKET_CONSISTENCY = "_ili_basket_consistency";
  public static final String BASKET_KIND = "_ili_basket_kind";
  public static final String BASKET_START_STATE = "_ili_basket_start_state";
  public static final String BASKET_END_STATE = "_ili_basket_end_state";

  public static final String TRANSFER_METADATA = "_ili_transfer_metadata";

  public static final List<String> FIELD_NAMES =
      List.of(
          EVENT_TYPE,
          MODEL,
          TOPIC,
          BID,
          CLASS,
          TID,
          OPERATION,
          OBJECT,
          LINE,
          COLUMN,
          BASKET_CONSISTENCY,
          BASKET_KIND,
          BASKET_START_STATE,
          BASKET_END_STATE,
          TRANSFER_METADATA);

  /** Indexes in the row value array. */
  public static final int EVENT_TYPE_INDEX = 0;

  public static final int MODEL_INDEX = 1;
  public static final int TOPIC_INDEX = 2;
  public static final int BID_INDEX = 3;
  public static final int CLASS_INDEX = 4;
  public static final int TID_INDEX = 5;
  public static final int OPERATION_INDEX = 6;
  public static final int OBJECT_INDEX = 7;
  public static final int LINE_INDEX = 8;
  public static final int COLUMN_INDEX = 9;
  public static final int BASKET_CONSISTENCY_INDEX = 10;
  public static final int BASKET_KIND_INDEX = 11;
  public static final int BASKET_START_STATE_INDEX = 12;
  public static final int BASKET_END_STATE_INDEX = 13;

  public static final int TRANSFER_METADATA_INDEX = 14;

  public static final int FIELD_COUNT = FIELD_NAMES.size();

  /** The number of envelope fields. */
  public static int fieldCount() {
    return FIELD_COUNT;
  }

  private InterlisEnvelopeRowLayout() {}

  /** Maps an envelope to row values in layout order. */
  public static Object[] toRow(InterlisObjectEnvelope envelope) {
    IomObject object = envelope == null ? null : envelope.object();
    InterlisBasketMetadata basket = envelope == null ? null : envelope.basket();
    return new Object[] {
      envelope == null ? null : envelope.eventType().name(),
      envelope == null ? null : envelope.modelName(),
      envelope == null ? null : envelope.topicName(),
      envelope == null ? null : envelope.basketId(),
      envelope == null ? null : envelope.className(),
      envelope == null ? null : envelope.objectId(),
      envelope == null ? null : envelope.operation().name(),
      object,
      sourceLocation(object, true),
      sourceLocation(object, false),
      basket == null ? null : basket.consistency(),
      basket == null ? null : basket.kind(),
      basket == null ? null : basket.startState(),
      basket == null ? null : basket.endState(),
      envelope == null || envelope.transferMetadata() == null
          ? null
          : envelope.transferMetadata().toJson()
    };
  }

  /** Parses row values back into an envelope. */
  public static InterlisObjectEnvelope fromRow(Object[] values) {
    if (values == null || values.length < 14) {
      throw new IllegalArgumentException(
          "Envelope row must have "
              + FIELD_COUNT
              + " values but has "
              + (values == null ? 0 : values.length));
    }
    InterlisEventType eventType =
        enumValue(
            InterlisEventType.class, string(values[EVENT_TYPE_INDEX]), InterlisEventType.OBJECT);
    InterlisObjectOperation operation =
        enumValue(
            InterlisObjectOperation.class,
            string(values[OPERATION_INDEX]),
            InterlisObjectOperation.NONE);
    Object objectValue = values[OBJECT_INDEX];
    if (objectValue != null && !(objectValue instanceof IomObject))
      throw new IllegalArgumentException("Field _ili_object must contain an INTERLIS object");
    IomObject object = (IomObject) objectValue;
    if (eventType == InterlisEventType.OBJECT && object == null)
      throw new IllegalArgumentException(
          "OBJECT event has no _ili_object (TID "
              + values[TID_INDEX]
              + ", basket "
              + values[BID_INDEX]
              + ")");
    InterlisBasketMetadata basket =
        new InterlisBasketMetadata(
            string(values[BASKET_CONSISTENCY_INDEX]),
            string(values[BASKET_KIND_INDEX]),
            string(values[BASKET_START_STATE_INDEX]),
            string(values[BASKET_END_STATE_INDEX]));
    return new InterlisObjectEnvelope(
        eventType,
        string(values[MODEL_INDEX]),
        string(values[TOPIC_INDEX]),
        string(values[BID_INDEX]),
        string(values[CLASS_INDEX]),
        string(values[TID_INDEX]),
        operation,
        object,
        basket.isEmpty() ? null : basket,
        values.length > TRANSFER_METADATA_INDEX
            ? InterlisTransferMetadata.fromJson(string(values[TRANSFER_METADATA_INDEX]))
            : null);
  }

  /** The event type value of a row, or {@code null} when absent. */
  public static String eventType(Object[] values) {
    return values == null ? null : string(values[EVENT_TYPE_INDEX]);
  }

  /** The basket id of a row, or {@code null} when absent. */
  public static String basketId(Object[] values) {
    return values == null ? null : string(values[BID_INDEX]);
  }

  /** The object id of a row, or {@code null} when absent. */
  public static String objectId(Object[] values) {
    return values == null ? null : string(values[TID_INDEX]);
  }

  /** The class name of a row, or {@code null} when absent. */
  public static String className(Object[] values) {
    return values == null ? null : string(values[CLASS_INDEX]);
  }

  /** The IOM object of a row, or {@code null} when absent. */
  public static IomObject object(Object[] values) {
    if (values == null) {
      return null;
    }
    Object value = values[OBJECT_INDEX];
    return value instanceof IomObject iomObject ? iomObject : null;
  }

  /**
   * Builds an OBJECT envelope row value array for a mapped object.
   *
   * @param object the mapped INTERLIS object
   * @param basketId basket identifier
   * @param topicName qualified topic name
   * @param operation transfer operation
   */
  public static Object[] objectRow(
      IomObject object, String basketId, String topicName, InterlisObjectOperation operation) {
    InterlisObjectEnvelope envelope =
        new InterlisObjectEnvelope(
            InterlisEventType.OBJECT,
            modelNameOf(topicName),
            topicName,
            basketId,
            object == null ? null : object.getobjecttag(),
            object == null ? null : object.getobjectoid(),
            operation == null ? InterlisObjectOperation.NONE : operation,
            object,
            null);
    return toRow(envelope);
  }

  /** Copies a raw IOM object into a serializable {@link Iom_jObject}. */
  public static IomObject serializableCopy(IomObject object) {
    return object instanceof Iom_jObject ? object : new Iom_jObject(object);
  }

  private static String modelNameOf(String topicScopedName) {
    if (topicScopedName == null) {
      return null;
    }
    int separator = topicScopedName.indexOf('.');
    return separator < 0 ? topicScopedName : topicScopedName.substring(0, separator);
  }

  private static Long sourceLocation(IomObject object, boolean line) {
    if (object == null) {
      return null;
    }
    long value = line ? object.getobjectline() : object.getobjectcol();
    return value <= 0 ? null : value;
  }

  private static String string(Object value) {
    return value == null ? null : value.toString();
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
    if (name == null || name.isBlank()) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid "
              + (type == InterlisEventType.class ? EVENT_TYPE : OPERATION)
              + " value <"
              + name
              + ">",
          e);
    }
  }
}
