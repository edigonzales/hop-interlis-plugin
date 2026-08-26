package ch.so.agi.hop.interlis.core.io;

import java.util.List;

/**
 * Central definition of the validation error row layout for Hop streams.
 *
 * <p>Field names and order are defined exactly once here; INTERLIS Validate and the error schema
 * factory use this layout. Values that iox-ili does not provide (column, basket, raw event type)
 * stay {@code null}.
 *
 * <pre>
 * _ili_severity         String
 * _ili_message          String
 * _ili_source_file      String
 * _ili_line             Integer
 * _ili_column           Integer
 * _ili_model            String
 * _ili_topic            String
 * _ili_bid              String
 * _ili_class            String
 * _ili_tid              String
 * _ili_attribute_path   String
 * _ili_constraint_name  String
 * _ili_raw_event_type   String
 * </pre>
 */
public final class InterlisValidationRowLayout {

  public static final String SEVERITY = "_ili_severity";
  public static final String MESSAGE = "_ili_message";
  public static final String SOURCE_FILE = "_ili_source_file";
  public static final String LINE = "_ili_line";
  public static final String COLUMN = "_ili_column";
  public static final String MODEL = "_ili_model";
  public static final String TOPIC = "_ili_topic";
  public static final String BID = "_ili_bid";
  public static final String CLASS = "_ili_class";
  public static final String TID = "_ili_tid";
  public static final String ATTRIBUTE_PATH = "_ili_attribute_path";
  public static final String CONSTRAINT_NAME = "_ili_constraint_name";
  public static final String RAW_EVENT_TYPE = "_ili_raw_event_type";

  public static final List<String> FIELD_NAMES =
      List.of(
          SEVERITY,
          MESSAGE,
          SOURCE_FILE,
          LINE,
          COLUMN,
          MODEL,
          TOPIC,
          BID,
          CLASS,
          TID,
          ATTRIBUTE_PATH,
          CONSTRAINT_NAME,
          RAW_EVENT_TYPE);

  public static final int FIELD_COUNT = FIELD_NAMES.size();

  private InterlisValidationRowLayout() {}

  /** The number of validation fields. */
  public static int fieldCount() {
    return FIELD_COUNT;
  }
}
